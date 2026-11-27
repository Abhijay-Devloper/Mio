package com.example.data.model

import com.example.core.config.CatMood

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}

enum class MessageStatus(val wire: String, val label: String) {
  SENDING("sending", "Sending..."),
  SENT("sent", "Sent"),
  DELIVERED("delivered", "Delivered"),
  READ("read", "Read"),
  FAILED("failed", "Not sent");

  companion object {
    fun fromWire(raw: String?): MessageStatus =
      entries.firstOrNull { it.wire == raw } ?: SENT
  }
}

enum class ChatRequestStatus(val wire: String) {
  PENDING("pending"),
  ACCEPTED("accepted"),
  DECLINED("declined");

  companion object {
    fun fromWire(raw: String?): ChatRequestStatus =
      entries.firstOrNull { it.wire == raw } ?: PENDING
  }
}

enum class MipStatus(val wire: String) {
  NONE("none"),
  PENDING("pending"),
  ACCEPTED("accepted"),
  DECLINED("declined"),
  UNLINKED("unlinked");

  companion object {
    fun fromWire(raw: String?): MipStatus =
      entries.firstOrNull { it.wire == raw } ?: NONE
  }
}

enum class ReportReason(val wire: String, val label: String) {
  HARASSMENT("harassment", "Harassment or unwanted pressure"),
  SPAM("spam", "Spam or unsolicited links"),
  NON_CONSENSUAL("non_consensual", "Non-consensual or harmful content"),
  IMPERSONATION("impersonation", "Impersonation"),
  OTHER("other", "Other safety concern"),
}

data class ReplyQuote(
  val messageId: String,
  val senderName: String,
  val snippet: String,
)

/**
 * Discriminated union for message content.
 * Phase 1 implements Text and Mood; later phases extend with Voice, Photo, etc.
 */
sealed interface MessageContent {
  data class Text(val body: String) : MessageContent
  data class Mood(val mood: CatMood, val caption: String) : MessageContent
}

data class ChatMessage(
  val id: String,
  val chatId: String,
  val senderUid: String,
  val recipientUid: String,
  val content: MessageContent,
  val replyQuote: ReplyQuote? = null,
  val status: MessageStatus = MessageStatus.SENT,
  val deletedForEveryone: Boolean = false,
  val reactions: Map<String, String> = emptyMap(),
  val timestampMs: Long,
)

data class PresenceStatus(
  val state: String = "offline",
  val lastActiveMs: Long = 0L,
) {
  val isOnline: Boolean get() = state == "online"
}

data class ChatThreadPreview(
  val chatId: String,
  val partnerUid: String,
  val partnerUsername: String,
  val partnerDisplayName: String,
  val partnerPublicKey: String,
  val status: ChatRequestStatus,
  val initiatorUid: String,
  val blockedByMe: Boolean,
  val blockedByPartner: Boolean,
  val partnerTyping: Boolean,
  val partnerOnline: Boolean = false,
  val partnerLastActiveMs: Long = 0L,
  val unreadCount: Int,
  val lastMessagePreview: String,
  val lastMessageSenderUid: String,
  val lastMessageStatus: MessageStatus?,
  val lastMessageTimeMs: Long,
  val mipStatus: MipStatus,
  val mipSenderUid: String,
  val mipNoteDecrypted: String,
  val mipUpdatedTimeMs: Long,
) {
  val isBlockedAny: Boolean get() = blockedByMe || blockedByPartner
  val isMipActive: Boolean get() = mipStatus == MipStatus.ACCEPTED
}
