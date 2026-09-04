package com.atatech.app

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MessageInputBar(viewModel: AssistantViewModel) {
    val input by viewModel.currentInput.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = viewModel::onInputChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Écris un message...") },
            singleLine = true
        )

        IconButton(onClick = {
            // TODO: ouvrir le selecteur d'image
        }) {
            Icon(Icons.Default.Image, contentDescription = "Joindre une image")
        }

        MicButton(viewModel = viewModel, modifier = Modifier.size(40.dp))

        IconButton(onClick = { viewModel.sendMessage() }) {
            Icon(Icons.Default.Send, contentDescription = "Envoyer")
        }
    }
}
