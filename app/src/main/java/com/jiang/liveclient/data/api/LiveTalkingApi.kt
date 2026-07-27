package com.jiang.liveclient.data.api

import com.jiang.liveclient.data.model.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface LiveTalkingApi {

    @POST("offer")
    suspend fun postOffer(@Body body: OfferRequest): Response<OfferResponse>

    @POST("human")
    suspend fun postHuman(@Body body: HumanRequest): Response<Unit>

    @Multipart
    @POST("humanaudio")
    suspend fun postHumanAudio(
        @Part file: MultipartBody.Part,
        @Part("sessionid") sessionid: RequestBody
    ): Response<Unit>

    @POST("record")
    suspend fun postRecord(@Body body: RecordRequest): Response<Unit>

    @POST("set_audiotype")
    suspend fun postAudiotype(@Body body: AudioTypeRequest): Response<Unit>

    @POST("interrupt_talk")
    suspend fun postInterrupt(@Body body: SessionRequest): Response<Unit>

    @POST("is_speaking")
    suspend fun postIsSpeaking(@Body body: SessionRequest): Response<IsSpeakingResponse>

    @GET("record/{sessionid}")
    suspend fun getRecord(@Path("sessionid") sessionid: String): Response<ResponseBody>
}
