package com.atatech.app.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/** Voir docs/API_DEMARCHES.md §2 pour le détail de chaque route. */
interface NyeGbeApi {

    @GET("api/v1/ping")
    suspend fun ping(): Response<PingResponse>

    @POST("api/v1/demarches/session")
    suspend fun ouvrirSession(@Body corps: OuvrirSessionRequest): Response<ReponseDemarches>

    @POST("api/v1/demarches/message")
    suspend fun envoyerMessage(@Body corps: MessageRequest): Response<ReponseDemarches>

    /**
     * En mode "état" (sans session_id), le champ etat se passe en JSON texte
     * dans le part "etat" — voir §2.4.
     */
    @Multipart
    @POST("api/v1/demarches/photo")
    suspend fun envoyerPhoto(
        @Part("session_id") sessionId: RequestBody? = null,
        @Part("etat") etat: RequestBody? = null,
        @Part photo: MultipartBody.Part
    ): Response<ReponseDemarches>
}
