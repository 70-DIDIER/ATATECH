package com.atatech.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

@Composable
fun MicButton(viewModel: AssistantViewModel) {
    val context = LocalContext.current
    var showRationale by remember { mutableStateOf(false) }
    var showSettingsPrompt by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startListening()
        } else {
            val activity = context as Activity
            if (!activity.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
                showSettingsPrompt = true
            }
        }
    }

    IconButton(onClick = {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        when {
            granted -> viewModel.startListening()
            else -> launcher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }) {
        Icon(Icons.Default.Mic, contentDescription = "Parler à l'assistant")
    }

    if (showSettingsPrompt) {
        AlertDialog(
            onDismissRequest = { showSettingsPrompt = false },
            title = { Text("Micro désactivé") },
            text = { Text("Active le micro dans les paramètres pour parler à l'assistant.") },
            confirmButton = {
                TextButton(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                    )
                    showSettingsPrompt = false
                }) { Text("Ouvrir les paramètres") }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsPrompt = false }) { Text("Annuler") }
            }
        )
    }
}
