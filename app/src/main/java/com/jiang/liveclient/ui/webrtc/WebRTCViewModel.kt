package com.jiang.liveclient.ui.webrtc

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

class WebRTCViewModel(application: Application) : AndroidViewModel(application) {

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
    val eglBaseContext get() = webRTCManager.getEglBaseContext()

    val isRecording = MutableStateFlow(false)
    val recordingStatus = MutableStateFlow("")

    // Connection inputs
    val avatarId = MutableStateFlow("wav2lip256_avatar1")
    val refAudio = MutableStateFlow("")
    val refText = MutableStateFlow("")

    // Text driver inputs
    val textInput = MutableStateFlow("你好，欢迎使用LiveTalking")
    val textType = MutableStateFlow("echo")
    val interruptEnabled = MutableStateFlow(true)

    // Audiotype
    val audioType = MutableStateFlow(2)

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
            viewModelScope.launch { _toastMessage.emit("请先连接 WebRTC") }
            return
        }
        if (textInput.value.isBlank()) {
            viewModelScope.launch { _toastMessage.emit("请输入文本") }
            return
        }

        viewModelScope.launch {
            try {
                val request = HumanRequest(
                    sessionid = sid,
                    text = textInput.value,
                    type = textType.value,
                    interrupt = interruptEnabled.value
                )
                val response = api.postHuman(request)
                if (response.isSuccessful) {
                    textInput.value = ""
                    _toastMessage.emit("文本已发送")
                } else {
                    _toastMessage.emit("发送失败: ${response.message()}")
                }
            } catch (e: Exception) {
                _toastMessage.emit("错误: ${e.message}")
            }
        }
    }

    fun interrupt() {
        val sid = sessionId.value
        if (sid.isNullOrBlank()) {
            viewModelScope.launch { _toastMessage.emit("请先连接 WebRTC") }
            return
        }

        viewModelScope.launch {
            try {
                val response = api.postInterrupt(SessionRequest(sessionid = sid))
                if (response.isSuccessful) {
                    _toastMessage.emit("已打断")
                } else {
                    _toastMessage.emit("打断失败: ${response.message()}")
                }
            } catch (e: Exception) {
                _toastMessage.emit("错误: ${e.message}")
            }
        }
    }

    fun sendAudio() {
        val sid = sessionId.value
        val uri = _selectedAudioUri
        if (sid.isNullOrBlank()) {
            viewModelScope.launch { _toastMessage.emit("请先连接 WebRTC") }
            return
        }
        if (uri == null) {
            viewModelScope.launch { _toastMessage.emit("请选择音频文件") }
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
                    _toastMessage.emit("音频已发送")
                    _selectedAudioUri = null
                    _selectedFileName = ""
                } else {
                    _toastMessage.emit("发送失败: ${response.message()}")
                }
            } catch (e: Exception) {
                _toastMessage.emit("错误: ${e.message}")
            }
        }
    }

    fun toggleRecording() {
        val sid = sessionId.value
        if (sid.isNullOrBlank()) {
            viewModelScope.launch { _toastMessage.emit("请先连接 WebRTC") }
            return
        }

        viewModelScope.launch {
            try {
                val type = if (isRecording.value) "end_record" else "start_record"
                val response = api.postRecord(RecordRequest(sessionid = sid, type = type))
                if (response.isSuccessful) {
                    isRecording.value = !isRecording.value
                    recordingStatus.value = if (isRecording.value) "录制中..." else "已停止"
                    _toastMessage.emit(if (isRecording.value) "开始录制" else "停止录制")
                } else {
                    _toastMessage.emit("录制失败: ${response.message()}")
                }
            } catch (e: Exception) {
                _toastMessage.emit("错误: ${e.message}")
            }
        }
    }

    fun downloadRecording() {
        val sid = sessionId.value
        if (sid.isNullOrBlank()) {
            viewModelScope.launch { _toastMessage.emit("请先连接 WebRTC") }
            return
        }
        viewModelScope.launch {
            _toastMessage.emit("下载录像: /record/$sid")
        }
    }

    fun setAudiotype() {
        val sid = sessionId.value
        if (sid.isNullOrBlank()) {
            viewModelScope.launch { _toastMessage.emit("请先连接 WebRTC") }
            return
        }

        viewModelScope.launch {
            try {
                val response = api.postAudiotype(
                    AudioTypeRequest(sessionid = sid, audiotype = audioType.value)
                )
                if (response.isSuccessful) {
                    _toastMessage.emit("动作已切换")
                } else {
                    _toastMessage.emit("切换失败: ${response.message()}")
                }
            } catch (e: Exception) {
                _toastMessage.emit("错误: ${e.message}")
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
