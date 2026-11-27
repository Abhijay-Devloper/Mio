package com.example.ui.calls

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CallEnd
import androidx.compose.material.icons.outlined.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.config.MioCopy
import com.example.data.model.CallType
import com.example.data.model.IncomingCallInvite
import com.example.ui.theme.MioColors
import com.example.ui.theme.MioRadii
import com.example.ui.theme.MioSpacing

@Composable
fun IncomingCallOverlay(
  invite: IncomingCallInvite,
  onAccept: () -> Unit,
  onDecline: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val pulseTransition = rememberInfiniteTransition(label = "incoming_call_pulse")
  val ringScale by pulseTransition.animateFloat(
    initialValue = 1f,
    targetValue = 1.24f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 1100),
      repeatMode = RepeatMode.Reverse,
    ),
    label = "ring_scale",
  )

  val isVideo = invite.type == CallType.VIDEO

  Box(
    modifier = modifier
      .fillMaxSize()
      .background(MioColors.PureBlack.copy(alpha = 0.78f))
      .statusBarsPadding()
      .padding(horizontal = MioSpacing.md, vertical = MioSpacing.lg),
    contentAlignment = Alignment.TopCenter,
  ) {
    Surface(
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 520.dp)
        .testTag("incoming_call_overlay"),
      shape = RoundedCornerShape(MioRadii.lg),
      color = MioColors.CardSurface,
      border = BorderStroke(1.dp, MioColors.RoseAccent.copy(alpha = 0.45f)),
      tonalElevation = 12.dp,
      shadowElevation = 20.dp,
    ) {
      Column(
        modifier = Modifier.padding(MioSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(MioSpacing.xs),
        ) {
          Icon(
            imageVector = if (isVideo) Icons.Outlined.Videocam else Icons.Outlined.Call,
            contentDescription = null,
            tint = MioColors.RoseAccent,
            modifier = Modifier.size(18.dp),
          )
          Text(
            text = if (isVideo) MioCopy.CALL_INCOMING_VIDEO else MioCopy.CALL_INCOMING_VOICE,
            style = MaterialTheme.typography.labelLarge,
            color = MioColors.RoseAccent,
          )
        }

        Spacer(Modifier.height(MioSpacing.lg))

        Box(contentAlignment = Alignment.Center) {
          Box(
            modifier = Modifier
              .size(88.dp)
              .scale(ringScale)
              .clip(CircleShape)
              .background(MioColors.RoseAccent.copy(alpha = 0.18f)),
          )
          Box(
            modifier = Modifier
              .size(72.dp)
              .clip(CircleShape)
              .background(MioColors.RoseAccent),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = invite.callerName.take(1).uppercase(),
              style = MaterialTheme.typography.headlineMedium,
              color = MioColors.PureBlack,
              fontWeight = FontWeight.Bold,
            )
          }
        }

        Spacer(Modifier.height(MioSpacing.md))

        Text(
          text = invite.callerName,
          style = MaterialTheme.typography.headlineSmall,
          color = MioColors.PrimaryText,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (invite.callerUsername.isNotBlank()) {
          Spacer(Modifier.height(MioSpacing.xxs))
          Text(
            text = "@${invite.callerUsername}",
            style = MaterialTheme.typography.bodyMedium,
            color = MioColors.MutedText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }

        Spacer(Modifier.height(MioSpacing.lg))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(MioSpacing.md),
        ) {
          Button(
            onClick = onDecline,
            colors = ButtonDefaults.buttonColors(
              containerColor = MioColors.DangerEmber,
              contentColor = MioColors.PrimaryText,
            ),
            shape = RoundedCornerShape(MioRadii.pill),
            modifier = Modifier
              .weight(1f)
              .height(52.dp)
              .testTag("incoming_call_decline"),
          ) {
            Icon(
              imageVector = Icons.Outlined.CallEnd,
              contentDescription = null,
              modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(MioSpacing.xs))
            Text(
              text = MioCopy.CALL_DECLINE,
              style = MaterialTheme.typography.labelLarge,
            )
          }

          Button(
            onClick = onAccept,
            colors = ButtonDefaults.buttonColors(
              containerColor = MioColors.OnlineGreen,
              contentColor = MioColors.PureBlack,
            ),
            shape = RoundedCornerShape(MioRadii.pill),
            modifier = Modifier
              .weight(1f)
              .height(52.dp)
              .testTag("incoming_call_accept"),
          ) {
            Icon(
              imageVector = if (isVideo) Icons.Outlined.Videocam else Icons.Outlined.Call,
              contentDescription = null,
              modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(MioSpacing.xs))
            Text(
              text = MioCopy.CALL_ACCEPT,
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }
    }
  }
}
