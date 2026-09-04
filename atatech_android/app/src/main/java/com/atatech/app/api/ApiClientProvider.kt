package com.atatech.app.api

import android.content.Context
import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Reconstruit le client si l'URL de base change (écran réglages).
 * La clé API est relue à chaque requête : pas besoin de reconstruire pour ça.
 */
object ApiClientProvider {
    private var cachedBaseUrl: String? = null
    private var cachedApi: NyeGbeApi? = null

    fun getApi(context: Context): NyeGbeApi {
        val baseUrl = ApiConfig.getBaseUrl(context)
        cachedApi?.let { if (cachedBaseUrl == baseUrl) return it }

        val moshi = MoshiProvider.moshi

        val apiKeyInterceptor = Interceptor { chain ->
            val request = chain.request()
            // /ping ne demande pas de clé — voir §1
            val cle = ApiConfig.getApiKey(context)
            val nextRequest = if (!request.url.encodedPath.endsWith("/ping") && cle.isNotBlank()) {
                request.newBuilder().addHeader("X-Api-Cle", cle).build()
            } else {
                request
            }
            chain.proceed(nextRequest)
        }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(apiKeyInterceptor)
            .addInterceptor(logging)
            // L'ASR/la synthèse vocale (§5) peuvent être lentes — voir §1.
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(180, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        val api = retrofit.create(NyeGbeApi::class.java)
        cachedBaseUrl = baseUrl
        cachedApi = api
        return api
    }
}
