package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class UsernameClaim(
  @DocumentId val id: String = "",
  val uid: String = "",
  val username: String = "",
  val displayName: String = "",
  val publicKey: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class UserProfile(
  @DocumentId val id: String = "",
  val uid: String = "",
  val username: String = "",
  val displayName: String = "",
  val publicKey: String = "",
  val ageConfirmed: Boolean = false,
  val ageConfirmedAt: Timestamp? = null,
  val mipPartnerUid: String = "",
  val mipChatId: String = "",
  val mipCooldownUntil: Timestamp? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class BlockRecord(
  @DocumentId val id: String = "",
  val blockedUid: String = "",
  val blockedUsername: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class ChatThreadDoc(
  @DocumentId val id: String = "",
  val participantUids: List<String> = emptyList(),
  val initiatorUid: String = "",
  val status: String = "pending",
  val participantUsernames: Map<String, String> = emptyMap(),
  val participantDisplayNames: Map<String, String> = emptyMap(),
  val participantPublicKeys: Map<String, String> = emptyMap(),
  val blockedBy: List<String> = emptyList(),
  val typingBy: Map<String, Boolean> = emptyMap(),
  val unreadCounts: Map<String, Long> = emptyMap(),
  val lastMessageCiphertext: String = "",
  val lastMessageNonce: String = "",
  val lastMessageSenderUid: String = "",
  val lastMessageType: String = "",
  val lastMessageStatus: String = "",
  val lastMessageAt: Timestamp? = null,
  val mipStatus: String = "none",
  val mipSenderUid: String = "",
  val mipCiphertext: String = "",
  val mipNonce: String = "",
  val mipUpdatedAt: Timestamp? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
)

@Keep
@IgnoreExtraProperties
data class ChatMessageDoc(
  @DocumentId val id: String = "",
  val chatId: String = "",
  val senderUid: String = "",
  val recipientUid: String = "",
  val participantUids: List<String> = emptyList(),
  val type: String = "text",
  val ciphertext: String = "",
  val nonce: String = "",
  val moodId: String? = null,
  val status: String = "sent",
  val deletedFor: List<String> = emptyList(),
  val deletedForEveryone: Boolean = false,
  val reactions: Map<String, String> = emptyMap(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null,
  val expiresAt: Timestamp? = null,
)
