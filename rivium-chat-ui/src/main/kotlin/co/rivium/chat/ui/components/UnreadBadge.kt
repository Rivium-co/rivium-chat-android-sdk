package co.rivium.chat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Badge showing unread message count.
 *
 * @param count Number of unread messages.
 * @param maxCount Maximum count to display (shows "99+" if exceeded).
 * @param backgroundColor Background color of the badge.
 * @param textColor Text color.
 * @param minSize Minimum size of the badge.
 * @param modifier Modifier for the component.
 */
@Composable
fun UnreadBadge(
    count: Int,
    maxCount: Int = 99,
    backgroundColor: Color = MaterialTheme.colorScheme.error,
    textColor: Color = MaterialTheme.colorScheme.onError,
    minSize: Dp = 20.dp,
    modifier: Modifier = Modifier
) {
    if (count <= 0) return

    val displayText = if (count > maxCount) "$maxCount+" else count.toString()
    val isLargeNumber = displayText.length > 2

    Box(
        modifier = modifier
            .sizeIn(minWidth = minSize, minHeight = minSize)
            .clip(if (isLargeNumber) RoundedCornerShape(10.dp) else CircleShape)
            .background(backgroundColor)
            .padding(horizontal = if (isLargeNumber) 6.dp else 0.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayText,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

/**
 * Small dot indicator for unread status without count.
 */
@Composable
fun UnreadDot(
    hasUnread: Boolean,
    size: Dp = 8.dp,
    color: Color = MaterialTheme.colorScheme.error,
    modifier: Modifier = Modifier
) {
    if (!hasUnread) return

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color)
    )
}
