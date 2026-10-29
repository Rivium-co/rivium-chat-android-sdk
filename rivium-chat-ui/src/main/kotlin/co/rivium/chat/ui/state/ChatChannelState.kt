package co.rivium.chat.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
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

    // Participants' display names (user id -> name), from the room
    private val _participantDisplayNames = mutableStateMapOf<String, String>()

    /** Display names of the room's participants, by user id. */
    val participantDisplayNames: Map<String, String> get() = _participantDisplayNames

    /** The participant's display name, or their user id when they have none. */
    fun displayNameFor(userId: String): String = _participantDisplayNames[userId] ?: userId

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
            showQueuedMessages()

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
                room.participants.forEach { participant ->
                    participant.displayName?.takeIf { it.isNotBlank() }?.let {
                        _participantDisplayNames[participant.externalUserId] = it
                    }
                }
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
            showQueuedMessages()
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

            if (sentMessage.isPending) {
                // Queued by the offline outbox: shown as "Waiting for network…"
                // until onMessageSent / onMessageFailed.
                val index = _messages.indexOfFirst { it.id == pendingId }
                if (index >= 0) _messages[index] = sentMessage else _messages.add(0, sentMessage)
                return
            }

            // The live event may already show it (matched by clientMessageId): never twice.
            val shownIndex = sentMessage.clientMessageId?.let { cmid ->
                _messages.indexOfFirst { it.id != pendingId && !it.isPending && it.clientMessageId == cmid }
            } ?: -1
            if (shownIndex >= 0) {
                _messages.removeAll { it.id == pendingId }
                val again = _messages.indexOfFirst { it.clientMessageId == sentMessage.clientMessageId && !it.isPending }
                if (again >= 0) _messages[again] = sentMessage
                return
            }

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

    /** Shows messages the offline outbox still holds for this room (e.g. after a restart). */
    private fun showQueuedMessages() {
        val queued = try {
            client.getPendingMessages(roomId)
        } catch (e: Exception) {
            emptyList()
        }
        queued.forEach { pending ->
            if (_messages.none { it.id == pending.id || it.clientMessageId == pending.clientMessageId }) {
                _messages.add(0, pending) // oldest first in, so the newest ends on top
            }
        }
    }

    /**
     * A queued message was stored: replace its "Waiting for network…" copy,
     * or drop the copy when the live event already showed the message.
     */
    private fun applySent(localId: String, message: Message) = mergeSentMessage(_messages, localId, message)

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
            val edited = client.editMessage(messageId, content)
            // Show the edit right away; the realtime event confirms it again.
            val index = _messages.indexOfFirst { it.id == messageId }
            if (index >= 0) {
                _messages[index] = _messages[index].copy(
                    content = edited.content,
                    isEdited = true,
                    editedAt = edited.editedAt ?: _messages[index].editedAt
                )
            }
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
            // Show it pinned right away; the realtime event confirms it again.
            setPinned(messageId, true)
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
            setPinned(messageId, false)
        } catch (e: Exception) {
            error = e
        }
    }

    private fun setPinned(messageId: String, pinned: Boolean) {
        val index = _messages.indexOfFirst { it.id == messageId }
        if (index < 0 || _messages[index].isPinned == pinned) return
        _messages[index] = _messages[index].copy(
            isPinned = pinned,
            pinnedBy = if (pinned) currentUserId else null,
            pinnedAt = if (pinned) Instant.now().toString() else null
        )
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

                    mergeLiveMessage(_messages, message)

                    // Mark as read if from other user
                    if (message.senderUserId != currentUserId) {
                        markAsReadSilently()
                    }
                }
            },

            // Offline outbox: a queued message was stored
            scope.launch {
                client.onMessageSent.collect { event ->
                    if (event.message.roomId != roomId) return@collect
                    applySent(event.localId, event.message)
                }
            },

            // Offline outbox: a queued message left the queue unsent
            scope.launch {
                client.onMessageFailed.collect { event ->
                    if (event.roomId != roomId) return@collect
                    markQueuedFailed(_messages, event.localId)
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
     *
     * Leaves the room's presence and typing channels but keeps its chat
     * channel, so anything else watching the room (a conversation list,
     * unread badges) keeps receiving its messages. Only this screen's own
     * listeners are dropped.
     */
    fun dispose() {
        collectionJobs.forEach { it.cancel() }
        typingTimers.values.forEach { it.cancel() }
        try {
            client.leaveRoom(roomId)
        } catch (e: Exception) {
            // Client already disposed: nothing left to leave.
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

/**
 * A live message event: updates the copy already shown (same id, or the
 * message queued offline with the same clientMessageId, or the optimistic
 * copy with the same sender and text), else adds it on top. Never twice.
 */
internal fun mergeLiveMessage(messages: MutableList<Message>, message: Message) {
    val existingIndex = messages.indexOfFirst { it.id == message.id }
    val queuedIndex = message.clientMessageId?.let { cmid ->
        messages.indexOfFirst { it.id != message.id && it.clientMessageId == cmid }
    } ?: -1
    when {
        existingIndex >= 0 -> {
            messages[existingIndex] = message
            if (queuedIndex >= 0) messages.removeAt(queuedIndex)
        }
        queuedIndex >= 0 -> messages[queuedIndex] = message
        else -> {
            // Pending optimistic message from the same sender with the same text
            // (the realtime event arrived before the API response)
            val pendingIndex = messages.indexOfFirst {
                it.isPending && !it.id.startsWith("local_") &&
                    it.senderUserId == message.senderUserId && it.content == message.content
            }
            if (pendingIndex >= 0) messages[pendingIndex] = message else messages.add(0, message)
        }
    }
}

/**
 * The offline outbox stored a queued message: replaces its "Waiting for
 * network…" copy ([localId]), or drops that copy when the live event already
 * showed the message. Never twice.
 */
internal fun mergeSentMessage(messages: MutableList<Message>, localId: String, message: Message) {
    val shownIndex = messages.indexOfFirst {
        it.id == message.id ||
            (!it.isPending && message.clientMessageId != null && it.clientMessageId == message.clientMessageId)
    }
    val localIndex = messages.indexOfFirst { it.id == localId }
    when {
        shownIndex >= 0 -> {
            if (localIndex >= 0 && localIndex != shownIndex) messages.removeAt(localIndex)
            val again = messages.indexOfFirst { it.id == message.id }
            if (again >= 0) messages[again] = message
        }
        localIndex >= 0 -> messages[localIndex] = message
        else -> messages.add(0, message)
    }
}

/** A failed queued message: shown as failed with Retry. */
internal fun markQueuedFailed(messages: MutableList<Message>, localId: String) {
    val index = messages.indexOfFirst { it.id == localId }
    if (index >= 0) messages[index] = messages[index].copy(isPending = false, isFailed = true)
}
