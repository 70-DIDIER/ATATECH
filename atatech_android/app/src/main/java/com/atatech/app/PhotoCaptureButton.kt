package com.atatech.app

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File

/** Ouvre l'appareil photo (ActivityResult + FileProvider, voir §6) et renvoie le fichier via [onPhotoReady]. */
@Composable
fun PhotoCaptureButton(enabled: Boolean = true, onPhotoReady: (File) -> Unit) {
    val context = LocalContext.current
    var fichierEnAttente by remember { mutableStateOf<File?>(null) }

    val cameraPermission = rememberPermissionState(
        permission = Manifest.permission.CAMERA,
        onResult = {}
    )

    val lanceur = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { succes ->
        val fichier = fichierEnAttente
        if (succes && fichier != null) {
            onPhotoReady(fichier)
        }
        fichierEnAttente = null
    }

    Button(
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        onClick = {
            if (!cameraPermission.isGranted) {
                cameraPermission.request()
                return@Button
            }
            val dossier = File(context.cacheDir, "photos").apply { mkdirs() }
            val fichier = File(dossier, "photo_${System.currentTimeMillis()}.jpg")
            fichierEnAttente = fichier
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", fichier)
            lanceur.launch(uri)
        }
    ) {
        Icon(Icons.Filled.PhotoCamera, contentDescription = null)
        Text(text = "  Prendre une photo")
    }
}
