package com.atatech.app

import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun DemarcheMessageList(viewModel: DemarcheViewModel, baseUrl: String, modifier: Modifier = Modifier) {
    val messages by viewModel.messages.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val lastAudioUrl = messages.lastOrNull()?.audioUrl
    LaunchedEffect(lastAudioUrl) {
        if (lastAudioUrl == null) return@LaunchedEffect
        val fullUrl = baseUrl.trimEnd('/') + lastAudioUrl
        try {
            val player = MediaPlayer()
            player.setDataSource(fullUrl)
            player.setOnCompletionListener { it.release() }
            player.setOnPreparedListener { it.start() }
            player.prepareAsync()
        } catch (e: Exception) {
            // Voix indisponible : on continue sans bloquer l'utilisateur
        }
    }

    if (messages.isEmpty()) {
        Box(modifier = modifier.fillMaxSize()) {
            Text(
                text = "Chargement de l'assistant…",
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(messages, key = { index, _ -> index }) { _, message ->
            DemarcheMessageBubble(message)
        }
    }
}

@Composable
private fun DemarcheMessageBubble(message: DemarcheMessage) {
    if (message.carte == "fiche") {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                message.titre?.let {
                    Text(it, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                message.titreFr?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                message.lignes.forEach { ligne ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text("• ${ligne.ewe}", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            ligne.fr,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Text(
                    message.ewe,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    message.fr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    message.ewe,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    message.fr,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
