package com.atatech.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * La charte de l'interface web (interface_web/static/style.css), reprise à
 * l'identique pour que le mobile et le web se ressemblent.
 *
 * Ces valeurs ne sont pas décoratives : les contrastes ont été vérifiés côté
 * web (Texte 11,4:1 sur le fond, TexteDoux 6,1:1, TexteDiscret 4,6:1). Ne pas
 * les éclaircir sans revérifier — l'application s'adresse à des gens qui lisent
 * mal, la lisibilité est une exigence, pas un détail.
 */
object NyeGbe {
    val Violet       = Color(0xFF662483)   // --color-primary
    val VioletFonce  = Color(0xFF4A1A5F)   // --color-primary-dark
    val VioletClair  = Color(0xFF8F4FAE)   // --color-primary-light
    val VioletSoft   = Color(0xFFE3D3EC)   // --color-primary-soft
    val VioletPale   = Color(0xFFF4EEF8)   // --color-primary-pale

    val Safran       = Color(0xFFF9B233)   // --color-accent (décoratif)

    val Fond         = Color(0xFFF6F6F8)   // --bg
    val Surface      = Color(0xFFFFFFFF)   // --surface
    val Bordure      = Color(0xFFE6E6EC)   // --border

    val Texte        = Color(0xFF343535)   // --text
    val TexteDoux    = Color(0xFF5C5D64)   // --text-soft
    val TexteDiscret = Color(0xFF6E6F78)   // --text-faint

    val Valide       = Color(0xFF16A34A)   // --ok
    val Erreur       = Color(0xFFD92D20)
}

private val SchemaClair = lightColorScheme(
    primary = NyeGbe.Violet,
    onPrimary = Color.White,
    primaryContainer = NyeGbe.VioletPale,
    onPrimaryContainer = NyeGbe.VioletFonce,
    secondary = NyeGbe.VioletClair,
    onSecondary = Color.White,
    background = NyeGbe.Fond,
    onBackground = NyeGbe.Texte,
    surface = NyeGbe.Surface,
    onSurface = NyeGbe.Texte,
    surfaceVariant = NyeGbe.VioletPale,
    onSurfaceVariant = NyeGbe.TexteDoux,
    outline = NyeGbe.Bordure,
    error = NyeGbe.Erreur
)

@Composable
fun NyeGbeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SchemaClair, content = content)
}

/**
 * L'éwé SANS ses signes spéciaux — la version la plus lisible, celle qu'on
 * affiche en gros. Reprise de sansSignes() dans interface_web/static/app.js.
 *
 * Attention : ne JAMAIS mettre un mot éwé en majuscules automatiques.
 * « Woezɔ » deviendrait « WOEZƆ ».
 */
private val SIGNES = mapOf(
    'ŋ' to "ng", 'Ŋ' to "Ng", 'ɖ' to "d", 'Ɖ' to "D",
    'ɔ' to "o",  'Ɔ' to "O",  'ɛ' to "e", 'Ɛ' to "E",
    'ƒ' to "f",  'Ƒ' to "F",  'ʋ' to "v", 'Ʋ' to "V",
    'ɣ' to "h",  'Ɣ' to "H"
)

fun sansSignes(texte: String?): String {
    if (texte.isNullOrEmpty()) return texte.orEmpty()
    val remplace = buildString {
        texte.forEach { c -> append(SIGNES[c] ?: c.toString()) }
    }
    // Décompose puis retire les diacritiques combinants (U+0300–U+036F).
    val decompose = java.text.Normalizer.normalize(remplace, java.text.Normalizer.Form.NFD)
    val sansTons = decompose.filterNot { it.code in 0x0300..0x036F }
    return java.text.Normalizer.normalize(sansTons, java.text.Normalizer.Form.NFC)
}
