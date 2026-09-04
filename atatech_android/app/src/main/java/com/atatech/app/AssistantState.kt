package com.atatech.app

import com.atatech.app.api.Attend
import com.atatech.app.api.EtatConversation

data class AssistantState(
    val demarre: Boolean = false,
    val tours: List<TourConversation> = emptyList(),
    /** Renvoyé au tour suivant (mode "état", voir §2.4) — pas de session_id. */
    val etat: EtatConversation? = null,
    /** Pilote la zone de saisie du bas — voir §3. */
    val attendActuel: Attend? = null,
    val fini: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
