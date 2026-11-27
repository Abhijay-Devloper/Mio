package com.example.core.calls

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.util.Log

/**
 * Manages Android voice-communication audio routing (earpiece vs speakerphone),
 * microphone mute state, audio focus, and incoming ringtone playback.
 */
class CallAudioController(context: Context) {

  private val appContext = context.applicationContext
  private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

  private var previousMode: Int = AudioManager.MODE_NORMAL
  private var previousSpeaker: Boolean = false
  private var previousMicMute: Boolean = false
  private var audioFocusRequest: AudioFocusRequest? = null
  private var activeRingtone: Ringtone? = null
  private var isConfigured: Boolean = false

  fun startCallAudio(speakerOn: Boolean) {
    val am = audioManager ?: return
    runCatching {
      if (!isConfigured) {
        previousMode = am.mode
        @Suppress("DEPRECATION")
        previousSpeaker = am.isSpeakerphoneOn
        previousMicMute = am.isMicrophoneMute
        isConfigured = true
      }

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val attrs = AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
          .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
          .build()
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
          .setAudioAttributes(attrs)
          .setWillPauseWhenDucked(false)
          .build()
        audioFocusRequest = req
        am.requestAudioFocus(req)
      } else {
        @Suppress("DEPRECATION")
        am.requestAudioFocus(
          null,
          AudioManager.STREAM_VOICE_CALL,
          AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
        )
      }

      am.mode = AudioManager.MODE_IN_COMMUNICATION
      am.isMicrophoneMute = false
      setSpeakerphone(speakerOn)
    }.onFailure { err ->
      Log.w(TAG, "Unable to configure call audio: ${err.javaClass.simpleName}")
    }
  }

  fun setSpeakerphone(enabled: Boolean) {
    val am = audioManager ?: return
    runCatching {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val targetType = if (enabled) {
          AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        } else {
          AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
        }
        val targetDevice = am.availableCommunicationDevices.firstOrNull { it.type == targetType }
        if (targetDevice != null) {
          am.setCommunicationDevice(targetDevice)
          return
        }
      }
      @Suppress("DEPRECATION")
      am.isSpeakerphoneOn = enabled
    }
  }

  fun setMicrophoneMuted(muted: Boolean) {
    runCatching {
      audioManager?.isMicrophoneMute = muted
    }
  }

  fun startIncomingRingtone() {
    stopIncomingRingtone()
    runCatching {
      val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ?: return
      val ringtone = RingtoneManager.getRingtone(appContext, uri) ?: return
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        ringtone.isLooping = true
      }
      ringtone.play()
      activeRingtone = ringtone
    }
  }

  fun stopIncomingRingtone() {
    runCatching {
      activeRingtone?.stop()
    }
    activeRingtone = null
  }

  fun restore() {
    stopIncomingRingtone()
    val am = audioManager ?: return
    runCatching {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        am.clearCommunicationDevice()
      }
      if (isConfigured) {
        am.mode = previousMode
        @Suppress("DEPRECATION")
        am.isSpeakerphoneOn = previousSpeaker
        am.isMicrophoneMute = previousMicMute
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
      } else {
        @Suppress("DEPRECATION")
        am.abandonAudioFocus(null)
      }
    }
    audioFocusRequest = null
    isConfigured = false
  }

  private companion object {
    const val TAG = "MioCallAudio"
  }
}
