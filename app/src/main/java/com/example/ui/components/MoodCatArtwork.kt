package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.core.config.CatMood
import com.example.ui.theme.MioColors

/**
 * Custom Canvas illustration for each Mood Cat (no emojis or stock assets).
 */
@Composable
fun MoodCatArtwork(
  mood: CatMood,
  modifier: Modifier = Modifier,
  size: Dp = 44.dp,
) {
  Canvas(modifier = modifier.size(size)) {
    val w = this.size.width
    val h = this.size.height
    val cx = w / 2f
    val cy = h * 0.56f
    val headRx = w * 0.34f
    val headRy = h * 0.29f

    val furColor = when (mood) {
      CatMood.HAPPY -> MioColors.BlushRose
      CatMood.SAD -> MioColors.TwilightBlue
      CatMood.TIRED -> MioColors.MutedRose
      CatMood.BORED -> MioColors.WarmOffWhite
      CatMood.ANGRY -> MioColors.DangerEmber
    }
    val inkColor = MioColors.PlumBase
    val strokeW = w * 0.048f

    // Ears
    val leftEar = Path().apply {
      moveTo(cx - headRx * 0.85f, cy - headRy * 0.35f)
      lineTo(cx - headRx * 0.92f, cy - headRy * 1.38f)
      lineTo(cx - headRx * 0.22f, cy - headRy * 0.82f)
      close()
    }
    val rightEar = Path().apply {
      moveTo(cx + headRx * 0.85f, cy - headRy * 0.35f)
      lineTo(cx + headRx * 0.92f, cy - headRy * 1.38f)
      lineTo(cx + headRx * 0.22f, cy - headRy * 0.82f)
      close()
    }
    drawPath(leftEar, color = furColor)
    drawPath(rightEar, color = furColor)

    // Inner ears
    val innerLeft = Path().apply {
      moveTo(cx - headRx * 0.72f, cy - headRy * 0.55f)
      lineTo(cx - headRx * 0.76f, cy - headRy * 1.12f)
      lineTo(cx - headRx * 0.36f, cy - headRy * 0.78f)
      close()
    }
    val innerRight = Path().apply {
      moveTo(cx + headRx * 0.72f, cy - headRy * 0.55f)
      lineTo(cx + headRx * 0.76f, cy - headRy * 1.12f)
      lineTo(cx + headRx * 0.36f, cy - headRy * 0.78f)
      close()
    }
    drawPath(innerLeft, color = MioColors.BurgundySurface.copy(alpha = 0.65f))
    drawPath(innerRight, color = MioColors.BurgundySurface.copy(alpha = 0.65f))

    // Cat head
    drawOval(
      color = furColor,
      topLeft = Offset(cx - headRx, cy - headRy),
      size = Size(headRx * 2f, headRy * 2f),
    )

    // Subtle rosy cheeks
    drawCircle(
      color = MioColors.CrimsonPrimary.copy(alpha = 0.28f),
      radius = w * 0.065f,
      center = Offset(cx - headRx * 0.56f, cy + headRy * 0.28f),
    )
    drawCircle(
      color = MioColors.CrimsonPrimary.copy(alpha = 0.28f),
      radius = w * 0.065f,
      center = Offset(cx + headRx * 0.56f, cy + headRy * 0.28f),
    )

    // Eyes by mood
    val eyeY = cy - headRy * 0.08f
    val eyeDx = headRx * 0.42f
    when (mood) {
      CatMood.HAPPY -> {
        // Happy curved arcs ^_^
        drawArc(
          color = inkColor,
          startAngle = 195f,
          sweepAngle = 150f,
          useCenter = false,
          topLeft = Offset(cx - eyeDx - w * 0.07f, eyeY - w * 0.04f),
          size = Size(w * 0.14f, w * 0.11f),
          style = Stroke(width = strokeW, cap = StrokeCap.Round),
        )
        drawArc(
          color = inkColor,
          startAngle = 195f,
          sweepAngle = 150f,
          useCenter = false,
          topLeft = Offset(cx + eyeDx - w * 0.07f, eyeY - w * 0.04f),
          size = Size(w * 0.14f, w * 0.11f),
          style = Stroke(width = strokeW, cap = StrokeCap.Round),
        )
      }
      CatMood.TIRED -> {
        // Closed sleepy downward arcs
        drawArc(
          color = inkColor,
          startAngle = 15f,
          sweepAngle = 150f,
          useCenter = false,
          topLeft = Offset(cx - eyeDx - w * 0.07f, eyeY - w * 0.04f),
          size = Size(w * 0.14f, w * 0.09f),
          style = Stroke(width = strokeW, cap = StrokeCap.Round),
        )
        drawArc(
          color = inkColor,
          startAngle = 15f,
          sweepAngle = 150f,
          useCenter = false,
          topLeft = Offset(cx + eyeDx - w * 0.07f, eyeY - w * 0.04f),
          size = Size(w * 0.14f, w * 0.09f),
          style = Stroke(width = strokeW, cap = StrokeCap.Round),
        )
      }
      CatMood.BORED -> {
        // Half-lidded flat eyes
        drawCircle(color = inkColor, radius = w * 0.045f, center = Offset(cx - eyeDx, eyeY + w * 0.015f))
        drawCircle(color = inkColor, radius = w * 0.045f, center = Offset(cx + eyeDx, eyeY + w * 0.015f))
        drawLine(
          color = inkColor,
          start = Offset(cx - eyeDx - w * 0.07f, eyeY - w * 0.02f),
          end = Offset(cx - eyeDx + w * 0.07f, eyeY - w * 0.02f),
          strokeWidth = strokeW,
          cap = StrokeCap.Round,
        )
        drawLine(
          color = inkColor,
          start = Offset(cx + eyeDx - w * 0.07f, eyeY - w * 0.02f),
          end = Offset(cx + eyeDx + w * 0.07f, eyeY - w * 0.02f),
          strokeWidth = strokeW,
          cap = StrokeCap.Round,
        )
      }
      CatMood.SAD -> {
        // Big glistening pleading eyes + highlight
        drawCircle(color = inkColor, radius = w * 0.058f, center = Offset(cx - eyeDx, eyeY))
        drawCircle(color = inkColor, radius = w * 0.058f, center = Offset(cx + eyeDx, eyeY))
        drawCircle(color = MioColors.WarmOffWhite, radius = w * 0.02f, center = Offset(cx - eyeDx - w * 0.015f, eyeY - w * 0.02f))
        drawCircle(color = MioColors.WarmOffWhite, radius = w * 0.02f, center = Offset(cx + eyeDx - w * 0.015f, eyeY - w * 0.02f))
        // Sad brows
        drawLine(
          color = inkColor,
          start = Offset(cx - eyeDx - w * 0.06f, eyeY - w * 0.09f),
          end = Offset(cx - eyeDx + w * 0.05f, eyeY - w * 0.12f),
          strokeWidth = strokeW * 0.85f,
          cap = StrokeCap.Round,
        )
        drawLine(
          color = inkColor,
          start = Offset(cx + eyeDx + w * 0.06f, eyeY - w * 0.09f),
          end = Offset(cx + eyeDx - w * 0.05f, eyeY - w * 0.12f),
          strokeWidth = strokeW * 0.85f,
          cap = StrokeCap.Round,
        )
      }
      CatMood.ANGRY -> {
        // Sharp slanted brows + intense eyes
        drawCircle(color = inkColor, radius = w * 0.052f, center = Offset(cx - eyeDx, eyeY + w * 0.01f))
        drawCircle(color = inkColor, radius = w * 0.052f, center = Offset(cx + eyeDx, eyeY + w * 0.01f))
        drawLine(
          color = inkColor,
          start = Offset(cx - eyeDx - w * 0.07f, eyeY - w * 0.11f),
          end = Offset(cx - eyeDx + w * 0.06f, eyeY - w * 0.04f),
          strokeWidth = strokeW,
          cap = StrokeCap.Round,
        )
        drawLine(
          color = inkColor,
          start = Offset(cx + eyeDx + w * 0.07f, eyeY - w * 0.11f),
          end = Offset(cx + eyeDx - w * 0.06f, eyeY - w * 0.04f),
          strokeWidth = strokeW,
          cap = StrokeCap.Round,
        )
      }
    }

    // Little nose
    val noseY = cy + headRy * 0.14f
    val nosePath = Path().apply {
      moveTo(cx - w * 0.036f, noseY)
      lineTo(cx + w * 0.036f, noseY)
      lineTo(cx, noseY + w * 0.04f)
      close()
    }
    drawPath(nosePath, color = inkColor)

    // Mouth ("w" for happy, tiny "o" for tired, frown for sad/angry, flat for bored)
    val mouthY = noseY + w * 0.04f
    when (mood) {
      CatMood.HAPPY -> {
        val mouth = Path().apply {
          moveTo(cx - w * 0.09f, mouthY + w * 0.02f)
          quadraticTo(cx - w * 0.045f, mouthY + w * 0.075f, cx, mouthY + w * 0.025f)
          quadraticTo(cx + w * 0.045f, mouthY + w * 0.075f, cx + w * 0.09f, mouthY + w * 0.02f)
        }
        drawPath(mouth, color = inkColor, style = Stroke(width = strokeW * 0.85f, cap = StrokeCap.Round, join = StrokeJoin.Round))
      }
      CatMood.TIRED -> {
        drawOval(
          color = inkColor,
          topLeft = Offset(cx - w * 0.028f, mouthY + w * 0.015f),
          size = Size(w * 0.056f, w * 0.07f),
          style = Stroke(width = strokeW * 0.8f),
        )
      }
      CatMood.BORED -> {
        drawLine(
          color = inkColor,
          start = Offset(cx - w * 0.06f, mouthY + w * 0.04f),
          end = Offset(cx + w * 0.06f, mouthY + w * 0.04f),
          strokeWidth = strokeW * 0.85f,
          cap = StrokeCap.Round,
        )
      }
      CatMood.SAD, CatMood.ANGRY -> {
        drawArc(
          color = inkColor,
          startAngle = 200f,
          sweepAngle = 140f,
          useCenter = false,
          topLeft = Offset(cx - w * 0.075f, mouthY + w * 0.015f),
          size = Size(w * 0.15f, w * 0.09f),
          style = Stroke(width = strokeW * 0.85f, cap = StrokeCap.Round),
        )
      }
    }

    // Whiskers
    val whiskerStroke = strokeW * 0.65f
    for (dir in listOf(-1f, 1f)) {
      drawLine(
        color = inkColor.copy(alpha = 0.7f),
        start = Offset(cx + dir * headRx * 0.62f, cy + headRy * 0.12f),
        end = Offset(cx + dir * headRx * 1.05f, cy + headRy * 0.04f),
        strokeWidth = whiskerStroke,
        cap = StrokeCap.Round,
      )
      drawLine(
        color = inkColor.copy(alpha = 0.7f),
        start = Offset(cx + dir * headRx * 0.62f, cy + headRy * 0.26f),
        end = Offset(cx + dir * headRx * 1.05f, cy + headRy * 0.32f),
        strokeWidth = whiskerStroke,
        cap = StrokeCap.Round,
      )
    }
  }
}
