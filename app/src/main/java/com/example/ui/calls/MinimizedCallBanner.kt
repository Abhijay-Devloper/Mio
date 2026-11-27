package com.example.ui.calls

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CallEnd
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.config.MioCopy
import com.example.data.model.ActiveCallUiState
import com.example.data.model.CallStatus
import com.example.ui.theme.MioColors
import com.example.ui.theme.MioRadii
import com.example.ui.theme.MioSpacing
import kotlinx.coroutines.delay

/**
 * Floating ongoing-call pill shown when the user leaves the chat or minimizes the call screen
 * so they can keep talking while navigating inside Mio.
 */
@Composable
fun MinimizedCallBanner(
  callState: ActiveCallUiState,
  onExpand: () -> Unit,
  onToggleMic: () -> Unit,
  onEndCall: () -> Unit,
  modifier: Modifier = Modifier,
) {
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

  val pulseTransition = rememberInfiniteTransition(label = "minimized_call_dot")
  val dotAlpha by pulseTransition.animateFloat(
    initialValue = 1f,
    targetValue = 0.35f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 900),
      repeatMode = RepeatMode.Reverse,
    ),
    label = "dot_alpha",
  )

  val subtitle = when (callState.status) {
    CallStatus.CONNECTED -> "${formatBannerDuration(elapsedSec)} • ${MioCopy.CALL_RETURN_TO_CALL}"
    CallStatus.RINGING -> "${MioCopy.CALL_BANNER_RINGING} • ${MioCopy.CALL_RETURN_TO_CALL}"
    else -> "${MioCopy.CALL_BANNER_CONNECTING} • ${MioCopy.CALL_RETURN_TO_CALL}"
  }

  Box(
    modifier = modifier
      .fillMaxWidth()
      .statusBarsPadding()
      .padding(horizontal = MioSpacing.md, vertical = MioSpacing.xs),
    contentAlignment = Alignment.TopCenter,
  ) {
    Surface(
      shape = RoundedCornerShape(MioRadii.lg),
      color = MioColors.CardSurface,
      border = BorderStroke(
        1.dp,
        if (callState.status == CallStatus.CONNECTED) {
          MioColors.OnlineGreen.copy(alpha = 0.55f)
        } else {
          MioColors.RoseAccent.copy(alpha = 0.55f)
        },
      ),
      tonalElevation = 10.dp,
      shadowElevation = 14.dp,
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 560.dp)
        .clip(RoundedCornerShape(MioRadii.lg))
        .clickable(onClick = onExpand)
        .testTag("minimized_call_banner"),
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = MioSpacing.sm, vertical = MioSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MioSpacing.xs),
      ) {
        Box(
          modifier = Modifier
            .size(10.dp)
            .alpha(dotAlpha)
            .clip(CircleShape)
            .background(
              if (callState.status == CallStatus.CONNECTED) MioColors.OnlineGreen else MioColors.RoseAccent,
            ),
        )

        Column(
          modifier = Modifier
            .weight(1f)
            .padding(horizontal = MioSpacing.xxs),
        ) {
          Text(
            text = callState.peerName,
            style = MaterialTheme.typography.titleSmall,
            color = MioColors.PrimaryText,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = if (callState.status == CallStatus.CONNECTED) MioColors.OnlineGreen else MioColors.RoseAccent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        IconButton(
          onClick = onToggleMic,
          modifier = Modifier
            .size(MioSpacing.minTouchTarget)
            .clip(CircleShape)
            .background(if (callState.isMicMuted) MioColors.RoseAccent else MioColors.ReceivedBubble)
            .testTag("minimized_call_mute"),
        ) {
          Icon(
            imageVector = if (callState.isMicMuted) Icons.Outlined.MicOff else Icons.Outlined.Mic,
            contentDescription = if (callState.isMicMuted) "Unmute microphone" else "Mute microphone",
            tint = if (callState.isMicMuted) MioColors.PureBlack else MioColors.PrimaryText,
            modifier = Modifier.size(20.dp),
          )
        }

        IconButton(
          onClick = onExpand,
          modifier = Modifier
            .size(MioSpacing.minTouchTarget)
            .clip(CircleShape)
            .background(MioColors.ReceivedBubble)
            .testTag("minimized_call_expand"),
        ) {
          Icon(
            imageVector = Icons.Outlined.OpenInFull,
            contentDescription = "Expand call",
            tint = MioColors.PrimaryText,
            modifier = Modifier.size(18.dp),
          )
        }

        IconButton(
          onClick = onEndCall,
          modifier = Modifier
            .size(MioSpacing.minTouchTarget)
            .clip(CircleShape)
            .background(MioColors.DangerEmber)
            .testTag("minimized_call_end"),
        ) {
          Icon(
            imageVector = Icons.Outlined.CallEnd,
            contentDescription = "End call",
            tint = MioColors.PrimaryText,
            modifier = Modifier.size(20.dp),
          )
        }
      }
    }
  }
}

private fun formatBannerDuration(totalSeconds: Long): String {
  val mins = totalSeconds / 60L
  val secs = totalSeconds % 60L
  return String.format("%02d:%02d", mins, secs)
}
