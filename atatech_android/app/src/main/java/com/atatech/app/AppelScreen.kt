package com.atatech.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Une ligne du journal affiché à l'écran — purement informatif. */
private sealed class LigneJournal {
    data class Utilisateur(val texte: String) : LigneJournal()
    data class Info(val texte: String) : LigneJournal()
    data class Erreur(val texte: String) : LigneJournal()
}

/** Ce qui attend une décision de l'utilisateur, au-dessus de la barre de saisie. */
private sealed class EtapeEnAttente {
    object Rien : EtapeEnAttente()
    data class Choix(val candidats: List<ContactTrouve>) : EtapeEnAttente()
    data class Confirmation(val nomAffiche: String, val numero: String) : EtapeEnAttente()
}

/**
 * Écran « Appeler quelqu'un » — SANS backend, SANS simulation : la commande
 * est comprise sur le téléphone (CommandeAppelParser), le contact vient du
 * VRAI répertoire (ContactLookup), et l'appel est un VRAI Intent.ACTION_CALL,
 * jamais déclenché sans un appui explicite de l'utilisateur.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppelScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val portee = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var journal by remember { mutableStateOf<List<LigneJournal>>(emptyList()) }
    var etape by remember { mutableStateOf<EtapeEnAttente>(EtapeEnAttente.Rien) }
    var texte by remember { mutableStateOf("") }
    // Recherche à refaire dès que la permission Contacts vient d'être accordée.
    var recherchePendante by remember { mutableStateOf<String?>(null) }

    fun ajouter(ligne: LigneJournal) {
        journal = journal + ligne
    }

    fun chercherContact(nom: String) {
        portee.launch {
            val candidats = withContext(Dispatchers.IO) { ContactLookup.rechercher(context, nom) }
            when {
                candidats.isEmpty() -> {
                    ajouter(LigneJournal.Erreur("Aucun contact « $nom » trouvé dans le répertoire."))
                    etape = EtapeEnAttente.Rien
                }
                candidats.size == 1 -> {
                    etape = EtapeEnAttente.Confirmation(candidats[0].nom, candidats[0].numero)
                }
                else -> etape = EtapeEnAttente.Choix(candidats)
            }
        }
    }

    val permissionContacts = rememberPermissionState(
        permission = Manifest.permission.READ_CONTACTS,
        onResult = { accorde ->
            val nom = recherchePendante
            recherchePendante = null
            if (accorde && nom != null) {
                chercherContact(nom)
            } else if (!accorde) {
                ajouter(LigneJournal.Erreur("Permission Contacts refusée — impossible de chercher « ${nom.orEmpty()} »."))
            }
        }
    )

    val permissionAppel = rememberPermissionState(
        permission = Manifest.permission.CALL_PHONE,
        onResult = { accorde ->
            val cible = (etape as? EtapeEnAttente.Confirmation)
            if (cible != null) {
                lancerAppel(context, cible.numero, appelDirect = accorde)
                ajouter(LigneJournal.Info(messageApresAppel(cible.nomAffiche, appelDirect = accorde)))
                etape = EtapeEnAttente.Rien
            }
        }
    )

    fun soumettre() {
        val phrase = texte.trim()
        if (phrase.isEmpty()) return
        ajouter(LigneJournal.Utilisateur(phrase))
        texte = ""

        when (val commande = analyserCommandeAppel(phrase)) {
            null -> ajouter(LigneJournal.Erreur(
                "Je n'ai reconnu qu'une commande d'appel. Essaie : « Appelle maman »."
            ))
            is CommandeAppel.VersNumero -> {
                etape = EtapeEnAttente.Confirmation(commande.numero, commande.numero)
            }
            is CommandeAppel.VersContact -> {
                if (permissionContacts.isGranted) {
                    chercherContact(commande.nom)
                } else {
                    recherchePendante = commande.nom
                    permissionContacts.request()
                }
            }
        }
    }

    LaunchedEffect(journal.size) {
        if (journal.isNotEmpty()) listState.animateScrollToItem(journal.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appeler quelqu'un") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            if (journal.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth().background(NyeGbe.Fond)) {
                    Text(
                        text = "Écris par exemple « Appelle maman » ou « Appelle le 90 00 00 00 ».",
                        modifier = Modifier.padding(24.dp).fillMaxWidth(),
                        color = NyeGbe.TexteDoux
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth().background(NyeGbe.Fond),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(journal) { ligne -> LigneJournalVue(ligne) }
                }
            }

            when (val e = etape) {
                is EtapeEnAttente.Choix -> ZoneChoixContact(
                    candidats = e.candidats,
                    onChoisir = { candidat ->
                        etape = EtapeEnAttente.Confirmation(candidat.nom, candidat.numero)
                    },
                    onAnnuler = { etape = EtapeEnAttente.Rien }
                )
                is EtapeEnAttente.Confirmation -> ZoneConfirmationAppel(
                    nomAffiche = e.nomAffiche,
                    numero = e.numero,
                    onAnnuler = { etape = EtapeEnAttente.Rien },
                    onAppeler = {
                        if (permissionAppel.isGranted) {
                            lancerAppel(context, e.numero, appelDirect = true)
                            ajouter(LigneJournal.Info(messageApresAppel(e.nomAffiche, appelDirect = true)))
                            etape = EtapeEnAttente.Rien
                        } else {
                            permissionAppel.request()
                        }
                    }
                )
                EtapeEnAttente.Rien -> Unit
            }

            BarreSaisieAppel(
                texte = texte,
                onTexteChange = { texte = it },
                onEnvoyer = ::soumettre
            )
        }
    }
}

/** Message honnête selon ce qui s'est vraiment passé : un appel réel, ou
 *  juste le composeur ouvert (permission refusée). */
private fun messageApresAppel(nomAffiche: String, appelDirect: Boolean): String =
    if (appelDirect) "Appel de $nomAffiche lancé."
    else "Composeur ouvert pour $nomAffiche (permission d'appel refusée — appuie sur le bouton vert pour appeler)."

/**
 * Place le vrai appel. `appelDirect` = la permission CALL_PHONE est accordée
 * → `ACTION_CALL` (l'appel part vraiment). Sinon, repli sur `ACTION_DIAL`
 * (ouvre le composeur pré-rempli, ne nécessite AUCUNE permission) plutôt que
 * de bloquer l'utilisateur.
 */
private fun lancerAppel(context: android.content.Context, numero: String, appelDirect: Boolean) {
    val action = if (appelDirect) Intent.ACTION_CALL else Intent.ACTION_DIAL
    val intent = Intent(action, Uri.parse("tel:$numero")).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
        .onFailure {
            // CALL_PHONE accordée en théorie mais l'appel échoue quand même
            // (double-SIM capricieux, etc.) : on retente en ACTION_DIAL plutôt
            // que de planter.
            if (appelDirect) {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_DIAL, Uri.parse("tel:$numero"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }
}

@Composable
private fun LigneJournalVue(ligne: LigneJournal) {
    when (ligne) {
        is LigneJournal.Utilisateur -> Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            Surface(
                color = NyeGbe.Violet,
                shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
            ) {
                Text(ligne.texte, color = Color.White, fontSize = 15.sp,
                     modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
            }
        }
        is LigneJournal.Info -> Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            Surface(
                color = NyeGbe.Surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, NyeGbe.Bordure),
                shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
            ) {
                Text(ligne.texte, color = NyeGbe.Texte, fontSize = 15.sp,
                     modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
            }
        }
        is LigneJournal.Erreur -> Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            Surface(
                color = NyeGbe.Surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, NyeGbe.Erreur),
                shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
            ) {
                Text(ligne.texte, color = NyeGbe.Erreur, fontSize = 14.sp,
                     modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
            }
        }
    }
}

@Composable
private fun ZoneChoixContact(
    candidats: List<ContactTrouve>,
    onChoisir: (ContactTrouve) -> Unit,
    onAnnuler: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Plusieurs contacts correspondent — lequel ?", fontSize = 13.sp, color = NyeGbe.TexteDoux)
        candidats.forEach { candidat ->
            Button(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NyeGbe.Violet),
                onClick = { onChoisir(candidat) }
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(candidat.nom, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White)
                    Text(candidat.numero, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
                }
            }
        }
        OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = onAnnuler) { Text("Annuler") }
    }
}

@Composable
private fun ZoneConfirmationAppel(
    nomAffiche: String,
    numero: String,
    onAnnuler: () -> Unit,
    onAppeler: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .background(NyeGbe.VioletPale, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Appeler", fontSize = 12.sp, color = NyeGbe.TexteDiscret)
            Text(nomAffiche, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = NyeGbe.Texte)
            if (nomAffiche != numero) {
                Text(numero, fontSize = 13.sp, color = NyeGbe.TexteDoux)
            }
        }
        OutlinedButton(onClick = onAnnuler) { Text("Annuler") }
        Button(
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = NyeGbe.Valide),
            onClick = onAppeler
        ) {
            Icon(Icons.Filled.Call, contentDescription = null, tint = Color.White, modifier = Modifier.padding(end = 6.dp))
            Text("Appeler", color = Color.White)
        }
    }
}

@Composable
private fun BarreSaisieAppel(
    texte: String,
    onTexteChange: (String) -> Unit,
    onEnvoyer: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(NyeGbe.Surface)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 40.dp)
                .background(NyeGbe.Fond, RoundedCornerShape(20.dp))
                .border(1.dp, NyeGbe.Bordure, RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 9.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (texte.isEmpty()) {
                Text("Appelle maman…", color = NyeGbe.TexteDiscret, fontSize = 14.sp)
            }
            BasicTextField(
                value = texte,
                onValueChange = onTexteChange,
                singleLine = true,
                textStyle = TextStyle(color = NyeGbe.Texte, fontSize = 14.sp),
                cursorBrush = SolidColor(NyeGbe.Violet),
                modifier = Modifier.fillMaxWidth()
            )
        }
        IconButton(
            onClick = onEnvoyer,
            enabled = texte.isNotBlank(),
            modifier = Modifier.background(
                if (texte.isNotBlank()) NyeGbe.Violet else Color.Transparent,
                androidx.compose.foundation.shape.CircleShape
            )
        ) {
            Icon(
                Icons.Filled.Send,
                contentDescription = "Envoyer",
                tint = if (texte.isNotBlank()) Color.White else NyeGbe.TexteDiscret
            )
        }
    }
}
