package com.jiang.liveclient.data.model

data class OfferRequest(
    val sdp: String,
    val type: String,
    val avatar: String? = null,
    val refaudio: String? = null,
    val reftext: String? = null,
    val custom_config: String? = null
)

data class OfferResponse(
    val sdp: String,
    val type: String,
    val sessionid: String
)

data class HumanRequest(
    val sessionid: String,
    val text: String,
    val type: String = "echo",
    val interrupt: Boolean = false,
    val tts: String? = null
)

data class RecordRequest(
    val sessionid: String,
    val type: String
)

data class AudioTypeRequest(
    val sessionid: String,
    val audiotype: Int
)

data class SessionRequest(
    val sessionid: String
)

data class IsSpeakingResponse(
    val data: Boolean
)

data class ApiResponse<T>(
    val code: Int,
    val msg: String,
    val data: T?
)
