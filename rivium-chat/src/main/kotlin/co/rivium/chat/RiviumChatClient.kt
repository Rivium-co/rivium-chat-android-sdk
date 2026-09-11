package co.rivium.chat

import co.rivium.chat.events.*
import co.rivium.chat.models.*
import co.rivium.chat.services.ApiService
import co.rivium.chat.services.RealtimeService
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking

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
class RiviumChatClient(val config: RiviumChatConfig) {

    private val _authErrors = MutableSharedFlow<AuthErrorEvent>(extraBufferCapacity = 10)

    /**
     * Identity errors a token refresh cannot fix (revoked or invalid token,
     * project requires a token, tokenProvider failing). Send the user to
     * login. Only emitted when [RiviumChatConfig.tokenProvider] is set.
     */
    val onAuthError: SharedFlow<AuthErrorEvent> = _authErrors.asSharedFlow()

    private val apiService = ApiService(config) { _authErrors.tryEmit(it) }
    private val realtimeService = RealtimeService(
        config,
        {
            // With a tokenProvider the same user token authenticates REST and
            // the realtime connection; centrifuge asks again before it expires.
            runBlocking { apiService.userTokenOrNull() }
                ?: apiService.getCentrifugoToken(config.userId, info = config.userInfo?.mapValues { it.value as Any })
        }
    )

    private var isDisposed = false

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

    /** Gets currently online users in a room. */
    fun getRoomPresence(roomId: String): Set<String> {
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

    /** Send a message to a room. */
    suspend fun sendMessage(
        roomId: String,
        content: String,
        type: MessageType = MessageType.TEXT,
        attachments: List<Attachment>? = null,
        metadata: Map<String, Any>? = null,
        replyToId: String? = null
    ): Message {
        checkDisposed()
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
        realtimeService.dispose()
        apiService.clearUserToken()
        apiService.dispose()
    }
}
