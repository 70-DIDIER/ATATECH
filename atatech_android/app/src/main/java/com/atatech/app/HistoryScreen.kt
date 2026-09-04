package com.atatech.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val sampleRequests = listOf(
    PastRequest("1", "Demande de carte de nationalité", RequestStatus.EN_COURS, "02/09/2026"),
    PastRequest("2", "Certificat de résidence", RequestStatus.VALIDEE, "28/08/2026"),
    PastRequest("3", "Acte de naissance", RequestStatus.REJETEE, "15/08/2026")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historique des demandes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        if (sampleRequests.isEmpty()) {
            Text(
                text = "Aucune demande pour le moment",
                modifier = Modifier.padding(padding).padding(16.dp)
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp)
        ) {
            items(sampleRequests, key = { it.id }) { request ->
                RequestCard(request, modifier = Modifier.padding(bottom = 12.dp))
            }
        }
    }
}

@Composable
private fun RequestCard(request: PastRequest, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = request.title, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text(
                    text = request.date,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                StatusBadge(request.status)
            }
        }
    }
}

@Composable
private fun StatusBadge(status: RequestStatus) {
    val (label, color) = when (status) {
        RequestStatus.EN_COURS -> "En cours" to MaterialTheme.colorScheme.tertiary
        RequestStatus.VALIDEE -> "Validée" to MaterialTheme.colorScheme.primary
        RequestStatus.REJETEE -> "Rejetée" to MaterialTheme.colorScheme.error
    }
    Text(text = label, color = color, fontWeight = FontWeight.Medium)
}
