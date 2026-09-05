package com.atatech.app

/**
 * Comprend une commande d'appel écrite, SANS réseau ni modèle : une simple
 * détection par mots-clés. Suffisant ici parce que le besoin est fermé —
 * « appelle X » — contrairement à une conversation libre. Testable sans
 * Android (aucune dépendance au SDK).
 */
sealed class CommandeAppel {
    data class VersNumero(val numero: String) : CommandeAppel()
    data class VersContact(val nom: String) : CommandeAppel()
}

/**
 * Déclencheurs reconnus, du plus long au plus court pour ne pas laisser un
 * préfixe court (« appel ») couper une phrase avant un déclencheur plus
 * précis (« appelle »). Uniquement des formes SANS accent : `normalisee` a
 * déjà été passée par `sansSignes()`, qui retire tous les accents français
 * (à, é…) en plus des signes éwé — une variante accentuée ici ne pourrait
 * jamais matcher.
 */
private val DECLENCHEURS = listOf(
    "telephone a ", "telephoner a ",
    "compose le ", "compose ",
    "appeler le ", "appeler la ", "appeler ",
    "appelle le ", "appelle la ", "appelle ",
    "appel a ", "appel "
)

/** Vrai si la cible est essentiellement une suite de chiffres (numéro). */
private fun ressembleAUnNumero(cible: String): Boolean {
    val chiffres = cible.count { it.isDigit() }
    val autresLettres = cible.count { it.isLetter() }
    return chiffres >= 4 && autresLettres == 0
}

/**
 * Analyse une phrase et renvoie la commande d'appel qu'elle exprime, ou
 * `null` si ce n'en est pas une.
 */
fun analyserCommandeAppel(phrase: String): CommandeAppel? {
    val normalisee = sansSignes(phrase).lowercase().trim()
    if (normalisee.isEmpty()) return null

    val declencheur = DECLENCHEURS.firstOrNull { normalisee.startsWith(it) } ?: return null
    val cible = normalisee.removePrefix(declencheur).trim()
    if (cible.isEmpty()) return null

    return if (ressembleAUnNumero(cible)) {
        CommandeAppel.VersNumero(cible.filter { it.isDigit() })
    } else {
        // La normalisation (sansSignes + minuscule) peut changer la longueur
        // du texte (ex. "ŋ" → "ng") : impossible de retrouver fiablement la
        // sous-chaîne d'origine par un simple calcul de longueur. On affiche
        // donc la cible normalisée, remise en casse "Titre" — assez lisible
        // pour une confirmation, et c'est de toute façon cette même version
        // normalisée qui sert à comparer avec le répertoire.
        CommandeAppel.VersContact(cible.replaceFirstChar { it.uppercase() })
    }
}
