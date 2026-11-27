package com.example.data.model

import org.webrtc.EglBase
import org.webrtc.VideoTrack

enum class CallType(val wire: String) {
  AUDIO("audio"),
  VIDEO("video");

  companion object {
    fun fromWire(raw: String?): CallType =
      entries.firstOrNull { it.wire == raw } ?: AUDIO
  }
}

enum class CallStatus(val wire: String) {
  CONNECTING("connecting"),
  RINGING("ringing"),
  CONNECTED("connected"),
  ENDED("ended"),
  DECLINED("declined"),
  MISSED("missed");

  val isTerminal: Boolean
    get() = this == ENDED || this == DECLINED || this == MISSED

  companion object {
    fun fromWire(raw: String?): CallStatus =
      entries.firstOrNull { it.wire == raw } ?: RINGING
  }
}

enum class CallTransport {
  RTDB,
  FIRESTORE,
}

enum class CallConnectionMode {
  UNKNOWN,
  P2P,
  RELAY,
}

data class SessionDescriptionSignal(
  val type: String = "",
  val sdp: String = "",
)

data class IceCandidateSignal(
  val id: String = "",
  val sdpMid: String = "",
  val sdpMLineIndex: Int = 0,
  val candidate: String = "",
)

data class IncomingCallInvite(
  val callId: String,
  val chatId: String,
  val callerId: String,
  val callerName: String,
  val callerUsername: String,
  val type: CallType,
  val status: CallStatus,
  val createdAtMs: Long,
  val transport: CallTransport = CallTransport.RTDB,
)

data class CallSessionSnapshot(
  val callId: String,
  val callerId: String,
  val receiverId: String,
  val type: CallType,
  val status: CallStatus,
  val offer: SessionDescriptionSignal?,
  val answer: SessionDescriptionSignal?,
)

data class ActiveCallUiState(
  val callId: String,
  val chatId: String,
  val peerUid: String,
  val peerName: String,
  val peerUsername: String,
  val isOutgoing: Boolean,
  val type: CallType,
  val status: CallStatus,
  val connectionMode: CallConnectionMode = CallConnectionMode.UNKNOWN,
  val connectedAtMs: Long = 0L,
  val isMicMuted: Boolean = false,
  val isCameraEnabled: Boolean = false,
  val isSpeakerOn: Boolean = false,
  val isUsingFrontCamera: Boolean = true,
  val errorMessage: String? = null,
  val eglContext: EglBase.Context? = null,
  val localVideoTrack: VideoTrack? = null,
  val remoteVideoTrack: VideoTrack? = null,
)
