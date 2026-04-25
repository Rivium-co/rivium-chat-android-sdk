package co.rivium.chat.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.rivium.chat.models.Message
import co.rivium.chat.ui.theme.RiviumChatTheme

/**
 * Input field component for composing chat messages.
 *
 * @param onSendMessage Callback when send button is pressed.
 * @param onTyping Callback when user is typing.
 * @param replyingTo The message being replied to, if any.
 * @param onCancelReply Callback to cancel the reply.
 * @param onAttachmentClick Callback when attachment button is pressed.
 * @param onCameraClick Callback when camera button is pressed.
 * @param onEmojiClick Callback when emoji button is pressed.
 * @param onVoiceRecordStart Callback when voice recording starts.
 * @param enabled Whether the input is enabled.
 * @param placeholder Placeholder text.
 * @param modifier Modifier for the component.
 */
@Composable
fun ChatInputField(
    onSendMessage: (String) -> Unit,
    onTyping: (() -> Unit)? = null,
    replyingTo: Message? = null,
    onCancelReply: (() -> Unit)? = null,
    onAttachmentClick: (() -> Unit)? = null,
    onCameraClick: (() -> Unit)? = null,
    onEmojiClick: (() -> Unit)? = null,
    onVoiceRecordStart: (() -> Unit)? = null,
    enabled: Boolean = true,
    placeholder: String = "Type a message...",
    modifier: Modifier = Modifier
) {
    var textFieldValue by remember { mutableStateOf(TextFieldValue()) }
    val focusRequester = remember { FocusRequester() }
    val colors = RiviumChatTheme.colors
    val dimensions = RiviumChatTheme.dimensions

    // Notify typing when text changes
    LaunchedEffect(textFieldValue.text) {
        if (textFieldValue.text.isNotEmpty()) {
            onTyping?.invoke()
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Reply preview
        AnimatedVisibility(
            visible = replyingTo != null,
            enter = slideInVertically { it },
            exit = slideOutVertically { it }
        ) {
            replyingTo?.let { message ->
                ReplyInputPreview(
                    message = message,
                    onCancel = { onCancelReply?.invoke() }
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Attachment button
                if (onAttachmentClick != null) {
                    IconButton(
                        onClick = onAttachmentClick,
                        enabled = enabled,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Attach file",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Text input field
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(dimensions.inputFieldRadius),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Emoji button
                        if (onEmojiClick != null) {
                            IconButton(
                                onClick = onEmojiClick,
                                enabled = enabled,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EmojiEmotions,
                                    contentDescription = "Emoji",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        // Text field
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 24.dp, max = 120.dp)
                        ) {
                            BasicTextField(
                                value = textFieldValue,
                                onValueChange = { textFieldValue = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester),
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                enabled = enabled,
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Send
                                ),
                                keyboardActions = KeyboardActions(
                                    onSend = {
                                        if (textFieldValue.text.isNotBlank()) {
                                            onSendMessage(textFieldValue.text)
                                            textFieldValue = TextFieldValue()
                                        }
                                    }
                                ),
                                decorationBox = { innerTextField ->
                                    Box {
                                        if (textFieldValue.text.isEmpty()) {
                                            Text(
                                                text = placeholder,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )
                        }

                        // Camera button
                        if (onCameraClick != null && textFieldValue.text.isEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = onCameraClick,
                                enabled = enabled,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = "Camera",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                // Send or voice button
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(enabled = enabled) {
                            if (textFieldValue.text.isNotBlank()) {
                                onSendMessage(textFieldValue.text)
                                textFieldValue = TextFieldValue()
                            } else {
                                onVoiceRecordStart?.invoke()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (textFieldValue.text.isNotBlank()) {
                            Icons.AutoMirrored.Filled.Send
                        } else {
                            Icons.Default.Mic
                        },
                        contentDescription = if (textFieldValue.text.isNotBlank()) "Send" else "Voice",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ReplyInputPreview(
    message: Message,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = RiviumChatTheme.colors

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.replyBackground
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(40.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(2.dp)
                    )
            )
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Replying to ${message.senderUserId}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onCancel,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel reply",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Attachment picker bottom sheet content.
 */
@Composable
fun AttachmentPickerContent(
    onGalleryClick: () -> Unit,
    onCameraClick: () -> Unit,
    onFileClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        AttachmentOption(
            icon = { Icon(Icons.Default.Image, "Gallery") },
            label = "Gallery",
            onClick = onGalleryClick
        )
        AttachmentOption(
            icon = { Icon(Icons.Default.CameraAlt, "Camera") },
            label = "Camera",
            onClick = onCameraClick
        )
        AttachmentOption(
            icon = { Icon(Icons.Default.AttachFile, "File") },
            label = "File",
            onClick = onFileClick
        )
    }
}

@Composable
private fun AttachmentOption(
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                icon()
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
