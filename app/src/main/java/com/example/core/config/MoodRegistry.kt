package com.example.core.config

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.MioColors

enum class CatMood(
  val id: String,
  val label: String,
  val tagline: String,
  val soundAssetKey: String,
  val hapticTimingsMs: LongArray,
  val hapticAmplitudes: IntArray,
  val accentColor: Color,
) {
  HAPPY(
    id = "happy",
    label = "Happy",
    tagline = "Purring softly",
    soundAssetKey = "cat_happy",
    hapticTimingsMs = longArrayOf(0, 45, 70, 65),
    hapticAmplitudes = intArrayOf(0, 120, 0, 180),
    accentColor = MioColors.BlushRose,
  ),
  SAD(
    id = "sad",
    label = "Sad",
    tagline = "Needs a warm hug",
    soundAssetKey = "cat_sad",
    hapticTimingsMs = longArrayOf(0, 140, 110, 180),
    hapticAmplitudes = intArrayOf(0, 75, 0, 60),
    accentColor = MioColors.TwilightBlue,
  ),
  TIRED(
    id = "tired",
    label = "Tired",
    tagline = "Curled up sleepy",
    soundAssetKey = "cat_tired",
    hapticTimingsMs = longArrayOf(0, 220),
    hapticAmplitudes = intArrayOf(0, 55),
    accentColor = MioColors.MutedRose,
  ),
  BORED(
    id = "bored",
    label = "Bored",
    tagline = "Bat-bat at your heart",
    soundAssetKey = "cat_bored",
    hapticTimingsMs = longArrayOf(0, 35, 160, 35),
    hapticAmplitudes = intArrayOf(0, 90, 0, 90),
    accentColor = MioColors.WarmOffWhite,
  ),
  ANGRY(
    id = "angry",
    label = "Angry",
    tagline = "Tiny spicy hiss",
    soundAssetKey = "cat_angry",
    hapticTimingsMs = longArrayOf(0, 55, 45, 55, 45, 95),
    hapticAmplitudes = intArrayOf(0, 220, 0, 220, 0, 255),
    accentColor = MioColors.DangerEmber,
  );

  companion object {
    fun fromId(raw: String?): CatMood? = entries.firstOrNull { it.id == raw }
  }
}
