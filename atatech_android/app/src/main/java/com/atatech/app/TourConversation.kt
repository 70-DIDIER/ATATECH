package com.atatech.app

import com.atatech.app.api.MessageAssistant
import java.io.File

/** Un tour affiché dans le fil — soit ce que l'utilisateur a envoyé, soit un message de l'assistant. */
sealed class TourConversation {

    /**
     * Message écrit de l'utilisateur.
     * [masque] : true pour un code_secret — on n'affiche jamais le texte réel, voir §3 de la doc.
     * [libelle] : ce qu'on MONTRE quand ce n'est pas le texte envoyé. Un choix
     *   envoie « 2 » au serveur mais doit afficher « Mercredi » : sans ça, le fil
     *   se remplit de chiffres nus, illisibles pour quelqu'un qui ne lit pas.
     */
    data class Utilisateur(
        val texte: String,
        val masque: Boolean = false,
        val libelle: String? = null
    ) : TourConversation() {
        val affichage: String get() = libelle ?: texte
    }

    /**
     * NOTE VOCALE de l'utilisateur — le type qui manquait, et sans lequel une
     * bulle audio ne pouvait tout simplement pas exister.
     *
     * [fichier] reste dans le cache de l'application : il n'est jamais envoyé
     * au serveur (le scénario est scripté, la voix n'est pas transcrite), mais
     * il doit survivre à toute la conversation pour rester réécoutable.
     */
    data class Vocal(val fichier: File, val dureeMs: Long) : TourConversation()

    /** Photo envoyée par l'utilisateur — affichée comme telle, pas comme du texte. */
    data class Photo(val fichier: File) : TourConversation()

    data class Assistant(val message: MessageAssistant) : TourConversation()
}
