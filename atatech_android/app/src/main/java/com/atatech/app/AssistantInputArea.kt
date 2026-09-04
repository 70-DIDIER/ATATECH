package com.atatech.app

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atatech.app.api.Attend
import java.io.File
import kotlinx.coroutines.delay

/**
 * LA BARRE DE SAISIE EST PERMANENTE.
 *
 * Elle offre TOUJOURS les trois entrées — note vocale, appareil photo, texte —
 * plus le bouton envoyer, quelle que soit l'étape du scénario. C'est
 * l'utilisateur qui choisit son mode et qui envoie lui-même.
 *
 * Avant, une étape « photo » remplaçait toute la barre par un unique bouton
 * « Prendre une photo » : l'utilisateur perdait l'accès aux autres modes et
 * l'application décidait à sa place. Désormais, l'étape ne change qu'UNE seule
 * chose, non bloquante : une SUGGESTION affichée au-dessus de la barre.
 *
 * Aucun changement d'étape ne déclenche d'action : ni intent caméra, ni envoi.
 * Les deux états transitoires de la barre (enregistrement, relecture) sont
 * déclenchés par l'utilisateur lui-même, exactement comme sur le web.
 *
 * Pas de boutons de choix : au menu, l'utilisateur répond à la voix et le
 * scénario suit son ordre — nationalité d'abord, passeport ensuite.
 */
@Composable
fun AssistantInputArea(
    attend: Attend?,
    fini: Boolean,
    isLoading: Boolean,
    onEnvoyerTexte: (texte: String, libelle: String?) -> Unit,
    onEnvoyerVoix: (NoteVocale) -> Unit,
    onEnvoyerPhoto: (File) -> Unit,
    onRecommencer: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().background(NyeGbe.Surface)) {

        // Fin de parcours : on le dit, sans retirer la barre.
        if (fini) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Démarche terminée.",
                    color = NyeGbe.TexteDoux,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = onRecommencer) { Text("Nouvelle demande", fontSize = 13.sp) }
            }
        }

        // La suggestion informe, elle ne contraint pas.
        suggestion(attend, fini)?.let { texte ->
            Text(
                text = texte,
                fontSize = 12.sp,
                color = NyeGbe.TexteDiscret,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp)
            )
        }

        BarrePermanente(
            masque = attend?.type == "code_secret",
            enabled = !isLoading,
            onEnvoyerTexte = onEnvoyerTexte,
            onEnvoyerVoix = onEnvoyerVoix,
            onEnvoyerPhoto = onEnvoyerPhoto
        )
    }
}

/** Noms lisibles des pièces : le code technique du serveur (`acte_naissance`)
 *  ne veut rien dire pour l'utilisateur. */
private val PIECES = mapOf(
    "acte_naissance" to "l'acte de naissance",
    "nationalite_parent" to "le certificat de nationalité d'un parent",
    "certificat_residence" to "le certificat de résidence (ou une facture)",
    "photo_identite" to "la photo d'identité",
    "cni" to "la carte d'identité (ou la carte d'électeur)",
    "attestation_profession" to "l'attestation de profession"
)

private fun suggestion(attend: Attend?, fini: Boolean): String? {
    if (fini) return null
    return when (attend?.type) {
        "photo" -> "Photographie ${PIECES[attend.piece] ?: "le document demandé"}."
        "code_secret" -> {
            val montant = attend.montant?.let { " ($it ${attend.devise.orEmpty()})".trimEnd() }
            "Tape ton code${montant.orEmpty()} — il n'est ni enregistré ni relu."
        }
        else -> null
    }
}

/**
 * La barre elle-même : compacte, arrondie, quatre éléments alignés.
 * Elle ne connaît PAS l'étape du scénario — seulement si la saisie doit être
 * masquée (code secret).
 */
@Composable
private fun BarrePermanente(
    masque: Boolean,
    enabled: Boolean,
    onEnvoyerTexte: (String, String?) -> Unit,
    onEnvoyerVoix: (NoteVocale) -> Unit,
    onEnvoyerPhoto: (File) -> Unit
) {
    val context = LocalContext.current
    // `masque` ne sert PAS de clé : effacer le champ à chaque changement
    // d'étape ferait perdre ce que l'utilisateur est en train d'écrire.
    var texte by remember { mutableStateOf("") }

    val enregistreur = remember { VoiceRecorder(context) }
    var enregistrement by remember { mutableStateOf(false) }
    var chrono by remember { mutableLongStateOf(0L) }
    var noteEnAttente by remember { mutableStateOf<NoteVocale?>(null) }
    var messageErreur by remember { mutableStateOf<String?>(null) }

    // AU PREMIER LANCEMENT la permission n'est pas encore accordée : le premier
    // appui ouvre la boîte de dialogue système. Si on se contente d'enregistrer
    // la réponse, l'utilisateur accorde le micro et il ne se passe rien — il
    // doit réappuyer sans comprendre. On enchaîne donc directement.
    val permissionMicro = rememberPermissionState(
        permission = Manifest.permission.RECORD_AUDIO,
        onResult = { accorde ->
            when {
                !accorde -> messageErreur = "Micro refusé — impossible d'enregistrer."
                enregistreur.demarrer() -> { enregistrement = true; messageErreur = null }
                else -> messageErreur = "Micro indisponible."
            }
        }
    )

    LaunchedEffect(enregistrement) {
        while (enregistrement) {
            chrono = enregistreur.dureeEcouleeMs()
            delay(100)
        }
    }

    DisposableEffect(Unit) {
        onDispose { if (enregistreur.enCours) enregistreur.annuler() }
    }

    when {
        // Enregistrement en cours — état transitoire VOULU par l'utilisateur.
        enregistrement -> BarreEnregistrement(
            chronoMs = chrono,
            onAnnuler = { enregistreur.annuler(); enregistrement = false },
            onValider = {
                val note = enregistreur.arreterEtGarder()
                enregistrement = false
                if (note == null) messageErreur = "Enregistrement trop court."
                else { noteEnAttente = note; messageErreur = null }
            }
        )

        // Relecture : rien n'est parti tant qu'il n'a pas validé.
        noteEnAttente != null -> BarreRelecture(
            note = noteEnAttente!!,
            enabled = enabled,
            onJeter = { noteEnAttente?.fichier?.delete(); noteEnAttente = null },
            onEnvoyer = { noteEnAttente?.let(onEnvoyerVoix); noteEnAttente = null }
        )

        // Repos : micro, appareil photo, champ, envoyer. Toujours les quatre.
        else -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            BoutonRond(
                icone = Icons.Filled.Mic,
                description = "Enregistrer une note vocale",
                fond = Color.Transparent,
                teinte = NyeGbe.Violet,
                enabled = enabled,
                onClick = {
                    messageErreur = null
                    if (!permissionMicro.isGranted) permissionMicro.request()
                    else if (enregistreur.demarrer()) enregistrement = true
                    else messageErreur = "Micro indisponible."
                }
            )

            // L'appareil photo, à côté du micro : la photo est un mode de
            // réponse comme un autre, disponible à toutes les étapes.
            BoutonPhoto(enabled = enabled, onPhotoReady = onEnvoyerPhoto)

            ChampArrondi(
                texte = texte,
                onTexteChange = { texte = it },
                masque = masque,
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )

            BoutonRond(
                icone = Icons.Filled.Send,
                description = "Envoyer",
                fond = if (texte.isNotBlank()) NyeGbe.Violet else Color.Transparent,
                teinte = if (texte.isNotBlank()) Color.White else NyeGbe.TexteDiscret,
                enabled = enabled && texte.isNotBlank(),
                onClick = {
                    onEnvoyerTexte(texte, null)
                    texte = ""
                }
            )
        }
    }

    messageErreur?.let {
        Text(
            text = it,
            color = NyeGbe.Erreur,
            fontSize = 12.sp,
            modifier = Modifier.padding(start = 20.dp, bottom = 8.dp)
        )
    }
}

/**
 * Le champ de saisie, en pilule fine.
 *
 * BasicTextField plutôt qu'OutlinedTextField : ce dernier impose une hauteur
 * minimale de 56 dp (règle Material), ce qui donnait la barre épaisse et carrée
 * qu'on voulait éviter. Ici on maîtrise la hauteur et l'arrondi.
 */
@Composable
private fun ChampArrondi(
    texte: String,
    onTexteChange: (String) -> Unit,
    masque: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .heightIn(min = 40.dp)
            .background(NyeGbe.Fond, RoundedCornerShape(20.dp))
            .border(1.dp, NyeGbe.Bordure, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (texte.isEmpty()) {
            Text(
                text = if (masque) "Ton code…" else "Écris ton message…",
                color = NyeGbe.TexteDiscret,
                fontSize = 14.sp
            )
        }
        BasicTextField(
            value = texte,
            onValueChange = onTexteChange,
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle(color = NyeGbe.Texte, fontSize = 14.sp),
            cursorBrush = SolidColor(NyeGbe.Violet),
            visualTransformation =
                if (masque) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions =
                if (masque) KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                else KeyboardOptions.Default,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun BarreEnregistrement(chronoMs: Long, onAnnuler: () -> Unit, onValider: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .background(NyeGbe.VioletPale, RoundedCornerShape(22.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Croix rouge : on jette. Coche verte : on garde. Comme sur le web.
        BoutonRond(
            icone = Icons.Filled.Close,
            description = "Annuler l'enregistrement",
            fond = Color.Transparent,
            teinte = NyeGbe.Erreur,
            enabled = true,
            onClick = onAnnuler
        )

        Box(modifier = Modifier.size(8.dp).background(NyeGbe.Erreur, CircleShape))

        Text(
            text = "%d:%02d".format((chronoMs / 1000) / 60, (chronoMs / 1000) % 60),
            color = NyeGbe.Texte,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )

        BoutonRond(
            icone = Icons.Filled.Check,
            description = "Terminer l'enregistrement",
            fond = NyeGbe.Valide,
            teinte = Color.White,
            enabled = true,
            onClick = onValider
        )
    }
}

@Composable
private fun BarreRelecture(
    note: NoteVocale,
    enabled: Boolean,
    onJeter: () -> Unit,
    onEnvoyer: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .background(NyeGbe.Fond, RoundedCornerShape(22.dp))
            .border(1.dp, NyeGbe.Bordure, RoundedCornerShape(22.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        BoutonRond(
            icone = Icons.Filled.Close,
            description = "Jeter la note vocale",
            fond = Color.Transparent,
            teinte = NyeGbe.Erreur,
            enabled = enabled,
            onClick = onJeter
        )

        LecteurAudio(
            source = note.fichier.absolutePath,
            modifier = Modifier.weight(1f),
            couleurActive = NyeGbe.Violet,
            couleurInactive = NyeGbe.VioletSoft,
            couleurTexte = NyeGbe.TexteDoux,
            dureeConnueMs = note.dureeMs
        )

        BoutonRond(
            icone = Icons.Filled.Send,
            description = "Envoyer la note vocale",
            fond = NyeGbe.Violet,
            teinte = Color.White,
            enabled = enabled,
            onClick = onEnvoyer
        )
    }
}

/** Bouton circulaire de 40 dp — assez grand pour le doigt, assez discret pour
 *  ne pas écraser le champ de saisie. */
@Composable
private fun BoutonRond(
    icone: ImageVector,
    description: String,
    fond: Color,
    teinte: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .background(if (enabled) fond else fond.copy(alpha = 0.4f), CircleShape)
    ) {
        Icon(
            imageVector = icone,
            contentDescription = description,
            tint = if (enabled) teinte else teinte.copy(alpha = 0.4f),
            modifier = Modifier.size(21.dp)
        )
    }
}

/** L'icône appareil photo de la barre. Même mécanique de permission que le
 *  gros bouton, mais ronde, pour tenir à côté du micro. */
@Composable
private fun BoutonPhoto(enabled: Boolean, onPhotoReady: (File) -> Unit) {
    PhotoCaptureAction(onPhotoReady = onPhotoReady) { ouvrir ->
        BoutonRond(
            icone = Icons.Filled.PhotoCamera,
            description = "Prendre une photo",
            fond = Color.Transparent,
            teinte = NyeGbe.Violet,
            enabled = enabled,
            onClick = ouvrir
        )
    }
}
