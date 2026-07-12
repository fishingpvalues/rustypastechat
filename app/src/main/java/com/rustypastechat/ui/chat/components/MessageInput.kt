package com.rustypastechat.ui.chat.components

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.rustypastechat.ui.animations.rememberPulseAnim
import java.io.FileOutputStream

private val LOCK_THRESHOLD = 90.dp
private val CANCEL_THRESHOLD = 140.dp

@Composable
fun MessageInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onMediaSelected: (Uri) -> Unit,
    isRecording: Boolean = false,
    recordingElapsedMs: Long = 0L,
    onStartRecording: () -> Unit = {},
    onStopRecording: () -> Unit = {},
    onCancelRecording: () -> Unit = {},
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let { onMediaSelected(it) } }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let {
            val tempFile = java.io.File(context.cacheDir, "camera_${System.currentTimeMillis()}.jpg")
            FileOutputStream(tempFile).use { out ->
                it.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            onMediaSelected(Uri.fromFile(tempFile))
        }
    }

    val recordAudioPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* granted or not — user just presses the mic again to record */ }

    // WhatsApp-style press-and-hold: drag up past LOCK_THRESHOLD frees the finger (recording
    // keeps going until an explicit send/cancel tap); drag left past CANCEL_THRESHOLD discards.
    var isLocked by remember { mutableStateOf(false) }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isRecording) {
        if (!isRecording) {
            isLocked = false
            dragX = 0f
            dragY = 0f
        }
    }

    val cancelProgress = (-dragX / with(density) { CANCEL_THRESHOLD.toPx() }).coerceIn(0f, 1f)
    val lockProgress = (-dragY / with(density) { LOCK_THRESHOLD.toPx() }).coerceIn(0f, 1f)

    Box(modifier = modifier.fillMaxWidth()) {
        AnimatedVisibility(
            visible = isRecording && !isLocked,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = (-14).dp, y = (-52).dp),
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .graphicsLayer { alpha = 1f - lockProgress * 0.3f; translationY = -lockProgress * 20f }
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "Slide up to lock recording",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when {
                    isRecording && isLocked -> {
                        LockedRecordingRow(
                            elapsedMs = recordingElapsedMs,
                            onCancel = onCancelRecording,
                            onSend = onStopRecording,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    isRecording -> {
                        SlidingRecordingRow(
                            elapsedMs = recordingElapsedMs,
                            cancelProgress = cancelProgress,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    else -> {
                        IconButton(
                            onClick = { imagePickerLauncher.launch("*/*") },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Attach photo, video, or file",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        IconButton(
                            onClick = { cameraLauncher.launch(null) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoCamera,
                                contentDescription = "Take photo",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        OutlinedTextField(
                            value = value,
                            onValueChange = onValueChange,
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text(
                                    "Text message",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = Color.Transparent,
                                focusedContainerColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedBorderColor = Color.Transparent,
                                cursorColor = MaterialTheme.colorScheme.primary
                            ),
                            maxLines = 4,
                            textStyle = MaterialTheme.typography.bodyLarge,
                            shape = MaterialTheme.shapes.medium
                        )
                    }
                }

                Spacer(Modifier.width(4.dp))

                if (value.isBlank() && !isLocked) {
                    val micScale by animateFloatAsState(if (isRecording) 1.25f else 1f, label = "micScale")
                    // Plain Box (not FilledIconButton) — a Material button's own built-in
                    // clickable gesture would compete with the press/drag detector below.
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .graphicsLayer {
                                translationX = dragX.coerceIn(-(with(density) { CANCEL_THRESHOLD.toPx() } * 0.3f), 0f)
                                scaleX = micScale
                                scaleY = micScale
                            }
                            .background(
                                if (enabled) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                CircleShape
                            )
                            .pointerInput(enabled) {
                                if (!enabled) return@pointerInput
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    val granted = ContextCompat.checkSelfPermission(
                                        context, Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (!granted) {
                                        recordAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
                                        return@awaitEachGesture
                                    }
                                    onStartRecording()
                                    var locked = false
                                    var cancelled = false
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull { it.id == down.id }
                                        if (change == null || !change.pressed) break
                                        val dx = change.position.x - down.position.x
                                        val dy = change.position.y - down.position.y
                                        dragX = dx
                                        dragY = dy
                                        if (!locked && -dy > with(density) { LOCK_THRESHOLD.toPx() }) {
                                            locked = true
                                            isLocked = true
                                        }
                                        if (!locked && -dx > with(density) { CANCEL_THRESHOLD.toPx() }) {
                                            cancelled = true
                                            change.consume()
                                            break
                                        }
                                        change.consume()
                                    }
                                    dragX = 0f
                                    dragY = 0f
                                    if (cancelled) onCancelRecording()
                                    else if (!locked) onStopRecording()
                                    // if locked, recording continues — LockedRecordingRow's
                                    // explicit buttons take over from here.
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            "Hold to record a voice message, slide up to lock",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else if (!isRecording) {
                    FilledIconButton(
                        onClick = onSend,
                        enabled = enabled && value.isNotBlank(),
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            "Send",
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SlidingRecordingRow(
    elapsedMs: Long,
    cancelProgress: Float,
    modifier: Modifier = Modifier
) {
    val pulse = rememberPulseAnim(minScale = 0.85f, maxScale = 1.15f, durationMs = 700)
    val totalSeconds = elapsedMs / 1000
    val timeText = "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)

    Row(
        modifier = modifier.padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size((10 * pulse).dp)
                .background(MaterialTheme.colorScheme.error, CircleShape)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            timeText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { contentDescription = "Recording, $timeText" }
        )
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier.graphicsLayer { alpha = 1f - cancelProgress },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.KeyboardArrowLeft,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Slide to cancel",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LockedRecordingRow(
    elapsedMs: Long,
    onCancel: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pulse = rememberPulseAnim(minScale = 0.85f, maxScale = 1.15f, durationMs = 700)
    val totalSeconds = elapsedMs / 1000
    val timeText = "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)

    Row(
        modifier = modifier.padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size((10 * pulse).dp)
                .background(MaterialTheme.colorScheme.error, CircleShape)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            timeText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { contentDescription = "Recording locked, $timeText" }
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onCancel, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Default.Delete, "Discard recording", tint = MaterialTheme.colorScheme.error)
        }
        FilledIconButton(onClick = onSend, modifier = Modifier.size(40.dp), shape = CircleShape) {
            Icon(Icons.AutoMirrored.Filled.Send, "Send voice message", modifier = Modifier.size(18.dp))
        }
    }
}
