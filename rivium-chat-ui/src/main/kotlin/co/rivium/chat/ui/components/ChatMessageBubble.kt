package co.rivium.chat.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import coil.compose.AsyncImage
import co.rivium.chat.models.Message
import co.rivium.chat.models.MessageType
import co.rivium.chat.ui.theme.RiviumChatTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * A message bubble component for displaying chat messages.
 *
 * @param message The message to display.
 * @param isMe Whether this message was sent by the current user.
 * @param isRead Whether this message has been read by the recipient.
 * @param showAvatar Whether to show the sender's avatar.
 * @param otherUserName Display name for non-current user messages.
 * @param mentionDisplayNames Map of user IDs to display names for @mentions.
 * @param onRetry Callback when retry button is clicked for failed messages.
 * @param onReactionTap Callback when a reaction is tapped.
 * @param onLongClick Callback when message is long-pressed.
 * @param onImageClick Callback when an image attachment is clicked.
 * @param modifier Modifier for the component.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun ChatMessageBubble(
    message: Message,
    isMe: Boolean,
    isRead: Boolean = false,
    showAvatar: Boolean = true,
    otherUserName: String? = null,
    mentionDisplayNames: Map<String, String>? = null,
    onRetry: (() -> Unit)? = null,
    onReactionTap: ((String) -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onImageClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = RiviumChatTheme.colors
    val dimensions = RiviumChatTheme.dimensions
    val configuration = LocalConfiguration.current
    val maxWidth = (configuration.screenWidthDp * dimensions.maxBubbleWidth).dp

    // Handle deleted messages
    if (message.isDeleted) {
        DeletedMessageBubble(isMe = isMe, modifier = modifier)
        return
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        // Avatar for other users
        if (!isMe && showAvatar) {
            Box(
                modifier = Modifier
                    .size(dimensions.smallAvatarSize)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (otherUserName?.firstOrNull() ?: 'U').uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        } else if (!isMe) {
            Spacer(modifier = Modifier.width(dimensions.smallAvatarSize + 8.dp))
        }

        Column(
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
        ) {
            // Reply preview
            message.replyTo?.let { replyMessage ->
                ReplyPreview(
                    message = replyMessage,
                    isMe = isMe,
                    modifier = Modifier
                        .widthIn(max = maxWidth)
                        .padding(bottom = 2.dp)
                )
            }

            // Message bubble
            val bubbleColor by animateColorAsState(
                targetValue = when {
                    message.isFailed -> colors.failedMessage.copy(alpha = 0.2f)
                    message.isPending -> if (isMe) colors.myMessageBubble.copy(alpha = 0.6f) else colors.otherMessageBubble
                    isMe -> colors.myMessageBubble
                    else -> colors.otherMessageBubble
                },
                label = "bubbleColor"
            )

            Surface(
                modifier = Modifier
                    .widthIn(max = maxWidth)
                    .then(
                        if (onLongClick != null) {
                            Modifier.combinedClickable(
                                onClick = {},
                                onLongClick = onLongClick
                            )
                        } else Modifier
                    ),
                shape = RoundedCornerShape(
                    topStart = dimensions.messageBubbleRadius,
                    topEnd = dimensions.messageBubbleRadius,
                    bottomStart = if (isMe) dimensions.messageBubbleRadius else 4.dp,
                    bottomEnd = if (isMe) 4.dp else dimensions.messageBubbleRadius
                ),
                color = bubbleColor
            ) {
                Column(
                    modifier = Modifier.padding(dimensions.messagePadding)
                ) {
                    // Image attachments
                    val imageAttachments = message.attachments?.filter {
                        it.mimeType?.startsWith("image/") == true
                    } ?: emptyList()
                    if (imageAttachments.isNotEmpty()) {
                        imageAttachments.forEach { attachment ->
                            val imageModifier = Modifier
                                .widthIn(max = dimensions.imagePreviewSize)
                                .height(dimensions.imagePreviewSize)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onImageClick?.invoke(attachment.url) }

                            if (attachment.url.startsWith("data:")) {
                                // Decode base64 data URI to bitmap
                                val base64Data = attachment.url.substringAfter("base64,")
                                val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = attachment.name,
                                        modifier = imageModifier,
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            } else {
                                AsyncImage(
                                    model = attachment.url,
                                    contentDescription = attachment.name,
                                    modifier = imageModifier,
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }

                    // File attachments
                    val fileAttachments = message.attachments?.filter {
                        it.mimeType?.startsWith("image/") != true
                    } ?: emptyList()
                    fileAttachments.forEach { attachment ->
                        FileAttachmentChip(
                            name = attachment.name ?: "File",
                            size = attachment.size?.toLong(),
                            isMe = isMe
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Message content
                    if (message.content.isNotBlank()) {
                        val textColor = if (isMe) colors.myMessageText else colors.otherMessageText
                        val annotatedText = buildAnnotatedString {
                            val content = message.content
                            var lastIndex = 0

                            // Find and highlight @mentions
                            val mentionRegex = Regex("@(\\w+)")
                            mentionRegex.findAll(content).forEach { match ->
                                append(content.substring(lastIndex, match.range.first))
                                withStyle(
                                    SpanStyle(
                                        color = if (isMe) Color.White.copy(alpha = 0.9f) else colors.linkText,
                                        background = colors.mentionHighlight
                                    )
                                ) {
                                    val userId = match.groupValues[1]
                                    val displayName = mentionDisplayNames?.get(userId) ?: match.value
                                    append(displayName)
                                }
                                lastIndex = match.range.last + 1
                            }
                            append(content.substring(lastIndex))
                        }

                        Text(
                            text = annotatedText,
                            color = textColor,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    // Timestamp and status row
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        // Edited indicator
                        if (message.isEdited) {
                            Text(
                                text = "edited",
                                color = colors.timestampText,
                                style = MaterialTheme.typography.labelSmall,
                                fontStyle = FontStyle.Italic
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        // Timestamp
                        val timestamp = try {
                            val instant = Instant.parse(message.createdAt)
                            val formatter = DateTimeFormatter.ofPattern("HH:mm")
                                .withZone(ZoneId.systemDefault())
                            formatter.format(instant)
                        } catch (e: Exception) {
                            ""
                        }

                        Text(
                            text = timestamp,
                            color = if (isMe) colors.myMessageText.copy(alpha = 0.7f) else colors.timestampText,
                            style = MaterialTheme.typography.labelSmall
                        )

                        // Status indicator for own messages
                        if (isMe) {
                            Spacer(modifier = Modifier.width(4.dp))
                            when {
                                message.isFailed -> {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.ErrorOutline,
                                            contentDescription = "Failed",
                                            tint = colors.failedMessage,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        if (onRetry != null) {
                                            IconButton(
                                                onClick = onRetry,
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Refresh,
                                                    contentDescription = "Retry",
                                                    tint = colors.failedMessage,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                message.isPending -> {
                                    Icon(
                                        imageVector = Icons.Default.AccessTime,
                                        contentDescription = "Pending",
                                        tint = colors.myMessageText.copy(alpha = 0.7f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                isRead -> {
                                    Icon(
                                        imageVector = Icons.Default.DoneAll,
                                        contentDescription = "Read",
                                        tint = colors.readReceipt,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                else -> {
                                    Icon(
                                        imageVector = Icons.Default.Done,
                                        contentDescription = "Sent",
                                        tint = colors.myMessageText.copy(alpha = 0.7f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Reactions
            if (message.reactions?.isNotEmpty() == true) {
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val groupedReactions = message.reactions.orEmpty().groupBy { it.emoji }
                    groupedReactions.forEach { (emoji, reactions) ->
                        ReactionPill(
                            emoji = emoji,
                            count = reactions.size,
                            onClick = { onReactionTap?.invoke(emoji) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeletedMessageBubble(
    isMe: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(RiviumChatTheme.dimensions.messageBubbleRadius),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Text(
                text = "This message was deleted",
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(RiviumChatTheme.dimensions.messagePadding)
            )
        }
    }
}

@Composable
private fun ReplyPreview(
    message: Message,
    isMe: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = RiviumChatTheme.colors

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = colors.replyBackground
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(32.dp)
                    .background(
                        if (isMe) colors.myMessageBubble else MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(2.dp)
                    )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = message.senderUserId,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isMe) colors.myMessageBubble else MaterialTheme.colorScheme.primary
                )
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ReactionPill(
    emoji: String,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(RiviumChatTheme.dimensions.reactionPillRadius),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = emoji,
                fontSize = 14.sp
            )
            if (count > 1) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun FileAttachmentChip(
    name: String,
    size: Long?,
    isMe: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = RiviumChatTheme.colors

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = if (isMe) colors.myMessageText.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "📎", fontSize = 16.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isMe) colors.myMessageText else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                size?.let {
                    Text(
                        text = formatFileSize(it),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isMe) colors.myMessageText.copy(alpha = 0.7f) else colors.timestampText
                    )
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }
}
