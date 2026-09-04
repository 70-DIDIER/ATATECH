package com.atatech.app

import android.media.MediaPlayer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atatech.app.api.ApiConfig
import com.atatech.app.api.MessageAssistant

@Composable
fun ConversationList(viewModel: AssistantViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.assistantState.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    LaunchedEffect(state.tours.size) {
        if (state.tours.isNotEmpty()) {
            listState.animateScrollToItem(state.tours.size - 1)
        }
    }

    // Lit à voix haute le dernier message de l'assistant — comportement attendu, voir Prompt A.
    val dernierMessageAssistant = state.tours.lastOrNull { it is TourConversation.Assistant }
        as? TourConversation.Assistant
    DisposableEffect(dernierMessageAssistant?.message?.audioUrl) {
        val url = dernierMessageAssistant?.message?.audioUrl
        var lecteur: MediaPlayer? = null
        if (url != null) {
            val urlComplete = ApiConfig.getBaseUrl(context).trimEnd('/') + url
            lecteur = MediaPlayer().apply {
                try {
                    setDataSource(urlComplete)
                    setOnPreparedListener { start() }
                    prepareAsync()
                } catch (_: Exception) {
                    // audio_url peut pointer vers un fichier pas encore généré — on ignore, le texte suffit.
                }
            }
        }
        onDispose { lecteur?.release() }
    }

    if (state.tours.isEmpty()) {
        Box(modifier = modifier.fillMaxSize()) {
            Text(
                text = "Ndi o ! Écris ou parle pour commencer.",
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(state.tours) { tour ->
            when (tour) {
                is TourConversation.Utilisateur -> BulleUtilisateur(tour)
                is TourConversation.Assistant -> BulleAssistant(tour.message)
            }
        }
    }
}

@Composable
private fun BulleUtilisateur(tour: TourConversation.Utilisateur) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(
            color = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                text = tour.texte,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

/** Éwé en premier et en gros, français en dessous en petit — voir §4. */
@Composable
private fun BulleAssistant(message: MessageAssistant) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        if (message.carte == "fiche") {
            Card(modifier = Modifier.widthIn(max = 320.dp)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    message.titre?.let {
                        Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    message.titreFr?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    message.lignes.forEach { ligne ->
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            Text("• ${ligne.ewe}", style = MaterialTheme.typography.bodyMedium)
                            Text(ligne.fr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Column(modifier = Modifier.padding(top = 10.dp)) {
                        Text(message.ewe, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(message.fr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        } else {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.widthIn(max = 280.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(
                        text = message.ewe,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = message.fr,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
