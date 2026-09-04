package com.atatech.app.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

/** Instance Moshi partagée — utilisée par Retrofit et par l'envoi de photo (etat en JSON texte). */
object MoshiProvider {
    val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
}
