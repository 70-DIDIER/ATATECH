package com.atatech.app

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

/**
 * Toute la mécanique « prendre une photo » — permission, fichier temporaire,
 * FileProvider, intent caméra — SANS imposer d'apparence.
 *
 * Le composant appelant dessine le bouton qu'il veut et reçoit `ouvrir` : la
 * barre de saisie en fait une petite icône ronde à côté du micro. C'est ce qui
 * permet d'avoir la photo comme mode de réponse permanent, au lieu d'un gros
 * bouton qui remplaçait toute la barre quand l'étape attendait une image.
 *
 * ORDRE DE DÉCLARATION IMPORTANT : le lanceur de l'appareil photo doit exister
 * AVANT l'état de permission, parce que la réponse à la demande de permission
 * doit pouvoir le déclencher. Au tout premier appui, la caméra n'est pas encore
 * autorisée : sans cet enchaînement, l'utilisateur accorde la permission et il
 * ne se passe rien — il doit réappuyer sans comprendre pourquoi.
 */
@Composable
fun PhotoCaptureAction(
    onPhotoReady: (File) -> Unit,
    contenu: @Composable (ouvrir: () -> Unit) -> Unit
) {
    val context = LocalContext.current
    var fichierEnAttente by remember { mutableStateOf<File?>(null) }

    val lanceur = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { succes ->
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

    contenu {
        if (permissionCamera.isGranted) ouvrirAppareilPhoto()
        else permissionCamera.request()
    }
}
