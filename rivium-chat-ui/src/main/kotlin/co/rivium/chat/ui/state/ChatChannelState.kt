package co.rivium.chat.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import co.rivium.chat.RiviumChatClient
import co.rivium.chat.models.Attachment
import co.rivium.chat.models.Message
import co.rivium.chat.models.MessageType
import co.rivium.chat.models.Reaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

/**
 * State holder for a chat channel/room.
 * Manages messages, typing indicators, presence, and pagination.
 */
@Stable
class ChatChannelState(
    private val client: RiviumChatClient,
    val roomId: String,
    private val currentUserId: String,
    private val scope: CoroutineScope
) {
    // Messages list (newest first for reversed LazyColumn)
    private val _messages = mutableStateListOf<Message>()
    val messages: SnapshotStateList<Message> = _messages

    // Pagination state
    var hasMore by mutableStateOf(true)
        private set
    var isLoadingMore by mutableStateOf(false)
        private set
    var isInitialLoading by mutableStateOf(true)
        private set

    // Typing users (excluding current user)
    private val _typingUsers = mutableStateListOf<String>()
    val typingUsers: SnapshotStateList<String> = _typingUsers

    // Online users in the room
    private val _onlineUsers = mutableStateListOf<String>()
    val onlineUsers: SnapshotStateList<String> = _onlineUsers

    // Other user's last read timestamp (for read receipts)
    var otherUserLastRead: Instant? by mutableStateOf(null)
        private set

    // Reply state
    var replyingTo: Message? by mutableStateOf(null)
        private set

    // Error state
    var error: Throwable? by mutableStateOf(null)
        private set

    val hasError: Boolean get() = error != null

    // Internal state
    private var lastTypingTime: Long = 0
    private val typingTimers = mutableMapOf<String, Job>()
    private var collectionJobs: List<Job> = emptyList()

    /**
     * Initialize the channel state by subscribing and loading messages.
     */
    suspend fun initialize() {
        try {
            // Subscribe to room events
            client.subscribeRoom(roomId)

            // Load initial messages (API returns oldest first, we need newest first)
            val result = client.getMessages(roomId, limit = 50)
            _messages.clear()
            _messages.addAll(result.messages.reversed()) // Newest first
            hasMore = result.hasMore

            // Get initial presence
            try {
                val presence = client.getRoomPresence(roomId)
                _onlineUsers.clear()
                _onlineUsers.addAll(presence)
            } catch (e: Exception) {
                // Presence fetch failed, continue anyway
            }

            // Load initial read state from room participants
            try {
                val room = client.getRoom(roomId)
                val otherParticipant = room.participants.firstOrNull {
                    it.externalUserId != currentUserId
                }
                otherParticipant?.lastReadAt?.let { lastRead ->
                    otherUserLastRead = Instant.parse(lastRead)
                }
            } catch (e: Exception) {
                // Non-critical: read receipts will still work via real-time events
            }

            isInitialLoading = false
            error = null

            // Mark messages as read
            markAsReadSilently()

            // Start collecting events
            startEventCollection()

        } catch (e: Exception) {
            error = e
            isInitialLoading = false
        }
    }

    /**
     * Refreshes all messages.
     */
    suspend fun refresh() {
        isInitialLoading = true
        error = null

        try {
            val result = client.getMessages(roomId)
            _messages.clear()
            _messages.addAll(result.messages.reversed()) // Newest first
            hasMore = result.hasMore
        } catch (e: Exception) {
            error = e
        } finally {
            isInitialLoading = false
        }
    }

    /**
     * Load more (older) messages for pagination.
     */
    suspend fun loadMore() {
        if (isLoadingMore || !hasMore || _messages.isEmpty()) return

        isLoadingMore = true
        try {
            val oldestMessage = _messages.lastOrNull()
            val result = client.getMessages(
                roomId = roomId,
                limit = 50,
                before = oldestMessage?.id
            )
            _messages.addAll(result.messages.reversed())
            hasMore = result.hasMore
        } catch (e: Exception) {
            error = e
        } finally {
            isLoadingMore = false
        }
    }

    /**
     * Send a new message.
     */
    suspend fun sendMessage(
        content: String,
        type: MessageType = MessageType.TEXT,
        attachments: List<Attachment>? = null,
        metadata: Map<String, Any>? = null,
        replyToId: String? = null
    ) {
        // Create pending message for optimistic update
        val pendingId = "pending_${UUID.randomUUID()}"
        val effectiveReplyToId = replyToId ?: replyingTo?.id
        val pendingMessage = Message(
            id = pendingId,
            roomId = roomId,
            senderUserId = currentUserId,
            content = content,
            type = type,
            attachments = attachments ?: emptyList(),
            metadata = null,
            replyToId = effectiveReplyToId,
            replyTo = replyingTo,
            isDeleted = false,
            createdAt = Instant.now().toString(),
            isEdited = false,
            editedAt = null,
            editHistory = null,
            isPinned = false,
            pinnedAt = null,
            pinnedBy = null,
            reactions = emptyList(),
            isPending = true,
            isFailed = false
        )

        // Add to top of list (newest first)
        _messages.add(0, pendingMessage)

        // Clear reply state
        replyingTo = null

        try {
            val sentMessage = client.sendMessage(
                roomId = roomId,
                content = content,
                type = type,
                attachments = attachments,
                metadata = metadata,
                replyToId = effectiveReplyToId
            )

            // Replace pending message with real one
            val index = _messages.indexOfFirst { it.id == pendingId }
            if (index >= 0) {
                _messages[index] = sentMessage
            } else {
                // Pending was already replaced by realtime event — update if exists, skip otherwise
                val realIndex = _messages.indexOfFirst { it.id == sentMessage.id }
                if (realIndex >= 0) {
                    _messages[realIndex] = sentMessage
                }
            }
        } catch (e: Exception) {
            // Mark as failed
            val index = _messages.indexOfFirst { it.id == pendingId }
            if (index >= 0) {
                _messages[index] = pendingMessage.copy(isPending = false, isFailed = true)
            }
        }
    }

    /**
     * Retry sending a failed message.
     */
    suspend fun retryMessage(failedMessage: Message) {
        if (!failedMessage.isFailed) return

        // Remove failed message
        _messages.removeAll { it.id == failedMessage.id }

        // Resend
        sendMessage(
            content = failedMessage.content,
            type = failedMessage.type,
            attachments = failedMessage.attachments?.ifEmpty { null },
            replyToId = failedMessage.replyToId
        )
    }

    /**
     * Delete a message.
     */
    suspend fun deleteMessage(messageId: String) {
        try {
            client.deleteMessage(messageId)
            // Message will be marked as deleted via event stream
        } catch (e: Exception) {
            error = e
        }
    }

    /**
     * Edit a message.
     */
    suspend fun editMessage(messageId: String, content: String) {
        try {
            client.editMessage(messageId, content)
            // Message will be updated via event stream
        } catch (e: Exception) {
            error = e
        }
    }

    /**
     * Add a reaction to a message.
     */
    suspend fun addReaction(messageId: String, emoji: String) {
        try {
            client.addReaction(messageId, emoji)
        } catch (e: Exception) {
            error = e
        }
    }

    /**
     * Remove a reaction from a message.
     */
    suspend fun removeReaction(messageId: String, emoji: String) {
        try {
            client.removeReaction(messageId, emoji)
        } catch (e: Exception) {
            error = e
        }
    }

    /**
     * Pin a message.
     */
    suspend fun pinMessage(messageId: String) {
        try {
            client.pinMessage(messageId)
        } catch (e: Exception) {
            error = e
        }
    }

    /**
     * Unpin a message.
     */
    suspend fun unpinMessage(messageId: String) {
        try {
            client.unpinMessage(messageId)
        } catch (e: Exception) {
            error = e
        }
    }

    /**
     * Publish typing indicator (throttled to 2 seconds).
     */
    suspend fun publishTyping() {
        val now = System.currentTimeMillis()
        if (now - lastTypingTime < 2000) return // Throttle to 2 seconds

        lastTypingTime = now
        try {
            client.publishTyping(roomId)
        } catch (e: Exception) {
            // Ignore typing errors
        }
    }

    /**
     * Mark messages as read.
     */
    suspend fun markAsRead() {
        try {
            client.markAsRead(roomId)
        } catch (e: Exception) {
            // Ignore read errors
        }
    }

    /**
     * Set the message being replied to.
     */
    fun updateReplyingTo(message: Message?) {
        replyingTo = message
    }

    /**
     * Clear error state.
     */
    fun clearError() {
        error = null
    }

    private fun markAsReadSilently() {
        scope.launch {
            try {
                client.markAsRead(roomId)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    /**
     * Start collecting events from the client.
     */
    private fun startEventCollection() {
        collectionJobs = listOf(
            // New messages
            scope.launch {
                client.onMessage.collect { message ->
                    if (message.roomId != roomId) return@collect

                    // Check if message already exists by server ID
                    val existingIndex = _messages.indexOfFirst { it.id == message.id }
                    if (existingIndex >= 0) {
                        _messages[existingIndex] = message
                    } else {
                        // Check for pending optimistic message from same sender with matching content
                        // (handles race where realtime event arrives before API response)
                        val pendingIndex = _messages.indexOfFirst {
                            it.isPending && it.senderUserId == message.senderUserId && it.content == message.content
                        }
                        if (pendingIndex >= 0) {
                            _messages[pendingIndex] = message
                        } else {
                            // Add new message at beginning (newest first)
                            _messages.add(0, message)
                        }
                    }

                    // Mark as read if from other user
                    if (message.senderUserId != currentUserId) {
                        markAsReadSilently()
                    }
                }
            },

            // Message deletions
            scope.launch {
                client.onMessageDeleted.collect { event ->
                    if (event.roomId != roomId) return@collect
                    val index = _messages.indexOfFirst { it.id == event.messageId }
                    if (index >= 0) {
                        _messages[index] = _messages[index].copy(isDeleted = true)
                    }
                }
            },

            // Message edits
            scope.launch {
                client.onMessageEdited.collect { event ->
                    if (event.roomId != roomId) return@collect
                    val index = _messages.indexOfFirst { it.id == event.messageId }
                    if (index >= 0) {
                        _messages[index] = _messages[index].copy(
                            content = event.content,
                            isEdited = true,
                            editedAt = event.editedAt.toString()
                        )
                    }
                }
            },

            // Reactions
            scope.launch {
                client.onReactionEvent.collect { event ->
                    if (event.roomId != roomId) return@collect
                    val index = _messages.indexOfFirst { it.id == event.messageId }
                    if (index >= 0) {
                        val message = _messages[index]
                        val reactions = message.reactions.orEmpty().toMutableList()

                        if (event.added) {
                            val alreadyExists = reactions.any {
                                it.userId == event.userId && it.emoji == event.emoji
                            }
                            if (!alreadyExists) {
                                reactions.add(
                                    Reaction(
                                        id = event.reactionId ?: "",
                                        messageId = event.messageId,
                                        userId = event.userId,
                                        emoji = event.emoji,
                                        createdAt = Instant.now().toString()
                                    )
                                )
                            }
                        } else {
                            reactions.removeAll {
                                it.userId == event.userId && it.emoji == event.emoji
                            }
                        }
                        _messages[index] = message.copy(reactions = reactions)
                    }
                }
            },

            // Pin changes
            scope.launch {
                client.onMessagePinChanged.collect { event ->
                    if (event.roomId != roomId) return@collect
                    val index = _messages.indexOfFirst { it.id == event.messageId }
                    if (index >= 0) {
                        _messages[index] = _messages[index].copy(
                            isPinned = event.pinned,
                            pinnedBy = if (event.pinned) event.userId else null,
                            pinnedAt = if (event.pinned) Instant.now().toString() else null
                        )
                    }
                }
            },

            // Typing indicators
            scope.launch {
                client.onTypingEvent.collect { event ->
                    if (event.roomId != roomId) return@collect
                    if (event.userId == currentUserId) return@collect

                    if (event.isTyping) {
                        if (!_typingUsers.contains(event.userId)) {
                            _typingUsers.add(event.userId)
                        }

                        // Cancel existing timer and start new one
                        typingTimers[event.userId]?.cancel()
                        typingTimers[event.userId] = scope.launch {
                            delay(3000) // Remove after 3 seconds
                            _typingUsers.remove(event.userId)
                        }
                    } else {
                        _typingUsers.remove(event.userId)
                        typingTimers[event.userId]?.cancel()
                    }
                }
            },

            // Presence changes
            scope.launch {
                client.onPresenceChange.collect { event ->
                    if (event.roomId != roomId) return@collect

                    if (event.isOnline) {
                        if (!_onlineUsers.contains(event.userId)) {
                            _onlineUsers.add(event.userId)
                        }
                    } else {
                        _onlineUsers.remove(event.userId)
                    }
                }
            },

            // Read receipts
            scope.launch {
                client.onReadReceipt.collect { event ->
                    if (event.roomId != roomId) return@collect
                    if (event.userId == currentUserId) return@collect

                    val timestamp = event.readAt
                    val currentLastRead = otherUserLastRead
                    if (currentLastRead == null || timestamp.isAfter(currentLastRead)) {
                        otherUserLastRead = timestamp
                    }
                }
            },

            // Recovery failed — refresh all messages
            scope.launch {
                client.onRecoveryFailed.collect { failedRoomId ->
                    if (failedRoomId == roomId) {
                        refresh()
                    }
                }
            }
        )
    }

    /**
     * Clean up resources.
     */
    fun dispose() {
        collectionJobs.forEach { it.cancel() }
        typingTimers.values.forEach { it.cancel() }
        scope.launch {
            try {
                client.unsubscribeRoom(roomId)
            } catch (e: Exception) {
                // Ignore unsubscribe errors
            }
        }
    }
}

/**
 * Composable to remember and provide ChatChannelState.
 */
@Composable
fun rememberChatChannelState(
    roomId: String,
    currentUserId: String
): ChatChannelState {
    val client = LocalRiviumChatClient.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val state = remember(roomId, currentUserId) {
        ChatChannelState(client, roomId, currentUserId, scope)
    }

    LaunchedEffect(state) {
        state.initialize()
    }

    DisposableEffect(state) {
        onDispose {
            state.dispose()
        }
    }

    return state
}
