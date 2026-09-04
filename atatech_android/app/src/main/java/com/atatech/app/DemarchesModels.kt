package com.atatech.app

import com.squareup.moshi.Json

data class PingResponse(
    val ok: Boolean,
    val version: String,
    val service: String,
    @Json(name = "cle_requise") val cleRequise: Boolean
)

data class EtatDemarche(
    val parcours: String? = null,
    val etape: Int = 0,
    val jour: String? = null,
    @Json(name = "nationalite_faite") val nationaliteFaite: Boolean = false,
    val relance: Boolean = false
)

data class ChoixOption(
    val num: Int,
    val fr: String,
    val ewe: String
)

data class Attend(
    val type: String,
    val options: List<ChoixOption>? = null,
    val piece: String? = null,
    val masquer: Boolean? = null,
    val montant: Int? = null,
    val devise: String? = null,
    val service: String? = null
)

data class LigneMessage(
    val ewe: String,
    val fr: String
)

data class DemarcheMessage(
    val cle: String,
    val carte: String? = null,
    val titre: String? = null,
    @Json(name = "titre_fr") val titreFr: String? = null,
    val lignes: List<LigneMessage> = emptyList(),
    val ewe: String,
    val fr: String,
    val attend: Attend? = null,
    @Json(name = "audio_url") val audioUrl: String? = null
)

data class DemarchesResponse(
    @Json(name = "session_id") val sessionId: String? = null,
    val etat: EtatDemarche? = null,
    val fini: Boolean = false,
    val messages: List<DemarcheMessage> = emptyList()
)

data class SessionRequest(
    @Json(name = "session_id") val sessionId: String? = null
)

data class MessageRequest(
    @Json(name = "session_id") val sessionId: String? = null,
    val texte: String,
    val etat: EtatDemarche? = null
)

data class ApiErrorResponse(
    val erreur: String
)
