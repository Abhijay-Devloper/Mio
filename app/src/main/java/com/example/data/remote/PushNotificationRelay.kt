package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import com.example.core.config.MioConstants
import com.example.data.model.CallType
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

interface PushNotificationRelay {
  suspend fun notifyNewMessage(
    chatId: String,
    messageId: String,
    recipientUid: String,
    senderDisplayName: String,
    senderUsername: String,
  )

  suspend fun notifyIncomingCall(
    callId: String,
    chatId: String,
    receiverUid: String,
    callerName: String,
    callerUsername: String,
    callType: CallType,
  )

  suspend fun notifyCallEnded(
    callId: String,
    receiverUid: String,
  )
}

/**
 * Triggers high-priority FCM push notifications via the user's configured serverless
 * endpoint (e.g. Vercel /api/notify) so Firebase Spark plan users still receive
 * instant alerts and call rings when the screen is off or app is closed.
 *
 * Strictly sends routing IDs and sender display metadata only—never message plaintext or keys.
 */
class OkHttpPushNotificationRelay(
  private val authProvider: (() -> FirebaseAuth?)? = null,
  private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(MioConstants.PUSH_RELAY_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    .readTimeout(MioConstants.PUSH_RELAY_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    .callTimeout(MioConstants.PUSH_RELAY_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    .build(),
) : PushNotificationRelay {

  override suspend fun notifyNewMessage(
    chatId: String,
    messageId: String,
    recipientUid: String,
    senderDisplayName: String,
    senderUsername: String,
  ) {
    val payload = JSONObject().apply {
      put("type", MioConstants.FCM_TYPE_NEW_MESSAGE)
      put("chatId", chatId)
      put("messageId", messageId)
      put("recipientUid", recipientUid)
      put("senderDisplayName", senderDisplayName.take(60))
      put("senderUsername", senderUsername.take(32))
    }
    postPushEvent(payload)
  }

  override suspend fun notifyIncomingCall(
    callId: String,
    chatId: String,
    receiverUid: String,
    callerName: String,
    callerUsername: String,
    callType: CallType,
  ) {
    val payload = JSONObject().apply {
      put("type", MioConstants.FCM_TYPE_INCOMING_CALL)
      put("callId", callId)
      put("chatId", chatId)
      put("receiverUid", receiverUid)
      put("callerName", callerName.take(60))
      put("callerUsername", callerUsername.take(32))
      put("callType", callType.wire)
    }
    postPushEvent(payload)
  }

  override suspend fun notifyCallEnded(
    callId: String,
    receiverUid: String,
  ) {
    if (!MioConstants.SAFE_ID_REGEX.matches(callId) || !MioConstants.SAFE_ID_REGEX.matches(receiverUid)) {
      return
    }
    val payload = JSONObject().apply {
      put("type", MioConstants.FCM_TYPE_CALL_ENDED)
      put("callId", callId)
      put("receiverUid", receiverUid)
    }
    postPushEvent(payload)
  }

  private suspend fun postPushEvent(payload: JSONObject) = withContext(Dispatchers.IO) {
    val pushUrl = readBuildConfigField("BACKEND_PUSH_URL")
      ?.takeIf { it.startsWith("https://") }
      ?: return@withContext

    runCatching {
      val auth = authProvider?.invoke() ?: runCatching { Firebase.auth }.getOrNull()
      val idToken = auth?.currentUser?.getIdToken(false)?.await()?.token
      if (idToken.isNullOrBlank()) return@withContext

      val body = payload.toString().toRequestBody(JSON_MEDIA_TYPE)
      val request = Request.Builder()
        .url(pushUrl)
        .header("Authorization", "Bearer $idToken")
        .post(body)
        .build()

      client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          Log.w(TAG, "Push relay HTTP ${response.code}")
        }
      }
    }.onFailure { err ->
      Log.w(TAG, "Push relay skipped: ${err.javaClass.simpleName}")
    }
  }

  private fun readBuildConfigField(name: String): String? {
    return runCatching {
      BuildConfig::class.java.getField(name).get(null) as? String
    }.getOrNull()?.trim()
  }

  private companion object {
    const val TAG = "MioPushRelay"
    val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
  }
}
