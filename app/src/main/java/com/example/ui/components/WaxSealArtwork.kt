package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.MioColors
import com.example.ui.theme.MioGradients
import com.example.ui.theme.MioMotion
import com.example.ui.theme.MioRadii
import com.example.ui.theme.MioSpacing
import kotlin.math.cos
import kotlin.math.sin

/**
 * Custom Canvas wax seal emblem with scalloped organic wax perimeter and embossed heart crest.
 */
@Composable
fun WaxSealEmblem(
  modifier: Modifier = Modifier,
  size: Dp = 64.dp,
  broken: Boolean = false,
) {
  Canvas(
    modifier = modifier
      .size(size)
      .semantics { contentDescription = if (broken) "Opened wax seal" else "Crimson wax seal" }
  ) {
    val cx = this.size.width / 2f
    val cy = this.size.height / 2f
    val r = this.size.minDimension * 0.44f

    // Organic scalloped wax perimeter
    val lobes = 14
    val waxPath = Path()
    for (i in 0 until lobes * 4) {
      val angle = (i.toFloat() / (lobes * 4)) * (2f * Math.PI.toFloat())
      val wobble = 1f + 0.055f * sin(angle * lobes)
      val px = cx + r * wobble * cos(angle)
      val py = cy + r * wobble * sin(angle)
      if (i == 0) waxPath.moveTo(px, py) else waxPath.lineTo(px, py)
    }
    waxPath.close()

    drawPath(waxPath, color = MioColors.CrimsonPrimary)
    drawCircle(
      color = MioColors.BurgundySurface.copy(alpha = 0.65f),
      radius = r * 0.78f,
      center = Offset(cx, cy),
      style = Stroke(width = r * 0.08f),
    )
    drawCircle(
      color = MioColors.BlushRose.copy(alpha = 0.25f),
      radius = r * 0.72f,
      center = Offset(cx, cy),
      style = Stroke(width = r * 0.03f),
    )

    // Embossed heart inside seal
    val hs = r * 0.46f
    val heart = Path().apply {
      moveTo(cx, cy + hs * 0.65f)
      cubicTo(cx - hs * 1.15f, cy - hs * 0.05f, cx - hs * 0.65f, cy - hs * 0.95f, cx, cy - hs * 0.35f)
      cubicTo(cx + hs * 0.65f, cy - hs * 0.95f, cx + hs * 1.15f, cy - hs * 0.05f, cx, cy + hs * 0.65f)
      close()
    }
    drawPath(heart, color = MioColors.BlushRose.copy(alpha = if (broken) 0.55f else 0.9f))

    if (broken) {
      drawLine(
        color = MioColors.PlumBase.copy(alpha = 0.8f),
        start = Offset(cx - r * 0.7f, cy - r * 0.45f),
        end = Offset(cx + r * 0.65f, cy + r * 0.5f),
        strokeWidth = r * 0.1f,
      )
    }
  }
}

/**
 * Animated sealed letter reveal dialog for MIP invitations using the vertical sunset gradient
 * paired with a dark PlumBase scrim for accessible contrast.
 */
@Composable
fun SealedLetterRevealDialog(
  senderName: String,
  note: String,
  isIncoming: Boolean,
  onAccept: () -> Unit,
  onDecline: () -> Unit,
  onDismiss: () -> Unit,
) {
  var isOpened by rememberSaveable(senderName, isIncoming) { mutableStateOf(!isIncoming) }
  val sealScale by animateFloatAsState(
    targetValue = if (isOpened) 0.82f else 1f,
    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
    label = "sealScale",
  )

  Dialog(onDismissRequest = onDismiss) {
    Surface(
      shape = RoundedCornerShape(MioRadii.lg),
      border = BorderStroke(1.dp, MioColors.SubtleBorder),
      color = MioColors.BurgundySurface,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .background(MioGradients.sunsetVertical),
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .background(MioColors.PlumBase.copy(alpha = 0.72f))
            .padding(MioSpacing.lg),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Text(
            text = if (isOpened) "A Letter For You" else "Sealed MIP Letter",
            style = MaterialTheme.typography.headlineMedium,
            color = MioColors.WarmOffWhite,
            textAlign = TextAlign.Center,
          )
          Spacer(Modifier.height(MioSpacing.xs))
          Text(
            text = "From $senderName",
            style = MaterialTheme.typography.labelMedium,
            color = MioColors.BlushRose,
          )
          Spacer(Modifier.height(MioSpacing.lg))

          Box(
            modifier = Modifier
              .scale(sealScale)
              .clip(RoundedCornerShape(MioRadii.pill))
              .clickable(enabled = !isOpened) { isOpened = true }
              .padding(MioSpacing.xs),
            contentAlignment = Alignment.Center,
          ) {
            WaxSealEmblem(size = 84.dp, broken = isOpened)
          }

          if (!isOpened) {
            Spacer(Modifier.height(MioSpacing.sm))
            Text(
              text = "Tap the wax seal to open",
              style = MaterialTheme.typography.bodyMedium,
              color = MioColors.WarmOffWhite,
            )
          }

          AnimatedVisibility(
            visible = isOpened,
            enter = fadeIn(tween(MioMotion.slowMs)) + expandVertically(tween(MioMotion.slowMs)),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth(),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Spacer(Modifier.height(MioSpacing.md))
              Surface(
                color = MioColors.BurgundySurface.copy(alpha = 0.92f),
                shape = RoundedCornerShape(MioRadii.md),
                border = BorderStroke(1.dp, MioColors.SubtleBorder),
                modifier = Modifier.fillMaxWidth(),
              ) {
                Text(
                  text = if (note.isNotBlank()) "“$note”" else "“You are my Most Important Person.”",
                  style = MaterialTheme.typography.titleLarge.copy(fontStyle = FontStyle.Italic),
                  color = MioColors.WarmOffWhite,
                  textAlign = TextAlign.Center,
                  modifier = Modifier.padding(MioSpacing.lg),
                )
              }
              Spacer(Modifier.height(MioSpacing.lg))
              if (isIncoming) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(MioSpacing.sm),
                ) {
                  OutlinedButton(
                    onClick = onDecline,
                    border = BorderStroke(1.dp, MioColors.SubtleBorder),
                    modifier = Modifier
                      .weight(1f)
                      .heightIn(min = MioSpacing.minTouchTarget),
                  ) {
                    Text("Decline", color = MioColors.WarmOffWhite)
                  }
                  Button(
                    onClick = onAccept,
                    colors = ButtonDefaults.buttonColors(
                      containerColor = MioColors.CrimsonPrimary,
                      contentColor = MioColors.WarmOffWhite,
                    ),
                    modifier = Modifier
                      .weight(1f)
                      .heightIn(min = MioSpacing.minTouchTarget),
                  ) {
                    Text("Accept MIP")
                  }
                }
              } else {
                Button(
                  onClick = onDismiss,
                  colors = ButtonDefaults.buttonColors(
                    containerColor = MioColors.CrimsonPrimary,
                    contentColor = MioColors.WarmOffWhite,
                  ),
                  modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MioSpacing.minTouchTarget),
                ) {
                  Text("Close")
                }
              }
            }
          }
        }
      }
    }
  }
}
