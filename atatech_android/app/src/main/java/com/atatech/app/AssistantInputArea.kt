package com.atatech.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.atatech.app.api.Attend
import java.io.File

/**
 * Le champ attend.type de chaque message pilote ce qui s'affiche ici — voir §3 de la doc.
 * On ne devine jamais l'étape à partir du texte reçu.
 */
@Composable
fun AssistantInputArea(
    attend: Attend?,
    fini: Boolean,
    isLoading: Boolean,
    onEnvoyerTexte: (String) -> Unit,
    onEnvoyerPhoto: (File) -> Unit,
    onRecommencer: () -> Unit
) {
    if (fini || attend?.type == "rien") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Démarche terminée.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onRecommencer, modifier = Modifier.padding(top = 8.dp)) {
                Text("Nouvelle demande")
            }
        }
        return
    }

    when (attend?.type) {
        "choix" -> ZoneChoix(
            options = attend.options.orEmpty(),
            enabled = !isLoading,
            onChoisir = { option -> onEnvoyerTexte(option.num.toString()) }
        )

        "photo" -> PhotoCaptureButton(enabled = !isLoading, onPhotoReady = onEnvoyerPhoto)

        "code_secret" -> ZoneTexteLibre(
            masque = true,
            enabled = !isLoading,
            placeholder = "Code secret (${attend.montant ?: ""} ${attend.devise ?: ""})".trim(),
            onEnvoyer = onEnvoyerTexte
        )

        else -> ZoneTexteLibre(
            masque = false,
            enabled = !isLoading,
            placeholder = "Écris ta réponse…",
            onEnvoyer = onEnvoyerTexte
        )
    }
}

@Composable
private fun ZoneChoix(
    options: List<com.atatech.app.api.OptionChoix>,
    enabled: Boolean,
    onChoisir: (com.atatech.app.api.OptionChoix) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            Button(
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onChoisir(option) }
            ) {
                Column {
                    Text(option.ewe)
                    Text(option.fr, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun ZoneTexteLibre(
    masque: Boolean,
    enabled: Boolean,
    placeholder: String,
    onEnvoyer: (String) -> Unit
) {
    var texte by remember(masque) { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = texte,
            onValueChange = { texte = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text(placeholder) },
            singleLine = true,
            enabled = enabled,
            visualTransformation = if (masque) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = if (masque) KeyboardOptions(keyboardType = KeyboardType.NumberPassword) else KeyboardOptions.Default
        )

        MicButton(enabled = enabled) { reconnu -> texte = reconnu }

        IconButton(
            enabled = enabled && texte.isNotBlank(),
            onClick = {
                onEnvoyer(texte)
                texte = ""
            }
        ) {
            Icon(Icons.Filled.Send, contentDescription = "Envoyer")
        }
    }
}
