package com.example.data.remote

import com.example.core.config.MioConstants
import com.example.core.crypto.CryptoService
import com.example.data.model.ChatRequestStatus
import com.example.data.model.ChatThreadPreview
import com.example.data.model.MipStatus
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.concurrent.TimeUnit

interface MipService {
  suspend fun sendMipLetter(chat: ChatThreadPreview, myProfile: UserProfile, note: String): Result<Unit>
  suspend fun respondToMipLetter(chatId: String, myProfile: UserProfile, partnerUid: String, accept: Boolean): Result<Unit>
  suspend fun unlinkMip(chatId: String, myProfile: UserProfile): Result<Unit>
}

class FirebaseMipRepository(
  private val db: FirebaseFirestore,
  private val cryptoService: CryptoService,
  private val auth: FirebaseAuth = Firebase.auth,
) : MipService {

  private fun requireUserId(): String {
    return auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google.")
  }

  private suspend fun hasActiveMipElsewhere(uid: String, currentChatId: String): Boolean {
    val activeSnap = db.collection("chats")
      .whereArrayContains("participantUids", uid)
      .whereEqualTo("mipStatus", MipStatus.ACCEPTED.wire)
      .limit(1)
      .get()
      .await()
    return activeSnap.documents.any { it.id != currentChatId }
  }

  override suspend fun sendMipLetter(
    chat: ChatThreadPreview,
    myProfile: UserProfile,
    note: String,
  ): Result<Unit> {
    val path = "chats/${chat.chatId}"
    return try {
      val uid = requireUserId()
      check(uid == myProfile.uid) { "User mismatch" }
      require(chat.status == ChatRequestStatus.ACCEPTED && !chat.isBlockedAny) {
        "MIP letters can only be sent in an accepted, unblocked chat."
      }
      require(!hasActiveMipElsewhere(uid, chat.chatId) && chat.mipStatus != MipStatus.ACCEPTED) {
        "You already have an active MIP. Unlink first to seal a new letter."
      }
      val profileCooldownMs = myProfile.mipCooldownUntil?.toDate()?.time ?: 0L
      val chatCooldownMs = if (chat.mipStatus == MipStatus.UNLINKED && chat.mipUpdatedTimeMs > 0L) {
        chat.mipUpdatedTimeMs + TimeUnit.HOURS.toMillis(MioConstants.MIP_COOLDOWN_HOURS)
      } else 0L
      val cooldownUntilMs = maxOf(profileCooldownMs, chatCooldownMs)
      if (cooldownUntilMs > System.currentTimeMillis()) {
        val hoursLeft = ((cooldownUntilMs - System.currentTimeMillis()) / 3_600_000L).coerceAtLeast(1L)
        throw IllegalStateException("Please wait ${hoursLeft}h before sealing a new MIP letter.")
      }
      val cleanNote = note.trim()
      require(cleanNote.length in 1..MioConstants.MAX_MIP_NOTE_LENGTH) {
        "Your sealed letter must be 1–${MioConstants.MAX_MIP_NOTE_LENGTH} characters."
      }

      val encrypted = cryptoService.encrypt(cleanNote, chat.partnerPublicKey, uid)
      db.collection("chats").document(chat.chatId)
        .update(
          mapOf(
            "mipStatus" to MipStatus.PENDING.wire,
            "mipSenderUid" to uid,
            "mipCiphertext" to encrypted.ciphertext,
            "mipNonce" to encrypted.nonce,
            "mipUpdatedAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        ).await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  override suspend fun respondToMipLetter(
    chatId: String,
    myProfile: UserProfile,
    partnerUid: String,
    accept: Boolean,
  ): Result<Unit> {
    val path = "chats/$chatId"
    return try {
      val uid = requireUserId()
      check(uid == myProfile.uid) { "User mismatch" }
      if (accept) {
        require(!hasActiveMipElsewhere(uid, chatId)) {
          "You already have a MIP. Unlink your current MIP first."
        }
      }

      val batch = db.batch()
      batch.update(
        db.collection("chats").document(chatId),
        mapOf(
          "mipStatus" to if (accept) MipStatus.ACCEPTED.wire else MipStatus.DECLINED.wire,
          "mipUpdatedAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      if (accept) {
        batch.update(
          db.collection("users").document(uid),
          mapOf(
            "mipPartnerUid" to partnerUid,
            "mipChatId" to chatId,
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  override suspend fun unlinkMip(chatId: String, myProfile: UserProfile): Result<Unit> {
    val path = "chats/$chatId"
    return try {
      val uid = requireUserId()
      check(uid == myProfile.uid) { "User mismatch" }
      val cooldownDate = Date(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(MioConstants.MIP_COOLDOWN_HOURS))
      val batch = db.batch()
      batch.update(
        db.collection("chats").document(chatId),
        mapOf(
          "mipStatus" to MipStatus.UNLINKED.wire,
          "mipUpdatedAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.update(
        db.collection("users").document(uid),
        mapOf(
          "mipPartnerUid" to "",
          "mipChatId" to "",
          "mipCooldownUntil" to Timestamp(cooldownDate),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }
}
