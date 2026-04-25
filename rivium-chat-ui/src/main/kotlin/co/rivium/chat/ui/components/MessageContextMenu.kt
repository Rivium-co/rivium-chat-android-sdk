package co.rivium.chat.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import co.rivium.chat.models.Message

/**
 * Actions available in the message context menu.
 */
enum class MessageAction {
    REPLY,
    COPY,
    EDIT,
    DELETE,
    PIN,
    REACT
}

/**
 * Context menu for message actions.
 *
 * @param message The message this menu is for.
 * @param isMe Whether the current user sent this message.
 * @param isPinned Whether the message is currently pinned.
 * @param onAction Callback when an action is selected.
 * @param availableActions List of actions to show.
 * @param modifier Modifier for the component.
 */
@Composable
fun MessageContextMenu(
    message: Message,
    isMe: Boolean,
    isPinned: Boolean = false,
    onAction: (MessageAction) -> Unit,
    availableActions: List<MessageAction> = defaultActionsFor(isMe),
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            availableActions.forEachIndexed { index, action ->
                val (icon, label) = when (action) {
                    MessageAction.REPLY -> Icons.Default.Reply to "Reply"
                    MessageAction.COPY -> Icons.Default.ContentCopy to "Copy"
                    MessageAction.EDIT -> Icons.Default.Edit to "Edit"
                    MessageAction.DELETE -> Icons.Default.Delete to "Delete"
                    MessageAction.PIN -> Icons.Default.PushPin to if (isPinned) "Unpin" else "Pin"
                    MessageAction.REACT -> Icons.Outlined.EmojiEmotions to "React"
                }

                val isDestructive = action == MessageAction.DELETE

                ContextMenuItem(
                    icon = icon,
                    label = label,
                    isDestructive = isDestructive,
                    onClick = { onAction(action) }
                )

                if (index < availableActions.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ContextMenuItem(
    icon: ImageVector,
    label: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isDestructive) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isDestructive) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

/**
 * Default actions based on message ownership.
 */
private fun defaultActionsFor(isMe: Boolean): List<MessageAction> {
    return if (isMe) {
        listOf(
            MessageAction.REPLY,
            MessageAction.COPY,
            MessageAction.EDIT,
            MessageAction.DELETE,
            MessageAction.PIN,
            MessageAction.REACT
        )
    } else {
        listOf(
            MessageAction.REPLY,
            MessageAction.COPY,
            MessageAction.PIN,
            MessageAction.REACT
        )
    }
}
