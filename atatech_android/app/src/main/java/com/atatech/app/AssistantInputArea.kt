package com.atatech.app

import android.Manifest
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalContext
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
 * Le champ attend.type de chaque message pilote ce qui s'affiche ici — voir §3
 * de la doc. On ne devine jamais l'étape à partir du texte reçu.
 *
 * LA NOTE VOCALE, en trois temps, comme sur l'interface web :
 *   1. repos        — champ de saisie + micro
 *   2. enregistrement — chronomètre, [annuler] et [valider]
 *   3. relecture    — le lecteur audio, on peut réécouter, jeter ou envoyer
 * Rien n'est envoyé tant que l'utilisateur n'a pas appuyé sur envoyer.
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
    if (fini || attend?.type == "rien") {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(NyeGbe.Surface)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Démarche terminée.", color = NyeGbe.TexteDoux)
            OutlinedButton(onClick = onRecommencer, modifier = Modifier.padding(top = 8.dp)) {
                Text("Nouvelle demande")
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxWidth().background(NyeGbe.Surface)) {
        when (attend?.type) {
            "choix" -> ZoneChoix(
                options = attend.options.orEmpty(),
                enabled = !isLoading,
                // On envoie le NUMÉRO au serveur, on AFFICHE le libellé.
                onChoisir = { option -> onEnvoyerTexte(option.num.toString(), option.fr) }
            )

            "photo" -> PhotoCaptureButton(enabled = !isLoading, onPhotoReady = onEnvoyerPhoto)

            "code_secret" -> ZoneSaisie(
                masque = true,
                enabled = !isLoading,
                placeholder = listOfNotNull(
                    "Code secret",
                    attend.montant?.let { "($it ${attend.devise.orEmpty()})".trim() }
                ).joinToString(" "),
                onEnvoyerTexte = onEnvoyerTexte,
                onEnvoyerVoix = onEnvoyerVoix
            )

            else -> ZoneSaisie(
                masque = false,
                enabled = !isLoading,
                placeholder = "Écris ton message…",
                onEnvoyerTexte = onEnvoyerTexte,
                onEnvoyerVoix = onEnvoyerVoix
            )
        }
    }
}

@Composable
private fun ZoneChoix(
    options: List<com.atatech.app.api.OptionChoix>,
    enabled: Boolean,
    onChoisir: (com.atatech.app.api.OptionChoix) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            Button(
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NyeGbe.Violet),
                onClick = { onChoisir(option) }
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    // L'éwé d'abord et en gros : c'est la langue de l'utilisateur.
                    Text(option.ewe, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                         color = Color.White)
                    Text(option.fr, fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                }
            }
        }
    }
}

/**
 * Champ de saisie + micro. C'est ici que vivait le bug : le texte reconnu par
 * le SpeechRecognizer était affecté au champ (`texte = reconnu`). Il n'y a plus
 * ni reconnaissance vocale ni traduction sur le téléphone — on enregistre un
 * fichier, on le montre, on l'envoie.
 */
@Composable
private fun ZoneSaisie(
    masque: Boolean,
    enabled: Boolean,
    placeholder: String,
    onEnvoyerTexte: (String, String?) -> Unit,
    onEnvoyerVoix: (NoteVocale) -> Unit
) {
    val context = LocalContext.current
    var texte by remember(masque) { mutableStateOf("") }

    val enregistreur = remember { VoiceRecorder(context) }
    var enregistrement by remember { mutableStateOf(false) }
    var chrono by remember { mutableLongStateOf(0L) }
    var noteEnAttente by remember { mutableStateOf<NoteVocale?>(null) }
    var messageErreur by remember { mutableStateOf<String?>(null) }

    // AU PREMIER LANCEMENT, la permission n'est pas encore accordée : le premier
    // appui ouvre la boîte de dialogue système. Si on se contente d'enregistrer
    // la réponse, l'utilisateur accorde le micro... et il ne se passe rien — il
    // doit réappuyer sans comprendre pourquoi. On enchaîne donc directement sur
    // l'enregistrement dès que la permission est donnée.
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

    // Le chronomètre affiché pendant l'enregistrement.
    LaunchedEffect(enregistrement) {
        while (enregistrement) {
            chrono = enregistreur.dureeEcouleeMs()
            delay(100)
        }
    }

    // Si l'écran disparaît en plein enregistrement, on relâche le micro.
    DisposableEffect(Unit) {
        onDispose { if (enregistreur.enCours) enregistreur.annuler() }
    }

    when {
        // ── 2. Enregistrement en cours ───────────────────────────────────────
        enregistrement -> BarreEnregistrement(
            chronoMs = chrono,
            onAnnuler = {
                enregistreur.annuler()
                enregistrement = false
            },
            onValider = {
                val note = enregistreur.arreterEtGarder()
                enregistrement = false
                if (note == null) {
                    messageErreur = "Enregistrement trop court."
                } else {
                    noteEnAttente = note
                    messageErreur = null
                }
            }
        )

        // ── 3. Relecture avant envoi ─────────────────────────────────────────
        noteEnAttente != null -> BarreRelecture(
            note = noteEnAttente!!,
            enabled = enabled,
            onJeter = {
                noteEnAttente?.fichier?.delete()
                noteEnAttente = null
            },
            onEnvoyer = {
                noteEnAttente?.let(onEnvoyerVoix)
                noteEnAttente = null
            }
        )

        // ── 1. Repos ─────────────────────────────────────────────────────────
        else -> Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value = texte,
                onValueChange = { texte = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(placeholder, color = NyeGbe.TexteDiscret) },
                singleLine = true,
                enabled = enabled,
                shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NyeGbe.VioletClair,
                    unfocusedBorderColor = NyeGbe.Bordure
                ),
                visualTransformation =
                    if (masque) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions =
                    if (masque) KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
                    else KeyboardOptions.Default
            )

            BoutonRond(
                icone = Icons.Filled.Mic,
                description = "Enregistrer une note vocale",
                fond = NyeGbe.VioletPale,
                teinte = NyeGbe.Violet,
                enabled = enabled,
                onClick = {
                    messageErreur = null
                    if (!permissionMicro.isGranted) {
                        permissionMicro.request()
                    } else if (enregistreur.demarrer()) {
                        enregistrement = true
                    } else {
                        messageErreur = "Micro indisponible."
                    }
                }
            )

            BoutonRond(
                icone = Icons.Filled.Send,
                description = "Envoyer",
                fond = NyeGbe.Violet,
                teinte = Color.White,
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
            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
        )
    }
}

@Composable
private fun BarreEnregistrement(chronoMs: Long, onAnnuler: () -> Unit, onValider: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .background(NyeGbe.VioletPale, RoundedCornerShape(24.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
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

        Box(modifier = Modifier.size(9.dp).background(NyeGbe.Erreur, CircleShape))

        Text(
            text = "%d:%02d".format((chronoMs / 1000) / 60, (chronoMs / 1000) % 60),
            color = NyeGbe.Texte,
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
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Text(
            text = "Écoute avant d'envoyer",
            fontSize = 11.sp,
            color = NyeGbe.TexteDiscret,
            modifier = Modifier.padding(start = 6.dp, bottom = 6.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, NyeGbe.Bordure, RoundedCornerShape(24.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
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
}

@Composable
private fun BoutonRond(
    icone: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    fond: Color,
    teinte: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.size(42.dp).background(
            if (enabled) fond else fond.copy(alpha = 0.4f), CircleShape
        )
    ) {
        Icon(icone, contentDescription = description, tint = teinte)
    }
}
