package com.atatech.app

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface DemarchesApi {

    @GET("api/v1/ping")
    suspend fun ping(): PingResponse

    @POST("api/v1/demarches/session")
    suspend fun session(@Body body: SessionRequest): DemarchesResponse

    @POST("api/v1/demarches/message")
    suspend fun message(@Body body: MessageRequest): DemarchesResponse

    @Multipart
    @POST("api/v1/demarches/photo")
    suspend fun photo(
        @Part("session_id") sessionId: RequestBody?,
        @Part("etat") etat: RequestBody?,
        @Part photo: MultipartBody.Part
    ): DemarchesResponse
}
