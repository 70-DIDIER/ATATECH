package com.atatech.app

import com.atatech.app.api.MessageAssistant

/** Un tour affiché dans le fil — soit ce que l'utilisateur a envoyé, soit un message de l'assistant. */
sealed class TourConversation {
    /** [masque] : true pour un code_secret — on n'affiche jamais le texte réel, voir §3 de la doc. */
    data class Utilisateur(val texte: String, val masque: Boolean = false) : TourConversation()
    data class Assistant(val message: MessageAssistant) : TourConversation()
}
