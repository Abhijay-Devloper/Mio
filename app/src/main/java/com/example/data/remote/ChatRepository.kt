package com.example.data.remote

import android.util.Log
import com.example.core.config.CatMood
import com.example.core.config.MioConstants
import com.example.core.config.MioCopy
import com.example.core.crypto.CryptoService
import com.example.data.local.LocalMessageDao
import com.example.data.local.LocalMessageEntity
import com.example.data.model.ChatMessage
import com.example.data.model.ChatMessageDoc
import com.example.data.model.ChatRequestStatus
import com.example.data.model.ChatThreadDoc
import com.example.data.model.ChatThreadPreview
import com.example.data.model.MessageContent
import com.example.data.model.MessageStatus
import com.example.data.model.MipStatus
import com.example.data.model.ReplyQuote
import com.example.data.model.ReportReason
import com.example.data.model.UserProfile
import com.example.data.model.UsernameClaim
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.util.Date
import java.util.UUID
import java.util.concurrent.TimeUnit

interface ChatService {
  fun observeChats(myUid: String): Flow<List<ChatThreadPreview>>
  fun observeChat(chatId: String, myUid: String): Flow<ChatThreadPreview?>
  fun observeMessages(
    chatId: String,
    myUid: String,
    partnerDisplayName: String,
    partnerPublicKey: String,
  ): Flow<List<ChatMessage>>
  suspend fun findUserByExactUsername(rawUsername: String, myUid: String): Result<UsernameClaim?>
  suspend fun sendChatRequest(myProfile: UserProfile, target: UsernameClaim): Result<String>
  suspend fun respondToChatRequest(chatId: String, accept: Boolean): Result<Unit>
  suspend fun sendMessage(
    chat: ChatThreadPreview,
    myProfile: UserProfile,
    content: MessageContent,
    replyQuote: ReplyQuote?,
  ): Result<Unit>
  suspend fun retryFailedMessage(
    chat: ChatThreadPreview,
    myProfile: UserProfile,
    messageId: String,
  ): Result<Unit>
  suspend fun markChatAsRead(
    chatId: String,
    myUid: String,
    unreadMessageIds: List<String>,
    updateLastMessagePreviewStatus: Boolean,
  )
  suspend fun setTyping(chatId: String, myUid: String, typing: Boolean)
  suspend fun toggleReaction(chatId: String, messageId: String, myUid: String, emoji: String): Result<Unit>
  suspend fun deleteMessageForMe(chatId: String, messageId: String, myUid: String): Result<Unit>
  suspend fun deleteMessageForEveryone(chatId: String, messageId: String, myUid: String): Result<Unit>
  suspend fun blockUser(chatId: String, myUid: String, partnerUid: String, partnerUsername: String): Result<Unit>
  suspend fun unblockUser(chatId: String, myUid: String, partnerUid: String): Result<Unit>
  suspend fun submitReport(
    myUid: String,
    reportedUid: String,
    chatId: String,
    reason: ReportReason,
    excerpt: String?,
  ): Result<Unit>
  suspend fun cleanupExpiredRelayDocs(myUid: String)
}

class FirebaseChatRepository(
  private val db: FirebaseFirestore,
  private val cryptoService: CryptoService,
  private val localMessageDao: LocalMessageDao,
  private val auth: FirebaseAuth = Firebase.auth,
) : ChatService {

  private var lastLookupAtMs: Long = 0L
  private var lastReportAtMs: Long = 0L

  private fun requireUserId(): String {
    return auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing chats.")
  }

  override fun observeChats(myUid: String): Flow<List<ChatThreadPreview>> = flow {
    if (myUid.isBlank()) {
      emit(emptyList())
      return@flow
    }
    val path = "chats"
    emitAll(
      db.collection("chats")
        .whereArrayContains("participantUids", myUid)
        .limit(MioConstants.CHATS_QUERY_LIMIT)
        .snapshots()
        .map { snapshot ->
          val docs = snapshot.toObjects(
            ChatThreadDoc::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
          )
          docs.mapNotNull { doc -> toChatPreview(doc, myUid) }
            .sortedByDescending { maxOf(it.lastMessageTimeMs, it.mipUpdatedTimeMs) }
        }
        .catch { error ->
          val root = unwrapFirestoreError(error)
          if (root == null) throw error
          if (auth.currentUser?.uid == myUid) {
            handleFirestoreError(root, OperationType.LIST, path)
          }
          throw error
        }
    )
  }

  override fun observeChat(chatId: String, myUid: String): Flow<ChatThreadPreview?> = flow {
    if (chatId.isBlank() || myUid.isBlank()) {
      emit(null)
      return@flow
    }
    val path = "chats/$chatId"
    emitAll(
      db.collection("chats").document(chatId)
        .snapshots()
        .map { snapshot ->
          if (!snapshot.exists()) null
          else {
            val doc = snapshot.toObject(
              ChatThreadDoc::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
            doc?.let { toChatPreview(it, myUid) }
          }
        }
        .catch { error ->
          val root = unwrapFirestoreError(error)
          if (root == null) throw error
          if (auth.currentUser?.uid == myUid) {
            handleFirestoreError(root, OperationType.GET, path)
          }
          throw error
        }
    )
  }

  override fun observeMessages(
    chatId: String,
    myUid: String,
    partnerDisplayName: String,
    partnerPublicKey: String,
  ): Flow<List<ChatMessage>> = flow {
    if (chatId.isBlank() || myUid.isBlank()) {
      emit(emptyList())
      return@flow
    }
    val path = "chats/$chatId/messages"
    val remoteFlow = db.collection("chats").document(chatId)
      .collection("messages")
      .whereArrayContains("participantUids", myUid)
      .limit(MioConstants.MESSAGES_QUERY_LIMIT)
      .snapshots()
      .map { snapshot ->
        val remoteDocs = snapshot.toObjects(
          ChatMessageDoc::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
        )
        syncRemoteMessagesToLocal(
          chatId = chatId,
          myUid = myUid,
          partnerPublicKey = partnerPublicKey,
          remoteDocs = remoteDocs,
        )
      }
      .catch { error ->
        val root = unwrapFirestoreError(error)
        if (root == null) throw error
        if (auth.currentUser?.uid == myUid) {
          handleFirestoreError(root, OperationType.LIST, path)
        }
        throw error
      }

    val localFlow = localMessageDao.observeChatMessages(chatId, myUid)
      .map { entities -> entities.map { entityToDomain(it) } }

    emitAll(
      combine(remoteFlow, localFlow) { _, localMessages ->
        localMessages
      }
    )
  }

  private suspend fun syncRemoteMessagesToLocal(
    chatId: String,
    myUid: String,
    partnerPublicKey: String,
    remoteDocs: List<ChatMessageDoc>,
  ) {
    val entitiesToUpsert = mutableListOf<LocalMessageEntity>()
    val toMarkDelivered = mutableListOf<String>()

    for (doc in remoteDocs) {
      val existingLocal = localMessageDao.getById(doc.id, myUid)
      val deletedForMe = (myUid in doc.deletedFor) || (existingLocal?.deletedForMe == true)
      val timestampMs = doc.createdAt?.toDate()?.time ?: existingLocal?.timestampMs ?: System.currentTimeMillis()

      var textBody = existingLocal?.textBody ?: ""
      var replyId = existingLocal?.replyToMessageId
      var replySender = existingLocal?.replyToSenderName
      var replySnippet = existingLocal?.replyToSnippet

      if (doc.deletedForEveryone) {
        textBody = ""
        replyId = null
        replySender = null
        replySnippet = null
      } else if (doc.ciphertext.isNotBlank() && doc.nonce.isNotBlank() && partnerPublicKey.isNotBlank()) {
        val needsDecrypt = textBody.isBlank() ||
          textBody == UNDECRYPTED_PLACEHOLDER ||
          existingLocal == null
        if (needsDecrypt) {
          try {
            val jsonStr = cryptoService.decrypt(doc.ciphertext, doc.nonce, partnerPublicKey, myUid)
            val parsed = parseDecryptedPayload(jsonStr)
            textBody = parsed.text
            replyId = parsed.replyToId
            replySender = parsed.replyToSender
            replySnippet = parsed.replyToSnippet
          } catch (e: Exception) {
            Log.w(TAG, "Unable to decrypt message ${doc.id}: ${e.javaClass.simpleName}")
            if (textBody.isBlank()) {
              textBody = UNDECRYPTED_PLACEHOLDER
            }
          }
        }
      }

      if (doc.recipientUid == myUid && doc.status == MessageStatus.SENT.wire) {
        toMarkDelivered.add(doc.id)
      }

      val reactionsJson = if (doc.deletedForEveryone) {
        "{}"
      } else {
        JSONObject(doc.reactions).toString()
      }

      entitiesToUpsert.add(
        LocalMessageEntity(
          id = doc.id,
          chatId = chatId,
          ownerUid = myUid,
          senderUid = doc.senderUid,
          recipientUid = doc.recipientUid,
          type = doc.type,
          textBody = textBody,
          moodId = doc.moodId,
          replyToMessageId = replyId,
          replyToSenderName = replySender,
          replyToSnippet = replySnippet,
          status = doc.status,
          deletedForMe = deletedForMe,
          deletedForEveryone = doc.deletedForEveryone,
          reactionsJson = reactionsJson,
          timestampMs = timestampMs,
        )
      )
    }

    if (entitiesToUpsert.isNotEmpty()) {
      localMessageDao.upsertAll(entitiesToUpsert)
    }

    // Mark incoming messages as delivered once persisted locally
    for (msgId in toMarkDelivered) {
      try {
        db.collection("chats").document(chatId)
          .collection("messages").document(msgId)
          .update(
            mapOf(
              "status" to MessageStatus.DELIVERED.wire,
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          ).await()
      } catch (_: Exception) {
        // Non-fatal: next sync will retry delivery receipt
      }
    }
  }

  override suspend fun findUserByExactUsername(rawUsername: String, myUid: String): Result<UsernameClaim?> {
    val normalized = rawUsername.trim().lowercase().removePrefix("@")
    val path = "usernames/$normalized"
    return try {
      requireUserId()
      val now = System.currentTimeMillis()
      if (now - lastLookupAtMs < MioConstants.CONNECT_COOLDOWN_MS) {
        return Result.failure(IllegalStateException("Please pause a moment before searching again."))
      }
      lastLookupAtMs = now

      if (!MioConstants.USERNAME_REGEX.matches(normalized) || normalized in MioConstants.RESERVED_USERNAMES) {
        return Result.success(null)
      }
      val snap = db.collection("usernames").document(normalized).get().await()
      if (!snap.exists()) {
        return Result.success(null)
      }
      val claim = snap.toObject(UsernameClaim::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        ?: return Result.success(null)
      if (claim.uid == myUid) {
        return Result.success(null)
      }
      // Check own blocklist; return same neutral null so blocked vs missing is indistinguishable
      val blockSnap = db.collection("users").document(myUid)
        .collection("blocks").document(claim.uid)
        .get().await()
      if (blockSnap.exists()) {
        return Result.success(null)
      }
      Result.success(claim)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, path)
      Result.failure(e)
    }
  }

  override suspend fun sendChatRequest(myProfile: UserProfile, target: UsernameClaim): Result<String> {
    val uid = requireUserId()
    val pair = listOf(uid, target.uid).sorted()
    val chatId = "${pair[0]}_${pair[1]}"
    val path = "chats/$chatId"
    var currentOp = OperationType.GET
    return try {
      check(uid == myProfile.uid) { "User mismatch" }
      require(target.uid != uid) { "You cannot start a chat with yourself." }

      val myPublicKey = myProfile.publicKey.ifBlank { cryptoService.ensureKeyPair(uid) }
      require(myPublicKey.isNotBlank() && target.publicKey.isNotBlank()) {
        "Partner encryption key is missing. Ask them to open Mio once and retry."
      }
      val myDisplay = myProfile.displayName.trim().ifBlank { myProfile.username }
      val targetDisplay = target.displayName.trim().ifBlank { target.username }

      val existing = db.collection("chats").document(chatId).get().await()
      if (existing.exists()) {
        val status = existing.getString("status")
        val blockedBy = (existing.get("blockedBy") as? List<*>) ?: emptyList<Any>()
        if (blockedBy.isNotEmpty()) {
          return Result.failure(IllegalStateException("Unable to send invitation right now."))
        }
        if (status == ChatRequestStatus.DECLINED.wire) {
          return Result.failure(IllegalStateException("That conversation invitation was declined."))
        }
        return Result.success(chatId)
      }

      currentOp = OperationType.CREATE
      val payload = mapOf(
        "participantUids" to pair,
        "initiatorUid" to uid,
        "status" to ChatRequestStatus.PENDING.wire,
        "participantUsernames" to mapOf(uid to myProfile.username, target.uid to target.username),
        "participantDisplayNames" to mapOf(uid to myDisplay, target.uid to targetDisplay),
        "participantPublicKeys" to mapOf(uid to myPublicKey, target.uid to target.publicKey),
        "blockedBy" to emptyList<String>(),
        "typingBy" to mapOf(uid to false, target.uid to false),
        "unreadCounts" to mapOf(uid to 0L, target.uid to 0L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp(),
      )
      db.collection("chats").document(chatId).set(payload).await()
      Result.success(chatId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, currentOp, path)
      Result.failure(e)
    }
  }

  override suspend fun respondToChatRequest(chatId: String, accept: Boolean): Result<Unit> {
    val path = "chats/$chatId"
    return try {
      requireUserId()
      val newStatus = if (accept) ChatRequestStatus.ACCEPTED.wire else ChatRequestStatus.DECLINED.wire
      db.collection("chats").document(chatId)
        .update(
          mapOf(
            "status" to newStatus,
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

  override suspend fun sendMessage(
    chat: ChatThreadPreview,
    myProfile: UserProfile,
    content: MessageContent,
    replyQuote: ReplyQuote?,
  ): Result<Unit> {
    val messageId = UUID.randomUUID().toString().replace("-", "")
    val myUid = myProfile.uid
    val nowMs = System.currentTimeMillis()
    val (typeStr, plainText, moodId) = when (content) {
      is MessageContent.Text -> Triple("text", content.body.trim(), null)
      is MessageContent.Mood -> Triple("mood", content.caption.trim(), content.mood.id)
    }

    val optimisticEntity = LocalMessageEntity(
      id = messageId,
      chatId = chat.chatId,
      ownerUid = myUid,
      senderUid = myUid,
      recipientUid = chat.partnerUid,
      type = typeStr,
      textBody = plainText,
      moodId = moodId,
      replyToMessageId = replyQuote?.messageId,
      replyToSenderName = replyQuote?.senderName,
      replyToSnippet = replyQuote?.snippet,
      status = MessageStatus.SENDING.wire,
      deletedForMe = false,
      deletedForEveryone = false,
      reactionsJson = "{}",
      timestampMs = nowMs,
    )
    localMessageDao.upsert(optimisticEntity)

    return dispatchEncryptedMessage(
      chat = chat,
      myProfile = myProfile,
      messageId = messageId,
      typeStr = typeStr,
      plainText = plainText,
      moodId = moodId,
      replyQuote = replyQuote,
      nowMs = nowMs,
    )
  }

  override suspend fun retryFailedMessage(
    chat: ChatThreadPreview,
    myProfile: UserProfile,
    messageId: String,
  ): Result<Unit> {
    val myUid = myProfile.uid
    val existing = localMessageDao.getById(messageId, myUid)
      ?: return Result.failure(IllegalStateException("Message is no longer available to retry."))
    if (existing.senderUid != myUid) {
      return Result.failure(IllegalStateException("Only outgoing messages can be retried."))
    }

    localMessageDao.updateStatus(messageId, myUid, MessageStatus.SENDING.wire)
    val quote = if (!existing.replyToMessageId.isNullOrBlank()) {
      ReplyQuote(
        messageId = existing.replyToMessageId,
        senderName = existing.replyToSenderName ?: "",
        snippet = existing.replyToSnippet ?: "",
      )
    } else null

    return dispatchEncryptedMessage(
      chat = chat,
      myProfile = myProfile,
      messageId = messageId,
      typeStr = existing.type,
      plainText = existing.textBody,
      moodId = existing.moodId,
      replyQuote = quote,
      nowMs = System.currentTimeMillis(),
    )
  }

  private suspend fun dispatchEncryptedMessage(
    chat: ChatThreadPreview,
    myProfile: UserProfile,
    messageId: String,
    typeStr: String,
    plainText: String,
    moodId: String?,
    replyQuote: ReplyQuote?,
    nowMs: Long,
  ): Result<Unit> {
    val path = "chats/${chat.chatId}/messages/$messageId"
    val myUid = myProfile.uid
    return try {
      val signedInUid = requireUserId()
      check(signedInUid == myUid) { "User mismatch" }
      require(chat.status == ChatRequestStatus.ACCEPTED && !chat.isBlockedAny) {
        "Messaging is currently paused for this chat."
      }
      require(plainText.length <= MioConstants.MAX_MESSAGE_LENGTH) {
        "Message exceeds ${MioConstants.MAX_MESSAGE_LENGTH} characters."
      }
      if (typeStr == "text") {
        require(plainText.isNotEmpty()) { "Cannot send an empty message." }
      }

      val payloadJson = JSONObject().apply {
        put("text", plainText)
        if (replyQuote != null) {
          put("replyToId", replyQuote.messageId)
          put("replyToSender", replyQuote.senderName)
          put("replyToSnippet", replyQuote.snippet)
        }
      }.toString()

      val encrypted = cryptoService.encrypt(
        plaintext = payloadJson,
        recipientPublicKeyBase64 = chat.partnerPublicKey,
        myUid = myUid,
      )
      val expiresDate = Date(nowMs + TimeUnit.DAYS.toMillis(MioConstants.MESSAGE_TTL_DAYS))
      val pair = listOf(myUid, chat.partnerUid).sorted()

      val msgPayload = buildMap<String, Any> {
        put("chatId", chat.chatId)
        put("senderUid", myUid)
        put("recipientUid", chat.partnerUid)
        put("participantUids", pair)
        put("type", typeStr)
        put("ciphertext", encrypted.ciphertext)
        put("nonce", encrypted.nonce)
        if (moodId != null) put("moodId", moodId)
        put("status", MessageStatus.SENT.wire)
        put("deletedFor", emptyList<String>())
        put("deletedForEveryone", false)
        put("reactions", emptyMap<String, String>())
        put("createdAt", FieldValue.serverTimestamp())
        put("updatedAt", FieldValue.serverTimestamp())
        put("expiresAt", Timestamp(expiresDate))
      }

      val chatDocRef = db.collection("chats").document(chat.chatId)
      val msgDocRef = chatDocRef.collection("messages").document(messageId)

      val batch = db.batch()
      batch.set(msgDocRef, msgPayload)
      batch.update(
        chatDocRef,
        mapOf(
          "lastMessageCiphertext" to encrypted.ciphertext,
          "lastMessageNonce" to encrypted.nonce,
          "lastMessageSenderUid" to myUid,
          "lastMessageType" to typeStr,
          "lastMessageStatus" to MessageStatus.SENT.wire,
          "lastMessageAt" to FieldValue.serverTimestamp(),
          "unreadCounts.${chat.partnerUid}" to FieldValue.increment(1L),
          "typingBy.$myUid" to false,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.commit().await()
      localMessageDao.updateStatus(messageId, myUid, MessageStatus.SENT.wire)
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      localMessageDao.updateStatus(messageId, myUid, MessageStatus.FAILED.wire)
      handleFirestoreError(e, OperationType.CREATE, path)
      Result.failure(e)
    }
  }

  override suspend fun markChatAsRead(
    chatId: String,
    myUid: String,
    unreadMessageIds: List<String>,
    updateLastMessagePreviewStatus: Boolean,
  ) {
    try {
      if (auth.currentUser?.uid != myUid) return
      val chatRef = db.collection("chats").document(chatId)
      val batch = db.batch()

      for (msgId in unreadMessageIds.take(MioConstants.MESSAGES_QUERY_LIMIT.toInt())) {
        batch.update(
          chatRef.collection("messages").document(msgId),
          mapOf(
            "status" to MessageStatus.READ.wire,
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        )
      }

      val chatUpdates = mutableMapOf<String, Any>(
        "unreadCounts.$myUid" to 0L,
        "updatedAt" to FieldValue.serverTimestamp(),
      )
      if (unreadMessageIds.isNotEmpty() && updateLastMessagePreviewStatus) {
        chatUpdates["lastMessageStatus"] = MessageStatus.READ.wire
      }
      batch.update(chatRef, chatUpdates)
      batch.commit().await()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, "chats/$chatId")
    }
  }

  override suspend fun setTyping(chatId: String, myUid: String, typing: Boolean) {
    try {
      if (auth.currentUser?.uid != myUid) return
      db.collection("chats").document(chatId)
        .update(
          mapOf(
            "typingBy.$myUid" to typing,
            "updatedAt" to FieldValue.serverTimestamp(),
          ),
        ).await()
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      // Typing indicator updates are best-effort
    }
  }

  override suspend fun toggleReaction(
    chatId: String,
    messageId: String,
    myUid: String,
    emoji: String,
  ): Result<Unit> {
    val path = "chats/$chatId/messages/$messageId"
    return try {
      requireUserId()
      val msgRef = db.collection("chats").document(chatId).collection("messages").document(messageId)
      val snap = msgRef.get().await()
      if (!snap.exists()) {
        return Result.failure(IllegalStateException("Wait until this message is sent before reacting."))
      }
      val currentMap = (snap.get("reactions") as? Map<*, *>)
        ?.entries
        ?.mapNotNull { (k, v) -> if (k is String && v is String) k to v else null }
        ?.toMap()
        ?.toMutableMap()
        ?: mutableMapOf()

      if (currentMap[myUid] == emoji) {
        currentMap.remove(myUid)
      } else {
        currentMap[myUid] = emoji
      }

      msgRef.update(
        mapOf(
          "reactions" to currentMap,
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

  override suspend fun deleteMessageForMe(chatId: String, messageId: String, myUid: String): Result<Unit> {
    val path = "chats/$chatId/messages/$messageId"
    return try {
      requireUserId()
      localMessageDao.markDeletedForMe(messageId, myUid)
      try {
        db.collection("chats").document(chatId)
          .collection("messages").document(messageId)
          .update(
            mapOf(
              "deletedFor" to FieldValue.arrayUnion(myUid),
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          ).await()
      } catch (e: FirebaseFirestoreException) {
        // Local-only failed messages have no remote doc; treat NOT_FOUND as cleanly deleted
        if (e.code != FirebaseFirestoreException.Code.NOT_FOUND) throw e
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  override suspend fun deleteMessageForEveryone(chatId: String, messageId: String, myUid: String): Result<Unit> {
    val path = "chats/$chatId/messages/$messageId"
    return try {
      requireUserId()
      val msgRef = db.collection("chats").document(chatId)
        .collection("messages").document(messageId)
      try {
        msgRef.update(
          mapOf(
            "deletedForEveryone" to true,
            "ciphertext" to "",
            "nonce" to "",
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        ).await()
      } catch (e: FirebaseFirestoreException) {
        if (e.code == FirebaseFirestoreException.Code.NOT_FOUND) {
          localMessageDao.markDeletedForMe(messageId, myUid)
        } else {
          throw e
        }
      }
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, path)
      Result.failure(e)
    }
  }

  override suspend fun blockUser(
    chatId: String,
    myUid: String,
    partnerUid: String,
    partnerUsername: String,
  ): Result<Unit> {
    val path = "chats/$chatId"
    return try {
      requireUserId()
      val batch = db.batch()
      val blockRef = db.collection("users").document(myUid).collection("blocks").document(partnerUid)
      batch.set(
        blockRef,
        mapOf(
          "blockedUid" to partnerUid,
          "blockedUsername" to partnerUsername,
          "createdAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.update(
        db.collection("chats").document(chatId),
        mapOf(
          "blockedBy" to FieldValue.arrayUnion(myUid),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path)
      Result.failure(e)
    }
  }

  override suspend fun unblockUser(chatId: String, myUid: String, partnerUid: String): Result<Unit> {
    val path = "chats/$chatId"
    return try {
      requireUserId()
      val batch = db.batch()
      batch.delete(db.collection("users").document(myUid).collection("blocks").document(partnerUid))
      batch.update(
        db.collection("chats").document(chatId),
        mapOf(
          "blockedBy" to FieldValue.arrayRemove(myUid),
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      )
      batch.commit().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, path)
      Result.failure(e)
    }
  }

  override suspend fun submitReport(
    myUid: String,
    reportedUid: String,
    chatId: String,
    reason: ReportReason,
    excerpt: String?,
  ): Result<Unit> {
    val reportId = UUID.randomUUID().toString().replace("-", "")
    val path = "reports/$reportId"
    return try {
      requireUserId()
      val now = System.currentTimeMillis()
      if (now - lastReportAtMs < MioConstants.REPORT_COOLDOWN_MS) {
        return Result.failure(IllegalStateException("Your previous report was just sent. Please wait a moment before submitting another."))
      }
      val cleanExcerpt = excerpt?.trim()?.take(MioConstants.MAX_REPORT_EXCERPT_LENGTH)
      val payload = buildMap<String, Any> {
        put("reporterUid", myUid)
        put("reportedUid", reportedUid)
        put("chatId", chatId)
        put("reason", reason.wire)
        if (!cleanExcerpt.isNullOrBlank()) {
          put("excerpt", cleanExcerpt)
        }
        put("createdAt", FieldValue.serverTimestamp())
      }
      db.collection("reports").document(reportId).set(payload).await()
      lastReportAtMs = now
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, path)
      Result.failure(e)
    }
  }

  override suspend fun cleanupExpiredRelayDocs(myUid: String) {
    try {
      if (auth.currentUser?.uid != myUid) return
      val nowMs = System.currentTimeMillis()
      // Recover any stale SENDING rows interrupted by process death or offline kill
      localMessageDao.markStaleSendingFailed(
        ownerUid = myUid,
        sendingStatus = MessageStatus.SENDING.wire,
        failedStatus = MessageStatus.FAILED.wire,
        cutoffMs = nowMs - MioConstants.STALE_SENDING_MS,
      )

      // Client-side TTL cleanup (no billing TTL required) for user's own expired outgoing relay docs
      val chatsSnap = db.collection("chats")
        .whereArrayContains("participantUids", myUid)
        .limit(MioConstants.CHATS_QUERY_LIMIT)
        .get()
        .await()
      for (chatDoc in chatsSnap.documents) {
        val msgSnap = chatDoc.reference.collection("messages")
          .whereArrayContains("participantUids", myUid)
          .limit(MioConstants.MESSAGES_QUERY_LIMIT)
          .get()
          .await()
        val expiredDocs = msgSnap.documents.filter { doc ->
          val sender = doc.getString("senderUid")
          val expiresAt = doc.getTimestamp("expiresAt")?.toDate()?.time ?: Long.MAX_VALUE
          sender == myUid && expiresAt < nowMs
        }
        if (expiredDocs.isNotEmpty()) {
          val batch = db.batch()
          expiredDocs.forEach { batch.delete(it.reference) }
          batch.commit().await()
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "Relay cleanup skipped: ${e.javaClass.simpleName}")
    }
  }

  private suspend fun toChatPreview(doc: ChatThreadDoc, myUid: String): ChatThreadPreview? {
    val partnerUid = doc.participantUids.firstOrNull { it != myUid } ?: return null
    val partnerUsername = doc.participantUsernames[partnerUid] ?: "partner"
    val partnerDisplay = doc.participantDisplayNames[partnerUid] ?: partnerUsername
    val partnerPubKey = doc.participantPublicKeys[partnerUid] ?: ""
    val status = ChatRequestStatus.fromWire(doc.status)
    val unread = (doc.unreadCounts[myUid] ?: 0L).toInt()
    val partnerTyping = doc.typingBy[partnerUid] == true

    val lastMessagePreview = when {
      status == ChatRequestStatus.PENDING && doc.initiatorUid == myUid -> MioCopy.PENDING_OUTGOING_BANNER
      status == ChatRequestStatus.PENDING -> "@$partnerUsername ${MioCopy.PENDING_INCOMING_BANNER}"
      status == ChatRequestStatus.DECLINED -> "Invitation declined"
      doc.lastMessageCiphertext.isBlank() || doc.lastMessageNonce.isBlank() -> "Say hello in your private room"
      else -> {
        try {
          val jsonStr = cryptoService.decrypt(
            doc.lastMessageCiphertext,
            doc.lastMessageNonce,
            partnerPubKey,
            myUid,
          )
          val text = parseDecryptedPayload(jsonStr).text
          if (doc.lastMessageType == "mood") {
            if (text.isNotBlank()) "Mood cat · $text" else "Sent a mood cat"
          } else {
            text.ifBlank { "Encrypted message" }
          }
        } catch (_: Exception) {
          "Encrypted message"
        }
      }
    }

    val mipStatus = MipStatus.fromWire(doc.mipStatus)
    val mipNoteDecrypted = if (doc.mipCiphertext.isNotBlank() && doc.mipNonce.isNotBlank() && partnerPubKey.isNotBlank()) {
      try {
        cryptoService.decrypt(doc.mipCiphertext, doc.mipNonce, partnerPubKey, myUid)
      } catch (_: Exception) {
        ""
      }
    } else ""

    return ChatThreadPreview(
      chatId = doc.id,
      partnerUid = partnerUid,
      partnerUsername = partnerUsername,
      partnerDisplayName = partnerDisplay,
      partnerPublicKey = partnerPubKey,
      status = status,
      initiatorUid = doc.initiatorUid,
      blockedByMe = myUid in doc.blockedBy,
      blockedByPartner = partnerUid in doc.blockedBy,
      partnerTyping = partnerTyping,
      unreadCount = unread,
      lastMessagePreview = lastMessagePreview,
      lastMessageSenderUid = doc.lastMessageSenderUid,
      lastMessageStatus = doc.lastMessageStatus.takeIf { it.isNotBlank() }?.let { MessageStatus.fromWire(it) },
      lastMessageTimeMs = (doc.lastMessageAt ?: doc.updatedAt ?: doc.createdAt)?.toDate()?.time ?: 0L,
      mipStatus = mipStatus,
      mipSenderUid = doc.mipSenderUid,
      mipNoteDecrypted = mipNoteDecrypted,
      mipUpdatedTimeMs = doc.mipUpdatedAt?.toDate()?.time ?: 0L,
    )
  }

  private fun entityToDomain(entity: LocalMessageEntity): ChatMessage {
    val content = if (entity.type == "mood") {
      val mood = CatMood.fromId(entity.moodId) ?: CatMood.HAPPY
      MessageContent.Mood(mood, entity.textBody)
    } else {
      MessageContent.Text(entity.textBody)
    }
    val quote = if (!entity.replyToMessageId.isNullOrBlank()) {
      ReplyQuote(
        messageId = entity.replyToMessageId,
        senderName = entity.replyToSenderName ?: "",
        snippet = entity.replyToSnippet ?: "",
      )
    } else null

    val reactionsMap = mutableMapOf<String, String>()
    try {
      val json = JSONObject(entity.reactionsJson)
      val keys = json.keys()
      while (keys.hasNext()) {
        val k = keys.next()
        reactionsMap[k] = json.optString(k)
      }
    } catch (_: Exception) {
    }

    return ChatMessage(
      id = entity.id,
      chatId = entity.chatId,
      senderUid = entity.senderUid,
      recipientUid = entity.recipientUid,
      content = content,
      replyQuote = quote,
      status = MessageStatus.fromWire(entity.status),
      deletedForEveryone = entity.deletedForEveryone,
      reactions = reactionsMap,
      timestampMs = entity.timestampMs,
    )
  }

  private data class ParsedPayload(
    val text: String,
    val replyToId: String?,
    val replyToSender: String?,
    val replyToSnippet: String?,
  )

  private fun parseDecryptedPayload(raw: String): ParsedPayload {
    return try {
      val obj = JSONObject(raw)
      ParsedPayload(
        text = obj.optString("text", ""),
        replyToId = obj.optString("replyToId").takeIf { it.isNotBlank() },
        replyToSender = obj.optString("replyToSender").takeIf { it.isNotBlank() },
        replyToSnippet = obj.optString("replyToSnippet").takeIf { it.isNotBlank() },
      )
    } catch (_: Exception) {
      ParsedPayload(raw, null, null, null)
    }
  }

  private companion object {
    const val TAG = "ChatRepo"
    const val UNDECRYPTED_PLACEHOLDER = "[Encrypted on another device]"
  }
}
