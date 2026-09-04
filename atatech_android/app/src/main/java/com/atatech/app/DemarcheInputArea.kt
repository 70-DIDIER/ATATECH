package com.atatech.app

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import java.io.File

@Composable
fun DemarcheInputArea(viewModel: DemarcheViewModel) {
    val attend by viewModel.attend.collectAsState()
    val isFini by viewModel.isFini.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    if (isFini) {
        Text(
            text = "Parcours terminé",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )
        return
    }

    when (attend?.type) {
        "choix" -> ChoixInput(viewModel, attend?.options.orEmpty(), enabled = !isLoading)
        "photo" -> PhotoInput(viewModel, enabled = !isLoading)
        "code_secret" -> CodeSecretInput(viewModel, attend, enabled = !isLoading)
        "texte" -> TexteInput(viewModel, enabled = !isLoading)
        else -> {}
    }
}

@Composable
private fun ChoixInput(viewModel: DemarcheViewModel, options: List<ChoixOption>, enabled: Boolean) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        options.forEach { option ->
            Button(
                onClick = { viewModel.sendText(context, option.num.toString()) },
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Text(option.fr)
            }
        }
    }
}

@Composable
private fun PhotoInput(viewModel: DemarcheViewModel, enabled: Boolean) {
    val context = LocalContext.current
    var pendingFile by remember { mutableStateOf<File?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingFile
        if (success && file != null) {
            viewModel.sendPhoto(context, file)
        }
        pendingFile = null
    }

    fun launchCamera() {
        val file = createTempImageFile(context)
        pendingFile = file
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        launcher.launch(uri)
    }

    val permissionState = rememberPermissionState(
        permission = Manifest.permission.CAMERA,
        onResult = { granted -> if (granted) launchCamera() }
    )

    Button(
        onClick = {
            if (permissionState.isGranted) launchCamera() else permissionState.request()
        },
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        Text("Prendre une photo")
    }
}

private fun createTempImageFile(context: Context): File {
    val dir = File(context.cacheDir, "images").apply { mkdirs() }
    return File(dir, "photo_${System.currentTimeMillis()}.jpg")
}

@Composable
private fun CodeSecretInput(viewModel: DemarcheViewModel, attend: Attend?, enabled: Boolean) {
    val context = LocalContext.current
    var code by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        if (attend?.montant != null) {
            Text(
                text = "Montant : ${attend.montant} ${attend.devise ?: "FCFA"}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                visualTransformation = PasswordVisualTransformation('•'),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                placeholder = { Text("••••") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                enabled = enabled,
                onClick = {
                    if (code.isNotBlank()) {
                        val toSend = code
                        code = ""
                        viewModel.sendText(context, toSend)
                    }
                }
            ) {
                Icon(Icons.Default.Send, contentDescription = "Envoyer")
            }
        }
    }
}

@Composable
private fun TexteInput(viewModel: DemarcheViewModel, enabled: Boolean) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Écris ta réponse...") },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            enabled = enabled,
            onClick = {
                if (text.isNotBlank()) {
                    val toSend = text
                    text = ""
                    viewModel.sendText(context, toSend)
                }
            }
        ) {
            Icon(Icons.Default.Send, contentDescription = "Envoyer")
        }
    }
}
