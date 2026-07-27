package com.jiang.liveclient.ui.debug

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jiang.liveclient.data.api.RetrofitClient
import com.jiang.liveclient.data.model.*
import com.jiang.liveclient.webrtc.WebRTCManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

class DebugViewModel(application: Application) : AndroidViewModel(application) {

    private val api = RetrofitClient.api
    private val webRTCManager = WebRTCManager(
        context = application.applicationContext,
        api = api,
        scope = viewModelScope
    )

    val connectionState = webRTCManager.connectionState
    val sessionId = webRTCManager.sessionId
    val remoteVideoTrack = webRTCManager.remoteVideoTrack
    val remoteAudioTrack = webRTCManager.remoteAudioTrack
    val errorMessage = webRTCManager.errorMessage

    val isRecording = MutableStateFlow(false)
    val recordingStatus = MutableStateFlow("")

    // Input states
    val avatarId = MutableStateFlow("wav2lip256_avatar1")
    val refAudio = MutableStateFlow("")
    val refText = MutableStateFlow("")
    val textInput = MutableStateFlow("")
    val textType = MutableStateFlow("echo")
    val audioType = MutableStateFlow(0)
    val serverUrl = MutableStateFlow("http://10.0.2.2:8010")

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage: SharedFlow<String> = _toastMessage

    private var _selectedAudioUri: Uri? = null
    private var _selectedFileName = ""

    init {
        webRTCManager.initialize()
    }

    fun setAudioUri(uri: Uri?, fileName: String) {
        _selectedAudioUri = uri
        _selectedFileName = fileName
    }

    fun startConnection() {
        webRTCManager.startConnection(
            avatar = avatarId.value.takeIf { it.isNotBlank() },
            refAudio = refAudio.value.takeIf { it.isNotBlank() },
            refText = refText.value.takeIf { it.isNotBlank() }
        )
    }

    fun stopConnection() {
        webRTCManager.stopConnection()
    }

    fun sendText() {
        val sid = sessionId.value
        if (sid.isNullOrBlank()) {
            viewModelScope.launch {
                _toastMessage.emit("Not connected")
            }
            return
        }

        viewModelScope.launch {
            try {
                val request = HumanRequest(
                    sessionid = sid,
                    text = textInput.value,
                    type = textType.value
                )
                val response = api.postHuman(request)
                if (response.isSuccessful) {
                    textInput.value = ""
                    _toastMessage.emit("Text sent")
                } else {
                    _toastMessage.emit("Failed: ${response.message()}")
                }
            } catch (e: Exception) {
                _toastMessage.emit("Error: ${e.message}")
            }
        }
    }

    fun sendAudio() {
        val sid = sessionId.value
        val uri = _selectedAudioUri
        if (sid.isNullOrBlank()) {
            viewModelScope.launch {
                _toastMessage.emit("Not connected")
            }
            return
        }
        if (uri == null) {
            viewModelScope.launch {
                _toastMessage.emit("No audio file selected")
            }
            return
        }

        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val inputStream = context.contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes() ?: return@launch
                inputStream.close()

                val requestBody = bytes.toRequestBody("audio/*".toMediaTypeOrNull())
                val multipartBody = MultipartBody.Part.createFormData("file", _selectedFileName, requestBody)
                val sessionIdBody = sid.toRequestBody("text/plain".toMediaTypeOrNull())

                val response = api.postHumanAudio(multipartBody, sessionIdBody)
                if (response.isSuccessful) {
                    _toastMessage.emit("Audio sent")
                    _selectedAudioUri = null
                    _selectedFileName = ""
                } else {
                    _toastMessage.emit("Failed: ${response.message()}")
                }
            } catch (e: Exception) {
                _toastMessage.emit("Error: ${e.message}")
            }
        }
    }

    fun toggleRecording() {
        val sid = sessionId.value
        if (sid.isNullOrBlank()) {
            viewModelScope.launch {
                _toastMessage.emit("Not connected")
            }
            return
        }

        viewModelScope.launch {
            try {
                val type = if (isRecording.value) "end_record" else "start_record"
                val response = api.postRecord(RecordRequest(sessionid = sid, type = type))
                if (response.isSuccessful) {
                    isRecording.value = !isRecording.value
                    recordingStatus.value = if (isRecording.value) "Recording..." else "Stopped"
                    _toastMessage.emit(if (isRecording.value) "Recording started" else "Recording stopped")
                } else {
                    _toastMessage.emit("Failed: ${response.message()}")
                }
            } catch (e: Exception) {
                _toastMessage.emit("Error: ${e.message}")
            }
        }
    }

    fun setAudiotype() {
        val sid = sessionId.value
        if (sid.isNullOrBlank()) {
            viewModelScope.launch {
                _toastMessage.emit("Not connected")
            }
            return
        }

        viewModelScope.launch {
            try {
                val response = api.postAudiotype(
                    AudioTypeRequest(sessionid = sid, audiotype = audioType.value)
                )
                if (response.isSuccessful) {
                    _toastMessage.emit("Audiotype set to ${audioType.value}")
                } else {
                    _toastMessage.emit("Failed: ${response.message()}")
                }
            } catch (e: Exception) {
                _toastMessage.emit("Error: ${e.message}")
            }
        }
    }

    fun clearError() {
        webRTCManager.clearError()
    }

    override fun onCleared() {
        super.onCleared()
        webRTCManager.release()
    }
}
