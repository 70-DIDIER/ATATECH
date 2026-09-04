package com.atatech.app

import android.content.Context

enum class AppLanguage(val code: String, val label: String) {
    FRANCAIS("fr", "Français"),
    EWE("ee", "Éwé")
}

private const val PREFS_NAME = "atatech_prefs"
private const val KEY_LANGUAGE = "language"

object AppPreferences {
    fun getLanguage(context: Context): AppLanguage {
        val code = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, AppLanguage.FRANCAIS.code)
        return AppLanguage.entries.firstOrNull { it.code == code } ?: AppLanguage.FRANCAIS
    }

    fun setLanguage(context: Context, language: AppLanguage) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LANGUAGE, language.code)
            .apply()
    }
}
