package com.atatech.app

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
import androidx.compose.material.icons.filled.MicOff
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
import java.util.Locale

@Composable
fun MicButton(viewModel: AssistantViewModel) {
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }

    val permissionState = rememberPermissionState(
        permission = Manifest.permission.RECORD_AUDIO,
        onResult = { granted -> if (granted) isListening = true }
    )

    val recognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }
    }

    DisposableEffect(Unit) {
        onDispose { recognizer?.destroy() }
    }

    LaunchedEffect(isListening) {
        if (isListening && recognizer != null) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.FRENCH)
            }
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                    if (!text.isNullOrBlank()) {
                        viewModel.onInputChange(text)
                        viewModel.sendMessage()
                    }
                    isListening = false
                }

                override fun onError(error: Int) {
                    isListening = false
                }

                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            recognizer.startListening(intent)
        } else if (!isListening) {
            recognizer?.stopListening()
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "mic-pulse")
    val pulseScale = if (isListening) {
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
        targetValue = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        label = "mic-color"
    )

    IconButton(
        onClick = {
            if (permissionState.isGranted) {
                isListening = !isListening
            } else {
                permissionState.request()
            }
        },
        modifier = Modifier
            .size(72.dp)
            .scale(pulseScale)
            .background(backgroundColor, CircleShape)
    ) {
        Icon(
            imageVector = if (isListening) Icons.Filled.MicOff else Icons.Filled.Mic,
            contentDescription = if (isListening) "Arrêter l'écoute" else "Démarrer l'écoute",
            tint = Color.White
        )
    }
}
