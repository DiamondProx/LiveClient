package com.jiang.liveclient.webrtc

import org.webrtc.PeerConnection
import org.webrtc.MediaConstraints
import org.webrtc.MediaConstraints.KeyValuePair

object WebRTCConfig {
    fun createPeerConnectionConfig(): PeerConnection.RTCConfiguration {
        val iceServer = PeerConnection.IceServer("stun:stun.l.google.com:19302")

        return PeerConnection.RTCConfiguration(listOf(iceServer))
    }

    fun createMediaConstraints(): MediaConstraints {
        return MediaConstraints().apply {
            // Remove OfferToReceiveAudio/Video - transceivers are added explicitly with recvonly direction
        }
    }
}
