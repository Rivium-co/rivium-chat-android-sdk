package co.rivium.chat.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.absoluteValue
import kotlin.math.roundToInt

/**
 * State of the voice recorder.
 */
enum class VoiceRecorderState {
    IDLE,
    RECORDING,
    LOCKED,
    CANCELLED
}

/**
 * Result of a voice recording.
 */
data class VoiceRecordingResult(
    val file: File,
    val duration: Long,
    val waveform: List<Float>? = null
)

/**
 * A voice message recorder widget with hold-to-record and slide-to-cancel.
 * Similar to WhatsApp and Telegram voice recording.
 *
 * Note: This widget provides the UI and gesture handling.
 * Actual audio recording requires a platform-specific implementation.
 *
 * @param onRecordingStart Called when recording starts.
 * @param onRecordingCancel Called when recording is cancelled.
 * @param onRecordingComplete Called when recording is completed with the result.
 * @param onRecordingUpdate Called periodically during recording with current duration.
 * @param startRecording Function to start recording. Should return the output file path.
 * @param stopRecording Function to stop recording. Should return the recorded file.
 * @param cancelRecording Function to cancel recording.
 * @param maxDuration Maximum recording duration in milliseconds.
 * @param minDuration Minimum recording duration to be valid in milliseconds.
 * @param cancelSlideThreshold Slide distance to cancel (as fraction of screen width).
 * @param lockSlideThreshold Slide distance to lock recording.
 * @param micIcon The mic button icon.
 * @param recordingColor The recording indicator color.
 * @param cancelColor The cancel indicator color.
 * @param modifier Modifier for the component.
 */
@Composable
fun VoiceMessageRecorder(
    onRecordingStart: (() -> Unit)? = null,
    onRecordingCancel: (() -> Unit)? = null,
    onRecordingComplete: ((VoiceRecordingResult) -> Unit)? = null,
    onRecordingUpdate: ((Long) -> Unit)? = null,
    startRecording: (suspend () -> String)? = null,
    stopRecording: (suspend () -> File?)? = null,
    cancelRecording: (suspend () -> Unit)? = null,
    maxDuration: Long = 5 * 60 * 1000, // 5 minutes
    minDuration: Long = 1000, // 1 second
    cancelSlideThreshold: Float = 0.3f,
    lockSlideThreshold: Float = 0.15f,
    micIcon: ImageVector = Icons.Default.Mic,
    recordingColor: Color? = null,
    cancelColor: Color? = null,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }

    var state by remember { mutableStateOf(VoiceRecorderState.IDLE) }
    var recordingDuration by remember { mutableLongStateOf(0L) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    val actualRecordingColor = recordingColor ?: MaterialTheme.colorScheme.error
    val actualCancelColor = cancelColor ?: MaterialTheme.colorScheme.error

    // Duration timer
    LaunchedEffect(state) {
        if (state == VoiceRecorderState.RECORDING || state == VoiceRecorderState.LOCKED) {
            while (true) {
                delay(1000)
                recordingDuration += 1000
                onRecordingUpdate?.invoke(recordingDuration)

                if (recordingDuration >= maxDuration) {
                    completeRecording(
                        stopRecording = stopRecording,
                        duration = recordingDuration,
                        minDuration = minDuration,
                        onComplete = onRecordingComplete,
                        onCancel = onRecordingCancel
                    )
                    state = VoiceRecorderState.IDLE
                    recordingDuration = 0
                    break
                }
            }
        }
    }

    when (state) {
        VoiceRecorderState.IDLE -> {
            IdleMicButton(
                micIcon = micIcon,
                onLongPressStart = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    scope.launch {
                        startRecording?.invoke()
                    }
                    state = VoiceRecorderState.RECORDING
                    recordingDuration = 0
                    dragOffsetX = 0f
                    dragOffsetY = 0f
                    onRecordingStart?.invoke()
                },
                modifier = modifier
            )
        }

        VoiceRecorderState.RECORDING -> {
            RecordingState(
                duration = recordingDuration,
                dragOffsetX = dragOffsetX,
                screenWidth = screenWidth,
                cancelThreshold = cancelSlideThreshold,
                recordingColor = actualRecordingColor,
                cancelColor = actualCancelColor,
                micIcon = micIcon,
                onDrag = { deltaX, deltaY ->
                    dragOffsetX = (dragOffsetX + deltaX).coerceIn(-screenWidth * 0.4f, 0f)
                    dragOffsetY += deltaY

                    // Check for cancel
                    if (dragOffsetX.absoluteValue >= screenWidth * cancelSlideThreshold) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        scope.launch { cancelRecording?.invoke() }
                        state = VoiceRecorderState.IDLE
                        recordingDuration = 0
                        onRecordingCancel?.invoke()
                    }

                    // Check for lock
                    if (dragOffsetY < -100 * lockSlideThreshold) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        state = VoiceRecorderState.LOCKED
                    }
                },
                onDragEnd = {
                    scope.launch {
                        completeRecording(
                            stopRecording = stopRecording,
                            duration = recordingDuration,
                            minDuration = minDuration,
                            onComplete = onRecordingComplete,
                            onCancel = onRecordingCancel
                        )
                    }
                    state = VoiceRecorderState.IDLE
                    recordingDuration = 0
                },
                modifier = modifier
            )
        }

        VoiceRecorderState.LOCKED -> {
            LockedRecordingState(
                duration = recordingDuration,
                recordingColor = actualRecordingColor,
                onCancel = {
                    scope.launch { cancelRecording?.invoke() }
                    state = VoiceRecorderState.IDLE
                    recordingDuration = 0
                    onRecordingCancel?.invoke()
                },
                onSend = {
                    scope.launch {
                        completeRecording(
                            stopRecording = stopRecording,
                            duration = recordingDuration,
                            minDuration = minDuration,
                            onComplete = onRecordingComplete,
                            onCancel = onRecordingCancel
                        )
                    }
                    state = VoiceRecorderState.IDLE
                    recordingDuration = 0
                },
                modifier = modifier
            )
        }

        VoiceRecorderState.CANCELLED -> {
            // Handled in RECORDING state
        }
    }
}

private suspend fun completeRecording(
    stopRecording: (suspend () -> File?)?,
    duration: Long,
    minDuration: Long,
    onComplete: ((VoiceRecordingResult) -> Unit)?,
    onCancel: (() -> Unit)?
) {
    if (duration < minDuration) {
        onCancel?.invoke()
        return
    }

    val file = stopRecording?.invoke()
    if (file != null) {
        onComplete?.invoke(VoiceRecordingResult(file = file, duration = duration))
    }
}

@Composable
private fun IdleMicButton(
    micIcon: ImageVector,
    onLongPressStart: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .size(48.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { onLongPressStart() }
                )
            },
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = micIcon,
                contentDescription = "Record voice message",
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
private fun RecordingState(
    duration: Long,
    dragOffsetX: Float,
    screenWidth: Float,
    cancelThreshold: Float,
    recordingColor: Color,
    cancelColor: Color,
    micIcon: ImageVector,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cancelProgress = (dragOffsetX.absoluteValue / (screenWidth * cancelThreshold)).coerceIn(0f, 1f)

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 16.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                    onDrag = { _, dragAmount ->
                        onDrag(dragAmount.x, dragAmount.y)
                    }
                )
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Recording indicator
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(recordingColor.copy(alpha = pulseAlpha), CircleShape)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Duration
        Text(
            text = formatDuration(duration),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.weight(1f))

        // Slide to cancel indicator
        Row(
            modifier = Modifier.alpha(1 - cancelProgress),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.ChevronLeft,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Slide to cancel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Lock indicator
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = "Lock recording",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Mic button
        Surface(
            modifier = Modifier
                .size(48.dp)
                .offset { IntOffset(dragOffsetX.roundToInt().coerceIn(-100, 0), 0) },
            shape = CircleShape,
            color = Color.lerp(recordingColor, cancelColor, cancelProgress)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (cancelProgress > 0.8f) Icons.Default.Delete else micIcon,
                    contentDescription = null,
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun LockedRecordingState(
    duration: Long,
    recordingColor: Color,
    onCancel: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Recording indicator
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(recordingColor.copy(alpha = pulseAlpha), CircleShape)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Duration
        Text(
            text = formatDuration(duration),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.weight(1f))

        // Cancel button
        IconButton(onClick = onCancel) {
            Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = "Cancel recording",
                tint = MaterialTheme.colorScheme.error
            )
        }

        // Send button
        FilledIconButton(onClick = onSend) {
            Icon(
                imageVector = Icons.Default.Send,
                contentDescription = "Send recording"
            )
        }
    }
}

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

@Composable
private fun Modifier.alpha(alpha: Float): Modifier = this.then(
    Modifier.background(Color.Transparent.copy(alpha = alpha))
)

/**
 * A widget to display a voice message with waveform and playback controls.
 *
 * @param duration Duration of the audio in milliseconds.
 * @param waveform Waveform data for visualization.
 * @param isMe Whether this message is from the current user.
 * @param position Current playback position in milliseconds.
 * @param isPlaying Whether audio is currently playing.
 * @param onPlayPause Called when play/pause is toggled.
 * @param onSeek Called when seeking to a position.
 * @param modifier Modifier for the component.
 */
@Composable
fun VoiceMessagePlayer(
    duration: Long,
    waveform: List<Float>? = null,
    isMe: Boolean = false,
    position: Long = 0,
    isPlaying: Boolean = false,
    onPlayPause: ((Boolean) -> Unit)? = null,
    onSeek: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val progress = if (duration > 0) position.toFloat() / duration else 0f

    Row(
        modifier = modifier.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Play/Pause button
        Surface(
            modifier = Modifier
                .size(40.dp)
                .pointerInput(Unit) {
                    detectTapGestures {
                        onPlayPause?.invoke(!isPlaying)
                    }
                },
            shape = CircleShape,
            color = if (isMe) {
                Color.White.copy(alpha = 0.2f)
            } else {
                MaterialTheme.colorScheme.primary
            }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = if (isMe) Color.White else MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            if (waveform != null && waveform.isNotEmpty()) {
                WaveformVisualizer(
                    waveform = waveform,
                    progress = progress,
                    isMe = isMe,
                    onSeek = { p ->
                        onSeek?.invoke((duration * p).toLong())
                    }
                )
            } else {
                // Simple progress bar fallback
                val progressColor = if (isMe) Color.White else MaterialTheme.colorScheme.primary
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                ) {
                    val lineY = size.height / 2
                    val progressWidth = size.width * progress

                    // Background line
                    drawLine(
                        color = if (isMe) Color.White.copy(alpha = 0.3f) else Color.Gray.copy(alpha = 0.3f),
                        start = Offset(0f, lineY),
                        end = Offset(size.width, lineY),
                        strokeWidth = 4f,
                        cap = StrokeCap.Round
                    )

                    // Progress line
                    if (progressWidth > 0) {
                        drawLine(
                            color = progressColor,
                            start = Offset(0f, lineY),
                            end = Offset(progressWidth, lineY),
                            strokeWidth = 4f,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = formatDuration(if (isPlaying) position else duration),
                style = MaterialTheme.typography.bodySmall,
                color = if (isMe) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WaveformVisualizer(
    waveform: List<Float>,
    progress: Float,
    isMe: Boolean,
    onSeek: ((Float) -> Unit)?
) {
    val playedColor = if (isMe) Color.White else MaterialTheme.colorScheme.primary
    val unplayedColor = if (isMe) Color.White.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .pointerInput(onSeek) {
                if (onSeek != null) {
                    detectTapGestures { offset ->
                        val p = (offset.x / size.width).coerceIn(0f, 1f)
                        onSeek(p)
                    }
                }
            }
    ) {
        if (waveform.isEmpty()) return@Canvas

        val barWidth = size.width / waveform.size
        val maxHeight = size.height * 0.8f
        val centerY = size.height / 2

        waveform.forEachIndexed { index, value ->
            val x = index * barWidth + barWidth / 2
            val barProgress = index.toFloat() / waveform.size
            val isPlayed = barProgress <= progress

            val height = value * maxHeight
            drawLine(
                color = if (isPlayed) playedColor else unplayedColor,
                start = Offset(x, centerY - height / 2),
                end = Offset(x, centerY + height / 2),
                strokeWidth = barWidth * 0.6f,
                cap = StrokeCap.Round
            )
        }
    }
}

private fun Color.Companion.lerp(start: Color, stop: Color, fraction: Float): Color {
    return Color(
        red = start.red + (stop.red - start.red) * fraction,
        green = start.green + (stop.green - start.green) * fraction,
        blue = start.blue + (stop.blue - start.blue) * fraction,
        alpha = start.alpha + (stop.alpha - start.alpha) * fraction
    )
}
