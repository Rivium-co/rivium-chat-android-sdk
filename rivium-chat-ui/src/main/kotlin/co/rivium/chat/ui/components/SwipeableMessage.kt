package co.rivium.chat.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/**
 * A swipeable message wrapper that enables swipe-to-reply gesture.
 * Similar to WhatsApp and Telegram swipe-to-reply functionality.
 *
 * @param isMe Whether this message is from the current user.
 * @param onReply Called when the user completes a swipe-to-reply gesture.
 * @param enabled Whether swipe-to-reply is enabled.
 * @param replyThreshold The threshold (0.0 to 1.0) at which the reply action is triggered.
 * @param replyIcon Custom reply icon.
 * @param replyIconColor Reply icon color.
 * @param modifier Modifier for the component.
 * @param content The message content to wrap.
 */
@Composable
fun SwipeableMessage(
    isMe: Boolean,
    onReply: (() -> Unit)? = null,
    enabled: Boolean = true,
    replyThreshold: Float = 0.2f,
    replyIcon: ImageVector = Icons.Default.Reply,
    replyIconColor: Color? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (!enabled || onReply == null) {
        Box(modifier = modifier) {
            content()
        }
        return
    }

    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }
    val maxDragDistance = screenWidth * 0.3f
    val triggerDistance = screenWidth * replyThreshold

    var dragOffset by remember { mutableFloatStateOf(0f) }
    var hasTriggeredHaptic by remember { mutableFloatStateOf(0f) }
    val animatedOffset = remember { Animatable(0f) }

    val progress = (dragOffset.absoluteValue / triggerDistance).coerceIn(0f, 1f)
    val isTriggered = dragOffset.absoluteValue >= triggerDistance

    Box(
        modifier = modifier,
        contentAlignment = if (isMe) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        // Reply icon indicator
        Box(
            modifier = Modifier
                .alpha(progress)
                .scale(0.5f + (progress * 0.5f))
        ) {
            Surface(
                shape = CircleShape,
                color = if (isTriggered) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.size(36.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = replyIcon,
                        contentDescription = "Reply",
                        tint = if (isTriggered) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            replyIconColor ?: MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Message content
        Box(
            modifier = Modifier
                .offset { IntOffset(animatedOffset.value.roundToInt() + dragOffset.roundToInt(), 0) }
                .pointerInput(enabled) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            hasTriggeredHaptic = 0f
                        },
                        onDragEnd = {
                            if (dragOffset.absoluteValue >= triggerDistance) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onReply()
                            }
                            scope.launch {
                                animatedOffset.snapTo(dragOffset)
                                dragOffset = 0f
                                animatedOffset.animateTo(0f, tween(200))
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                animatedOffset.snapTo(dragOffset)
                                dragOffset = 0f
                                animatedOffset.animateTo(0f, tween(200))
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            val newOffset = if (isMe) {
                                // Own messages: swipe left (negative)
                                (dragOffset + dragAmount).coerceIn(-maxDragDistance, 0f)
                            } else {
                                // Other messages: swipe right (positive)
                                (dragOffset + dragAmount).coerceIn(0f, maxDragDistance)
                            }
                            dragOffset = newOffset

                            // Trigger haptic when crossing threshold
                            val crossed = newOffset.absoluteValue >= triggerDistance
                            if (crossed && hasTriggeredHaptic == 0f) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                hasTriggeredHaptic = 1f
                            } else if (!crossed && hasTriggeredHaptic == 1f) {
                                hasTriggeredHaptic = 0f
                            }
                        }
                    )
                }
        ) {
            content()
        }
    }
}
