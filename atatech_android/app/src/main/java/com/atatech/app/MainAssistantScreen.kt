package com.atatech.app

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
    viewModel: AssistantViewModel = viewModel(),
    onOpenHistory: () -> Unit = {},
    onOpenSettings: () -> Unit = {}
) {
    val context = LocalContext.current
    val state by viewModel.assistantState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.demarrer(context)
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
            ConversationList(
                viewModel = viewModel,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            AssistantStatusArea(isLoading = state.isLoading, errorMessage = state.errorMessage)

            AssistantInputArea(
                attend = state.attendActuel,
                fini = state.fini,
                isLoading = state.isLoading,
                onEnvoyerTexte = { texte, libelle -> viewModel.envoyerTexte(context, texte, libelle) },
                onEnvoyerVoix = { note -> viewModel.envoyerVoix(context, note) },
                onEnvoyerPhoto = { fichier -> viewModel.envoyerPhoto(context, fichier) },
                onRecommencer = { viewModel.recommencer() }
            )
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
private fun AssistantStatusArea(isLoading: Boolean, errorMessage: String?) {
    when {
        isLoading -> CircularProgressIndicator(modifier = Modifier.padding(8.dp))
        errorMessage != null -> Text(text = errorMessage, modifier = Modifier.padding(8.dp))
    }
}
