package com.example.core.feedback

import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.example.R
import com.example.core.calls.CallSessionHolder
import com.example.core.config.MioConstants
import com.example.core.crypto.EcdhAesCryptoService
import com.example.data.local.LocalMessageEntity
import com.example.data.local.MioDatabase
import com.example.data.local.MioPreferences
import com.example.data.model.CallStatus
import com.example.data.model.CallTransport
import com.example.data.model.CallType
import com.example.data.model.ChatMessageDoc
import com.example.data.model.ChatThreadDoc
import com.example.data.model.IncomingCallInvite
import com.example.data.model.MessageStatus
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

/**
 * Handles FCM token refreshes, incoming chat message pushes (saving relay docs locally
 * when woken in background), and high-priority incoming/ended call pushes.
 */
class MioFirebaseMessagingService : FirebaseMessagingService() {

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override fun onNewToken(token: String) {
    super.onNewToken(token)
    if (token.isBlank() || token.length > MioConstants.MAX_FCM_TOKEN_LENGTH) return

    serviceScope.launch {
      runCatching {
        MioPreferences(applicationContext).setPendingFcmToken(token)
      }
      val uid = Firebase.auth.currentUser?.uid ?: return@launch
      runCatching {
        val db = FirebaseFirestore.getInstance(applicationContext.getString(R.string.firestore_database_id))
        db.collection("users").document(uid)
          .update(
            mapOf(
              "fcmToken" to token,
              "updatedAt" to FieldValue.serverTimestamp(),
            ),
          )
          .await()
      }.onFailure { err ->
        Log.w(TAG, "Token refresh sync skipped: ${err.javaClass.simpleName}")
      }
    }
  }

  override fun onMessageReceived(message: RemoteMessage) {
    super.onMessageReceived(message)
    val data = message.data
    val pushType = data["type"].orEmpty()

    when (pushType) {
      MioConstants.FCM_TYPE_INCOMING_CALL -> {
        handleIncomingCallPush(data)
        return
      }
      MioConstants.FCM_TYPE_CALL_ENDED -> {
        val callId = data["callId"]?.takeIf { MioConstants.SAFE_ID_REGEX.matches(it) }
        MioNotificationHelper(applicationContext).cancelIncomingCallNotification(callId)
        return
      }
    }

    val rawChatId = data["chatId"] ?: data["open_chat_id"]
    val chatId = rawChatId?.takeIf { MioConstants.SAFE_ID_REGEX.matches(it) }
    val messageId = data["messageId"]?.takeIf { MioConstants.SAFE_ID_REGEX.matches(it) }
    val senderId = data["senderId"]
    val currentUid = Firebase.auth.currentUser?.uid
    if (!senderId.isNullOrBlank() && senderId == currentUid) {
      return
    }
    if (ActiveChatTracker.isActivelyViewingChat(chatId)) {
      return
    }

    val senderDisplay = data["senderDisplayName"]?.trim()?.takeIf { it.isNotBlank() }
      ?: message.notification?.title?.trim()?.takeIf { it.isNotBlank() }
      ?: data["senderName"]?.trim()?.takeIf { it.isNotBlank() }
    val senderUsername = data["senderUsername"]?.trim()?.takeIf { it.isNotBlank() }
    val sentAtMs = data["sentAtMs"]?.toLongOrNull() ?: 0L

    val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
    val wakeLock = runCatching {
      pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Mio:FcmRelayWakeLock")?.apply {
        setReferenceCounted(false)
        acquire(FCM_WAKELOCK_MS)
      }
    }.getOrNull()

    try {
      runBlocking(Dispatchers.IO) {
        val settings = runCatching {
          MioPreferences(applicationContext).settings.first()
        }.getOrNull()
        val enabled = settings?.notificationsEnabled ?: true
        val isMutedNow = (settings?.muteAllUntilMs ?: 0L) > System.currentTimeMillis()
        if (enabled && !isMutedNow) {
          MioNotificationHelper(applicationContext).showIncomingChatMessage(
            chatId = chatId,
            senderDisplayName = senderDisplay,
            senderUsername = senderUsername,
            messageId = messageId,
            messageTimestampMs = sentAtMs,
            isFromFcm = true,
          )
        }

        if (currentUid != null && chatId != null && messageId != null) {
          withTimeoutOrNull(FCM_PROCESSING_TIMEOUT_MS) {
            deliverAndDeleteRelayDoc(
              currentUid = currentUid,
              chatId = chatId,
              messageId = messageId,
            )
          }
        }
      }
    } finally {
      runCatching {
        if (wakeLock?.isHeld == true) wakeLock.release()
      }
    }
  }

  private fun handleIncomingCallPush(data: Map<String, String>) {
    val callId = data["callId"]?.takeIf { MioConstants.SAFE_ID_REGEX.matches(it) } ?: return
    val callerId = data["callerId"]?.takeIf { MioConstants.SAFE_ID_REGEX.matches(it) } ?: return
    val chatId = data["chatId"]?.takeIf { MioConstants.SAFE_ID_REGEX.matches(it) }.orEmpty()
    val currentUid = Firebase.auth.currentUser?.uid
    if (currentUid != null && callerId == currentUid) return

    val sentAtMs = data["createdAtMs"]?.toLongOrNull()
      ?: data["sentAtMs"]?.toLongOrNull()
      ?: System.currentTimeMillis()
    if (sentAtMs > 0L && (System.currentTimeMillis() - sentAtMs) > MioConstants.CALL_STALE_INVITE_MS) {
      return
    }

    if (CallSessionHolder.activeManager?.activeCall?.value != null) {
      return
    }

    val settings = runBlocking(Dispatchers.IO) {
      runCatching { MioPreferences(applicationContext).settings.first() }.getOrNull()
    }
    val notificationsEnabled = settings?.notificationsEnabled ?: true
    val callNotificationsEnabled = settings?.callNotificationsEnabled ?: true
    val isMutedNow = (settings?.muteAllUntilMs ?: 0L) > System.currentTimeMillis()
    val whoCanCallMe = settings?.whoCanCallMe ?: "accepted"
    if (!notificationsEnabled || !callNotificationsEnabled || isMutedNow || whoCanCallMe == "nobody") {
      return
    }

    // In foreground, the live signaling listener presents IncomingCallOverlay + ringtone directly.
    if (ActiveChatTracker.isAppInForeground) {
      return
    }

    val callerName = MioNotificationHelper.resolveSenderTitle(
      displayName = data["callerName"],
      username = data["callerUsername"],
    )
    val callerUsername = data["callerUsername"]?.trim().orEmpty()
    val callType = CallType.fromWire(data["callType"])
    val transport = if (data["transport"] == CallTransport.FIRESTORE.name) {
      CallTransport.FIRESTORE
    } else {
      CallTransport.RTDB
    }

    MioNotificationHelper(applicationContext).showIncomingCallNotification(
      IncomingCallInvite(
        callId = callId,
        chatId = chatId,
        callerId = callerId,
        callerName = callerName,
        callerUsername = callerUsername,
        type = callType,
        status = CallStatus.RINGING,
        createdAtMs = sentAtMs,
        transport = transport,
      ),
    )
  }

  private suspend fun deliverAndDeleteRelayDoc(
    currentUid: String,
    chatId: String,
    messageId: String,
  ) {
    runCatching {
      val db = FirebaseFirestore.getInstance(applicationContext.getString(R.string.firestore_database_id))
      val chatRef = db.collection("chats").document(chatId)
      val msgRef = chatRef.collection("messages").document(messageId)

      val chatSnap = chatRef.get().await()
      val msgSnap = msgRef.get().await()
      if (!chatSnap.exists() || !msgSnap.exists()) return

      val chatDoc = chatSnap.toObject(
        ChatThreadDoc::class.java,
        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
      ) ?: return
      val msgDoc = msgSnap.toObject(
        ChatMessageDoc::class.java,
        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
      ) ?: return

      if (currentUid !in chatDoc.participantUids) return
      if (chatDoc.blockedBy.isNotEmpty()) return
      if (msgDoc.recipientUid != currentUid) return
      if (currentUid in msgDoc.deletedFor) return

      val partnerPubKey = chatDoc.participantPublicKeys[msgDoc.senderUid].orEmpty()
      var textBody = ""
      var replyId: String? = null
      var replySender: String? = null
      var replySnippet: String? = null

      if (!msgDoc.deletedForEveryone) {
        if (msgDoc.ciphertext.isBlank() || msgDoc.nonce.isBlank() || partnerPubKey.isBlank()) {
          return
        }
        val jsonStr = EcdhAesCryptoService(applicationContext).decrypt(
          ciphertextBase64 = msgDoc.ciphertext,
          nonceBase64 = msgDoc.nonce,
          peerPublicKeyBase64 = partnerPubKey,
          myUid = currentUid,
        )
        val parsed = runCatching { JSONObject(jsonStr) }.getOrNull()
        if (parsed != null) {
          textBody = parsed.optString("text", "")
          replyId = parsed.optString("replyToId").takeIf { it.isNotBlank() }
          replySender = parsed.optString("replyToSender").takeIf { it.isNotBlank() }
          replySnippet = parsed.optString("replyToSnippet").takeIf { it.isNotBlank() }
        } else {
          textBody = jsonStr
        }
      }

      val resolvedStatus = if (!msgDoc.deletedForEveryone && msgDoc.status == MessageStatus.SENT.wire) {
        MessageStatus.DELIVERED.wire
      } else {
        msgDoc.status
      }

      val timestampMs = msgDoc.createdAt?.toDate()?.time ?: System.currentTimeMillis()
      val entity = LocalMessageEntity(
        id = msgDoc.id.ifBlank { messageId },
        chatId = chatId,
        ownerUid = currentUid,
        senderUid = msgDoc.senderUid,
        recipientUid = msgDoc.recipientUid,
        type = msgDoc.type,
        textBody = textBody,
        moodId = msgDoc.moodId,
        replyToMessageId = replyId,
        replyToSenderName = replySender,
        replyToSnippet = replySnippet,
        status = resolvedStatus,
        deletedForMe = false,
        deletedForEveryone = msgDoc.deletedForEveryone,
        reactionsJson = if (msgDoc.deletedForEveryone) "{}" else JSONObject(msgDoc.reactions).toString(),
        timestampMs = timestampMs,
      )

      MioDatabase.getInstance(applicationContext).localMessageDao().upsert(entity)

      if (!msgDoc.deletedForEveryone) {
        if (msgDoc.status == MessageStatus.SENT.wire) {
          runCatching {
            msgRef.update(
              mapOf(
                "status" to MessageStatus.DELIVERED.wire,
                "updatedAt" to FieldValue.serverTimestamp(),
              ),
            ).await()
          }
          if (chatDoc.lastMessageSenderUid == msgDoc.senderUid &&
            chatDoc.lastMessageStatus == MessageStatus.SENT.wire
          ) {
            runCatching {
              chatRef.update(
                mapOf(
                  "lastMessageStatus" to MessageStatus.DELIVERED.wire,
                  "updatedAt" to FieldValue.serverTimestamp(),
                ),
              ).await()
            }
          }
        }
        runCatching { msgRef.delete().await() }
      }
    }.onFailure { err ->
      Log.w(TAG, "FCM relay delivery skipped: ${err.javaClass.simpleName}")
    }
  }

  override fun onDestroy() {
    serviceScope.cancel()
    super.onDestroy()
  }

  private companion object {
    const val TAG = "MioFCM"
    const val FCM_PROCESSING_TIMEOUT_MS = 8_000L
    const val FCM_WAKELOCK_MS = 10_000L
  }
}
