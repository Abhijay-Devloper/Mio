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
  const val CONNECT_COOLDOWN_MS = 2_000L
  const val REPORT_COOLDOWN_MS = 30_000L
  const val SEND_DEBOUNCE_MS = 350L
  const val STALE_SENDING_MS = 15_000L
  const val CHATS_QUERY_LIMIT = 50L
  const val MESSAGES_QUERY_LIMIT = 100L

  const val SWIPE_REPLY_THRESHOLD_DP = 64f
  val QUICK_REACTIONS = listOf("❤️", "🥰", "😂", "🥺", "🔥", "🤍", "😿")
}
