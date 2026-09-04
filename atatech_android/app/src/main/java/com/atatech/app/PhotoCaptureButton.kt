package com.atatech.app

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File

/**
 * Ouvre l'appareil photo (ActivityResult + FileProvider, voir §6) et renvoie le
 * fichier via [onPhotoReady].
 *
 * ORDRE DE DÉCLARATION IMPORTANT : le lanceur de l'appareil photo doit exister
 * AVANT l'état de permission, parce que la réponse à la demande de permission
 * doit pouvoir le déclencher. Au tout premier appui, la caméra n'est pas encore
 * autorisée : sans cet enchaînement, l'utilisateur accorde la permission et il
 * ne se passe rien — il doit réappuyer sans comprendre pourquoi. C'est le même
 * défaut que celui corrigé sur le micro.
 */
@Composable
fun PhotoCaptureButton(enabled: Boolean = true, onPhotoReady: (File) -> Unit) {
    val context = LocalContext.current
    var fichierEnAttente by remember { mutableStateOf<File?>(null) }

    val lanceur = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { succes ->
        val fichier = fichierEnAttente
        if (succes && fichier != null) {
            onPhotoReady(fichier)
        } else {
            // Photo annulée ou échouée : on ne laisse pas traîner un fichier vide.
            fichier?.delete()
        }
        fichierEnAttente = null
    }

    val ouvrirAppareilPhoto = {
        val dossier = File(context.cacheDir, "photos").apply { mkdirs() }
        val fichier = File(dossier, "photo_${System.currentTimeMillis()}.jpg")
        fichierEnAttente = fichier
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", fichier
        )
        lanceur.launch(uri)
    }

    val permissionCamera = rememberPermissionState(
        permission = Manifest.permission.CAMERA,
        onResult = { accorde -> if (accorde) ouvrirAppareilPhoto() }
    )

    Button(
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = NyeGbe.Violet),
        onClick = {
            if (permissionCamera.isGranted) {
                ouvrirAppareilPhoto()
            } else {
                permissionCamera.request()
            }
        }
    ) {
        Icon(Icons.Filled.PhotoCamera, contentDescription = null, tint = Color.White)
        Text(
            text = "  Prendre une photo",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
