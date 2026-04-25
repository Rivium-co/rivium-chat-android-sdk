package co.rivium.chat.ui.components

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import co.rivium.chat.models.Attachment
import co.rivium.chat.models.Message
import co.rivium.chat.models.MessageType
import co.rivium.chat.ui.state.ChatChannelState
import co.rivium.chat.ui.state.rememberChatChannelState
import co.rivium.chat.ui.theme.RiviumChatTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * File uploader interface for handling attachment uploads.
 */
interface FileUploader {
    suspend fun uploadFile(uri: String, mimeType: String?, fileName: String?): Attachment?
}

/**
 * Complete chat screen with messages, input, and typing indicator.
 *
 * @param roomId The room ID to display.
 * @param currentUserId The current user's ID.
 * @param readOnly Whether the chat is read-only (hides input).
 * @param messageBuilder Custom message bubble builder.
 * @param fileUploader File uploader for attachments.
 * @param onImageClick Callback when an image is clicked.
 * @param userDisplayNames Map of user IDs to display names.
 * @param modifier Modifier for the component.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    roomId: String,
    currentUserId: String,
    readOnly: Boolean = false,
    messageBuilder: (@Composable (Message, Boolean, Boolean) -> Unit)? = null,
    fileUploader: FileUploader? = null,
    onImageClick: ((String) -> Unit)? = null,
    userDisplayNames: Map<String, String> = emptyMap(),
    modifier: Modifier = Modifier
) {
    val state = rememberChatChannelState(roomId = roomId, currentUserId = currentUserId)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    // Context menu state
    var selectedMessage by remember { mutableStateOf<Message?>(null) }
    var showContextMenu by remember { mutableStateOf(false) }
    var showReactionPicker by remember { mutableStateOf(false) }
    var showAttachmentPicker by remember { mutableStateOf(false) }

    // Full screen image viewer state
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }
    var fullScreenImageName by remember { mutableStateOf<String?>(null) }

    val effectiveOnImageClick: (String) -> Unit = onImageClick ?: { url ->
        fullScreenImageUrl = url
        fullScreenImageName = null
    }

    val sheetState = rememberModalBottomSheetState()

    // File upload handler
    val handleFileUri: (Uri) -> Unit = { uri ->
        if (fileUploader != null) {
            scope.launch {
                val contentResolver = context.contentResolver
                val mimeType = contentResolver.getType(uri)
                var fileName: String? = null
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) {
                            fileName = cursor.getString(nameIndex)
                        }
                    }
                }
                val attachment = fileUploader.uploadFile(uri.toString(), mimeType, fileName)
                if (attachment != null) {
                    val isImage = mimeType?.startsWith("image/") == true
                    state.sendMessage(
                        content = fileName ?: "Attachment",
                        type = if (isImage) MessageType.IMAGE else MessageType.FILE,
                        attachments = listOf(attachment)
                    )
                }
            }
        }
    }

    // Image picker launcher
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { handleFileUri(it) } }

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { handleFileUri(it) } }

    // Mark as read when messages are visible
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect {
                state.markAsRead()
            }
    }

    // Load more when reaching the end (older messages)
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem != null &&
                    lastVisibleItem.index >= state.messages.size - 5 &&
                    state.hasMore &&
                    !state.isLoadingMore
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            state.loadMore()
        }
    }

    // Scroll to bottom when new message arrives
    LaunchedEffect(state.messages.firstOrNull()?.id) {
        if (state.messages.isNotEmpty() && listState.firstVisibleItemIndex < 3) {
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        bottomBar = {
            if (!readOnly) {
                ChatInputField(
                    onSendMessage = { content ->
                        scope.launch {
                            state.sendMessage(content)
                        }
                    },
                    onTyping = {
                        scope.launch {
                            state.publishTyping()
                        }
                    },
                    replyingTo = state.replyingTo,
                    onCancelReply = { state.updateReplyingTo(null) },
                    onAttachmentClick = if (fileUploader != null) {{ showAttachmentPicker = true }} else null,
                    enabled = !state.isInitialLoading
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                state.isInitialLoading -> {
                    // Loading state
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                state.error != null -> {
                    // Error state
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Failed to load messages",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.error?.message ?: "Unknown error",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                state.messages.isEmpty() -> {
                    // Empty state
                    Text(
                        text = "No messages yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {
                    // Messages list
                    MessagesList(
                        messages = state.messages,
                        currentUserId = currentUserId,
                        otherUserLastRead = state.otherUserLastRead,
                        typingUsers = state.typingUsers,
                        userDisplayNames = userDisplayNames,
                        listState = listState,
                        isLoadingMore = state.isLoadingMore,
                        messageBuilder = messageBuilder,
                        onMessageLongClick = { message ->
                            selectedMessage = message
                            showContextMenu = true
                        },
                        onRetry = { messageId ->
                            scope.launch {
                                val failedMsg = state.messages.find { it.id == messageId }
                                if (failedMsg != null) {
                                    state.retryMessage(failedMsg)
                                }
                            }
                        },
                        onReactionTap = { messageId, emoji ->
                            scope.launch {
                                val message = state.messages.find { it.id == messageId }
                                val hasReacted = message?.reactions?.any {
                                    it.userId == currentUserId && it.emoji == emoji
                                } == true

                                if (hasReacted) {
                                    state.removeReaction(messageId, emoji)
                                } else {
                                    state.addReaction(messageId, emoji)
                                }
                            }
                        },
                        onImageClick = effectiveOnImageClick
                    )
                }
            }
        }
    }

    // Context menu bottom sheet
    if (showContextMenu && selectedMessage != null) {
        ModalBottomSheet(
            onDismissRequest = {
                showContextMenu = false
                selectedMessage = null
            },
            sheetState = sheetState
        ) {
            MessageContextMenu(
                message = selectedMessage!!,
                isMe = selectedMessage!!.senderUserId == currentUserId,
                isPinned = selectedMessage!!.isPinned,
                onAction = { action ->
                    scope.launch {
                        when (action) {
                            MessageAction.REPLY -> {
                                state.updateReplyingTo(selectedMessage)
                            }
                            MessageAction.COPY -> {
                                clipboardManager.setText(
                                    AnnotatedString(selectedMessage!!.content)
                                )
                            }
                            MessageAction.DELETE -> {
                                state.deleteMessage(selectedMessage!!.id)
                            }
                            MessageAction.EDIT -> {
                                // Would open edit dialog
                            }
                            MessageAction.PIN -> {
                                if (selectedMessage!!.isPinned) {
                                    state.unpinMessage(selectedMessage!!.id)
                                } else {
                                    state.pinMessage(selectedMessage!!.id)
                                }
                            }
                            MessageAction.REACT -> {
                                showContextMenu = false
                                showReactionPicker = true
                            }
                        }
                        if (action != MessageAction.REACT) {
                            showContextMenu = false
                            selectedMessage = null
                        }
                    }
                },
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }
    }

    // Reaction picker bottom sheet
    if (showReactionPicker && selectedMessage != null) {
        ModalBottomSheet(
            onDismissRequest = {
                showReactionPicker = false
                showContextMenu = false
                selectedMessage = null
            },
            sheetState = sheetState
        ) {
            MessageReactionPicker(
                onReactionSelected = { emoji ->
                    scope.launch {
                        state.addReaction(selectedMessage!!.id, emoji)
                        showReactionPicker = false
                        showContextMenu = false
                        selectedMessage = null
                    }
                },
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.CenterHorizontally)
            )
        }
    }

    // Attachment picker bottom sheet
    if (showAttachmentPicker) {
        ModalBottomSheet(
            onDismissRequest = { showAttachmentPicker = false },
            sheetState = sheetState
        ) {
            AttachmentPickerContent(
                onGalleryClick = {
                    showAttachmentPicker = false
                    imagePickerLauncher.launch("image/*")
                },
                onCameraClick = {
                    showAttachmentPicker = false
                    imagePickerLauncher.launch("image/*")
                },
                onFileClick = {
                    showAttachmentPicker = false
                    filePickerLauncher.launch("*/*")
                }
            )
        }
    }

    // Full screen image viewer overlay
    if (fullScreenImageUrl != null) {
        FullScreenImageViewer(
            imageUrl = fullScreenImageUrl!!,
            fileName = fullScreenImageName,
            onDismiss = {
                fullScreenImageUrl = null
                fullScreenImageName = null
            }
        )
    }
}

@Composable
private fun MessagesList(
    messages: List<Message>,
    currentUserId: String,
    otherUserLastRead: Instant?,
    typingUsers: List<String>,
    userDisplayNames: Map<String, String>,
    listState: LazyListState,
    isLoadingMore: Boolean,
    messageBuilder: (@Composable (Message, Boolean, Boolean) -> Unit)?,
    onMessageLongClick: (Message) -> Unit,
    onRetry: (String) -> Unit,
    onReactionTap: (String, String) -> Unit,
    onImageClick: ((String) -> Unit)?
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        reverseLayout = true,
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        // Typing indicator at top (shows at bottom due to reverse layout)
        if (typingUsers.isNotEmpty()) {
            item(key = "typing") {
                TypingIndicator(
                    typingUsers = typingUsers,
                    userDisplayNames = userDisplayNames
                )
            }
        }

        // Messages
        items(
            items = messages,
            key = { it.id }
        ) { message ->
            val isMe = message.senderUserId == currentUserId

            // Check if message is read
            val isRead = if (isMe && otherUserLastRead != null) {
                try {
                    val messageTime = Instant.parse(message.createdAt)
                    !messageTime.isAfter(otherUserLastRead)
                } catch (e: Exception) {
                    false
                }
            } else {
                false
            }

            if (messageBuilder != null) {
                messageBuilder(message, isMe, isRead)
            } else {
                ChatMessageBubble(
                    message = message,
                    isMe = isMe,
                    isRead = isRead,
                    otherUserName = userDisplayNames[message.senderUserId],
                    mentionDisplayNames = userDisplayNames,
                    onRetry = if (message.isFailed) {
                        { onRetry(message.id) }
                    } else null,
                    onReactionTap = { emoji -> onReactionTap(message.id, emoji) },
                    onLongClick = { onMessageLongClick(message) },
                    onImageClick = onImageClick
                )
            }
        }

        // Loading more indicator at bottom (shows at top due to reverse layout)
        if (isLoadingMore) {
            item(key = "loading") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(24.dp)
                    )
                }
            }
        }
    }
}
