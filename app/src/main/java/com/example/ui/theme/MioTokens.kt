package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

object MioSpacing {
  val xxs = 4.dp
  val xs = 8.dp
  val sm = 12.dp
  val md = 16.dp
  val lg = 24.dp
  val xl = 32.dp
  val xxl = 48.dp
  val minTouchTarget = 48.dp
}

object MioRadii {
  val sm = 10.dp
  val md = 16.dp
  val lg = 22.dp
  val bubble = 20.dp
  val bubbleGrouped = 6.dp
  val wallpaperCardBottom = 32.dp
  val headerPill = 24.dp
  val pill = 999.dp
}

object MioMotion {
  const val fastMs = 200
  const val standardMs = 280
  const val slowMs = 380
  const val sealRevealMs = 700
}

object MioGradients {
  // Soft blush (#FE8492) to crimson (#A42527) on my bubbles
  val myBubble = Brush.linearGradient(
    colors = listOf(
      MioColors.BlushCrimsonSoft,
      MioColors.CrimsonPrimary,
    )
  )

  // Soft blush (#FE8492) to crimson (#A42527) on the send button
  val sendButton = Brush.linearGradient(
    colors = listOf(
      MioColors.BlushRose,
      MioColors.CrimsonPrimary,
    )
  )

  // Vertical sunset gradient permitted for Sign-In screen and MIP letter:
  // #221820 -> #A42527 -> #FE8492 -> #5196CE -> #262F45
  val sunsetVertical = Brush.verticalGradient(
    colors = listOf(
      MioColors.PlumBase,
      MioColors.CrimsonPrimary,
      MioColors.BlushRose,
      MioColors.TwilightBlue,
      MioColors.MidnightSlate,
    )
  )
}
