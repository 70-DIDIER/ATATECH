package com.atatech.app

import android.content.Context
import com.squareup.moshi.KotlinJsonAdapterFactory
import com.squareup.moshi.Moshi
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object ApiClient {
    fun create(context: Context): DemarchesApi {
        val apiKey = ApiPreferences.getApiKey(context)

        val apiKeyInterceptor = Interceptor { chain ->
            val request = chain.request()
            val isPing = request.url.encodedPath.endsWith("/api/v1/ping")
            val newRequest = if (isPing || apiKey.isBlank()) {
                request
            } else {
                request.newBuilder()
                    .addHeader("X-Api-Cle", apiKey)
                    .build()
            }
            chain.proceed(newRequest)
        }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(apiKeyInterceptor)
            .addInterceptor(logging)
            .build()

        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        val baseUrl = ApiPreferences.getBaseUrl(context).let {
            if (it.endsWith("/")) it else "$it/"
        }

        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        return retrofit.create(DemarchesApi::class.java)
    }
}
