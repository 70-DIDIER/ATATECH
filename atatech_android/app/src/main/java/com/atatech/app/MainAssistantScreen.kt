package com.atatech.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun MainAssistantScreen(
    viewModel: DemarcheViewModel = viewModel(),
    onOpenHistory: () -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val baseUrl = ApiPreferences.getBaseUrl(context)

    LaunchedEffect(Unit) {
        viewModel.startSession(context)
    }

    Scaffold(
        topBar = { AssistantTopBar(onOpenHistory = onOpenHistory, onOpenSettings = onOpenSettings) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Zone conversation (pilotée par l'API Démarches)
            DemarcheMessageList(
                viewModel = viewModel,
                baseUrl = baseUrl,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            if (errorMessage != null) {
                Text(
                    text = errorMessage ?: "",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            if (isLoading) {
                ThinkingIndicator()
            }

            // Zone de saisie (choix / photo / code masqué / texte selon `attend`)
            DemarcheInputArea(viewModel = viewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssistantTopBar(onOpenHistory: () -> Unit, onOpenSettings: () -> Unit) {
    TopAppBar(
        title = { Text("ATATECH") },
        actions = {
            IconButton(onClick = onOpenHistory) {
                Icon(Icons.Filled.History, contentDescription = "Historique des demandes")
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Paramètres")
            }
        }
    )
}
