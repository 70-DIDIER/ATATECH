package com.atatech.app

import android.Manifest
import android.media.MediaRecorder
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
fun MicButton(viewModel: AssistantViewModel, modifier: Modifier = Modifier.size(72.dp)) {
    val context = LocalContext.current
    var isRecording by remember { mutableStateOf(false) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var outputFile by remember { mutableStateOf<File?>(null) }

    val permissionState = rememberPermissionState(
        permission = Manifest.permission.RECORD_AUDIO,
        onResult = { granted -> if (granted) isRecording = true }
    )

    DisposableEffect(Unit) {
        onDispose { recorder?.release() }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            val file = File(context.filesDir, "note_${System.currentTimeMillis()}.m4a")
            @Suppress("DEPRECATION")
            val mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = mediaRecorder
            outputFile = file
        } else {
            val finishedFile = outputFile
            recorder?.apply {
                try {
                    stop()
                } catch (e: RuntimeException) {
                    // Enregistrement trop court ou invalide, on ignore le fichier
                }
                release()
            }
            recorder = null
            outputFile = null

            if (finishedFile != null && finishedFile.exists() && finishedFile.length() > 0) {
                viewModel.sendAudioMessage(finishedFile.absolutePath)
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "mic-pulse")
    val pulseScale = if (isRecording) {
        val animated by infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.22f,
            animationSpec = infiniteRepeatable(
                animation = tween(600),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse"
        )
        animated
    } else {
        1f
    }

    val backgroundColor by animateColorAsState(
        targetValue = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        label = "mic-color"
    )

    IconButton(
        onClick = {
            if (isRecording) {
                isRecording = false
            } else if (permissionState.isGranted) {
                isRecording = true
            } else {
                permissionState.request()
            }
        },
        modifier = modifier
            .scale(pulseScale)
            .background(backgroundColor, CircleShape)
    ) {
        Icon(
            imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
            contentDescription = if (isRecording) "Arrêter l'enregistrement" else "Enregistrer une note vocale",
            tint = Color.White
        )
    }
}
