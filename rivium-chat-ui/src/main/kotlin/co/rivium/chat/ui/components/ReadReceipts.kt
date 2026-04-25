package co.rivium.chat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * A user who has read a message.
 */
data class ReadReceiptUser(
    val id: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val readAt: Instant
)

/**
 * Message delivery and read status.
 */
enum class MessageStatus {
    /** Message is being sent. */
    SENDING,
    /** Message has been sent to the server. */
    SENT,
    /** Message has been delivered to the recipient's device. */
    DELIVERED,
    /** Message has been read by the recipient. */
    READ,
    /** Message failed to send. */
    FAILED
}

/**
 * A widget that displays read receipts for a message.
 * Shows check marks (like WhatsApp) or user avatars (like Messenger).
 *
 * @param status The current delivery/read status of the message.
 * @param readBy List of users who have read the message.
 * @param showAvatars Whether to show avatars for group chats.
 * @param maxAvatars Maximum number of avatars to show.
 * @param iconSize Size of the status icon.
 * @param avatarSize Size of user avatars.
 * @param readColor Color for the read status.
 * @param sentColor Color for the sent/delivered status.
 * @param failedColor Color for the failed status.
 * @param onTap Called when the receipts are tapped (to show details).
 * @param modifier Modifier for the component.
 */
@Composable
fun ReadReceipts(
    status: MessageStatus,
    readBy: List<ReadReceiptUser>? = null,
    showAvatars: Boolean = false,
    maxAvatars: Int = 3,
    iconSize: Dp = 16.dp,
    avatarSize: Dp = 14.dp,
    readColor: Color? = null,
    sentColor: Color? = null,
    failedColor: Color? = null,
    onTap: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (showAvatars && !readBy.isNullOrEmpty()) {
        AvatarReceipts(
            readBy = readBy,
            maxAvatars = maxAvatars,
            avatarSize = avatarSize,
            onTap = onTap,
            modifier = modifier
        )
    } else {
        CheckmarkReceipts(
            status = status,
            iconSize = iconSize,
            readColor = readColor,
            sentColor = sentColor,
            failedColor = failedColor,
            onTap = onTap,
            modifier = modifier
        )
    }
}

@Composable
private fun CheckmarkReceipts(
    status: MessageStatus,
    iconSize: Dp,
    readColor: Color?,
    sentColor: Color?,
    failedColor: Color?,
    onTap: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val (icon, color) = when (status) {
        MessageStatus.SENDING -> Icons.Default.AccessTime to (sentColor ?: MaterialTheme.colorScheme.onSurfaceVariant)
        MessageStatus.SENT -> Icons.Default.Done to (sentColor ?: MaterialTheme.colorScheme.onSurfaceVariant)
        MessageStatus.DELIVERED -> Icons.Default.DoneAll to (sentColor ?: MaterialTheme.colorScheme.onSurfaceVariant)
        MessageStatus.READ -> Icons.Default.DoneAll to (readColor ?: MaterialTheme.colorScheme.primary)
        MessageStatus.FAILED -> Icons.Default.ErrorOutline to (failedColor ?: MaterialTheme.colorScheme.error)
    }

    Icon(
        imageVector = icon,
        contentDescription = status.name,
        tint = color,
        modifier = modifier
            .size(iconSize)
            .then(
                if (onTap != null) Modifier.clickable { onTap() }
                else Modifier
            )
    )
}

@Composable
private fun AvatarReceipts(
    readBy: List<ReadReceiptUser>,
    maxAvatars: Int,
    avatarSize: Dp,
    onTap: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val users = readBy.take(maxAvatars)
    val remaining = readBy.size - maxAvatars

    Row(
        modifier = modifier
            .then(
                if (onTap != null) Modifier.clickable { onTap() }
                else Modifier
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Overlapping avatars
        Box {
            users.forEachIndexed { index, user ->
                UserAvatar(
                    user = user,
                    size = avatarSize,
                    modifier = Modifier.offset(x = (index * (avatarSize.value * 0.6f)).dp)
                )
            }
        }

        // Remaining count
        if (remaining > 0) {
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = "+$remaining",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun UserAvatar(
    user: ReadReceiptUser,
    size: Dp,
    modifier: Modifier = Modifier
) {
    if (user.avatarUrl != null) {
        AsyncImage(
            model = user.avatarUrl,
            contentDescription = user.displayName,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Surface(
            modifier = modifier
                .size(size)
                .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = getInitial(user.displayName),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.4f
                    ),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

private fun getInitial(name: String?): String {
    return name?.firstOrNull()?.uppercase() ?: "?"
}

/**
 * A bottom sheet that shows detailed read receipt information.
 *
 * @param readBy List of users who have read the message.
 * @param sentAt Message sent time.
 * @param deliveredAt Message delivered time.
 * @param onDismiss Called when the sheet is dismissed.
 * @param modifier Modifier for the component.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadReceiptDetails(
    readBy: List<ReadReceiptUser>,
    sentAt: Instant? = null,
    deliveredAt: Instant? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            // Title
            Text(
                text = "Message info",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Divider()

            // Status section
            if (sentAt != null || deliveredAt != null) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (sentAt != null) {
                        StatusRow(
                            icon = Icons.Default.Done,
                            label = "Sent",
                            time = sentAt
                        )
                    }
                    if (deliveredAt != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        StatusRow(
                            icon = Icons.Default.DoneAll,
                            label = "Delivered",
                            time = deliveredAt
                        )
                    }
                }
            }

            // Read by section
            if (readBy.isNotEmpty()) {
                Divider()

                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DoneAll,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Read by ${readBy.size}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                LazyColumn(
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    items(readBy) { user ->
                        ReadByUserRow(user = user)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    time: Instant
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            text = formatTime(time),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ReadByUserRow(user: ReadReceiptUser) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        if (user.avatarUrl != null) {
            AsyncImage(
                model = user.avatarUrl,
                contentDescription = user.displayName,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = user.displayName?.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Name
        Text(
            text = user.displayName ?: "Unknown",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )

        // Time
        Text(
            text = formatTime(user.readAt),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatTime(instant: Instant): String {
    val localDateTime = instant.atZone(ZoneId.systemDefault()).toLocalDateTime()
    val localDate = localDateTime.toLocalDate()
    val today = LocalDate.now()
    val yesterday = today.minusDays(1)

    val timeStr = localDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))

    return when (localDate) {
        today -> timeStr
        yesterday -> "Yesterday, $timeStr"
        else -> localDateTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy, HH:mm"))
    }
}
