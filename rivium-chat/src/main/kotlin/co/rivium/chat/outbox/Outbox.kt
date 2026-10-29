package co.rivium.chat.outbox

import co.rivium.chat.events.MessageFailedEvent
import co.rivium.chat.events.MessageSentEvent
import co.rivium.chat.models.Message
import co.rivium.chat.services.RiviumChatException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/**
 * Keeps messages written without network and sends them when the
 * connection is back: one at a time, in the order written, never twice
 * (every message carries its clientMessageId, so a resend whose first answer
 * was lost is stored once by the server).
 */
internal class Outbox(
    private val store: OutboxStore,
    /** Items another user wrote on this device are dropped, never sent. */
    private val userId: String,
    private val scope: CoroutineScope,
    private val sender: suspend (PendingMessage) -> Message,
    private val onSent: (MessageSentEvent) -> Unit,
    private val onFailed: (MessageFailedEvent) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val lock = Any()
    private var items: MutableList<PendingMessage>? = null

    /** One flush at a time: racing triggers wait, then find the sent items gone. */
    private val flushMutex = Mutex()

    @Volatile
    private var retryJob: Job? = null

    @Volatile
    private var backoffMs = 0L

    @Volatile
    private var disposed = false

    /** The current back-off delay (0 = none scheduled since the last success). */
    internal val currentBackoffMs: Long get() = backoffMs

    private fun list(): MutableList<PendingMessage> {
        items?.let { return it }
        val loaded = try {
            store.load().filter { it.senderUserId == userId }.toMutableList()
        } catch (e: Exception) {
            mutableListOf()
        }
        items = loaded
        return loaded
    }

    private fun persist(snapshot: List<PendingMessage>) {
        try {
            store.save(snapshot)
        } catch (e: Exception) {
            // Best effort: the queue keeps working in memory.
        }
    }

    private fun enqueue(item: PendingMessage) {
        val snapshot = synchronized(lock) {
            val l = list()
            l += item
            l.toList()
        }
        persist(snapshot)
    }

    private fun remove(localId: String): Boolean {
        val snapshot = synchronized(lock) {
            val l = list()
            val removed = l.removeAll { it.localId == localId }
            if (!removed) return false
            l.toList()
        }
        persist(snapshot)
        return true
    }

    private fun hasPendingFor(roomId: String): Boolean =
        synchronized(lock) { list().any { it.roomId == roomId } }

    /**
     * Sends [item] now, or queues it when the failure is transient (or when
     * older messages of the same room still wait, so the order is kept).
     * Permanent failures are thrown.
     */
    suspend fun send(item: PendingMessage): Message {
        if (hasPendingFor(item.roomId)) {
            enqueue(item)
            scope.launch { flush() }
            return item.toMessage()
        }
        return try {
            sender(item)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (!isTransient(e)) throw e
            enqueue(item)
            scheduleRetry()
            item.toMessage()
        }
    }

    /** The realtime connection is up: send what waits. */
    fun onConnected() {
        if (disposed) return
        scope.launch { flush() }
    }

    /** Sends what waits now and returns when done (or stopped by a transient failure). */
    suspend fun flush() {
        if (disposed) return
        flushMutex.withLock { flushOnce() }
    }

    private suspend fun flushOnce() {
        while (!disposed) {
            val next = synchronized(lock) { list().firstOrNull() } ?: return

            if (clock() - next.createdAt > MAX_AGE_MS) {
                if (remove(next.localId)) {
                    onFailed(
                        MessageFailedEvent(
                            localId = next.localId,
                            clientMessageId = next.clientMessageId,
                            roomId = next.roomId,
                            error = "The message waited more than 24 hours without network",
                            reason = MessageFailedEvent.REASON_EXPIRED
                        )
                    )
                }
                continue
            }

            val message = try {
                sender(next)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (isTransient(e)) {
                    scheduleRetry()
                    return
                }
                if (remove(next.localId)) {
                    onFailed(
                        MessageFailedEvent(
                            localId = next.localId,
                            clientMessageId = next.clientMessageId,
                            roomId = next.roomId,
                            error = e.message ?: e.toString(),
                            reason = MessageFailedEvent.REASON_REJECTED
                        )
                    )
                }
                continue
            }

            backoffMs = 0
            // Cancelled while it was being sent: stored anyway, but no messageSent.
            if (remove(next.localId)) {
                onSent(MessageSentEvent(next.localId, next.clientMessageId, message))
            }
        }
    }

    private fun scheduleRetry() {
        if (disposed) return
        backoffMs = if (backoffMs == 0L) INITIAL_BACKOFF_MS else minOf(backoffMs * 2, MAX_BACKOFF_MS)
        val wait = backoffMs
        retryJob?.cancel()
        retryJob = scope.launch {
            delay(wait)
            retryJob = null
            flush()
        }
    }

    fun pending(roomId: String?): List<PendingMessage> = synchronized(lock) {
        list().filter { roomId == null || it.roomId == roomId }
    }

    fun cancel(localId: String): Boolean = remove(localId)

    fun clear() {
        synchronized(lock) { items = mutableListOf() }
        persist(emptyList())
    }

    /** Forgets everything in memory; a persistent store keeps its copy. */
    fun dispose() {
        disposed = true
        retryJob?.cancel()
        retryJob = null
        synchronized(lock) { items = mutableListOf() }
    }

    companion object {
        const val INITIAL_BACKOFF_MS = 2_000L
        const val MAX_BACKOFF_MS = 60_000L
        const val MAX_AGE_MS = 24L * 60 * 60 * 1000

        /**
         * Transient: no answer (no network, timeout, connection lost, a
         * failing tokenProvider), a 5xx, 408 or 429. Every other 4xx (400,
         * 401, 403, 404, 413 …) is permanent.
         */
        fun isTransient(e: Throwable): Boolean = when (e) {
            is RiviumChatException -> e.statusCode?.let { it >= 500 || it == 408 || it == 429 }
                ?: (e.cause?.let { isTransient(it) } ?: false)
            is IOException -> true
            else -> false
        }
    }
}
