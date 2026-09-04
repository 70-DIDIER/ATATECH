package com.atatech.app.api

import android.content.Context

private const val PREFS_NAME = "atatech_api_prefs"
private const val KEY_BASE_URL = "base_url"
private const val KEY_API_KEY = "api_key"

/**
 * Adresse du backend au premier lancement.
 *
 * ELLE CHANGE À CHAQUE CHANGEMENT DE WI-FI : c'est une adresse de réseau local.
 * Ne pas reconstruire l'APK pour autant — l'écran Réglages permet de la
 * corriger, et le serveur affiche la bonne adresse à son démarrage.
 *
 * Repli sans configuration : le tunnel HTTPS
 * https://transcript-dsc-rooms-limits.trycloudflare.com/
 * joint le même backend depuis n'importe quel réseau, au prix d'environ
 * 800 ms par appel (le trafic passe par Cloudflare).
 */
const val DEFAULT_BASE_URL = "http://172.20.10.2:5055/"

object ApiConfig {
    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getBaseUrl(context: Context): String =
        prefs(context).getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL

    fun setBaseUrl(context: Context, url: String) {
        val normalise = if (url.endsWith("/")) url else "$url/"
        prefs(context).edit().putString(KEY_BASE_URL, normalise).apply()
    }

    fun getApiKey(context: Context): String =
        prefs(context).getString(KEY_API_KEY, "") ?: ""

    fun setApiKey(context: Context, key: String) {
        prefs(context).edit().putString(KEY_API_KEY, key).apply()
    }
}
