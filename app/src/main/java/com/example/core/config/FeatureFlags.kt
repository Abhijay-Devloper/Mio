package com.example.core.config

/**
 * Feature availability registry.
 * Phase 1 features are enabled; later-phase seams are defined here and remain inert.
 */
object FeatureFlags {
  // Phase 1 (active)
  const val EXACT_USERNAME_CONNECT = true
  const val E2EE_MESSAGING = true
  const val SWIPE_TO_REPLY = true
  const val MESSAGE_REACTIONS = true
  const val MESSAGE_DELETE_FOR_EVERYONE = true
  const val MIP_SEALED_LETTER = true
  const val MOOD_CATS = true
  const val LOCAL_NEW_MESSAGE_ALERTS = true

  // Phase 1+ active capabilities
  const val PRESENCE_LAST_ACTIVE = true
  const val PUSH_FCM_REMOTE = true
  const val AUDIO_VIDEO_CALLS = true

  // Phase 2+ seams (inert)
  const val VOICE_NOTES = false
  const val PHOTO_MESSAGES = false
  const val SCHEDULED_MESSAGES = false
  const val DISAPPEARING_MESSAGES = false
  const val SHARED_CALENDAR = false
  const val MULTI_DEVICE_KEY_BACKUP = false
}
