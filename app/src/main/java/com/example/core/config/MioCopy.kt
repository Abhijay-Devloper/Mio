package com.example.core.config

object MioCopy {
  const val APP_NAME = "Mio"
  const val TAGLINE = "A quiet room built for two."
  const val SIGN_IN_SUBTITLE = "Private, end-to-end encrypted conversations, mood cats, and sealed letters for your favorite person."
  const val CONTINUE_WITH_GOOGLE = "Continue with Google"

  // Onboarding
  const val ONBOARDING_TITLE = "Claim your handle"
  const val ONBOARDING_SUBTITLE = "Friends can only find you when they know your exact username."
  const val USERNAME_HINT = "lowercase, numbers, underscores (3–20)"
  const val AGE_CONFIRMATION_LABEL = "I confirm I am 18 years of age or older."
  const val DEVICE_HISTORY_NOTICE = "Your keys stay on this device. Message history lives locally on your phone (no cloud key backup yet)."

  // Chat list
  const val CHATS_TITLE = "Conversations"
  const val REQUESTS_HEADER = "Invitations"
  const val EMPTY_CHATS_TITLE = "No conversations yet"
  const val EMPTY_CHATS_BODY = "Tap Connect to invite your partner by their exact @username."
  const val CONNECT_CTA = "Connect"
  const val SEARCH_USERNAME_PROMPT = "Enter their exact @username"
  const val SEARCH_NEUTRAL_NOT_FOUND = "No user available with that exact username."

  // Chat detail
  const val COMPOSER_HINT = "Write something warm..."
  const val TYPING_INDICATOR = "typing..."
  const val DELETED_FOR_EVERYONE_LABEL = "This message was deleted"
  const val BLOCKED_BANNER = "Messaging is paused because one of you blocked this chat."
  const val PENDING_OUTGOING_BANNER = "Waiting for them to accept your invitation."
  const val PENDING_INCOMING_BANNER = "wants to start a private conversation with you."

  // Receipts
  const val STATUS_SENDING = "Sending..."
  const val STATUS_SENT = "Sent"
  const val STATUS_DELIVERED = "Delivered"
  const val STATUS_READ = "Read"
  const val STATUS_FAILED = "Not sent"

  // MIP
  const val MIP_TITLE = "Most Important Person"
  const val MIP_SUBTITLE = "One person holds the wax seal. Only in an accepted, unblocked conversation."
  const val MIP_EMPTY_TITLE = "No MIP sealed yet"
  const val MIP_EMPTY_BODY = "Open any accepted chat and tap the seal icon to send your Most Important Person letter."
  const val MIP_COOLDOWN_NOTICE = "A 24-hour quiet cooldown applies after unlinking before sealing a new MIP."

  // Settings & Legal
  const val PRIVACY_DRAFT_BADGE = "Draft for legal review"
  const val DELETE_ACCOUNT_WARNING = "This permanently removes your profile, @username, active MIP link, and local message history on this device."
}
