package com.atatech.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.atatech.app.api.ApiConfig
import com.atatech.app.api.MessageAssistant

@Composable
fun ConversationList(viewModel: AssistantViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.assistantState.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val baseUrl = ApiConfig.getBaseUrl(context).trimEnd('/')

    LaunchedEffect(state.tours.size) {
        if (state.tours.isNotEmpty()) {
            listState.animateScrollToItem(state.tours.size - 1)
        }
    }

    if (state.tours.isEmpty()) {
        Box(modifier = modifier.fillMaxSize().background(NyeGbe.Fond)) {
            Text(
                text = "Ndi o ! Parle ou écris pour commencer.",
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                color = NyeGbe.TexteDoux
            )
        }
        return
    }

    // La clé de chaque élément est stable : sans elle, Compose recycle les
    // bulles au mauvais endroit quand la liste grandit, et un lecteur audio
    // peut se retrouver rattaché à la mauvaise note (bulle qui « clignote »).
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().background(NyeGbe.Fond),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(
            items = state.tours,
            key = { index, tour -> cleStable(index, tour) }
        ) { _, tour ->
            when (tour) {
                is TourConversation.Utilisateur -> BulleUtilisateur(tour)
                is TourConversation.Vocal -> BulleVocale(tour)
                is TourConversation.Photo -> BullePhoto()
                is TourConversation.Assistant -> BulleAssistant(
                    message = tour.message,
                    baseUrl = baseUrl,
                    // Seul le DERNIER message parle tout seul : sinon toute la
                    // conversation se remettrait à parler à chaque recomposition.
                    lireAutomatiquement = tour === state.tours.lastOrNull()
                )
            }
        }
    }
}

private fun cleStable(index: Int, tour: TourConversation): String {
    return when (tour) {
        is TourConversation.Vocal -> "vocal-${tour.fichier.name}"
        is TourConversation.Photo -> "photo-${tour.fichier.name}"
        is TourConversation.Assistant -> "assistant-$index-${tour.message.cle}"
        is TourConversation.Utilisateur -> "user-$index"
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Côté utilisateur — à droite, en violet
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun BulleUtilisateur(tour: TourConversation.Utilisateur) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(
            color = NyeGbe.Violet,
            shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Text(
                // `affichage` montre « Mercredi » là où « 2 » est envoyé au serveur.
                text = tour.affichage,
                color = Color.White,
                fontSize = 15.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

/**
 * LA BULLE QUI MANQUAIT. Elle porte le lecteur audio, reste dans le fil pour
 * toute la conversation, et le fichier vit dans le cache de l'application :
 * la note est réécoutable autant de fois qu'on veut.
 */
@Composable
private fun BulleVocale(tour: TourConversation.Vocal) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(
            color = NyeGbe.Violet,
            shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
            modifier = Modifier.widthIn(min = 220.dp, max = 280.dp)
        ) {
            LecteurAudio(
                source = tour.fichier.absolutePath,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                couleurActive = Color.White,
                couleurInactive = Color.White.copy(alpha = 0.40f),
                couleurTexte = Color.White.copy(alpha = 0.85f),
                dureeConnueMs = tour.dureeMs
            )
        }
    }
}

@Composable
private fun BullePhoto() {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Surface(
            color = NyeGbe.Violet,
            shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null,
                     tint = Color.White, modifier = Modifier.size(18.dp))
                Text("Photo envoyée", color = Color.White, fontSize = 15.sp)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════════
// Côté assistant — à gauche, sur fond blanc. Trois niveaux, comme le web :
//   1. l'éwé SANS signes, en gros    2. l'éwé avec signes, discret
//   3. la traduction française encadrée
// ══════════════════════════════════════════════════════════════════════════════
@Composable
private fun BulleAssistant(
    message: MessageAssistant,
    baseUrl: String,
    lireAutomatiquement: Boolean
) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Surface(
            color = NyeGbe.Surface,
            shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, NyeGbe.Bordure),
            modifier = Modifier.widthIn(max = if (message.carte == "fiche") 330.dp else 290.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {

                if (message.carte == "fiche") {
                    message.titre?.let { TexteEwePrincipal(it, 16.sp) }
                    message.titre?.let { TexteEweSignes(it) }
                    message.titreFr?.let { BlocTraduction(it) }

                    message.lignes.forEach { ligne ->
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            TexteEwePrincipal("• " + ligne.ewe, 15.sp)
                            TexteEweSignes("• " + ligne.ewe)
                            Text(
                                text = ligne.fr,
                                fontSize = 12.5.sp,
                                color = NyeGbe.TexteDoux,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                    if (message.ewe.isNotBlank()) {
                        Column(modifier = Modifier.padding(top = 14.dp)) {
                            TexteEwePrincipal(message.ewe, 15.5.sp)
                            TexteEweSignes(message.ewe)
                        }
                    }
                    if (message.fr.isNotBlank()) BlocTraduction(message.fr)
                } else {
                    TexteEwePrincipal(message.ewe, 15.5.sp)
                    TexteEweSignes(message.ewe)
                    if (message.fr.isNotBlank()) BlocTraduction(message.fr)
                }

                // La voix éwé : lue automatiquement à l'arrivée (l'utilisateur ne
                // lit pas), et réécoutable ensuite. audio_url peut être null —
                // le texte suffit alors, ce n'est pas une erreur.
                message.audioUrl?.let { chemin ->
                    LecteurAudio(
                        source = baseUrl + chemin,
                        modifier = Modifier.padding(top = 10.dp).fillMaxWidth(),
                        couleurActive = NyeGbe.Violet,
                        couleurInactive = NyeGbe.VioletSoft,
                        couleurTexte = NyeGbe.TexteDiscret,
                        demarrerAutomatiquement = lireAutomatiquement
                    )
                }
            }
        }
    }
}

/** L'éwé débarrassé de ses signes : la ligne la plus lisible, en premier. */
@Composable
private fun TexteEwePrincipal(texte: String, taille: androidx.compose.ui.unit.TextUnit) {
    Text(
        text = sansSignes(texte),
        fontSize = taille,
        fontWeight = FontWeight.Medium,
        color = NyeGbe.Texte
    )
}

/** La même phrase avec les signes : la graphie correcte, en dessous, discrète. */
@Composable
private fun TexteEweSignes(texte: String) {
    if (sansSignes(texte) == texte) return      // rien à montrer en double
    Text(
        text = texte,
        fontSize = 13.sp,
        color = NyeGbe.TexteDiscret,
        modifier = Modifier.padding(top = 3.dp)
    )
}

@Composable
private fun BlocTraduction(texte: String) {
    Column(
        modifier = Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .border(1.dp, NyeGbe.Bordure, RoundedCornerShape(12.dp))
            .background(NyeGbe.Fond, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Text(
            text = "TRADUCTION",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.06.em,
            color = NyeGbe.TexteDiscret
        )
        Text(
            text = texte,
            fontSize = 14.sp,
            color = NyeGbe.TexteDoux,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
