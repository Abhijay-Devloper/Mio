package com.example.core.feedback

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.example.core.config.CatMood

/**
 * Plays mood-specific haptic patterns and sound assets safely.
 * Missing audio files in res/raw never crash the app and degrade gracefully.
 */
class MoodFeedbackManager(private val context: Context) {

  private val appContext = context.applicationContext

  fun triggerMoodFeedback(
    mood: CatMood,
    hapticsEnabled: Boolean,
    soundsEnabled: Boolean,
  ) {
    if (hapticsEnabled) {
      playHaptic(mood)
    }
    if (soundsEnabled) {
      playSound(mood)
    }
  }

  @Suppress("DEPRECATION")
  fun playLightTick() {
    try {
      val vibrator = getVibrator() ?: return
      if (!vibrator.hasVibrator()) return
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
      } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        vibrator.vibrate(VibrationEffect.createOneShot(18L, 80))
      } else {
        vibrator.vibrate(18L)
      }
    } catch (e: Exception) {
      Log.w(TAG, "Tick haptic skipped: ${e.message}")
    }
  }

  @Suppress("DEPRECATION")
  private fun playHaptic(mood: CatMood) {
    try {
      val vibrator = getVibrator() ?: return
      if (!vibrator.hasVibrator()) return
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val effect = if (vibrator.hasAmplitudeControl()) {
          VibrationEffect.createWaveform(mood.hapticTimingsMs, mood.hapticAmplitudes, -1)
        } else {
          VibrationEffect.createWaveform(mood.hapticTimingsMs, -1)
        }
        vibrator.vibrate(effect)
      } else {
        vibrator.vibrate(mood.hapticTimingsMs, -1)
      }
    } catch (e: Exception) {
      Log.w(TAG, "Mood haptic failed safely: ${e.message}")
    }
  }

  private fun playSound(mood: CatMood) {
    try {
      val rawResId = appContext.resources.getIdentifier(
        mood.soundAssetKey,
        "raw",
        appContext.packageName,
      )
      if (rawResId != 0) {
        val player = MediaPlayer.create(appContext, rawResId)
        if (player != null) {
          player.setAudioAttributes(
            AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
              .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
              .build()
          )
          player.setOnCompletionListener { it.release() }
          player.setOnErrorListener { mp, _, _ ->
            mp.release()
            true
          }
          player.start()
          return
        }
      }
      // Graceful non-crashing fallback chime when supplied raw cat audio assets are absent
      playSoftFallbackTone(mood)
    } catch (e: Exception) {
      Log.w(TAG, "Mood audio skipped safely for ${mood.soundAssetKey}: ${e.message}")
    }
  }

  private fun playSoftFallbackTone(mood: CatMood) {
    try {
      val toneType = when (mood) {
        CatMood.HAPPY -> ToneGenerator.TONE_PROP_ACK
        CatMood.SAD -> ToneGenerator.TONE_PROP_BEEP2
        CatMood.TIRED -> ToneGenerator.TONE_SUP_PIP
        CatMood.BORED -> ToneGenerator.TONE_PROP_BEEP
        CatMood.ANGRY -> ToneGenerator.TONE_PROP_NACK
      }
      val generator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 35)
      generator.startTone(toneType, 120)
      android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
        try {
          generator.release()
        } catch (_: Exception) {
        }
      }, 220L)
    } catch (_: Exception) {
      // Audio hardware unavailable; fail silently without crashing
    }
  }

  @Suppress("DEPRECATION")
  private fun getVibrator(): Vibrator? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      val manager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
      manager?.defaultVibrator
    } else {
      appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
  }

  private companion object {
    const val TAG = "MoodFeedback"
  }
}
