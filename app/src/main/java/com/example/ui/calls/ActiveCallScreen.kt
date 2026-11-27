package com.example.ui.calls

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CallEnd
import androidx.compose.material.icons.outlined.FlipCameraAndroid
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material.icons.outlined.VolumeDown
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.core.config.MioCopy
import com.example.data.model.ActiveCallUiState
import com.example.data.model.CallConnectionMode
import com.example.data.model.CallStatus
import com.example.data.model.CallType
import com.example.ui.theme.MioColors
import com.example.ui.theme.MioRadii
import com.example.ui.theme.MioSpacing
import kotlinx.coroutines.delay
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import kotlin.math.roundToInt

@Composable
fun ActiveCallScreen(
  callState: ActiveCallUiState,
  onToggleMic: () -> Unit,
  onToggleCamera: () -> Unit,
  onFlipCamera: () -> Unit,
  onToggleSpeaker: () -> Unit,
  onMinimize: () -> Unit,
  onEndCall: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BackHandler { onMinimize() }

  var elapsedSec by remember(callState.callId, callState.connectedAtMs) {
    mutableLongStateOf(0L)
  }

  LaunchedEffect(callState.callId, callState.status, callState.connectedAtMs) {
    if (callState.status == CallStatus.CONNECTED && callState.connectedAtMs > 0L) {
      while (true) {
        elapsedSec = ((System.currentTimeMillis() - callState.connectedAtMs) / 1000L).coerceAtLeast(0L)
        delay(1000L)
      }
    } else {
      elapsedSec = 0L
    }
  }

  val statusLabel = when (callState.status) {
    CallStatus.CONNECTING -> MioCopy.CALL_STATUS_CONNECTING
    CallStatus.RINGING -> if (callState.isOutgoing) MioCopy.CALL_STATUS_RINGING else MioCopy.CALL_STATUS_CONNECTING
    CallStatus.CONNECTED -> when (callState.connectionMode) {
      CallConnectionMode.RELAY -> MioCopy.CALL_STATUS_CONNECTED_RELAY
      CallConnectionMode.P2P -> MioCopy.CALL_STATUS_CONNECTED_P2P
      CallConnectionMode.UNKNOWN -> MioCopy.CALL_STATUS_CONNECTED
    }
    CallStatus.ENDED -> MioCopy.CALL_STATUS_ENDED
    CallStatus.DECLINED -> MioCopy.CALL_STATUS_DECLINED
    CallStatus.MISSED -> MioCopy.CALL_STATUS_MISSED
  }

  val isVideoCall = callState.type == CallType.VIDEO
  val eglCtx = callState.eglContext

  BoxWithConstraints(
    modifier = modifier
      .fillMaxSize()
      .background(MioColors.PureBlack)
      .testTag("active_call_screen"),
  ) {
    val density = LocalDensity.current
    val pipWidthDp = 112.dp
    val pipHeightDp = 160.dp
    val maxWidthPx = with(density) { maxWidth.toPx() }
    val maxHeightPx = with(density) { maxHeight.toPx() }
    val pipWidthPx = with(density) { pipWidthDp.toPx() }
    val pipHeightPx = with(density) { pipHeightDp.toPx() }
    val edgeMarginPx = with(density) { MioSpacing.md.toPx() }
    val topMarginPx = with(density) { 88.dp.toPx() }
    val bottomSafePx = with(density) { 128.dp.toPx() }

    var pipOffsetX by remember(callState.callId) {
      mutableFloatStateOf((maxWidthPx - pipWidthPx - edgeMarginPx).coerceAtLeast(edgeMarginPx))
    }
    var pipOffsetY by remember(callState.callId) {
      mutableFloatStateOf(topMarginPx)
    }

    if (isVideoCall && callState.remoteVideoTrack != null && eglCtx != null) {
      WebRtcVideoSurface(
        videoTrack = callState.remoteVideoTrack,
        eglContext = eglCtx,
        mirror = false,
        zOrderMediaOverlay = false,
        modifier = Modifier.fillMaxSize(),
      )
    } else {
      VoiceOrWaitingBackdrop(
        peerName = callState.peerName,
        peerUsername = callState.peerUsername,
        statusLabel = statusLabel,
        isConnected = callState.status == CallStatus.CONNECTED,
        elapsedSec = elapsedSec,
        connectionMode = callState.connectionMode,
      )
    }

    // Top-left minimize button so leaving the screen never ends the call
    IconButton(
      onClick = onMinimize,
      modifier = Modifier
        .align(Alignment.TopStart)
        .statusBarsPadding()
        .padding(start = MioSpacing.md, top = MioSpacing.md)
        .size(MioSpacing.minTouchTarget)
        .clip(CircleShape)
        .background(MioColors.CardSurface.copy(alpha = 0.85f))
        .testTag("call_minimize_button"),
    ) {
      Icon(
        imageVector = Icons.Outlined.KeyboardArrowDown,
        contentDescription = MioCopy.CALL_MINIMIZE_DESC,
        tint = MioColors.PrimaryText,
        modifier = Modifier.size(26.dp),
      )
    }

    // Top status pill for video calls
    if (isVideoCall) {
      Column(
        modifier = Modifier
          .align(Alignment.TopCenter)
          .statusBarsPadding()
          .padding(top = MioSpacing.md, start = MioSpacing.md, end = MioSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Surface(
          shape = RoundedCornerShape(MioRadii.pill),
          color = MioColors.PureBlack.copy(alpha = 0.65f),
          border = BorderStroke(1.dp, MioColors.SubtleBorder),
        ) {
          Column(
            modifier = Modifier.padding(horizontal = MioSpacing.md, vertical = MioSpacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(
              text = callState.peerName,
              style = MaterialTheme.typography.titleMedium,
              color = MioColors.PrimaryText,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            val subText = if (callState.status == CallStatus.CONNECTED && callState.connectedAtMs > 0L) {
              "${formatCallDuration(elapsedSec)} · $statusLabel"
            } else {
              statusLabel
            }
            Text(
              text = subText,
              style = MaterialTheme.typography.labelMedium,
              color = if (callState.connectionMode == CallConnectionMode.RELAY) {
                MioColors.TwilightBlue
              } else {
                MioColors.RoseAccent
              },
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
      }
    }

    // Draggable Picture-in-Picture (PiP) local camera preview for video calls
    if (isVideoCall && callState.isCameraEnabled && callState.localVideoTrack != null && eglCtx != null) {
      Surface(
        modifier = Modifier
          .offset { IntOffset(pipOffsetX.roundToInt(), pipOffsetY.roundToInt()) }
          .size(width = pipWidthDp, height = pipHeightDp)
          .pointerInput(maxWidthPx, maxHeightPx) {
            detectDragGestures { change, dragAmount ->
              change.consume()
              val maxX = (maxWidthPx - pipWidthPx - edgeMarginPx).coerceAtLeast(edgeMarginPx)
              val maxY = (maxHeightPx - pipHeightPx - bottomSafePx).coerceAtLeast(edgeMarginPx)
              pipOffsetX = (pipOffsetX + dragAmount.x).coerceIn(edgeMarginPx, maxX)
              pipOffsetY = (pipOffsetY + dragAmount.y).coerceIn(edgeMarginPx, maxY)
            }
          }
          .testTag("local_pip_preview"),
        shape = RoundedCornerShape(MioRadii.md),
        color = MioColors.CardSurface,
        border = BorderStroke(1.5.dp, MioColors.RoseAccent.copy(alpha = 0.65f)),
        shadowElevation = 12.dp,
      ) {
        Box(modifier = Modifier.fillMaxSize()) {
          WebRtcVideoSurface(
            videoTrack = callState.localVideoTrack,
            eglContext = eglCtx,
            mirror = callState.isUsingFrontCamera,
            zOrderMediaOverlay = true,
            modifier = Modifier.fillMaxSize(),
          )
          IconButton(
            onClick = onFlipCamera,
            modifier = Modifier
              .align(Alignment.BottomEnd)
              .padding(MioSpacing.xxs)
              .size(36.dp)
              .clip(CircleShape)
              .background(MioColors.PureBlack.copy(alpha = 0.65f))
              .testTag("pip_flip_camera_button"),
          ) {
            Icon(
              imageVector = Icons.Outlined.FlipCameraAndroid,
              contentDescription = "Flip camera",
              tint = MioColors.PrimaryText,
              modifier = Modifier.size(18.dp),
            )
          }
        }
      }
    }

    // Bottom Call Controls Bar
    Surface(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .padding(horizontal = MioSpacing.md, vertical = MioSpacing.lg)
        .navigationBarsPadding(),
      shape = RoundedCornerShape(MioRadii.pill),
      color = MioColors.CardSurface.copy(alpha = 0.92f),
      border = BorderStroke(1.dp, MioColors.SubtleBorder),
      tonalElevation = 10.dp,
    ) {
      Row(
        modifier = Modifier.padding(horizontal = MioSpacing.md, vertical = MioSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(MioSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        CallControlCircleButton(
          icon = if (callState.isMicMuted) Icons.Outlined.MicOff else Icons.Outlined.Mic,
          contentDescription = if (callState.isMicMuted) "Unmute microphone" else "Mute microphone",
          active = callState.isMicMuted,
          onClick = onToggleMic,
          tag = "call_mute_button",
        )

        if (isVideoCall) {
          CallControlCircleButton(
            icon = if (callState.isCameraEnabled) Icons.Outlined.Videocam else Icons.Outlined.VideocamOff,
            contentDescription = if (callState.isCameraEnabled) "Turn camera off" else "Turn camera on",
            active = !callState.isCameraEnabled,
            onClick = onToggleCamera,
            tag = "call_camera_toggle_button",
          )
          CallControlCircleButton(
            icon = Icons.Outlined.FlipCameraAndroid,
            contentDescription = "Switch camera",
            active = false,
            enabled = callState.isCameraEnabled,
            onClick = onFlipCamera,
            tag = "call_flip_camera_button",
          )
        }

        CallControlCircleButton(
          icon = if (callState.isSpeakerOn) Icons.Outlined.VolumeUp else Icons.Outlined.VolumeDown,
          contentDescription = if (callState.isSpeakerOn) "Switch to earpiece" else "Switch to speaker",
          active = callState.isSpeakerOn,
          onClick = onToggleSpeaker,
          tag = "call_speaker_button",
        )

        Surface(
          onClick = onEndCall,
          shape = CircleShape,
          color = MioColors.DangerEmber,
          modifier = Modifier
            .size(54.dp)
            .testTag("call_end_button"),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(
              imageVector = Icons.Outlined.CallEnd,
              contentDescription = "End call",
              tint = MioColors.PrimaryText,
              modifier = Modifier.size(24.dp),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun VoiceOrWaitingBackdrop(
  peerName: String,
  peerUsername: String,
  statusLabel: String,
  isConnected: Boolean,
  elapsedSec: Long,
  connectionMode: CallConnectionMode,
) {
  val pulseTransition = rememberInfiniteTransition(label = "voice_call_pulse")
  val haloScale by pulseTransition.animateFloat(
    initialValue = 1f,
    targetValue = 1.18f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1400),
      repeatMode = RepeatMode.Reverse,
    ),
    label = "halo_scale",
  )

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = MioSpacing.lg),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Box(contentAlignment = Alignment.Center) {
      Box(
        modifier = Modifier
          .size(144.dp)
          .scale(if (isConnected) 1.04f else haloScale)
          .clip(CircleShape)
          .background(MioColors.RoseAccent.copy(alpha = 0.16f)),
      )
      Box(
        modifier = Modifier
          .size(112.dp)
          .clip(CircleShape)
          .background(MioColors.RoseAccent),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = peerName.take(1).uppercase(),
          style = MaterialTheme.typography.displaySmall,
          color = MioColors.PureBlack,
          fontWeight = FontWeight.Bold,
        )
      }
    }

    Spacer(Modifier.height(MioSpacing.lg))

    Text(
      text = peerName,
      style = MaterialTheme.typography.headlineMedium,
      color = MioColors.PrimaryText,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    if (peerUsername.isNotBlank()) {
      Spacer(Modifier.height(MioSpacing.xxs))
      Text(
        text = "@$peerUsername",
        style = MaterialTheme.typography.bodyMedium,
        color = MioColors.MutedText,
      )
    }

    Spacer(Modifier.height(MioSpacing.md))

    if (isConnected) {
      Text(
        text = formatCallDuration(elapsedSec),
        style = MaterialTheme.typography.headlineSmall,
        color = MioColors.PrimaryText,
        modifier = Modifier.testTag("call_duration_timer"),
      )
      Spacer(Modifier.height(MioSpacing.xs))
    }

    Surface(
      shape = RoundedCornerShape(MioRadii.pill),
      color = MioColors.CardSurface,
      border = BorderStroke(
        1.dp,
        if (connectionMode == CallConnectionMode.RELAY) {
          MioColors.TwilightBlue.copy(alpha = 0.6f)
        } else {
          MioColors.RoseAccent.copy(alpha = 0.45f)
        },
      ),
    ) {
      Text(
        text = statusLabel,
        style = MaterialTheme.typography.labelLarge,
        color = when {
          connectionMode == CallConnectionMode.RELAY -> MioColors.TwilightBlue
          isConnected -> MioColors.OnlineGreen
          else -> MioColors.RoseAccent
        },
        modifier = Modifier
          .padding(horizontal = MioSpacing.md, vertical = MioSpacing.xs)
          .testTag("call_status_badge"),
      )
    }
  }
}

@Composable
private fun CallControlCircleButton(
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  contentDescription: String,
  active: Boolean,
  enabled: Boolean = true,
  onClick: () -> Unit,
  tag: String,
) {
  Surface(
    onClick = onClick,
    enabled = enabled,
    shape = CircleShape,
    color = if (active) MioColors.RoseAccent else MioColors.ReceivedBubble,
    modifier = Modifier
      .size(50.dp)
      .testTag(tag),
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(
        imageVector = icon,
        contentDescription = contentDescription,
        tint = when {
          !enabled -> MioColors.MutedText.copy(alpha = 0.4f)
          active -> MioColors.PureBlack
          else -> MioColors.PrimaryText
        },
        modifier = Modifier.size(22.dp),
      )
    }
  }
}

@Composable
private fun WebRtcVideoSurface(
  videoTrack: VideoTrack?,
  eglContext: EglBase.Context,
  mirror: Boolean,
  zOrderMediaOverlay: Boolean,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val renderer = remember(eglContext, zOrderMediaOverlay) {
    SurfaceViewRenderer(context).apply {
      init(eglContext, null)
      setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
      setEnableHardwareScaler(true)
      setZOrderMediaOverlay(zOrderMediaOverlay)
    }
  }

  LaunchedEffect(renderer, mirror) {
    renderer.setMirror(mirror)
  }

  DisposableEffect(renderer, videoTrack) {
    if (videoTrack != null) {
      runCatching { videoTrack.addSink(renderer) }
    }
    onDispose {
      if (videoTrack != null) {
        runCatching { videoTrack.removeSink(renderer) }
      }
    }
  }

  DisposableEffect(renderer) {
    onDispose {
      runCatching { renderer.release() }
    }
  }

  AndroidView(
    factory = { renderer },
    modifier = modifier,
  )
}

private fun formatCallDuration(totalSeconds: Long): String {
  val mins = totalSeconds / 60L
  val secs = totalSeconds % 60L
  return String.format("%02d:%02d", mins, secs)
}
