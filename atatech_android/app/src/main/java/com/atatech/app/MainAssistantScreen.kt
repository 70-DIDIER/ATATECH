package com.atatech.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
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
fun MainAssistantScreen(viewModel: AssistantViewModel = viewModel()) {
    val state by viewModel.assistantState.collectAsState()

    Scaffold(
        topBar = { AssistantTopBar() }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Zone conversation — Julien branchera ConversationList(viewModel) ici
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                // slot réservé
            }

            // Zone d'état — c'est TA responsabilité
            AssistantStatusArea(state = state)

            // Zone micro — Julien branchera MicButton(viewModel) ici
            Box(modifier = Modifier.padding(24.dp)) {
                // slot réservé
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssistantTopBar() {
    TopAppBar(title = { Text("ATATECH") })
}
