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
import androidx.compose.material.icons.filled.Emergency
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
fun SosButton(viewModel: AssistantViewModel) {
    val context = LocalContext.current
    var showSettingsPrompt by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.sendSosAlert()
        } else {
            val activity = context as Activity
            if (!activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)) {
                showSettingsPrompt = true
            }
        }
    }

    IconButton(onClick = {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        when {
            granted -> viewModel.sendSosAlert()
            else -> launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }) {
        Icon(Icons.Default.Emergency, contentDescription = "Envoyer une alerte SOS")
    }

    if (showSettingsPrompt) {
        AlertDialog(
            onDismissRequest = { showSettingsPrompt = false },
            title = { Text("Localisation désactivée") },
            text = { Text("Active la localisation dans les paramètres pour envoyer une alerte SOS.") },
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
