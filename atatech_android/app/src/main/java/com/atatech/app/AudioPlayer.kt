package com.atatech.app

import android.media.MediaPlayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Nombre de barres de la forme d'onde — même valeur que le lecteur du web. */
private const val NB_BARRES = 30

/**
 * Forme d'onde PSEUDO-ALÉATOIRE mais DÉTERMINISTE, reprise telle quelle du
 * lecteur de l'interface web (static/lecteur-audio.js) : un générateur
 * congruentiel semé par la source, modulé par un sinus pour aplatir les bords.
 *
 * Pourquoi ne pas décoder le fichier pour dessiner la vraie amplitude : ce
 * serait lent, et le dessin doit être STABLE — la même note vocale doit
 * toujours produire le même dessin, à chaque recomposition.
 */
private fun hauteurs(graine: String): List<Float> {
    var x = graine.fold(0L) { acc, c -> (acc * 31 + c.code) and 0xFFFFFFFFL }
    if (x == 0L) x = 1L
    return (0 until NB_BARRES).map { i ->
        x = (x * 1664525L + 1013904223L) and 0xFFFFFFFFL
        val alea = (x.toDouble() / 0xFFFFFFFFL.toDouble()).toFloat()
        // Le sinus écrase les extrémités : la forme d'onde a un ventre au centre.
        val enveloppe = kotlin.math.sin(Math.PI * (i + 0.5) / NB_BARRES).toFloat()
        (0.18f + 0.82f * alea * enveloppe).coerceIn(0.12f, 1f)
    }
}

private fun formaterDuree(ms: Long): String {
    val total = (ms / 1000).toInt()
    return "%d:%02d".format(total / 60, total % 60)
}

/**
 * Lecteur audio compact : bouton lecture/pause, forme d'onde cliquable pour se
 * déplacer, durée. Sert AUSSI BIEN pour la note vocale de l'utilisateur (un
 * fichier local) que pour la voix éwé de l'assistant (une URL).
 *
 * [source] est le chemin ou l'URL passé à MediaPlayer, et sert de graine au
 * dessin de la forme d'onde.
 * [dureeConnueMs] évite d'attendre la préparation du lecteur pour afficher une
 * durée (l'enregistreur la connaît déjà) ; 0 = on prendra celle du fichier.
 */
@Composable
fun LecteurAudio(
    source: String,
    modifier: Modifier = Modifier,
    couleurActive: Color,
    couleurInactive: Color,
    couleurTexte: Color,
    dureeConnueMs: Long = 0L,
    demarrerAutomatiquement: Boolean = false
) {
    var enLecture by remember(source) { mutableStateOf(false) }
    var progression by remember(source) { mutableFloatStateOf(0f) }
    var dureeMs by remember(source) { mutableStateOf(dureeConnueMs) }
    var pret by remember(source) { mutableStateOf(false) }
    val barres = remember(source) { hauteurs(source) }

    val lecteur = remember(source) {
        MediaPlayer().apply {
            try {
                setDataSource(source)
                setOnPreparedListener { mp ->
                    pret = true
                    if (mp.duration > 0) dureeMs = mp.duration.toLong()
                    if (demarrerAutomatiquement) {
                        mp.start()
                        enLecture = true
                    }
                }
                setOnCompletionListener {
                    enLecture = false
                    progression = 0f
                    runCatching { seekTo(0) }
                }
                setOnErrorListener { _, _, _ ->
                    // Fichier absent ou illisible : on laisse le lecteur inerte
                    // plutôt que de faire planter la bulle.
                    pret = false
                    enLecture = false
                    true
                }
                prepareAsync()
            } catch (_: Exception) {
                pret = false
            }
        }
    }

    DisposableEffect(source) {
        onDispose {
            runCatching { if (lecteur.isPlaying) lecteur.stop() }
            runCatching { lecteur.release() }
        }
    }

    // Avance de la barre de progression pendant la lecture.
    LaunchedEffect(enLecture, pret) {
        while (enLecture && pret) {
            val total = runCatching { lecteur.duration }.getOrDefault(0)
            if (total > 0) {
                progression = (runCatching { lecteur.currentPosition }.getOrDefault(0)
                    .toFloat() / total).coerceIn(0f, 1f)
            }
            delay(80)
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = if (enLecture) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (enLecture) "Mettre en pause" else "Écouter",
            tint = couleurActive,
            modifier = Modifier
                .size(34.dp)
                .background(couleurInactive.copy(alpha = 0.28f), CircleShape)
                .padding(6.dp)
                .pointerInput(pret) {
                    detectTapGestures {
                        if (!pret) return@detectTapGestures
                        if (lecteur.isPlaying) {
                            lecteur.pause()
                            enLecture = false
                        } else {
                            lecteur.start()
                            enLecture = true
                        }
                    }
                }
        )

        // Forme d'onde. Un appui la parcourt : on se déplace dans le son.
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(30.dp)
                .pointerInput(pret) {
                    detectTapGestures { position: Offset ->
                        if (!pret) return@detectTapGestures
                        val ratio = (position.x / size.width).coerceIn(0f, 1f)
                        val total = runCatching { lecteur.duration }.getOrDefault(0)
                        if (total > 0) {
                            lecteur.seekTo((total * ratio).toInt())
                            progression = ratio
                        }
                    }
                }
        ) {
            val espace = size.width / NB_BARRES
            val largeurBarre = (espace * 0.5f).coerceAtLeast(2f)
            val lues = (progression * NB_BARRES).toInt()
            barres.forEachIndexed { i, h ->
                val hauteur = size.height * h
                drawRoundRect(
                    color = if (i < lues) couleurActive else couleurInactive,
                    topLeft = Offset(
                        x = i * espace + (espace - largeurBarre) / 2f,
                        y = (size.height - hauteur) / 2f
                    ),
                    size = androidx.compose.ui.geometry.Size(largeurBarre, hauteur),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(largeurBarre / 2f)
                )
            }
        }

        Text(
            text = formaterDuree(dureeMs),
            color = couleurTexte,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(34.dp)
        )
    }
}
