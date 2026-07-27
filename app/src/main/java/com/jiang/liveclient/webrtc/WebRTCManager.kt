package com.jiang.liveclient.webrtc

import android.content.Context
import android.util.Log
import com.jiang.liveclient.data.api.LiveTalkingApi
import com.jiang.liveclient.data.model.OfferRequest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.webrtc.*

private const val TAG = "WebRTCManager"

class WebRTCManager(
    private val context: Context,
    private val api: LiveTalkingApi,
    private val scope: CoroutineScope
) {
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null

    private val _connectionState = MutableStateFlow("new")
    val connectionState: StateFlow<String> = _connectionState

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack

    private val _remoteAudioTrack = MutableStateFlow<AudioTrack?>(null)
    val remoteAudioTrack: StateFlow<AudioTrack?> = _remoteAudioTrack

    private val _sessionId = MutableStateFlow<String?>(null)
    val sessionId: StateFlow<String?> = _sessionId

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    fun clearError() {
        _errorMessage.value = null
    }

    fun initialize() {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .createPeerConnectionFactory()
    }

    fun startConnection(
        avatar: String?,
        refAudio: String?,
        refText: String?
    ) {
        scope.launch {
            try {
                _connectionState.value = "connecting"
                Log.d(TAG, "startConnection: starting")

                if (peerConnectionFactory == null) {
                    Log.d(TAG, "startConnection: initializing factory")
                    initialize()
                }

                val config = WebRTCConfig.createPeerConnectionConfig()
                Log.d(TAG, "startConnection: config=$config")

                peerConnection = peerConnectionFactory?.createPeerConnection(
                    config,
                    createPeerConnectionObserver()
                )

                if (peerConnection == null) {
                    Log.e(TAG, "startConnection: peerConnection is null")
                    _errorMessage.value = "Failed to create peer connection"
                    _connectionState.value = "failed"
                    return@launch
                }
                Log.d(TAG, "startConnection: peerConnection created")

                // Add sendrecv transceivers so server's sendrecv works with client's sendrecv
                peerConnection?.addTransceiver(
                    MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                    RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV)
                )
                peerConnection?.addTransceiver(
                    MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
                    RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.SEND_RECV)
                )

                var capturedSdp: SessionDescription? = null
                var sdpSetSuccess = false
                val sdpObserver = object : SdpObserver {
                    override fun onCreateSuccess(sdp: SessionDescription?) {
                        Log.d(TAG, "onCreateSuccess: sdp=$sdp")
                        sdp?.let {
                            capturedSdp = it
                            Log.d(TAG, "onCreateSuccess: calling setLocalDescription")
                            peerConnection?.setLocalDescription(object : SdpObserver {
                                override fun onCreateSuccess(sdp: SessionDescription?) {}
                                override fun onSetSuccess() {
                                    Log.d(TAG, "setLocalDescription onSetSuccess")
                                    sdpSetSuccess = true
                                }
                                override fun onCreateFailure(info: String?) {
                                    Log.e(TAG, "setLocalDescription onCreateFailure: $info")
                                    scope.launch { _errorMessage.value = "Sdp set error: $info" }
                                }
                                override fun onSetFailure(info: String?) {
                                    Log.e(TAG, "setLocalDescription onSetFailure: $info")
                                    scope.launch { _errorMessage.value = "Sdp set error: $info" }
                                }
                            }, it)
                        }
                    }
                    override fun onSetSuccess() {}
                    override fun onCreateFailure(info: String?) {
                        Log.e(TAG, "onCreateFailure: $info")
                        scope.launch {
                            _errorMessage.value = "Sdp create error: $info"
                        }
                    }
                    override fun onSetFailure(info: String?) {
                        Log.e(TAG, "onSetFailure: $info")
                        scope.launch {
                            _errorMessage.value = "Sdp set error: $info"
                        }
                    }
                }

                // Create offer
                Log.d(TAG, "startConnection: calling createOffer")
                peerConnection?.createOffer(sdpObserver, MediaConstraints())

                // Wait for local description to be set
                delay(3000)

                Log.d(TAG, "startConnection: after delay, sdpSetSuccess=$sdpSetSuccess, capturedSdp=${capturedSdp?.description?.take(50)}")

                if (!sdpSetSuccess || capturedSdp == null) {
                    Log.e(TAG, "startConnection: failed sdpSetSuccess=$sdpSetSuccess capturedSdp=${capturedSdp == null}")
                    _errorMessage.value = "Failed to set local description"
                    _connectionState.value = "failed"
                    return@launch
                }

                val localSdp = capturedSdp!!.description
                Log.d(TAG, "startConnection: localSdp ready, length=${localSdp.length}")

                // Send offer to server
                val offerRequest = OfferRequest(
                    sdp = localSdp,
                    type = "offer",
                    avatar = avatar,
                    refaudio = refAudio,
                    reftext = refText
                )
                Log.d(TAG, "startConnection: posting offer to server")

                val response = api.postOffer(offerRequest)
                Log.d(TAG, "startConnection: response isSuccessful=${response.isSuccessful}, code=${response.code()}")
                if (!response.isSuccessful || response.body() == null) {
                    Log.e(TAG, "startConnection: offer failed ${response.message()}")
                    _errorMessage.value = "Offer failed: ${response.message()}"
                    _connectionState.value = "failed"
                    return@launch
                }

                val answer = response.body()!!
                Log.d(TAG, "startConnection: got answer sessionid=${answer.sessionid}")
                _sessionId.value = answer.sessionid

                // Set remote description with answer
                val answerSdp = SessionDescription(SessionDescription.Type.ANSWER, answer.sdp)
                peerConnection?.setRemoteDescription(sdpObserver, answerSdp)

                _connectionState.value = "connected"
                Log.d(TAG, "startConnection: connected!")

            } catch (e: Exception) {
                Log.e(TAG, "startConnection exception: ${e.message}", e)
                _errorMessage.value = e.message
                _connectionState.value = "failed"
            }
        }
    }

    private fun createPeerConnectionObserver(): PeerConnection.Observer {
        return object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                Log.d(TAG, "onSignalingChange: $state")
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "onIceConnectionChange: $state")
                _connectionState.value = when (state) {
                    PeerConnection.IceConnectionState.CONNECTED -> "connected"
                    PeerConnection.IceConnectionState.DISCONNECTED -> "disconnected"
                    PeerConnection.IceConnectionState.FAILED -> "failed"
                    PeerConnection.IceConnectionState.CLOSED -> "closed"
                    else -> state?.name ?: "unknown"
                }
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {
                Log.d(TAG, "onIceConnectionReceivingChange: $receiving")
            }

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                Log.d(TAG, "onIceGatheringChange: $state")
            }

            override fun onIceCandidate(candidate: IceCandidate?) {
                Log.d(TAG, "onIceCandidate: $candidate")
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

            override fun onAddStream(stream: MediaStream?) {
                Log.d(TAG, "onAddStream: $stream")
                stream?.videoTracks?.firstOrNull()?.let {
                    _remoteVideoTrack.value = it
                }
                stream?.audioTracks?.firstOrNull()?.let {
                    _remoteAudioTrack.value = it
                }
            }

            override fun onRemoveStream(stream: MediaStream?) {}

            override fun onDataChannel(channel: DataChannel?) {}

            override fun onRenegotiationNeeded() {
                Log.d(TAG, "onRenegotiationNeeded")
            }

            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                Log.d(TAG, "onAddTrack: receiver=$receiver")
                receiver?.track()?.let { track ->
                    when (track) {
                        is VideoTrack -> _remoteVideoTrack.value = track
                        is AudioTrack -> _remoteAudioTrack.value = track
                    }
                }
            }
        }
    }

    fun stopConnection() {
        peerConnection?.close()
        peerConnection = null
        _connectionState.value = "closed"
        _remoteVideoTrack.value = null
        _remoteAudioTrack.value = null
        _sessionId.value = null
    }

    fun release() {
        stopConnection()
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
    }
}
