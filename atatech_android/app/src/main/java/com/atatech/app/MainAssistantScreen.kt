package com.atatech.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun MainAssistantScreen(
    viewModel: AssistantViewModel = viewModel(),
    onOpenHistory: () -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val state by viewModel.assistantState.collectAsState()

    Scaffold(
        topBar = { AssistantTopBar(onOpenHistory = onOpenHistory, onOpenSettings = onOpenSettings) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Zone conversation
            ConversationList(
                viewModel = viewModel,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            // Zone d'état — responsabilité d'Afola
            AssistantStatusArea(state = state)

            // Zone micro
            Box(modifier = Modifier.padding(24.dp)) {
                MicButton(viewModel = viewModel)
            }
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

@Composable
private fun AssistantStatusArea(state: AssistantState) {
    when {
        state.isLoading -> CircularProgressIndicator(modifier = Modifier.padding(8.dp))
        state.errorMessage != null -> Text(text = state.errorMessage)
        else -> Text(text = "Prêt à vous écouter")
    }
}
