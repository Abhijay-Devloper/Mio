package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalMessageDao {
  @Query(
    """
    SELECT * FROM local_messages
    WHERE chatId = :chatId AND ownerUid = :ownerUid AND deletedForMe = 0
    ORDER BY timestampMs ASC
    """
  )
  fun observeChatMessages(chatId: String, ownerUid: String): Flow<List<LocalMessageEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsertAll(messages: List<LocalMessageEntity>)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun upsert(message: LocalMessageEntity)

  @Query("UPDATE local_messages SET deletedForMe = 1 WHERE id = :messageId AND ownerUid = :ownerUid")
  suspend fun markDeletedForMe(messageId: String, ownerUid: String)

  @Query("UPDATE local_messages SET status = :status WHERE id = :messageId AND ownerUid = :ownerUid")
  suspend fun updateStatus(messageId: String, ownerUid: String, status: String)

  @Query(
    """
    UPDATE local_messages
    SET status = :readStatus
    WHERE chatId = :chatId AND ownerUid = :ownerUid AND senderUid != :ownerUid AND status != :readStatus
    """
  )
  suspend fun markIncomingAsRead(
    chatId: String,
    ownerUid: String,
    readStatus: String,
  )

  @Query(
    """
    UPDATE local_messages
    SET status = :newStatus
    WHERE chatId = :chatId
      AND ownerUid = :ownerUid
      AND senderUid = :ownerUid
      AND timestampMs <= :upToMs
      AND status IN (:eligibleStatuses)
    """
  )
  suspend fun promoteOutgoingStatusUpTo(
    chatId: String,
    ownerUid: String,
    newStatus: String,
    eligibleStatuses: List<String>,
    upToMs: Long,
  )

  @Query(
    """
    UPDATE local_messages
    SET status = :failedStatus
    WHERE ownerUid = :ownerUid AND status = :sendingStatus AND timestampMs < :cutoffMs
    """
  )
  suspend fun markStaleSendingFailed(
    ownerUid: String,
    sendingStatus: String,
    failedStatus: String,
    cutoffMs: Long,
  )

  @Query("SELECT * FROM local_messages WHERE id = :messageId AND ownerUid = :ownerUid LIMIT 1")
  suspend fun getById(messageId: String, ownerUid: String): LocalMessageEntity?

  @Query("DELETE FROM local_messages WHERE id = :messageId AND ownerUid = :ownerUid")
  suspend fun deleteById(messageId: String, ownerUid: String)

  @Query("DELETE FROM local_messages WHERE chatId = :chatId AND ownerUid = :ownerUid")
  suspend fun deleteChatMessages(chatId: String, ownerUid: String)

  @Query("DELETE FROM local_messages WHERE ownerUid = :ownerUid")
  suspend fun deleteAllForUser(ownerUid: String)
}
