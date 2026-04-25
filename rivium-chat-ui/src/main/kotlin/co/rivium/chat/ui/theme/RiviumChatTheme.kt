package co.rivium.chat.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * RiviumChat-specific colors that extend Material3 color scheme.
 */
@Immutable
data class RiviumChatColors(
    val myMessageBubble: Color,
    val otherMessageBubble: Color,
    val myMessageText: Color,
    val otherMessageText: Color,
    val timestampText: Color,
    val linkText: Color,
    val onlineIndicator: Color,
    val offlineIndicator: Color,
    val typingIndicator: Color,
    val readReceipt: Color,
    val unreadReceipt: Color,
    val failedMessage: Color,
    val pendingMessage: Color,
    val replyBackground: Color,
    val mentionHighlight: Color
)

/**
 * RiviumChat-specific dimensions.
 */
@Immutable
data class RiviumChatDimensions(
    val messageBubbleRadius: Dp = 16.dp,
    val messagePadding: Dp = 12.dp,
    val avatarSize: Dp = 40.dp,
    val smallAvatarSize: Dp = 24.dp,
    val inputFieldRadius: Dp = 24.dp,
    val maxBubbleWidth: Float = 0.75f, // Fraction of screen width
    val imagePreviewSize: Dp = 200.dp,
    val reactionPillRadius: Dp = 12.dp
)

val LocalRiviumChatColors = staticCompositionLocalOf<RiviumChatColors> {
    error("No RiviumChatColors provided")
}

val LocalRiviumChatDimensions = staticCompositionLocalOf { RiviumChatDimensions() }

private val LightRiviumChatColors = RiviumChatColors(
    myMessageBubble = Color(0xFF007AFF),
    otherMessageBubble = Color(0xFFE9E9EB),
    myMessageText = Color.White,
    otherMessageText = Color.Black,
    timestampText = Color(0xFF8E8E93),
    linkText = Color(0xFF007AFF),
    onlineIndicator = Color(0xFF34C759),
    offlineIndicator = Color(0xFFC7C7CC),
    typingIndicator = Color(0xFF8E8E93),
    readReceipt = Color(0xFF007AFF),
    unreadReceipt = Color(0xFF8E8E93),
    failedMessage = Color(0xFFFF3B30),
    pendingMessage = Color(0xFF8E8E93),
    replyBackground = Color(0xFFF2F2F7),
    mentionHighlight = Color(0xFF007AFF).copy(alpha = 0.2f)
)

private val DarkRiviumChatColors = RiviumChatColors(
    myMessageBubble = Color(0xFF0A84FF),
    otherMessageBubble = Color(0xFF2C2C2E),
    myMessageText = Color.White,
    otherMessageText = Color.White,
    timestampText = Color(0xFF8E8E93),
    linkText = Color(0xFF0A84FF),
    onlineIndicator = Color(0xFF30D158),
    offlineIndicator = Color(0xFF636366),
    typingIndicator = Color(0xFF8E8E93),
    readReceipt = Color(0xFF0A84FF),
    unreadReceipt = Color(0xFF8E8E93),
    failedMessage = Color(0xFFFF453A),
    pendingMessage = Color(0xFF8E8E93),
    replyBackground = Color(0xFF1C1C1E),
    mentionHighlight = Color(0xFF0A84FF).copy(alpha = 0.3f)
)

/**
 * RiviumChat UI theme wrapper.
 * Provides RiviumChat-specific colors and dimensions alongside Material3.
 */
@Composable
fun RiviumChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colors: RiviumChatColors? = null,
    dimensions: RiviumChatDimensions = RiviumChatDimensions(),
    content: @Composable () -> Unit
) {
    val riviumChatColors = colors ?: if (darkTheme) DarkRiviumChatColors else LightRiviumChatColors

    CompositionLocalProvider(
        LocalRiviumChatColors provides riviumChatColors,
        LocalRiviumChatDimensions provides dimensions
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme(),
            content = content
        )
    }
}

/**
 * Access RiviumChat colors from the current theme.
 */
object RiviumChatTheme {
    val colors: RiviumChatColors
        @Composable
        get() = LocalRiviumChatColors.current

    val dimensions: RiviumChatDimensions
        @Composable
        get() = LocalRiviumChatDimensions.current
}
