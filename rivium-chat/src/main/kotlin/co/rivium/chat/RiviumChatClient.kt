package co.rivium.chat

import co.rivium.chat.events.*
import co.rivium.chat.models.*
import co.rivium.chat.outbox.MemoryOutboxStore
import co.rivium.chat.outbox.Outbox
import co.rivium.chat.outbox.PendingMessage
import co.rivium.chat.outbox.SharedPreferencesOutboxStore
import co.rivium.chat.services.ApiService
import co.rivium.chat.services.RealtimeService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import java.util.UUID

/**
 * Main entry point for the RiviumChat SDK.
 *
 * Usage:
 * ```kotlin
 * val config = RiviumChatConfig(
 *     apiKey = "your-api-key",
 *     userId = "user-123",
 *     userInfo = mapOf("displayName" to "John Doe")
 * )
 *
 * val client = RiviumChatClient(config)
 * client.connect()
 *
 * // Listen for messages
 * client.onMessage.collect { message ->
 *     println("New message: ${message.content}")
 * }
 *
 * // Send a message
 * val message = client.sendMessage(roomId, content = "Hello!")
 * ```
 */
class RiviumChatClient internal constructor(
    val config: RiviumChatConfig,
    /** Answers requests instead of the network; tests only. */
    transport: Interceptor?,
    /** Where the outbox runs its timers; tests pass a virtual-time scope. */
    outboxScope: CoroutineScope?,
    private val clock: () -> Long
) {

    constructor(config: RiviumChatConfig) : this(config, null, null, System::currentTimeMillis)

    private val _authErrors = MutableSharedFlow<AuthErrorEvent>(extraBufferCapacity = 10)

    /**
     * Identity errors a token refresh cannot fix (revoked or invalid token,
     * project requires a token, tokenProvider failing). Send the user to
     * login. Only emitted when [RiviumChatConfig.tokenProvider] is set.
     */
    val onAuthError: SharedFlow<AuthErrorEvent> = _authErrors.asSharedFlow()

    private val apiService = ApiService(config, { _authErrors.tryEmit(it) }, transport)
    private val realtimeService = RealtimeService(
        config,
        {
            // With a tokenProvider the same user token authenticates REST and
            // the realtime connection; the realtime client asks again before it expires.
            runBlocking { apiService.userTokenOrNull() }
                ?: apiService.getRealtimeToken(config.userId, info = config.userInfo?.mapValues { it.value as Any })
        }
    )

    private var isDisposed = false

    private val _messageSent = MutableSharedFlow<MessageSentEvent>(extraBufferCapacity = 100)
    private val _messageFailed = MutableSharedFlow<MessageFailedEvent>(extraBufferCapacity = 100)

    /**
     * A message queued while offline was stored by the server. Only with
     * [RiviumChatConfig.queueOfflineMessages].
     */
    val onMessageSent: SharedFlow<MessageSentEvent> = _messageSent.asSharedFlow()

    /**
     * A message queued while offline left the queue without being stored
     * (`reason` "expired" or "rejected"). Only with
     * [RiviumChatConfig.queueOfflineMessages].
     */
    val onMessageFailed: SharedFlow<MessageFailedEvent> = _messageFailed.asSharedFlow()

    private val ownsOutboxScope = outboxScope == null
    private val outboxScope: CoroutineScope = outboxScope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val outbox: Outbox? = if (!config.queueOfflineMessages) null else Outbox(
        store = config.outboxStore
            ?: config.context?.let {
                SharedPreferencesOutboxStore(it, SharedPreferencesOutboxStore.keyFor(config.apiKey, config.userId))
            }
            ?: MemoryOutboxStore(),
        userId = config.userId,
        scope = this.outboxScope,
        sender = { p ->
            apiService.sendMessage(
                p.roomId,
                senderUserId = p.senderUserId,
                content = p.content,
                type = p.type,
                attachments = p.attachments,
                metadata = p.metadata,
                replyToId = p.replyToId,
                clientMessageId = p.clientMessageId
            )
        },
        onSent = { _messageSent.tryEmit(it) },
        onFailed = { _messageFailed.tryEmit(it) },
        clock = clock
    )

    init {
        if (outbox != null) {
            this.outboxScope.launch {
                realtimeService.connectionState.collect { state ->
                    if (state == ConnectionState.CONNECTED) outbox.onConnected()
                }
            }
        }
    }

    private fun checkDisposed() {
        if (isDisposed) throw IllegalStateException("RiviumChatClient has been disposed")
    }

    // ─── Connection ──────────────────────────────────────────────────────

    /** Current connection state. */
    val connectionState: ConnectionState
        get() = realtimeService.currentConnectionState

    /** Stream of connection state changes. */
    val onConnectionStateChange: SharedFlow<ConnectionState>
        get() = realtimeService.connectionState

    /** Whether the client is connected. */
    val isConnected: Boolean
        get() = realtimeService.currentConnectionState == ConnectionState.CONNECTED

    /** Connect to the RiviumChat realtime server. */
    fun connect() {
        checkDisposed()
        realtimeService.connect()
    }

    /** Disconnect from the RiviumChat realtime server. */
    fun disconnect() {
        checkDisposed()
        realtimeService.disconnect()
    }

    // ─── Event Streams ───────────────────────────────────────────────────

    /** Stream of incoming messages. */
    val onMessage: SharedFlow<Message>
        get() = realtimeService.messages

    /** Stream of read receipts. */
    val onReadReceipt: SharedFlow<ReadReceipt>
        get() = realtimeService.readReceipts

    /** Stream of message deletions. */
    val onMessageDeleted: SharedFlow<MessageDeletion>
        get() = realtimeService.messageDeletions

    /** Stream of typing events. */
    val onTypingEvent: SharedFlow<TypingEvent>
        get() = realtimeService.typing

    /** Stream of presence changes. */
    val onPresenceChange: SharedFlow<PresenceEvent>
        get() = realtimeService.presence

    /** Stream of reaction events. */
    val onReactionEvent: SharedFlow<ReactionEvent>
        get() = realtimeService.reactions

    /** Stream of message edits. */
    val onMessageEdited: SharedFlow<MessageEditEvent>
        get() = realtimeService.messageEdits

    /** Stream of pin/unpin events. */
    val onMessagePinChanged: SharedFlow<MessagePinEvent>
        get() = realtimeService.messagePins

    /** Stream of connection errors. */
    val onConnectionError: SharedFlow<ConnectionErrorEvent>
        get() = realtimeService.connectionError

    /** Stream of subscription state changes. */
    val onSubscriptionState: SharedFlow<SubscriptionStateEvent>
        get() = realtimeService.subscriptionState

    /** Stream of recovery failures (room IDs that need full message refresh). */
    val onRecoveryFailed: SharedFlow<String>
        get() = realtimeService.recoveryFailed

    // ─── Room Subscriptions ──────────────────────────────────────────────

    /** Subscribe to realtime events for a room. */
    fun subscribeRoom(roomId: String) {
        checkDisposed()
        realtimeService.subscribeToRoom(roomId)
    }

    /** Subscribe only to a room's chat channel for messages/read receipts, without joining presence or typing. */
    fun observeRoom(roomId: String) {
        checkDisposed()
        realtimeService.observeRoom(roomId)
    }

    /** Unsubscribe from a room's realtime events. */
    fun unsubscribeRoom(roomId: String) {
        checkDisposed()
        realtimeService.unsubscribeFromRoom(roomId)
    }

    /** Leave presence and typing channels but keep chat channel for unread updates. */
    fun leaveRoom(roomId: String) {
        checkDisposed()
        realtimeService.leaveRoom(roomId)
    }

    /**
     * Gets currently online users in a room.
     *
     * Suspends until the server answers (at most 5 seconds, then an empty
     * set) instead of blocking the calling thread.
     */
    suspend fun getRoomPresence(roomId: String): Set<String> {
        checkDisposed()
        return realtimeService.getRoomPresence(roomId)
    }

    /** Publishes a typing indicator. */
    fun publishTyping(roomId: String) {
        checkDisposed()
        realtimeService.publishTyping(roomId)
    }

    // ─── Room Operations ─────────────────────────────────────────────────

    /** Create a new room. */
    suspend fun createRoom(
        type: RoomType = RoomType.DIRECT,
        name: String? = null,
        participants: List<Map<String, Any>>,
        metadata: Map<String, Any>? = null
    ): Room {
        checkDisposed()
        return apiService.createRoom(type, name, participants, metadata)
    }

    /** Find or create a room by external ID. */
    suspend fun findOrCreateRoom(
        externalId: String,
        type: RoomType = RoomType.DIRECT,
        name: String? = null,
        participants: List<Map<String, Any>>,
        metadata: Map<String, Any>? = null
    ): Room {
        checkDisposed()
        return apiService.findOrCreateRoom(externalId, type, name, participants, metadata)
    }

    /** Get a room by its external ID. */
    suspend fun getRoomByExternalId(externalId: String): Room {
        checkDisposed()
        return apiService.getRoomByExternalId(externalId)
    }

    /** List all rooms for the current user. */
    suspend fun listRooms(): List<Room> {
        checkDisposed()
        return apiService.listRooms(config.userId)
    }

    /** Get a room by ID. */
    suspend fun getRoom(roomId: String): Room {
        checkDisposed()
        return apiService.getRoom(roomId)
    }

    /** Add a participant to a room. */
    suspend fun addParticipant(
        roomId: String,
        externalUserId: String,
        displayName: String? = null,
        locale: String? = null,
        role: ParticipantRole = ParticipantRole.MEMBER
    ): Participant {
        checkDisposed()
        return apiService.addParticipant(roomId, externalUserId, displayName, locale, role)
    }

    // ─── Message Operations ──────────────────────────────────────────────

    /**
     * Send a message to a room.
     *
     * With [RiviumChatConfig.queueOfflineMessages] on, a send that fails
     * because of the network is queued and returned as a pending message
     * (`isPending = true`, id `local_<clientMessageId>`) instead of throwing;
     * it is sent when the connection is back. Errors the server answers
     * (not a member, invalid message, plan limit) still throw.
     */
    suspend fun sendMessage(
        roomId: String,
        content: String,
        type: MessageType = MessageType.TEXT,
        attachments: List<Attachment>? = null,
        metadata: Map<String, Any>? = null,
        replyToId: String? = null
    ): Message {
        checkDisposed()
        outbox?.let { queue ->
            val clientMessageId = UUID.randomUUID().toString()
            return queue.send(
                PendingMessage(
                    localId = PendingMessage.localIdFor(clientMessageId),
                    clientMessageId = clientMessageId,
                    roomId = roomId,
                    senderUserId = config.userId,
                    content = content,
                    type = type,
                    attachments = attachments,
                    metadata = metadata,
                    replyToId = replyToId,
                    createdAt = clock()
                )
            )
        }
        return apiService.sendMessage(
            roomId,
            senderUserId = config.userId,
            content = content,
            type = type,
            attachments = attachments,
            metadata = metadata,
            replyToId = replyToId
        )
    }

    // ─── Offline outbox (queueOfflineMessages) ───────────────────────────

    /**
     * Messages waiting to be sent, oldest first, as pending messages
     * (`isPending = true`). Only [roomId]'s when given. Empty when
     * [RiviumChatConfig.queueOfflineMessages] is off.
     */
    fun getPendingMessages(roomId: String? = null): List<Message> =
        outbox?.pending(roomId)?.map { it.toMessage() } ?: emptyList()

    /** Tries to send the waiting messages now; returns when done or stopped by the network. */
    suspend fun retryPendingMessages() {
        checkDisposed()
        outbox?.flush()
    }

    /**
     * Removes a waiting message ([localId] = the pending message's id) from
     * the queue. Emits nothing. Returns false when it is not queued (any more).
     */
    fun cancelPendingMessage(localId: String): Boolean = outbox?.cancel(localId) ?: false

    /**
     * Empties the queue, including a persistent store. Call it on sign-out
     * when another user may sign in on this device.
     */
    fun clearPendingMessages() {
        outbox?.clear()
    }

    /** Get messages for a room with pagination. */
    suspend fun getMessages(
        roomId: String,
        limit: Int = 50,
        before: String? = null
    ): PaginatedMessages {
        checkDisposed()
        return apiService.getMessages(
            roomId,
            userId = config.userId,
            limit = limit,
            before = before
        )
    }

    /** Mark messages in a room as read. */
    suspend fun markAsRead(roomId: String) {
        checkDisposed()
        apiService.markAsRead(roomId, config.userId)
    }

    /** Delete a message. */
    suspend fun deleteMessage(messageId: String) {
        checkDisposed()
        apiService.deleteMessage(messageId, config.userId)
    }

    /** Edit a message. */
    suspend fun editMessage(messageId: String, content: String): Message {
        checkDisposed()
        return apiService.editMessage(messageId, userId = config.userId, content = content)
    }

    /** Search messages in a room. */
    suspend fun searchMessages(
        roomId: String,
        query: String,
        limit: Int = 20,
        offset: Int = 0
    ): List<Message> {
        checkDisposed()
        return apiService.searchMessages(roomId, userId = config.userId, query = query, limit = limit, offset = offset)
    }

    /**
     * Searches messages in a room and reports how many match in total.
     *
     * Same search as [searchMessages]; the result also carries `total` (all
     * matches, not only this page) and `hasMore`.
     *
     * ```kotlin
     * val result = client.searchMessagesWithTotal(roomId, query = "Order #1001")
     * println("${result.total} matches")
     * ```
     */
    suspend fun searchMessagesWithTotal(
        roomId: String,
        query: String,
        limit: Int = 20,
        offset: Int = 0
    ): SearchResult {
        checkDisposed()
        return apiService.searchMessagesWithTotal(roomId, userId = config.userId, query = query, limit = limit, offset = offset)
    }

    // ─── Reaction Operations ─────────────────────────────────────────────

    /** Add a reaction to a message. */
    suspend fun addReaction(messageId: String, emoji: String): Reaction {
        checkDisposed()
        return apiService.addReaction(messageId, userId = config.userId, emoji = emoji)
    }

    /** Remove a reaction from a message. */
    suspend fun removeReaction(messageId: String, emoji: String) {
        checkDisposed()
        apiService.removeReaction(messageId, userId = config.userId, emoji = emoji)
    }

    /** Get all reactions for a message. */
    suspend fun getReactions(messageId: String): List<Reaction> {
        checkDisposed()
        return apiService.getReactions(messageId)
    }

    // ─── Pin Operations ─────────────────────────────────────────────────

    /** Pin a message. */
    suspend fun pinMessage(messageId: String): Message {
        checkDisposed()
        return apiService.pinMessage(messageId, userId = config.userId)
    }

    /** Unpin a message. */
    suspend fun unpinMessage(messageId: String) {
        checkDisposed()
        apiService.unpinMessage(messageId, userId = config.userId)
    }

    /** Get pinned messages in a room. */
    suspend fun getPinnedMessages(roomId: String): List<Message> {
        checkDisposed()
        return apiService.getPinnedMessages(roomId)
    }

    // ─── Other Operations ────────────────────────────────────────────────

    /** Get unread message summary for the current user. */
    suspend fun getUnreadSummary(): UnreadSummary {
        checkDisposed()
        return apiService.getUnreadSummary(config.userId)
    }

    /** Get messages where the current user was mentioned. */
    suspend fun getMentions(
        roomId: String,
        limit: Int = 20,
        offset: Int = 0
    ): List<Message> {
        checkDisposed()
        return apiService.getMentions(roomId, userId = config.userId, limit = limit, offset = offset)
    }

    // ─── Lifecycle ───────────────────────────────────────────────────────

    /** Disposes all resources. */
    fun dispose() {
        if (isDisposed) return
        isDisposed = true
        outbox?.dispose()
        if (ownsOutboxScope) outboxScope.cancel()
        realtimeService.dispose()
        apiService.clearUserToken()
        apiService.dispose()
    }
}
