package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Exact Mio Color Tokens — single source of truth for the entire app.
 * No other file in the project defines hex color values.
 */
object MioColors {
  // Core palette from user's shared color sheet
  val PlumBase = Color(0xFF221820)        // Background
  val BurgundySurface = Color(0xFF45141B) // Their bubbles and cards
  val CrimsonPrimary = Color(0xFFA42527)  // My bubbles, primary buttons, deep accents
  val BlushRose = Color(0xFFFE8492)       // Highlights, active icons, send button, mood chip, unread indicator
  val TwilightBlue = Color(0xFF5196CE)    // Read ticks, links, online status (small touches only)
  val MidnightSlate = Color(0xFF262F45)   // Header, bottom bar, input bar

  // Typography & semantic tokens
  val WarmOffWhite = Color(0xFFF8E8EE)    // Warm off-white primary text
  val MutedRose = Color(0xFFB89AA6)       // Muted secondary text
  val DangerEmber = Color(0xFFFF6B35)     // Destructive / block / report actions (distinct from crimson)

  // Derived tonal helpers (zero extra hex colors)
  val SubtleBorder = BlushRose.copy(alpha = 0.22f)
  val SlateBorder = TwilightBlue.copy(alpha = 0.28f)
  val BlushCrimsonSoft = lerp(CrimsonPrimary, BlushRose, 0.26f)
  val BlushCrimsonButton = lerp(CrimsonPrimary, BlushRose, 0.42f)
}
