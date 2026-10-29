package co.rivium.chat

import co.rivium.chat.events.MessageFailedEvent
import co.rivium.chat.events.MessageSentEvent
import co.rivium.chat.models.Message
import co.rivium.chat.outbox.MemoryOutboxStore
import co.rivium.chat.outbox.Outbox
import co.rivium.chat.outbox.PendingMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** Back-off, triggers and single-flight of the outbox, in virtual time. */
@OptIn(ExperimentalCoroutinesApi::class)
class OutboxTimingTest {

    private var online = false
    private val attempts = mutableListOf<Pair<Long, String>>()
    private val sent = mutableListOf<MessageSentEvent>()
    private val failed = mutableListOf<MessageFailedEvent>()

    private fun TestScope.outbox() = Outbox(
        store = MemoryOutboxStore(),
        userId = "user-42",
        scope = backgroundScope,
        sender = { p ->
            attempts += testScheduler.currentTime to p.content
            delay(100) // the request takes a while: lets triggers race
            if (!online) throw IOException("no network")
            Message(id = "m-${attempts.size}", roomId = p.roomId, senderUserId = p.senderUserId,
                content = p.content, createdAt = "2026-10-29T10:00:00Z", clientMessageId = p.clientMessageId)
        },
        onSent = { sent += it },
        onFailed = { failed += it },
        clock = { 1_800_000_000_000L + testScheduler.currentTime }
    )

    private fun TestScope.item(content: String, room: String = "room-1") = PendingMessage(
        localId = "local_$content", clientMessageId = content, roomId = room,
        senderUserId = "user-42", content = content, createdAt = 1_800_000_000_000L + testScheduler.currentTime
    )

    @Test
    fun `back-off doubles from 2 s to at most 60 s`() = runTest {
        val box = outbox()
        box.send(item("a")) // first try fails at t=0
        advanceTimeBy(5 * 60_000L)
        runCurrent()
        val starts = attempts.map { it.first }
        // each retry starts after the previous attempt (100 ms) plus the back-off
        val gaps = starts.zipWithNext { x, y -> y - x - 100 }
        assertEquals(listOf(2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 60_000L, 60_000L), gaps.take(7))
    }

    @Test
    fun `back-off resets after a success and a connect sends at once`() = runTest {
        val box = outbox()
        box.send(item("a"))
        advanceTimeBy(2_000 + 100 + 4_000 + 100 + 1) // three failed tries, next back-off 8 s
        assertEquals(8_000L, box.currentBackoffMs)
        online = true
        val before = attempts.size
        box.onConnected()
        runCurrent()
        advanceTimeBy(101)
        assertEquals(before + 1, attempts.size)
        assertEquals(listOf("a"), sent.map { it.message.content })
        assertEquals(0L, box.currentBackoffMs)
        advanceTimeBy(120_000)
        assertEquals(before + 1, attempts.size) // the pending back-off timer finds nothing left
    }

    @Test
    fun `racing connect events never send a message twice`() = runTest {
        val box = outbox()
        box.send(item("a"))
        box.send(item("b"))
        online = true
        repeat(5) { box.onConnected() }
        advanceTimeBy(120_000) // background work only runs when time is advanced
        assertEquals(listOf("a", "b"), sent.map { it.message.content })
        // a failed once (offline), b queued behind it without a try; then one try each
        assertEquals(listOf("a", "a", "b"), attempts.map { it.second })
    }

    @Test
    fun `cancel during an in-flight send cannot recall it and emits no messageSent`() = runTest {
        val box = outbox()
        box.send(item("a"))
        online = true
        box.onConnected()
        runCurrent() // the resend has started (it takes 100 ms)
        assertTrue(box.cancel("local_a"))
        advanceTimeBy(1_000)
        assertEquals(listOf("a", "a"), attempts.map { it.second })
        assertTrue(sent.isEmpty())
        assertTrue(box.pending(null).isEmpty())
    }

    @Test
    fun `a message older than 24 hours is dropped as expired, not sent`() = runTest {
        val box = outbox()
        box.send(item("old"))
        advanceTimeBy(24L * 60 * 60 * 1000 + 1)
        val tries = attempts.size
        online = true
        box.flush()
        assertEquals(tries, attempts.size)
        assertEquals("expired", failed.single().reason)
        assertTrue(box.pending(null).isEmpty())
    }
}
