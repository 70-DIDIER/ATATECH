package com.atatech.app.api

import com.squareup.moshi.Json

/**
 * Formes JSON du contrat — voir docs/API_DEMARCHES.md §2 à §4.
 * Pas de @JsonClass(generateAdapter) : on résout via KotlinJsonAdapterFactory
 * (réflexion), pas de codegen kapt/ksp — plus simple pour le hackathon.
 */

data class PingResponse(
    val ok: Boolean,
    val version: String,
    val service: String,
    @Json(name = "cle_requise") val cleRequise: Boolean
)

data class EtatConversation(
    val parcours: String?,
    val etape: Int,
    val jour: String?,
    @Json(name = "nationalite_faite") val nationaliteFaite: Boolean,
    val relance: Boolean,
    /**
     * Ajouté côté serveur pour la note vocale : sans reconnaissance vocale, une
     * 2e note sans choix doit déclencher une relance au lieu de réafficher le
     * menu en boucle. En mode « état », si ce champ n'est pas renvoyé tel quel,
     * le serveur le relit à false et la protection ne joue plus.
     */
    @Json(name = "menu_vu") val menuVu: Boolean = false
)

data class OptionChoix(
    val num: Int,
    val fr: String,
    val ewe: String
)

data class LigneBilingue(
    val ewe: String,
    val fr: String
)

/** attend.type : "choix" | "photo" | "code_secret" | "texte" | "rien" */
data class Attend(
    val type: String,
    val options: List<OptionChoix>? = null,
    val piece: String? = null,
    val masquer: Boolean? = null,
    val montant: Int? = null,
    val devise: String? = null,
    val service: String? = null
)

data class MessageAssistant(
    val cle: String,
    val titre: String?,
    @Json(name = "titre_fr") val titreFr: String?,
    val carte: String?,
    val lignes: List<LigneBilingue> = emptyList(),
    // Le serveur enverrait des OBJETS ici s'il remplissait ce champ (il envoie
    // toujours [] pour les démarches). Typé List<String>, un menu non vide
    // ferait échouer le décodage Moshi.
    val menu: List<OptionChoix> = emptyList(),
    val ewe: String,
    val fr: String,
    val attend: Attend,
    @Json(name = "audio_url") val audioUrl: String?
)

data class ReponseDemarches(
    @Json(name = "session_id") val sessionId: String?,
    val etat: EtatConversation,
    val fini: Boolean,
    val messages: List<MessageAssistant>
)

data class ErreurApi(
    val erreur: String
)

/** Corps de POST /api/v1/demarches/session — session_id facultatif. */
data class OuvrirSessionRequest(
    @Json(name = "session_id") val sessionId: String? = null
)

/**
 * Corps de POST /api/v1/demarches/message.
 * Mode "état" (recommandé pour l'app, voir §2.4) : ne pas remplir sessionId,
 * renvoyer le champ `etat` reçu au tour précédent.
 */
data class MessageRequest(
    @Json(name = "session_id") val sessionId: String? = null,
    val texte: String = "",
    val etat: EtatConversation? = null,
    /**
     * « texte » (défaut) ou « voix ».
     *
     * « voix » = l'utilisateur a répondu par une note vocale. AUCUNE
     * reconnaissance vocale n'est nécessaire et `texte` reste vide : le
     * scénario est scripté, il avance quoi que dise l'utilisateur
     * (API_DEMARCHES.md §3). Le fichier audio n'est pas envoyé — il reste sur
     * le téléphone pour la réécoute.
     */
    val type: String? = null
)
