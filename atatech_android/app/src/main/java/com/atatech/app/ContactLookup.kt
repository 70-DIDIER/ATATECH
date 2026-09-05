package com.atatech.app

import android.content.Context
import android.provider.ContactsContract

/** Un contact trouvé, avec un numéro exploitable. */
data class ContactTrouve(val nom: String, val numero: String)

/**
 * Recherche RÉELLE dans le répertoire du téléphone — aucune simulation,
 * aucun réseau. On compare des noms normalisés (accents/signes retirés,
 * minuscule) dans les deux sens : un contact enregistré « Papa Kodjo » ou
 * « Maman ❤️ » doit matcher la recherche « papa » / « maman ».
 */
object ContactLookup {

    fun rechercher(context: Context, nom: String): List<ContactTrouve> {
        val cible = sansSignes(nom).lowercase().trim()
        if (cible.isEmpty()) return emptyList()

        val resultats = mutableListOf<ContactTrouve>()
        val dejaVus = mutableSetOf<String>()

        val curseur = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            ),
            null, null, null
        )

        curseur?.use { c ->
            val indexNom = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val indexNumero = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            if (indexNom < 0 || indexNumero < 0) return@use

            while (c.moveToNext()) {
                val nomContact = c.getString(indexNom) ?: continue
                val numero = c.getString(indexNumero)?.trim().orEmpty()
                if (numero.isEmpty()) continue

                val nomNormalise = sansSignes(nomContact).lowercase()
                val correspond = nomNormalise.contains(cible) || cible.contains(nomNormalise)
                if (!correspond) continue

                // Dédoublonne par numéro : un même contact peut ressortir
                // plusieurs fois (plusieurs façons de le rattacher en base).
                val cleNumero = numero.filter { it.isDigit() }
                if (cleNumero.isEmpty() || !dejaVus.add(cleNumero)) continue

                resultats.add(ContactTrouve(nom = nomContact, numero = numero))
            }
        }

        return resultats
    }
}
