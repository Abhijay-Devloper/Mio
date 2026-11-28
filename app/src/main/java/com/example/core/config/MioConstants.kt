package com.example.core.config

object MioConstants {
  val USERNAME_REGEX = Regex("^[a-z0-9_]{3,20}$")
  val SAFE_ID_REGEX = Regex("^[a-zA-Z0-9_\\-]{1,128}$")
  val RESERVED_USERNAMES = setOf(
    "admin",
    "administrator",
    "support",
    "mio",
    "official",
    "system",
    "moderator",
    "mod",
    "staff",
    "security",
    "help",
    "root",
  )

  const val MIN_USERNAME_LENGTH = 3
  const val MAX_USERNAME_LENGTH = 20
  const val MAX_DISPLAY_NAME_LENGTH = 40
  const val MAX_MESSAGE_LENGTH = 2000
  const val MESSAGE_COUNTER_THRESHOLD = 1600
  const val MAX_MIP_NOTE_LENGTH = 280
  const val MAX_REPORT_EXCERPT_LENGTH = 500
  const val MESSAGE_TTL_DAYS = 30L
  const val MIP_COOLDOWN_HOURS = 24L

  // Rate limits & Free-tier Firestore quota guards
  const val TYPING_THROTTLE_MS = 3_000L
  const val TYPING_IDLE_TIMEOUT_MS = 5_000L
  const val TYPING_STALE_TIMEOUT_MS = 6_000L
  const val CONNECT_COOLDOWN_MS = 2_000L
  const val REPORT_COOLDOWN_MS = 30_000L
  const val SEND_DEBOUNCE_MS = 350L
  const val STALE_SENDING_MS = 15_000L
  const val CHATS_QUERY_LIMIT = 50L
  const val MESSAGES_QUERY_LIMIT = 100L

  const val SWIPE_REPLY_THRESHOLD_DP = 64f
  val QUICK_REACTIONS = listOf("❤️", "🥰", "😂", "🥺", "🔥", "🤍", "😿")

  const val INTRO_VIDEO_ASPECT_RATIO = 16f / 9f
  const val INTRO_FADE_OUT_MS = 220
  const val INTRO_SAFETY_TIMEOUT_MS = 3_200L

  const val DEFAULT_RTDB_URL =
    "https://gen-lang-client-0609526912-default-rtdb.asia-southeast1.firebasedatabase.app"

  val GOOGLE_STUN_URLS = listOf(
    "stun:stun.l.google.com:19302",
    "stun:stun1.l.google.com:19302",
  )
  val EXTRA_STUN_URLS = listOf(
    "stun:stun2.l.google.com:19302",
    "stun:stun3.l.google.com:19302",
    "stun:stun4.l.google.com:19302",
  )
  const val DEFAULT_BACKEND_TURN_URL =
    "https://asia-southeast1-gen-lang-client-0609526912.cloudfunctions.net/getTurnCredentials"
  const val TURN_FETCH_TIMEOUT_MS = 1_500L
  const val PUSH_RELAY_TIMEOUT_MS = 5_000L
  const val CALL_RING_TIMEOUT_MS = 45_000L
  const val CALL_CONNECT_TIMEOUT_MS = 25_000L
  const val CALL_STALE_INVITE_MS = 90_000L
  const val CALL_DISCONNECT_GRACE_MS = 10_000L
  const val CALL_STATS_POLL_MS = 3_000L
  const val MAX_NOTIFICATION_POPUPS_PER_CHAT = 4
  const val MAX_FCM_TOKEN_LENGTH = 512

  const val FCM_TYPE_NEW_MESSAGE = "new_message"
  const val FCM_TYPE_INCOMING_CALL = "incoming_call"
  const val FCM_TYPE_CALL_ENDED = "call_ended"
}
