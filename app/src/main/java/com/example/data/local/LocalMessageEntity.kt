package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * On-device decrypted message history stored in Room.
 * Message history lives on this device in Phase 1.
 */
@Entity(
  tableName = "local_messages",
  indices = [Index(value = ["chatId", "timestampMs"])],
)
data class LocalMessageEntity(
  @PrimaryKey val id: String,
  val chatId: String,
  val ownerUid: String,
  val senderUid: String,
  val recipientUid: String,
  val type: String,
  val textBody: String,
  val moodId: String?,
  val replyToMessageId: String?,
  val replyToSenderName: String?,
  val replyToSnippet: String?,
  val status: String,
  val deletedForMe: Boolean,
  val deletedForEveryone: Boolean,
  val reactionsJson: String,
  val timestampMs: Long,
)
