package co.rivium.chat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import co.rivium.chat.models.Message
import co.rivium.chat.models.MessageType

/**
 * A widget that displays a reply preview (quoted message).
 * Used both in message bubbles and in the input area when replying.
 *
 * @param message The message being replied to.
 * @param senderName The display name of the message sender.
 * @param inBubble Whether this is displayed in a message bubble (vs input area).
 * @param isMe Whether this is in a message from the current user.
 * @param onTap Called when the preview is tapped (to scroll to original message).
 * @param onClose Called when the close button is tapped (in input area).
 * @param accentColor The accent color for the left border.
 * @param maxLines Maximum lines for the preview text.
 * @param modifier Modifier for the component.
 */
@Composable
fun ReplyPreview(
    message: Message,
    senderName: String? = null,
    inBubble: Boolean = true,
    isMe: Boolean = false,
    onTap: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    accentColor: Color? = null,
    maxLines: Int = 2,
    modifier: Modifier = Modifier
) {
    val borderColor = accentColor ?: if (isMe && inBubble) {
        Color.White.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.primary
    }

    val backgroundColor = if (inBubble) {
        if (isMe) Color.White.copy(alpha = 0.1f)
        else MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = if (isMe && inBubble) {
        Color.White.copy(alpha = 0.8f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier
            .then(
                if (onTap != null) Modifier.clickable { onTap() }
                else Modifier
            ),
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left accent border
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(40.dp)
                    .background(borderColor, RoundedCornerShape(2.dp))
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Content
            Column(modifier = Modifier.weight(1f)) {
                // Sender name
                if (senderName != null) {
                    Text(
                        text = senderName,
                        style = MaterialTheme.typography.labelSmall,
                        color = borderColor,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                // Message content preview
                ReplyContentPreview(
                    message = message,
                    textColor = textColor,
                    maxLines = maxLines
                )
            }

            // Attachment thumbnail
            if (hasMediaAttachment(message)) {
                Spacer(modifier = Modifier.width(8.dp))
                AttachmentThumbnail(message = message)
            }

            // Close button (for input area)
            if (onClose != null) {
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel reply",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ReplyContentPreview(
    message: Message,
    textColor: Color,
    maxLines: Int
) {
    if (message.isDeleted) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Block,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Message deleted",
                style = MaterialTheme.typography.bodySmall,
                color = textColor,
                fontStyle = FontStyle.Italic
            )
        }
        return
    }

    val (content, icon) = getContentAndIcon(message)

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = content,
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun hasMediaAttachment(message: Message): Boolean {
    return message.type == MessageType.IMAGE && !message.attachments.isNullOrEmpty()
}

private fun getContentAndIcon(message: Message): Pair<String, androidx.compose.ui.graphics.vector.ImageVector?> {
    return when (message.type) {
        MessageType.IMAGE -> "Photo" to Icons.Default.Photo
        MessageType.FILE -> (message.attachments?.firstOrNull()?.name ?: "File") to Icons.Default.AttachFile
        MessageType.SYSTEM -> message.content to null
        MessageType.TEXT -> message.content to null
    }
}

@Composable
private fun AttachmentThumbnail(message: Message) {
    val attachment = message.attachments?.firstOrNull() ?: return

    AsyncImage(
        model = attachment.url,
        contentDescription = "Attachment",
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(4.dp)),
        contentScale = ContentScale.Crop
    )
}

/**
 * A compact reply indicator shown in the input area.
 *
 * @param message The message being replied to.
 * @param senderName The display name of the message sender.
 * @param onClose Called when the close button is tapped.
 * @param onTap Called when the preview is tapped.
 * @param modifier Modifier for the component.
 */
@Composable
fun ReplyInputPreview(
    message: Message,
    senderName: String? = null,
    onClose: () -> Unit,
    onTap: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .then(
                    if (onTap != null) Modifier.clickable { onTap() }
                    else Modifier
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left border
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Reply icon
            Icon(
                imageVector = Icons.Default.Reply,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Replying to ${senderName ?: "message"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (message.isDeleted) "Message deleted" else getPreviewText(message),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Close button
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel reply",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

private fun getPreviewText(message: Message): String {
    return when (message.type) {
        MessageType.IMAGE -> "Photo"
        MessageType.FILE -> message.attachments?.firstOrNull()?.name ?: "File"
        else -> message.content
    }
}
