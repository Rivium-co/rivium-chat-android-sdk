package co.rivium.chat.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import co.rivium.chat.ui.theme.RiviumChatTheme

/**
 * Online/offline presence indicator dot.
 *
 * @param isOnline Whether the user is online.
 * @param size Size of the indicator dot.
 * @param showBorder Whether to show a border around the dot.
 * @param borderColor Color of the border (usually matches the background).
 * @param modifier Modifier for the component.
 */
@Composable
fun PresenceIndicator(
    isOnline: Boolean,
    size: Dp = 12.dp,
    showBorder: Boolean = true,
    borderColor: Color = MaterialTheme.colorScheme.surface,
    modifier: Modifier = Modifier
) {
    val colors = RiviumChatTheme.colors

    val indicatorColor by animateColorAsState(
        targetValue = if (isOnline) colors.onlineIndicator else colors.offlineIndicator,
        animationSpec = tween(durationMillis = 300),
        label = "presenceColor"
    )

    Box(
        modifier = modifier
            .size(size)
            .then(
                if (showBorder) {
                    Modifier.border(
                        width = 2.dp,
                        color = borderColor,
                        shape = CircleShape
                    )
                } else Modifier
            )
            .clip(CircleShape)
            .background(indicatorColor)
    )
}

/**
 * Presence status with text label.
 */
@Composable
fun PresenceStatus(
    isOnline: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = RiviumChatTheme.colors

    val statusColor by animateColorAsState(
        targetValue = if (isOnline) colors.onlineIndicator else colors.offlineIndicator,
        animationSpec = tween(durationMillis = 300),
        label = "statusColor"
    )

    val statusText = if (isOnline) "Online" else "Offline"

    androidx.compose.foundation.layout.Row(
        modifier = modifier,
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(statusColor)
        )
        androidx.compose.material3.Text(
            text = statusText,
            style = MaterialTheme.typography.labelSmall,
            color = statusColor
        )
    }
}
