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
  const val EMPTY_THREAD_PREVIEW = "Tap to start chatting!"
  const val CONNECT_CTA = "Connect"
  const val SEARCH_USERNAME_PROMPT = "Enter their exact @username"
  const val SEARCH_NEUTRAL_NOT_FOUND = "No user available with that exact username."

  // Chat detail
  const val COMPOSER_HINT = "Your Message..."
  const val TYPING_INDICATOR = "typing..."
  const val DELETED_FOR_EVERYONE_LABEL = "This message was deleted"
  const val BLOCKED_BANNER = "Messaging is paused because one of you blocked this chat."
  const val PENDING_OUTGOING_BANNER = "Waiting for them to accept your invitation."
  const val PENDING_INCOMING_BANNER = "wants to start a private conversation with you."
  const val DELETE_CHATS_OPTION = "Delete chats"
  const val DELETE_CHATS_TITLE = "Delete chats?"
  const val DELETE_CHATS_BODY = "This permanently deletes all messages in this conversation from your device's internal storage. The contact stays on your Chats screen."
  const val DELETE_CHATS_CONFIRM = "Delete chats"
  const val CHAT_DELETED_NOTICE = "Chat messages deleted from internal storage."

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
  const val ACTIVE_STATUS_TITLE = "Active Status"
  const val ACTIVE_STATUS_SUBTITLE = "Share your online dot and last active time"
  const val ACTIVE_NOW_LABEL = "Active now"
  const val PRIVACY_DRAFT_BADGE = "Draft for legal review"
  const val DELETE_ACCOUNT_WARNING = "This permanently removes your profile, @username, active MIP link, and local message history on this device."

  // Voice & Video Calls
  const val CALL_TYPE_VOICE = "Voice Call"
  const val CALL_TYPE_VIDEO = "Video Call"
  const val CALL_INCOMING_VOICE = "Incoming voice call"
  const val CALL_INCOMING_VIDEO = "Incoming video call"
  const val CALL_ACCEPT = "Accept"
  const val CALL_DECLINE = "Decline"
  const val CALL_STATUS_RINGING = "Ringing..."
  const val CALL_STATUS_CONNECTING = "Connecting..."
  const val CALL_STATUS_CONNECTED = "Connected"
  const val CALL_STATUS_CONNECTED_P2P = "Connected (P2P)"
  const val CALL_STATUS_CONNECTED_RELAY = "Connected (Relay)"
  const val CALL_STATUS_ENDED = "Call ended"
  const val CALL_STATUS_DECLINED = "Call declined"
  const val CALL_STATUS_MISSED = "No answer"
  const val CALL_PERMISSION_REQUIRED = "Microphone (and camera for video) permission is required to place or answer calls."
  const val CALL_CONNECTION_FAILED = "Unable to connect the call. Please check your network and try again."
  const val CALL_MINIMIZE_DESC = "Minimize call"
  const val CALL_RETURN_TO_CALL = "Tap to return to call"
  const val CALL_HANGUP = "Hang up"
  const val CALL_BANNER_RINGING = "Ringing"
  const val CALL_BANNER_CONNECTING = "Connecting"
  const val CALL_BANNER_CONNECTED = "In call"

  // Notifications
  const val NOTIFICATION_NEW_MESSAGE_BODY = "Sent you a message !"
  const val NOTIF_RELIABILITY_TITLE = "Background Delivery & Call Alerts"
  const val NOTIF_RELIABILITY_SUBTITLE = "Keep message alerts and incoming call rings reliable when Mio is closed."
  const val NOTIF_BATTERY_UNRESTRICTED = "Battery optimization: Unrestricted"
  const val NOTIF_BATTERY_OPTIMIZED = "Battery optimization: Restricted (may delay closed-app alerts)"
  const val NOTIF_BATTERY_FIX_CTA = "Allow unrestricted background"
  const val NOTIF_FULLSCREEN_GRANTED = "Lock-screen call alerts: Allowed"
  const val NOTIF_FULLSCREEN_DENIED = "Lock-screen call alerts: Needs permission"
  const val NOTIF_FULLSCREEN_FIX_CTA = "Allow full-screen call alerts"
}
