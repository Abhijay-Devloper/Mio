package com.example.core.calls

import android.content.Context
import android.util.Log
import com.example.R
import com.example.core.config.MioConstants
import com.example.core.config.MioCopy
import com.example.core.feedback.ActiveChatTracker
import com.example.core.feedback.MioNotificationHelper
import com.example.data.local.MioPreferences
import com.example.data.model.ActiveCallUiState
import com.example.data.model.CallConnectionMode
import com.example.data.model.CallStatus
import com.example.data.model.CallType
import com.example.data.model.IceCandidateSignal
import com.example.data.model.IncomingCallInvite
import com.example.data.model.SessionDescriptionSignal
import com.example.data.model.UserProfile
import com.example.data.remote.CallSignalingService
import com.example.data.remote.IceServerProvider
import com.example.data.remote.MeteredIceServerProvider
import com.example.data.remote.OkHttpPushNotificationRelay
import com.example.data.remote.PushNotificationRelay
import com.example.data.remote.RtdbCallSignalingRepository
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Native Android WebRTC (RTCPeerConnection) service for 1-on-1 audio and video calls.
 * Attempts direct P2P via Google STUN first and automatically falls back to TURN relay
 * when configured, keeping all media peer-to-peer and RTDB strictly for signaling.
 */
class WebRtcCallManager(
  context: Context,
  private val signaling: CallSignalingService = RtdbCallSignalingRepository(
    firestoreProvider = {
      FirebaseFirestore.getInstance(context.applicationContext.getString(R.string.firestore_database_id))
    },
  ),
  private val iceServerProvider: IceServerProvider = MeteredIceServerProvider(),
  private val audioController: CallAudioController = CallAudioController(context),
  private val pushRelay: PushNotificationRelay = OkHttpPushNotificationRelay(),
) {

  private val appContext = context.applicationContext
  private val notificationHelper = MioNotificationHelper(appContext)
  private val preferences = MioPreferences(appContext)
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  private val _incomingInvite = MutableStateFlow<IncomingCallInvite?>(null)
  val incomingInvite: StateFlow<IncomingCallInvite?> = _incomingInvite.asStateFlow()

  private val _activeCall = MutableStateFlow<ActiveCallUiState?>(null)
  val activeCall: StateFlow<ActiveCallUiState?> = _activeCall.asStateFlow()

  private val _callErrorNotice = MutableStateFlow<String?>(null)
  val callErrorNotice: StateFlow<String?> = _callErrorNotice.asStateFlow()

  private var incomingWatchJob: Job? = null
  private var sessionWatchJob: Job? = null
  private var candidatesWatchJob: Job? = null
  private var ringTimeoutJob: Job? = null
  private var connectTimeoutJob: Job? = null
  private var disconnectGraceJob: Job? = null
  private var statsPollJob: Job? = null

  fun clearCallErrorNotice() {
    _callErrorNotice.value = null
  }

  private var eglBase: EglBase? = null
  private var peerConnectionFactory: PeerConnectionFactory? = null
  private var peerConnection: PeerConnection? = null
  private var localAudioSource: AudioSource? = null
  private var localAudioTrack: AudioTrack? = null
  private var localVideoSource: VideoSource? = null
  private var localVideoTrack: VideoTrack? = null
  private var remoteVideoTrack: VideoTrack? = null
  private var videoCapturer: CameraVideoCapturer? = null
  private var surfaceTextureHelper: SurfaceTextureHelper? = null

  private var remoteDescriptionSet = false
  private var callSessionReadyForCandidates = false
  private val pendingRemoteCandidates = mutableListOf<IceCandidate>()
  private val pendingLocalCandidates = mutableListOf<IceCandidateSignal>()
  private val processedCandidateIds = mutableSetOf<String>()
  private var currentCallId: String? = null
  private var currentReceiverIdForCleanup: String? = null
  private var isCurrentCaller: Boolean = false
  private var isEndingCall: Boolean = false

  fun startIncomingCallListener(myUid: String) {
    incomingWatchJob?.cancel()
    if (!MioConstants.SAFE_ID_REGEX.matches(myUid)) {
      _incomingInvite.value = null
      return
    }
    incomingWatchJob = scope.launch {
      signaling.observeIncomingCalls(myUid).collect { invite ->
        val inActiveCall = _activeCall.value != null
        val filtered = if (inActiveCall) null else invite
        val previousId = _incomingInvite.value?.callId
        _incomingInvite.value = filtered
        if (filtered != null) {
          if (ActiveChatTracker.isAppInForeground) {
            notificationHelper.cancelIncomingCallNotification(filtered.callId)
            if (filtered.callId != previousId) {
              audioController.startIncomingRingtone()
            }
          } else {
            audioController.stopIncomingRingtone()
            val settings = runCatching { preferences.settings.first() }.getOrNull()
            val canRing = (settings?.notificationsEnabled ?: true) &&
              (settings?.callNotificationsEnabled ?: true) &&
              (settings?.muteAllUntilMs ?: 0L) <= System.currentTimeMillis() &&
              (settings?.whoCanCallMe ?: "accepted") != "nobody"
            if (canRing) {
              notificationHelper.showIncomingCallNotification(filtered)
            }
          }
        } else {
          audioController.stopIncomingRingtone()
          notificationHelper.cancelIncomingCallNotification(previousId)
        }
      }
    }
  }

  fun onAppForegrounded() {
    val invite = _incomingInvite.value
    if (invite != null && _activeCall.value == null) {
      notificationHelper.cancelIncomingCallNotification(invite.callId)
      audioController.startIncomingRingtone()
    }
  }

  fun onAppBackgrounded() {
    val invite = _incomingInvite.value
    if (invite != null && _activeCall.value == null) {
      audioController.stopIncomingRingtone()
    }
  }

  fun startCall(
    callerProfile: UserProfile,
    receiverId: String,
    receiverName: String,
    receiverUsername: String,
    chatId: String,
    isVideo: Boolean,
  ) {
    if (_activeCall.value != null) return
    if (
      !MioConstants.SAFE_ID_REGEX.matches(callerProfile.uid) ||
      !MioConstants.SAFE_ID_REGEX.matches(receiverId) ||
      callerProfile.uid == receiverId
    ) {
      return
    }

    audioController.stopIncomingRingtone()
    notificationHelper.cancelIncomingCallNotification()
    _incomingInvite.value = null
    CallSessionHolder.setMinimized(false)

    val callId = UUID.randomUUID().toString().replace("-", "")
    val callType = if (isVideo) CallType.VIDEO else CallType.AUDIO
    isCurrentCaller = true
    currentCallId = callId
    currentReceiverIdForCleanup = receiverId
    isEndingCall = false
    remoteDescriptionSet = false
    callSessionReadyForCandidates = false
    pendingRemoteCandidates.clear()
    pendingLocalCandidates.clear()
    processedCandidateIds.clear()

    val egl = ensureEglBase()
    val resolvedPeerName = receiverName.ifBlank { receiverUsername }
    _activeCall.value = ActiveCallUiState(
      callId = callId,
      chatId = chatId,
      peerUid = receiverId,
      peerName = resolvedPeerName,
      peerUsername = receiverUsername,
      isOutgoing = true,
      type = callType,
      status = CallStatus.CONNECTING,
      isCameraEnabled = isVideo,
      isSpeakerOn = isVideo,
      isUsingFrontCamera = true,
      eglContext = egl.eglBaseContext,
    )
    MioCallForegroundService.startOrUpdate(appContext, resolvedPeerName, isVideo)

    scope.launch {
      try {
        audioController.startCallAudio(speakerOn = isVideo)
        val iceServers = iceServerProvider.buildIceServers()
        if (currentCallId != callId || isEndingCall) return@launch

        val pc = createPeerConnectionAndTracks(
          callId = callId,
          isCaller = true,
          isVideo = isVideo,
          iceServers = iceServers,
        )
        peerConnection = pc

        val offerConstraints = MediaConstraints().apply {
          mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
          mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (isVideo) "true" else "false"))
        }
        val offerSdp = pc.createOfferAwait(offerConstraints)
        pc.setLocalDescriptionAwait(offerSdp)

        signaling.createOutgoingCall(
          callId = callId,
          chatId = chatId,
          callerId = callerProfile.uid,
          callerName = callerProfile.displayName.ifBlank { callerProfile.username },
          callerUsername = callerProfile.username,
          receiverId = receiverId,
          type = callType,
          offer = SessionDescriptionSignal(
            type = offerSdp.type.canonicalForm(),
            sdp = offerSdp.description,
          ),
        )
        callSessionReadyForCandidates = true
        flushPendingLocalCandidates(callId, isCaller = true)
        signaling.armDisconnectCleanup(callId, receiverId)
        scope.launch {
          pushRelay.notifyIncomingCall(
            callId = callId,
            chatId = chatId,
            receiverUid = receiverId,
            callerName = callerProfile.displayName.ifBlank { callerProfile.username },
            callerUsername = callerProfile.username,
            callType = callType,
          )
        }

        _activeCall.value = _activeCall.value?.let { state ->
          if (state.callId == callId && state.status == CallStatus.CONNECTING) {
            state.copy(status = CallStatus.RINGING)
          } else state
        }
        observeSignalingForCaller(callId, receiverId)
        startRingTimeout(callId, receiverId)
      } catch (e: Exception) {
        Log.w(TAG, "Failed to start call: ${e.javaClass.simpleName}")
        _callErrorNotice.value = MioCopy.CALL_CONNECTION_FAILED
        endCall(reasonStatus = CallStatus.ENDED)
      }
    }
  }

  fun answerCall(invite: IncomingCallInvite, myUid: String) {
    if (_activeCall.value != null) return
    audioController.stopIncomingRingtone()
    notificationHelper.cancelIncomingCallNotification(invite.callId)
    _incomingInvite.value = null
    CallSessionHolder.setMinimized(false)

    val callId = invite.callId
    val isVideo = invite.type == CallType.VIDEO
    isCurrentCaller = false
    currentCallId = callId
    currentReceiverIdForCleanup = myUid
    isEndingCall = false
    remoteDescriptionSet = false
    callSessionReadyForCandidates = true
    pendingRemoteCandidates.clear()
    pendingLocalCandidates.clear()
    processedCandidateIds.clear()
    signaling.registerIncomingCallContext(callId, invite.chatId, invite.transport)

    val egl = ensureEglBase()
    _activeCall.value = ActiveCallUiState(
      callId = callId,
      chatId = invite.chatId,
      peerUid = invite.callerId,
      peerName = invite.callerName,
      peerUsername = invite.callerUsername,
      isOutgoing = false,
      type = invite.type,
      status = CallStatus.CONNECTING,
      isCameraEnabled = isVideo,
      isSpeakerOn = isVideo,
      isUsingFrontCamera = true,
      eglContext = egl.eglBaseContext,
    )
    MioCallForegroundService.startOrUpdate(appContext, invite.callerName, isVideo)

    scope.launch {
      try {
        signaling.updateCallStatus(callId, myUid, CallStatus.CONNECTING)
        signaling.removeIncomingInvite(myUid, callId)
        audioController.startCallAudio(speakerOn = isVideo)
        val iceServers = iceServerProvider.buildIceServers()
        if (currentCallId != callId || isEndingCall) return@launch

        val pc = createPeerConnectionAndTracks(
          callId = callId,
          isCaller = false,
          isVideo = isVideo,
          iceServers = iceServers,
        )
        peerConnection = pc

        observeSignalingForReceiver(callId, myUid)
        startConnectTimeout(callId)
      } catch (e: Exception) {
        Log.w(TAG, "Failed to answer call: ${e.javaClass.simpleName}")
        _callErrorNotice.value = MioCopy.CALL_CONNECTION_FAILED
        endCall(reasonStatus = CallStatus.ENDED)
      }
    }
  }

  fun declineCall(invite: IncomingCallInvite, myUid: String) {
    audioController.stopIncomingRingtone()
    notificationHelper.cancelIncomingCallNotification(invite.callId)
    _incomingInvite.value = null
    scope.launch {
      signaling.updateCallStatus(invite.callId, myUid, CallStatus.DECLINED)
      signaling.removeIncomingInvite(myUid, invite.callId)
    }
  }

  fun toggleMicMute() {
    val current = _activeCall.value ?: return
    val nextMuted = !current.isMicMuted
    localAudioTrack?.setEnabled(!nextMuted)
    audioController.setMicrophoneMuted(nextMuted)
    _activeCall.value = current.copy(isMicMuted = nextMuted)
  }

  fun toggleCameraEnabled() {
    val current = _activeCall.value ?: return
    val nextEnabled = !current.isCameraEnabled
    val track = localVideoTrack ?: return
    track.setEnabled(nextEnabled)
    val capturer = videoCapturer
    scope.launch(Dispatchers.IO) {
      runCatching {
        if (nextEnabled) {
          capturer?.startCapture(VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_FPS)
        } else {
          capturer?.stopCapture()
        }
      }
    }
    _activeCall.value = current.copy(isCameraEnabled = nextEnabled)
    MioCallForegroundService.startOrUpdate(
      context = appContext,
      peerName = current.peerName,
      isVideo = current.type == CallType.VIDEO && nextEnabled,
    )
  }

  fun switchCamera() {
    val current = _activeCall.value ?: return
    val capturer = videoCapturer ?: return
    capturer.switchCamera(
      object : CameraVideoCapturer.CameraSwitchHandler {
        override fun onCameraSwitchDone(isFrontCamera: Boolean) {
          _activeCall.value = _activeCall.value?.copy(isUsingFrontCamera = isFrontCamera)
        }

        override fun onCameraSwitchError(errorDescription: String?) {
          Log.w(TAG, "Camera switch failed: $errorDescription")
        }
      },
    )
  }

  fun toggleSpeaker() {
    val current = _activeCall.value ?: return
    val nextSpeaker = !current.isSpeakerOn
    audioController.setSpeakerphone(nextSpeaker)
    _activeCall.value = current.copy(isSpeakerOn = nextSpeaker)
  }

  fun endCall(reasonStatus: CallStatus = CallStatus.ENDED) {
    if (isEndingCall) return
    val callId = currentCallId ?: _activeCall.value?.callId
    val receiverId = currentReceiverIdForCleanup
    isEndingCall = true

    ringTimeoutJob?.cancel()
    connectTimeoutJob?.cancel()
    disconnectGraceJob?.cancel()
    statsPollJob?.cancel()
    sessionWatchJob?.cancel()
    candidatesWatchJob?.cancel()

    val wasCaller = isCurrentCaller
    val wasConnected = _activeCall.value?.status == CallStatus.CONNECTED
    releaseWebRtcResources()
    audioController.restore()
    MioCallForegroundService.stop(appContext)
    CallSessionHolder.setMinimized(false)
    _activeCall.value = null
    currentCallId = null
    currentReceiverIdForCleanup = null
    remoteDescriptionSet = false
    callSessionReadyForCandidates = false
    pendingRemoteCandidates.clear()
    pendingLocalCandidates.clear()
    processedCandidateIds.clear()

    if (!callId.isNullOrBlank()) {
      scope.launch {
        if (wasCaller && !wasConnected && !receiverId.isNullOrBlank()) {
          pushRelay.notifyCallEnded(callId, receiverId)
        }
        signaling.updateCallStatus(callId, receiverId, reasonStatus)
        delay(CLEANUP_DELAY_MS)
        signaling.cleanupCallNodes(callId, receiverId)
      }
    }
  }

  private fun observeSignalingForCaller(callId: String, receiverId: String) {
    var answerApplied = false
    sessionWatchJob?.cancel()
    sessionWatchJob = scope.launch {
      signaling.observeCallSession(callId).collect { session ->
        if (session == null) {
          if (!isEndingCall && currentCallId == callId) {
            endCall(CallStatus.ENDED)
          }
          return@collect
        }

        if (session.status.isTerminal) {
          if (!isEndingCall && currentCallId == callId) {
            endCall(session.status)
          }
          return@collect
        }

        if (session.status == CallStatus.CONNECTING) {
          ringTimeoutJob?.cancel()
          _activeCall.value = _activeCall.value?.let { state ->
            if (state.status == CallStatus.RINGING) state.copy(status = CallStatus.CONNECTING) else state
          }
          startConnectTimeout(callId)
        }

        val answer = session.answer
        val pc = peerConnection
        if (answer != null && !answerApplied && pc != null) {
          answerApplied = true
          ringTimeoutJob?.cancel()
          _activeCall.value = _activeCall.value?.let { state ->
            if (state.status == CallStatus.RINGING) state.copy(status = CallStatus.CONNECTING) else state
          }
          startConnectTimeout(callId)
          try {
            val remoteDesc = SessionDescription(SessionDescription.Type.ANSWER, answer.sdp)
            pc.setRemoteDescriptionAwait(remoteDesc)
            remoteDescriptionSet = true
            flushPendingCandidates(pc)
          } catch (e: Exception) {
            Log.w(TAG, "Applying remote answer failed: ${e.javaClass.simpleName}")
            _callErrorNotice.value = MioCopy.CALL_CONNECTION_FAILED
            endCall(CallStatus.ENDED)
          }
        }
      }
    }

    candidatesWatchJob?.cancel()
    candidatesWatchJob = scope.launch {
      signaling.observeRemoteCandidates(callId, isCallerObservingReceiver = true).collect { cand ->
        handleIncomingIceCandidate(cand)
      }
    }
  }

  private fun observeSignalingForReceiver(callId: String, myUid: String) {
    var answerCreated = false
    sessionWatchJob?.cancel()
    sessionWatchJob = scope.launch {
      signaling.observeCallSession(callId).collect { session ->
        if (session == null) {
          if (!isEndingCall && currentCallId == callId) {
            endCall(CallStatus.ENDED)
          }
          return@collect
        }

        if (session.status.isTerminal) {
          if (!isEndingCall && currentCallId == callId) {
            endCall(session.status)
          }
          return@collect
        }

        val offer = session.offer
        val pc = peerConnection
        if (offer != null && !answerCreated && pc != null) {
          answerCreated = true
          try {
            val remoteDesc = SessionDescription(SessionDescription.Type.OFFER, offer.sdp)
            pc.setRemoteDescriptionAwait(remoteDesc)
            remoteDescriptionSet = true
            flushPendingCandidates(pc)

            val answerConstraints = MediaConstraints().apply {
              mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
              mandatory.add(
                MediaConstraints.KeyValuePair(
                  "OfferToReceiveVideo",
                  if (session.type == CallType.VIDEO) "true" else "false",
                ),
              )
            }
            val answerSdp = pc.createAnswerAwait(answerConstraints)
            pc.setLocalDescriptionAwait(answerSdp)
            signaling.publishAnswer(
              callId = callId,
              receiverId = myUid,
              answer = SessionDescriptionSignal(
                type = answerSdp.type.canonicalForm(),
                sdp = answerSdp.description,
              ),
            )
            signaling.armDisconnectCleanup(callId, myUid)
            _activeCall.value = _activeCall.value?.let { state ->
              if (state.status != CallStatus.CONNECTED) state.copy(status = CallStatus.CONNECTING) else state
            }
          } catch (e: Exception) {
            Log.w(TAG, "Answer negotiation failed: ${e.javaClass.simpleName}")
            _callErrorNotice.value = MioCopy.CALL_CONNECTION_FAILED
            endCall(CallStatus.ENDED)
          }
        }
      }
    }

    candidatesWatchJob?.cancel()
    candidatesWatchJob = scope.launch {
      signaling.observeRemoteCandidates(callId, isCallerObservingReceiver = false).collect { cand ->
        handleIncomingIceCandidate(cand)
      }
    }
  }

  private fun handleIncomingIceCandidate(signal: IceCandidateSignal) {
    if (signal.id.isNotBlank() && !processedCandidateIds.add(signal.id)) return
    val ice = IceCandidate(signal.sdpMid, signal.sdpMLineIndex, signal.candidate)
    val pc = peerConnection
    if (remoteDescriptionSet && pc != null) {
      pc.addIceCandidate(ice)
    } else {
      pendingRemoteCandidates += ice
    }
  }

  private fun flushPendingCandidates(pc: PeerConnection) {
    pendingRemoteCandidates.forEach { pc.addIceCandidate(it) }
    pendingRemoteCandidates.clear()
  }

  private fun flushPendingLocalCandidates(callId: String, isCaller: Boolean) {
    val queued = pendingLocalCandidates.toList()
    pendingLocalCandidates.clear()
    queued.forEach { signal ->
      scope.launch {
        signaling.pushIceCandidate(
          callId = callId,
          isFromCaller = isCaller,
          candidate = signal,
        )
      }
    }
  }

  private fun createPeerConnectionAndTracks(
    callId: String,
    isCaller: Boolean,
    isVideo: Boolean,
    iceServers: List<PeerConnection.IceServer>,
  ): PeerConnection {
    val factory = ensurePeerConnectionFactory()
    val egl = ensureEglBase()

    val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
      sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
      iceTransportsType = PeerConnection.IceTransportsType.ALL
      continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
    }

    val observer = object : PeerConnection.Observer {
      override fun onSignalingChange(newState: PeerConnection.SignalingState?) = Unit

      override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
        scope.launch {
          when (newState) {
            PeerConnection.IceConnectionState.CONNECTED,
            PeerConnection.IceConnectionState.COMPLETED -> {
              markPeerConnected(callId, peerConnection)
            }
            PeerConnection.IceConnectionState.DISCONNECTED -> {
              disconnectGraceJob?.cancel()
              disconnectGraceJob = scope.launch {
                delay(MioConstants.CALL_DISCONNECT_GRACE_MS)
                if (!isEndingCall && currentCallId == callId) {
                  endCall(CallStatus.ENDED)
                }
              }
            }
            PeerConnection.IceConnectionState.FAILED,
            PeerConnection.IceConnectionState.CLOSED -> {
              if (!isEndingCall && currentCallId == callId) {
                if (newState == PeerConnection.IceConnectionState.FAILED &&
                  _activeCall.value?.status != CallStatus.CONNECTED
                ) {
                  _callErrorNotice.value = MioCopy.CALL_CONNECTION_FAILED
                }
                endCall(CallStatus.ENDED)
              }
            }
            else -> Unit
          }
        }
      }

      override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
        scope.launch {
          when (newState) {
            PeerConnection.PeerConnectionState.CONNECTED -> {
              markPeerConnected(callId, peerConnection)
            }
            PeerConnection.PeerConnectionState.FAILED -> {
              if (!isEndingCall && currentCallId == callId) {
                if (_activeCall.value?.status != CallStatus.CONNECTED) {
                  _callErrorNotice.value = MioCopy.CALL_CONNECTION_FAILED
                }
                endCall(CallStatus.ENDED)
              }
            }
            else -> Unit
          }
        }
      }

      override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
      override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) = Unit

      override fun onIceCandidate(candidate: IceCandidate?) {
        if (candidate == null || isEndingCall) return
        val signal = IceCandidateSignal(
          sdpMid = candidate.sdpMid ?: "",
          sdpMLineIndex = candidate.sdpMLineIndex,
          candidate = candidate.sdp,
        )
        scope.launch {
          if (isEndingCall || currentCallId != callId) return@launch
          if (!callSessionReadyForCandidates) {
            pendingLocalCandidates += signal
          } else {
            signaling.pushIceCandidate(
              callId = callId,
              isFromCaller = isCaller,
              candidate = signal,
            )
          }
        }
      }

      override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
      override fun onAddStream(stream: MediaStream?) = Unit
      override fun onRemoveStream(stream: MediaStream?) = Unit
      override fun onDataChannel(dataChannel: DataChannel?) = Unit
      override fun onRenegotiationNeeded() = Unit

      override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
        val track = receiver?.track()
        when (track) {
          is AudioTrack -> {
            runCatching { track.setEnabled(true) }
          }
          is VideoTrack -> {
            runCatching { track.setEnabled(true) }
            scope.launch {
              remoteVideoTrack = track
              _activeCall.value = _activeCall.value?.copy(remoteVideoTrack = track)
            }
          }
        }
      }
    }

    val pc = factory.createPeerConnection(rtcConfig, observer)
      ?: throw IllegalStateException("Unable to create RTCPeerConnection")

    // High-fidelity voice audio with AEC, NS, and AGC enabled
    val audioConstraints = MediaConstraints().apply {
      mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
      mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
      mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
      mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
    }
    val audioSource = factory.createAudioSource(audioConstraints)
    val audioTrack = factory.createAudioTrack(LOCAL_AUDIO_TRACK_ID, audioSource).apply {
      setEnabled(true)
    }
    localAudioSource = audioSource
    localAudioTrack = audioTrack
    pc.addTrack(audioTrack, listOf(STREAM_ID))

    if (isVideo) {
      val capturer = createCameraCapturer()
      if (capturer != null) {
        val texHelper = SurfaceTextureHelper.create("MioCaptureThread", egl.eglBaseContext)
        val videoSource = factory.createVideoSource(capturer.isScreencast)
        capturer.initialize(texHelper, appContext, videoSource.capturerObserver)
        runCatching { capturer.startCapture(VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_FPS) }
        val videoTrack = factory.createVideoTrack(LOCAL_VIDEO_TRACK_ID, videoSource).apply {
          setEnabled(true)
        }
        videoCapturer = capturer
        surfaceTextureHelper = texHelper
        localVideoSource = videoSource
        localVideoTrack = videoTrack
        pc.addTrack(videoTrack, listOf(STREAM_ID))
        _activeCall.value = _activeCall.value?.copy(localVideoTrack = videoTrack)
      }
    }

    return pc
  }

  private fun createCameraCapturer(): CameraVideoCapturer? {
    val enumerator = Camera2Enumerator(appContext)
    val names = enumerator.deviceNames
    val front = names.firstOrNull { enumerator.isFrontFacing(it) }
    val selected = front ?: names.firstOrNull() ?: return null
    return enumerator.createCapturer(selected, null)
  }

  private fun markPeerConnected(callId: String, pc: PeerConnection?) {
    if (isEndingCall || currentCallId != callId) return
    ringTimeoutJob?.cancel()
    connectTimeoutJob?.cancel()
    disconnectGraceJob?.cancel()
    val current = _activeCall.value ?: return
    val wasAlreadyConnected = current.status == CallStatus.CONNECTED
    val updated = current.copy(
      status = CallStatus.CONNECTED,
      connectedAtMs = if (current.connectedAtMs == 0L) System.currentTimeMillis() else current.connectedAtMs,
    )
    _activeCall.value = updated
    MioCallForegroundService.startOrUpdate(
      context = appContext,
      peerName = updated.peerName,
      isVideo = updated.type == CallType.VIDEO && updated.isCameraEnabled,
    )
    if (pc != null) {
      startConnectionStatsPolling(pc)
    }
    if (!wasAlreadyConnected) {
      scope.launch {
        signaling.updateCallStatus(callId, currentReceiverIdForCleanup, CallStatus.CONNECTED)
      }
    }
  }

  private fun startRingTimeout(callId: String, receiverId: String) {
    ringTimeoutJob?.cancel()
    ringTimeoutJob = scope.launch {
      delay(MioConstants.CALL_RING_TIMEOUT_MS)
      val current = _activeCall.value
      if (current != null && current.callId == callId && current.status == CallStatus.RINGING) {
        signaling.removeIncomingInvite(receiverId, callId)
        endCall(CallStatus.MISSED)
      }
    }
  }

  private fun startConnectTimeout(callId: String) {
    if (connectTimeoutJob?.isActive == true) return
    connectTimeoutJob = scope.launch {
      delay(MioConstants.CALL_CONNECT_TIMEOUT_MS)
      val current = _activeCall.value
      if (!isEndingCall && current != null && current.callId == callId && current.status != CallStatus.CONNECTED) {
        Log.w(TAG, "Call ICE connect timeout reached")
        _callErrorNotice.value = MioCopy.CALL_CONNECTION_FAILED
        endCall(CallStatus.ENDED)
      }
    }
  }

  private fun startConnectionStatsPolling(pc: PeerConnection) {
    statsPollJob?.cancel()
    statsPollJob = scope.launch {
      while (isActive && !isEndingCall && peerConnection === pc) {
        pc.getStats { report ->
          val mode = resolveConnectionMode(report.statsMap)
          if (mode != CallConnectionMode.UNKNOWN) {
            scope.launch {
              _activeCall.value = _activeCall.value?.let { state ->
                if (state.connectionMode != mode) state.copy(connectionMode = mode) else state
              }
            }
          }
        }
        delay(MioConstants.CALL_STATS_POLL_MS)
      }
    }
  }

  private fun resolveConnectionMode(statsMap: Map<String, org.webrtc.RTCStats>): CallConnectionMode {
    val activePair = statsMap.values.firstOrNull { stat ->
      stat.type == "candidate-pair" &&
        (stat.members["nominated"] == true || stat.members["state"] == "succeeded")
    } ?: return CallConnectionMode.UNKNOWN

    val localId = activePair.members["localCandidateId"] as? String
    val remoteId = activePair.members["remoteCandidateId"] as? String
    val localType = (localId?.let { statsMap[it]?.members?.get("candidateType") } as? String).orEmpty()
    val remoteType = (remoteId?.let { statsMap[it]?.members?.get("candidateType") } as? String).orEmpty()

    return if (localType == "relay" || remoteType == "relay") {
      CallConnectionMode.RELAY
    } else if (localType.isNotEmpty() || remoteType.isNotEmpty()) {
      CallConnectionMode.P2P
    } else {
      CallConnectionMode.UNKNOWN
    }
  }

  private fun ensureEglBase(): EglBase {
    return eglBase ?: EglBase.create().also { eglBase = it }
  }

  private fun ensurePeerConnectionFactory(): PeerConnectionFactory {
    peerConnectionFactory?.let { return it }
    val egl = ensureEglBase()
    PeerConnectionFactory.initialize(
      PeerConnectionFactory.InitializationOptions.builder(appContext)
        .createInitializationOptions(),
    )
    val adm = JavaAudioDeviceModule.builder(appContext)
      .setUseHardwareAcousticEchoCanceler(true)
      .setUseHardwareNoiseSuppressor(true)
      .createAudioDeviceModule()
    val factory = PeerConnectionFactory.builder()
      .setAudioDeviceModule(adm)
      .setVideoEncoderFactory(DefaultVideoEncoderFactory(egl.eglBaseContext, true, true))
      .setVideoDecoderFactory(DefaultVideoDecoderFactory(egl.eglBaseContext))
      .createPeerConnectionFactory()
    adm.release()
    peerConnectionFactory = factory
    return factory
  }

  private fun releaseWebRtcResources() {
    runCatching { videoCapturer?.stopCapture() }
    runCatching { videoCapturer?.dispose() }
    videoCapturer = null

    runCatching { localVideoTrack?.setEnabled(false) }
    runCatching { localVideoTrack?.dispose() }
    localVideoTrack = null

    runCatching { localVideoSource?.dispose() }
    localVideoSource = null

    runCatching { surfaceTextureHelper?.dispose() }
    surfaceTextureHelper = null

    runCatching { localAudioTrack?.setEnabled(false) }
    runCatching { localAudioTrack?.dispose() }
    localAudioTrack = null

    runCatching { localAudioSource?.dispose() }
    localAudioSource = null

    remoteVideoTrack = null

    runCatching { peerConnection?.close() }
    runCatching { peerConnection?.dispose() }
    peerConnection = null
  }

  fun dispose() {
    incomingWatchJob?.cancel()
    endCall(CallStatus.ENDED)
    runCatching { peerConnectionFactory?.dispose() }
    peerConnectionFactory = null
    runCatching { eglBase?.release() }
    eglBase = null
    scope.cancel()
  }

  private suspend fun PeerConnection.createOfferAwait(constraints: MediaConstraints): SessionDescription =
    suspendCancellableCoroutine { cont ->
      createOffer(
        object : SimpleSdpObserver() {
          override fun onCreateSuccess(sdp: SessionDescription?) {
            if (sdp != null && cont.isActive) cont.resume(sdp)
            else if (cont.isActive) cont.resumeWithException(IllegalStateException("Null SDP offer"))
          }
          override fun onCreateFailure(error: String?) {
            if (cont.isActive) cont.resumeWithException(IllegalStateException(error ?: "Offer failed"))
          }
        },
        constraints,
      )
    }

  private suspend fun PeerConnection.createAnswerAwait(constraints: MediaConstraints): SessionDescription =
    suspendCancellableCoroutine { cont ->
      createAnswer(
        object : SimpleSdpObserver() {
          override fun onCreateSuccess(sdp: SessionDescription?) {
            if (sdp != null && cont.isActive) cont.resume(sdp)
            else if (cont.isActive) cont.resumeWithException(IllegalStateException("Null SDP answer"))
          }
          override fun onCreateFailure(error: String?) {
            if (cont.isActive) cont.resumeWithException(IllegalStateException(error ?: "Answer failed"))
          }
        },
        constraints,
      )
    }

  private suspend fun PeerConnection.setLocalDescriptionAwait(sdp: SessionDescription) =
    suspendCancellableCoroutine { cont ->
      setLocalDescription(
        object : SimpleSdpObserver() {
          override fun onSetSuccess() {
            if (cont.isActive) cont.resume(Unit)
          }
          override fun onSetFailure(error: String?) {
            if (cont.isActive) cont.resumeWithException(IllegalStateException(error ?: "Set local SDP failed"))
          }
        },
        sdp,
      )
    }

  private suspend fun PeerConnection.setRemoteDescriptionAwait(sdp: SessionDescription) =
    suspendCancellableCoroutine { cont ->
      setRemoteDescription(
        object : SimpleSdpObserver() {
          override fun onSetSuccess() {
            if (cont.isActive) cont.resume(Unit)
          }
          override fun onSetFailure(error: String?) {
            if (cont.isActive) cont.resumeWithException(IllegalStateException(error ?: "Set remote SDP failed"))
          }
        },
        sdp,
      )
    }

  private open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(sdp: SessionDescription?) = Unit
    override fun onSetSuccess() = Unit
    override fun onCreateFailure(error: String?) = Unit
    override fun onSetFailure(error: String?) = Unit
  }

  private companion object {
    const val TAG = "MioWebRtc"
    const val STREAM_ID = "mio_stream"
    const val LOCAL_AUDIO_TRACK_ID = "mio_audio_0"
    const val LOCAL_VIDEO_TRACK_ID = "mio_video_0"
    const val VIDEO_WIDTH = 720
    const val VIDEO_HEIGHT = 1280
    const val VIDEO_FPS = 30
    const val CLEANUP_DELAY_MS = 1_200L
  }
}
