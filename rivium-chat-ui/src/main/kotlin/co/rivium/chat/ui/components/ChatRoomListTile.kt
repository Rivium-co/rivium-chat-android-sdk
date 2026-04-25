package co.rivium.chat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.rivium.chat.models.Message
import co.rivium.chat.models.MessageType
import co.rivium.chat.models.Room
import co.rivium.chat.ui.theme.RiviumChatTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * List tile for displaying a chat room in a room list.
 *
 * @param room The room to display.
 * @param lastMessage The last message in the room.
 * @param unreadCount Number of unread messages.
 * @param isMuted Whether the room is muted.
 * @param isPinned Whether the room is pinned.
 * @param isTyping Whether someone is typing in the room.
 * @param isOnline Whether the other user is online (for direct chats).
 * @param onClick Callback when the tile is clicked.
 * @param avatar Custom avatar composable.
 * @param title Custom title composable.
 * @param subtitle Custom subtitle composable.
 * @param trailing Custom trailing composable.
 * @param modifier Modifier for the component.
 */
@Composable
fun ChatRoomListTile(
    room: Room,
    lastMessage: Message? = null,
    unreadCount: Int = 0,
    isMuted: Boolean = false,
    isPinned: Boolean = false,
    isTyping: Boolean = false,
    isOnline: Boolean = false,
    onClick: () -> Unit,
    avatar: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    subtitle: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val dimensions = RiviumChatTheme.dimensions
    val colors = RiviumChatTheme.colors

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (isPinned) {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.surface
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box {
                if (avatar != null) {
                    avatar()
                } else {
                    DefaultAvatar(
                        name = room.name ?: room.id,
                        size = dimensions.avatarSize
                    )
                }

                // Online indicator
                if (isOnline) {
                    PresenceIndicator(
                        isOnline = true,
                        size = 14.dp,
                        borderColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.align(Alignment.BottomEnd)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                // Title row with pin indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (title != null) {
                        title()
                    } else {
                        Text(
                            text = room.name ?: "Chat",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }

                    if (isPinned) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = "Pinned",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    if (isMuted) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.NotificationsOff,
                            contentDescription = "Muted",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // Subtitle (last message or typing indicator)
                if (subtitle != null) {
                    subtitle()
                } else if (isTyping) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TypingDots(dotSize = 6, dotSpacing = 2)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "typing...",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.typingIndicator
                        )
                    }
                } else {
                    Text(
                        text = lastMessage?.let { formatLastMessage(it) } ?: "No messages yet",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (unreadCount > 0) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (unreadCount > 0) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Trailing (timestamp and unread badge)
            if (trailing != null) {
                trailing()
            } else {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Timestamp
                    lastMessage?.createdAt?.let { timestamp ->
                        Text(
                            text = formatTimestamp(timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (unreadCount > 0) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Unread badge
                    if (unreadCount > 0) {
                        UnreadBadge(
                            count = unreadCount,
                            backgroundColor = if (isMuted) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DefaultAvatar(
    name: String,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.firstOrNull()?.uppercase() ?: "?",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

/**
 * Skeleton placeholder for loading state.
 */
@Composable
fun ChatRoomListTileSkeleton(
    modifier: Modifier = Modifier
) {
    val shimmerColor = MaterialTheme.colorScheme.surfaceVariant

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar skeleton
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(shimmerColor)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            // Title skeleton
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(shimmerColor)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle skeleton
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(shimmerColor)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Timestamp skeleton
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(shimmerColor)
        )
    }
}

private fun formatLastMessage(message: Message): String {
    return when (message.type) {
        MessageType.IMAGE -> "📷 Photo"
        MessageType.FILE -> "📎 File"
        MessageType.SYSTEM -> message.content
        else -> message.content
    }
}

private fun formatTimestamp(isoTimestamp: String): String {
    return try {
        val instant = Instant.parse(isoTimestamp)
        val messageDate = instant.atZone(ZoneId.systemDefault()).toLocalDate()
        val today = LocalDate.now()

        when {
            messageDate == today -> {
                DateTimeFormatter.ofPattern("HH:mm")
                    .withZone(ZoneId.systemDefault())
                    .format(instant)
            }
            messageDate == today.minusDays(1) -> "Yesterday"
            messageDate.isAfter(today.minusDays(7)) -> {
                DateTimeFormatter.ofPattern("EEE")
                    .withZone(ZoneId.systemDefault())
                    .format(instant)
            }
            else -> {
                DateTimeFormatter.ofPattern("dd/MM/yy")
                    .withZone(ZoneId.systemDefault())
                    .format(instant)
            }
        }
    } catch (e: Exception) {
        ""
    }
}
