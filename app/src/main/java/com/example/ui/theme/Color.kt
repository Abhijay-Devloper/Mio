package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Exact Mio Color Tokens — Pure Black Theme.
 * Single source of truth for the entire app. No raw hex values elsewhere.
 */
object MioColors {
  // Pure Black & Dark Surfaces
  val PureBlack = Color(0xFF000000)        // Background for everything (canvas, headers, bottom nav)
  val CardSurface = Color(0xFF121212)     // Cards, sheets, search bar fill
  val ReceivedBubble = Color(0xFF1C1C1E)  // Their received chat bubbles

  // Accent & Highlights (Rose-pink identity)
  val RoseAccent = Color(0xFFFE8492)      // My sent bubbles, send button, unread badges, MIP heart, links

  // Typography
  val PrimaryText = Color(0xFFFFFFFF)     // Primary white text
  val MutedText = Color(0xFFA0A0A0)       // Secondary muted text

  // Destructive
  val DangerEmber = Color(0xFFFF6B35)     // Destructive / block / report actions

  // Backward compatibility aliases for existing codebase references
  val PlumBase = PureBlack
  val BurgundySurface = CardSurface
  val CrimsonPrimary = RoseAccent
  val BlushRose = RoseAccent
  val MidnightSlate = CardSurface
  val WarmOffWhite = PrimaryText
  val MutedRose = MutedText
  val TwilightBlue = Color(0xFF5196CE)
  val SubtleBorder = Color(0xFF2C2C2E)
  val SlateBorder = Color(0xFF2C2C2E)
  val OnlineGreen = Color(0xFF22C55E)
  val BlushCrimsonSoft = RoseAccent
  val BlushCrimsonButton = RoseAccent

  // Replicated Chat Screen Tokens (Wallpaper mode vs Pure Black mode)
  val ChatWallpaperTop = Color(0xFF7D858E)
  val ChatWallpaperUpperMid = Color(0xFF767C96)
  val ChatWallpaperMid = Color(0xFF706F9B)
  val ChatWallpaperLowerMid = Color(0xFF6B8095)
  val ChatWallpaperBottom = Color(0xFF69858E)
  val WallpaperReceivedBubble = Color(0x47FFFFFF)
  val WallpaperSentBubble = Color(0xBD2B2E38)
  val PureBlackReceivedBubble = Color(0xFF2E2E33)
  val PureBlackSentBubble = Color(0xFF1A1A1E)
  val WallpaperHeaderPill = Color(0x992D2E35)
  val PureBlackHeaderPill = Color(0xFF1C1C20)
  val ComposerPillFill = Color(0xFF141416)
  val ComposerPillBorder = Color(0xFF28282C)
  val ComposerSendIdle = Color(0xFF34343A)
  val BubbleTimestamp = Color(0xB3FFFFFF)
  val HeaderSubtitleMuted = Color(0xCCFFFFFF)
}
