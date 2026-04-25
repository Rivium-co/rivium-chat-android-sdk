package co.rivium.chat.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import co.rivium.chat.ui.theme.RiviumChatTheme

/**
 * Displays a typing indicator when users are typing.
 *
 * @param typingUsers List of user IDs currently typing.
 * @param userDisplayNames Map of user IDs to display names.
 * @param modifier Modifier for the component.
 * @param customBuilder Optional custom builder for the typing indicator content.
 */
@Composable
fun TypingIndicator(
    typingUsers: List<String>,
    userDisplayNames: Map<String, String> = emptyMap(),
    modifier: Modifier = Modifier,
    customBuilder: (@Composable (List<String>) -> Unit)? = null
) {
    if (typingUsers.isEmpty()) return

    if (customBuilder != null) {
        customBuilder(typingUsers)
        return
    }

    val colors = RiviumChatTheme.colors
    val dimensions = RiviumChatTheme.dimensions

    Row(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar placeholder for alignment with messages
        Spacer(modifier = Modifier.width(dimensions.smallAvatarSize + 8.dp))

        Surface(
            shape = RoundedCornerShape(dimensions.messageBubbleRadius),
            color = colors.otherMessageBubble
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TypingDots()

                Spacer(modifier = Modifier.width(8.dp))

                val displayText = when {
                    typingUsers.size == 1 -> {
                        val name = userDisplayNames[typingUsers[0]] ?: typingUsers[0]
                        "$name is typing..."
                    }
                    typingUsers.size == 2 -> {
                        val name1 = userDisplayNames[typingUsers[0]] ?: typingUsers[0]
                        val name2 = userDisplayNames[typingUsers[1]] ?: typingUsers[1]
                        "$name1 and $name2 are typing..."
                    }
                    else -> "${typingUsers.size} people are typing..."
                }

                Text(
                    text = displayText,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.typingIndicator
                )
            }
        }
    }
}

/**
 * Animated typing dots indicator.
 */
@Composable
fun TypingDots(
    modifier: Modifier = Modifier,
    dotCount: Int = 3,
    dotSize: Int = 8,
    dotSpacing: Int = 4,
    animationDuration: Int = 300,
    delayBetweenDots: Int = 150
) {
    val colors = RiviumChatTheme.colors
    val infiniteTransition = rememberInfiniteTransition(label = "typing")

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(dotSpacing.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(dotCount) { index ->
            val delay = index * delayBetweenDots

            val offsetY by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -4f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = animationDuration,
                        delayMillis = delay,
                        easing = LinearEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_$index"
            )

            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = animationDuration,
                        delayMillis = delay,
                        easing = LinearEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "alpha_$index"
            )

            Box(
                modifier = Modifier
                    .size(dotSize.dp)
                    .offset(y = offsetY.dp)
                    .alpha(alpha)
                    .clip(CircleShape)
                    .background(colors.typingIndicator)
            )
        }
    }
}

/**
 * Compact typing indicator showing just the dots.
 */
@Composable
fun CompactTypingIndicator(
    isTyping: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isTyping) return

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = RiviumChatTheme.colors.otherMessageBubble
    ) {
        TypingDots(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}
