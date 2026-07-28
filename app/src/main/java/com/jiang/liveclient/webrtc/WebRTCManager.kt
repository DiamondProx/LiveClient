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
    private var eglBase: EglBase? = null

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

        eglBase = EglBase.create()
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase!!.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase!!.eglBaseContext))
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

                // Add recvonly transceivers - we only receive video/audio from server
                peerConnection?.addTransceiver(
                    MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
                    RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
                )
                peerConnection?.addTransceiver(
                    MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
                    RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY)
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
                Log.d(TAG, "onAddStream: videoTracks class=${stream?.videoTracks?.javaClass?.name}, size=${stream?.videoTracks?.size}")
                stream?.videoTracks?.firstOrNull()?.let {
                    Log.d(TAG, "onAddStream: first videoTrack class=${it.javaClass.name}")
                    Log.d(TAG, "onAddStream: setting remoteVideoTrack")
                    _remoteVideoTrack.value = it
                } ?: Log.d(TAG, "onAddStream: videoTracks.firstOrNull() is null")
                stream?.audioTracks?.firstOrNull()?.let {
                    Log.d(TAG, "onAddStream: setting remoteAudioTrack")
                    _remoteAudioTrack.value = it
                }
            }

            override fun onRemoveStream(stream: MediaStream?) {}

            override fun onDataChannel(channel: DataChannel?) {}

            override fun onRenegotiationNeeded() {
                Log.d(TAG, "onRenegotiationNeeded")
            }

            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                Log.d(TAG, "onAddTrack: receiver=$receiver, streams=${streams?.map { it.id }}")
                val track = receiver?.track()
                Log.d(TAG, "onAddTrack: track=${track}, track class=${track?.javaClass?.name}")
                track?.let { t ->
                    Log.d(TAG, "onAddTrack: track type=${t.javaClass.name}, kind=${t.kind()}")
                    val trackKind = t.kind()
                    if (trackKind == "video") {
                        Log.d(TAG, "onAddTrack: adding frame monitoring to video track")
                        val frameCounter = object : VideoSink {
                            private var frameCount = 0
                            private var lastLogTime = System.currentTimeMillis()
                            override fun onFrame(frame: VideoFrame?) {
                                frame?.let {
                                    frameCount++
                                    val now = System.currentTimeMillis()
                                    if (now - lastLogTime > 1000) {
                                        Log.d(TAG, "VideoSink: received $frameCount frames in last second")
                                        frameCount = 0
                                        lastLogTime = now
                                    }
                                }
                            }
                        }
                        val videoTrack = t as? VideoTrack
                        if (videoTrack != null) {
                            videoTrack.addSink(frameCounter)
                            Log.d(TAG, "onAddTrack: setting VideoTrack")
                            _remoteVideoTrack.value = videoTrack
                        } else {
                            Log.e(TAG, "onAddTrack: cannot cast to VideoTrack, track class=${t.javaClass.name}")
                        }
                    } else if (trackKind == "audio") {
                        Log.d(TAG, "onAddTrack: setting AudioTrack")
                        val audioTrack = t as? AudioTrack
                        if (audioTrack != null) {
                            _remoteAudioTrack.value = audioTrack
                        }
                    } else {
                        Log.w(TAG, "onAddTrack: unknown track kind: $trackKind")
                    }
                } ?: Log.d(TAG, "onAddTrack: track is null")
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

    fun getEglBaseContext(): EglBase.Context? = eglBase?.eglBaseContext

    fun release() {
        stopConnection()
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
        eglBase?.release()
        eglBase = null
    }
}
