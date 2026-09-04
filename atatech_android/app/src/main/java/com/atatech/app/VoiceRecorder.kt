package com.atatech.app

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Enregistre une note vocale dans un fichier, et rien d'autre.
 *
 * POURQUOI DU M4A ET PAS DU WAV 16 kHz
 * Le scénario des démarches est SCRIPTÉ : le serveur avance quoi que dise
 * l'utilisateur et ne transcrit jamais la note vocale (voir API_DEMARCHES.md
 * §3, « Répondre par une note vocale »). Le fichier ne quitte donc jamais le
 * téléphone : il ne sert qu'à la réécoute dans le fil de discussion. Le format
 * AAC/M4A de MediaRecorder convient parfaitement, il est léger et MediaPlayer
 * le lit nativement.
 *
 * La contrainte « WAV PCM 16 bits mono 16 kHz » ne s'applique QU'À la
 * conversation libre (POST /api/v1/assistant/audio), qui fait tourner la vraie
 * reconnaissance vocale éwé côté serveur. Le jour où cette route sera branchée,
 * il faudra un enregistreur AudioRecord distinct — pas celui-ci.
 */
class VoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var fichierCourant: File? = null
    private var debutMs: Long = 0L

    val enCours: Boolean get() = recorder != null

    /** Démarre l'enregistrement. Renvoie false si le micro est indisponible. */
    fun demarrer(): Boolean {
        arreterSansGarder()
        val dossier = File(context.cacheDir, "vocaux").apply { mkdirs() }
        val fichier = File(dossier, "vocal_${System.currentTimeMillis()}.m4a")

        val nouveau = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        return try {
            nouveau.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64_000)
                setAudioSamplingRate(44_100)
                setOutputFile(fichier.absolutePath)
                prepare()
                start()
            }
            recorder = nouveau
            fichierCourant = fichier
            debutMs = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            // Micro occupé, permission refusée entre-temps, appareil capricieux :
            // on nettoie et on le dit, plutôt que de laisser un état incohérent.
            runCatching { nouveau.release() }
            fichier.delete()
            recorder = null
            fichierCourant = null
            false
        }
    }

    /**
     * Arrête et GARDE le fichier. Renvoie null si l'enregistrement est trop
     * court pour être exploitable (moins de 0,4 s : MediaRecorder produit alors
     * souvent un fichier vide ou illisible).
     */
    fun arreterEtGarder(): NoteVocale? {
        val enregistreur = recorder ?: return null
        val fichier = fichierCourant
        val duree = System.currentTimeMillis() - debutMs

        val ok = try {
            enregistreur.stop()
            true
        } catch (e: Exception) {
            false          // stop() lève si aucune donnée n'a été captée
        } finally {
            runCatching { enregistreur.release() }
            recorder = null
            fichierCourant = null
        }

        if (!ok || fichier == null || !fichier.exists() || fichier.length() < 1024L || duree < 400L) {
            fichier?.delete()
            return null
        }
        return NoteVocale(fichier = fichier, dureeMs = duree)
    }

    /** Arrête et JETTE le fichier (bouton « annuler »). */
    fun annuler() {
        arreterSansGarder()
    }

    private fun arreterSansGarder() {
        recorder?.let { enregistreur ->
            runCatching { enregistreur.stop() }
            runCatching { enregistreur.release() }
        }
        recorder = null
        fichierCourant?.delete()
        fichierCourant = null
    }

    /** Millisecondes écoulées depuis le début, pour le chronomètre affiché. */
    fun dureeEcouleeMs(): Long =
        if (recorder == null) 0L else System.currentTimeMillis() - debutMs
}

/** Une note vocale enregistrée sur le téléphone, prête à être réécoutée. */
data class NoteVocale(val fichier: File, val dureeMs: Long)
