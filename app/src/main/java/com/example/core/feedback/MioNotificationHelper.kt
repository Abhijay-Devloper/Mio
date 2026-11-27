package com.example.core.feedback

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.core.calls.CallActionReceiver
import com.example.core.config.MioConstants
import com.example.core.config.MioCopy
import com.example.data.model.IncomingCallInvite

/**
 * Notification helper for incoming chat alerts, FCM push notifications,
 * incoming CallStyle ring notifications, and ongoing call foreground notifications.
 * Enforces:
 * - Active-chat suppression via ActiveChatTracker
 * - Sender title: displayName only when set, otherwise username
 * - Body: "Sent you a message !"
 * - Per-chat popup cap: alerts/pops up 4 times on IMPORTANCE_HIGH, then switches to
 *   IMPORTANCE_LOW silent channel so subsequent messages sit quietly in the shade.
 */
class MioNotificationHelper(private val context: Context) {

  private val appContext = context.applicationContext
  private val prefs by lazy {
    appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  }

  fun ensureChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        ?: return
      val msgSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
      val msgAudioAttributes = AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .build()

      val alertChannel = NotificationChannel(
        CHANNEL_ID,
        "Chat Messages",
        NotificationManager.IMPORTANCE_HIGH,
      ).apply {
        description = "Alerts when someone sends you a new message."
        setSound(msgSoundUri, msgAudioAttributes)
        enableVibration(true)
      }

      val silentChannel = NotificationChannel(
        CHANNEL_ID_SILENT,
        "Chat Messages (Silent)",
        NotificationManager.IMPORTANCE_LOW,
      ).apply {
        description = "Quiet notifications after multiple alerts from the same chat."
        setSound(null, null)
        enableVibration(false)
      }

      val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ?: msgSoundUri
      val ringAudioAttributes = AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
        .build()

      val incomingCallChannel = NotificationChannel(
        CHANNEL_INCOMING_CALLS,
        "Incoming Calls",
        NotificationManager.IMPORTANCE_HIGH,
      ).apply {
        description = "Full-screen ring alerts for incoming voice and video calls."
        setSound(ringtoneUri, ringAudioAttributes)
        enableVibration(true)
        vibrationPattern = longArrayOf(0L, 800L, 600L, 800L)
        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
      }

      val ongoingCallChannel = NotificationChannel(
        CHANNEL_ONGOING_CALLS,
        "Ongoing Calls",
        NotificationManager.IMPORTANCE_LOW,
      ).apply {
        description = "Keeps active voice and video calls running in the background."
        setSound(null, null)
        enableVibration(false)
        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
      }

      manager.createNotificationChannels(
        listOf(alertChannel, silentChannel, incomingCallChannel, ongoingCallChannel),
      )
    }
  }

  fun showIncomingChatMessage(
    chatId: String?,
    senderDisplayName: String?,
    senderUsername: String?,
    messageId: String? = null,
    messageTimestampMs: Long = 0L,
    isFromFcm: Boolean = false,
  ) {
    val safeChatId = chatId?.takeIf { MioConstants.SAFE_ID_REGEX.matches(it) }
    if (ActiveChatTracker.isActivelyViewingChat(safeChatId)) {
      return
    }

    if (!hasPostNotificationPermission()) return

    val resolvedTitle = resolveSenderTitle(senderDisplayName, senderUsername)
    val stateKey = safeChatId ?: "fallback_${resolvedTitle.hashCode()}"

    val nextCount: Int
    synchronized(notifyLock) {
      val nowWallMs = System.currentTimeMillis()
      val prevMsgId = prefs.getString("$KEY_LAST_MSG_ID_PREFIX$stateKey", null)
      val prevMsgTs = prefs.getLong("$KEY_LAST_MSG_TS_PREFIX$stateKey", 0L)
      val prevFcmWallMs = prefs.getLong("$KEY_LAST_FCM_WALL_PREFIX$stateKey", 0L)

      if (!messageId.isNullOrBlank() && prevMsgId == messageId) {
        return
      }
      if (!isFromFcm) {
        if (prevFcmWallMs > 0L && (nowWallMs - prevFcmWallMs) < CROSS_SOURCE_DEDUP_MS &&
          messageTimestampMs > 0L && messageTimestampMs <= prevMsgTs + 5_000L
        ) {
          return
        }
        if (messageTimestampMs > 0L && messageTimestampMs <= prevMsgTs) {
          return
        }
      }

      val currentCount = prefs.getInt("$KEY_POPUP_COUNT_PREFIX$stateKey", 0)
      nextCount = currentCount + 1

      val editor = prefs.edit()
        .putInt("$KEY_POPUP_COUNT_PREFIX$stateKey", nextCount)
      if (!messageId.isNullOrBlank()) {
        editor.putString("$KEY_LAST_MSG_ID_PREFIX$stateKey", messageId)
      }
      if (messageTimestampMs > prevMsgTs) {
        editor.putLong("$KEY_LAST_MSG_TS_PREFIX$stateKey", messageTimestampMs)
      } else if (isFromFcm && prevMsgTs == 0L) {
        editor.putLong("$KEY_LAST_MSG_TS_PREFIX$stateKey", nowWallMs)
      }
      if (isFromFcm) {
        editor.putLong("$KEY_LAST_FCM_WALL_PREFIX$stateKey", nowWallMs)
      }
      editor.apply()
    }

    val isSilent = nextCount > MioConstants.MAX_NOTIFICATION_POPUPS_PER_CHAT
    postNotification(
      title = resolvedTitle,
      body = MioCopy.NOTIFICATION_NEW_MESSAGE_BODY,
      safeChatId = safeChatId,
      isSilent = isSilent,
    )
  }

  fun showNewMessageAlert(senderDisplayName: String, chatId: String) {
    showIncomingChatMessage(
      chatId = chatId,
      senderDisplayName = senderDisplayName,
      senderUsername = null,
      messageTimestampMs = System.currentTimeMillis(),
      isFromFcm = false,
    )
  }

  fun showPushMessageNotification(
    title: String,
    body: String,
    chatId: String?,
  ) {
    val safeChatId = chatId?.takeIf { MioConstants.SAFE_ID_REGEX.matches(it) }
    if (ActiveChatTracker.isActivelyViewingChat(safeChatId)) return
    postNotification(
      title = title.ifBlank { MioCopy.APP_NAME },
      body = body.ifBlank { MioCopy.NOTIFICATION_NEW_MESSAGE_BODY },
      safeChatId = safeChatId,
      isSilent = false,
    )
  }

  fun showIncomingCallNotification(invite: IncomingCallInvite) {
    if (!hasPostNotificationPermission()) return
    ensureChannel()

    synchronized(notifyLock) {
      val currentlyShowingId = prefs.getString(KEY_ACTIVE_INCOMING_CALL_ID, null)
      if (currentlyShowingId == invite.callId) {
        return
      }
      prefs.edit().putString(KEY_ACTIVE_INCOMING_CALL_ID, invite.callId).apply()
    }

    try {
      val callerName = invite.callerName.ifBlank { MioCopy.APP_NAME }
      val callerPerson = Person.Builder()
        .setName(callerName)
        .setImportant(true)
        .build()

      val requestCodeBase = invite.callId.hashCode()

      val fullScreenIntent = Intent(appContext, MainActivity::class.java).apply {
        action = ACTION_INCOMING_CALL
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
          Intent.FLAG_ACTIVITY_SINGLE_TOP or
          Intent.FLAG_ACTIVITY_CLEAR_TOP
        putCallInviteExtras(this, invite)
      }
      val fullScreenPendingIntent = PendingIntent.getActivity(
        appContext,
        requestCodeBase + 1,
        fullScreenIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

      val answerIntent = Intent(appContext, MainActivity::class.java).apply {
        action = ACTION_ANSWER_CALL
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
          Intent.FLAG_ACTIVITY_SINGLE_TOP or
          Intent.FLAG_ACTIVITY_CLEAR_TOP
        putCallInviteExtras(this, invite)
      }
      val answerPendingIntent = PendingIntent.getActivity(
        appContext,
        requestCodeBase + 2,
        answerIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

      val declineIntent = Intent(appContext, CallActionReceiver::class.java).apply {
        action = CallActionReceiver.ACTION_DECLINE_CALL
        putExtra(CallActionReceiver.EXTRA_CALL_ID, invite.callId)
        putExtra(CallActionReceiver.EXTRA_CHAT_ID, invite.chatId)
        putExtra(CallActionReceiver.EXTRA_CALL_TRANSPORT, invite.transport.name)
      }
      val declinePendingIntent = PendingIntent.getBroadcast(
        appContext,
        requestCodeBase + 3,
        declineIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )

      val callSubtitle = if (invite.type.wire == "video") {
        MioCopy.CALL_INCOMING_VIDEO
      } else {
        MioCopy.CALL_INCOMING_VOICE
      }

      val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

      val builder = NotificationCompat.Builder(appContext, CHANNEL_INCOMING_CALLS)
        .setSmallIcon(R.drawable.ic_notification_mio)
        .setColor(ContextCompat.getColor(appContext, R.color.mio_blush_rose))
        .setContentTitle(callerName)
        .setContentText(callSubtitle)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setCategory(NotificationCompat.CATEGORY_CALL)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setOngoing(true)
        .setAutoCancel(false)
        .setTimeoutAfter(MioConstants.CALL_RING_TIMEOUT_MS)
        .setSound(ringtoneUri)
        .setVibrate(longArrayOf(0L, 800L, 600L, 800L))
        .setContentIntent(fullScreenPendingIntent)
        .setFullScreenIntent(fullScreenPendingIntent, true)
        .addPerson(callerPerson)
        .setStyle(
          NotificationCompat.CallStyle.forIncomingCall(
            callerPerson,
            declinePendingIntent,
            answerPendingIntent,
          ),
        )

      val notification = builder.build().apply {
        flags = flags or Notification.FLAG_INSISTENT
      }
      NotificationManagerCompat.from(appContext).notify(INCOMING_CALL_NOTIFICATION_ID, notification)
    } catch (_: SecurityException) {
      Log.w(TAG, "Incoming call notification permission unavailable.")
    } catch (e: Exception) {
      Log.w(TAG, "Could not show incoming call notification: ${e.javaClass.simpleName}")
    }
  }

  fun cancelIncomingCallNotification(callId: String? = null) {
    synchronized(notifyLock) {
      val currentId = prefs.getString(KEY_ACTIVE_INCOMING_CALL_ID, null)
      if (callId == null || currentId == callId) {
        prefs.edit().remove(KEY_ACTIVE_INCOMING_CALL_ID).apply()
      }
    }
    runCatching {
      NotificationManagerCompat.from(appContext).cancel(INCOMING_CALL_NOTIFICATION_ID)
    }
  }

  fun buildOngoingCallNotification(
    peerName: String,
    statusLabel: String,
    isConnected: Boolean,
    connectedAtMs: Long,
    isVideo: Boolean,
  ): Notification {
    ensureChannel()
    val cleanPeer = peerName.ifBlank { MioCopy.APP_NAME }
    val callerPerson = Person.Builder()
      .setName(cleanPeer)
      .setImportant(true)
      .build()

    val contentIntent = Intent(appContext, MainActivity::class.java).apply {
      action = ACTION_OPEN_ACTIVE_CALL
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or
        Intent.FLAG_ACTIVITY_SINGLE_TOP or
        Intent.FLAG_ACTIVITY_CLEAR_TOP
      putExtra(EXTRA_OPEN_ACTIVE_CALL, true)
    }
    val contentPendingIntent = PendingIntent.getActivity(
      appContext,
      ONGOING_CALL_NOTIFICATION_ID,
      contentIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val hangupIntent = Intent(appContext, CallActionReceiver::class.java).apply {
      action = CallActionReceiver.ACTION_HANGUP_CALL
    }
    val hangupPendingIntent = PendingIntent.getBroadcast(
      appContext,
      ONGOING_CALL_NOTIFICATION_ID + 1,
      hangupIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    val typePrefix = if (isVideo) MioCopy.CALL_TYPE_VIDEO else MioCopy.CALL_TYPE_VOICE
    val builder = NotificationCompat.Builder(appContext, CHANNEL_ONGOING_CALLS)
      .setSmallIcon(R.drawable.ic_notification_mio)
      .setColor(ContextCompat.getColor(appContext, R.color.mio_blush_rose))
      .setContentTitle(cleanPeer)
      .setContentText("$typePrefix • $statusLabel")
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setCategory(NotificationCompat.CATEGORY_CALL)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setSilent(true)
      .setContentIntent(contentPendingIntent)
      .addPerson(callerPerson)
      .setStyle(
        NotificationCompat.CallStyle.forOngoingCall(
          callerPerson,
          hangupPendingIntent,
        ),
      )

    if (isConnected && connectedAtMs > 0L) {
      builder
        .setUsesChronometer(true)
        .setWhen(connectedAtMs)
        .setShowWhen(true)
    }

    return builder.build()
  }

  private fun putCallInviteExtras(intent: Intent, invite: IncomingCallInvite) {
    intent.putExtra(EXTRA_CALL_ID, invite.callId)
    intent.putExtra(EXTRA_CALL_CHAT_ID, invite.chatId)
    intent.putExtra(EXTRA_CALLER_ID, invite.callerId)
    intent.putExtra(EXTRA_CALLER_NAME, invite.callerName)
    intent.putExtra(EXTRA_CALLER_USERNAME, invite.callerUsername)
    intent.putExtra(EXTRA_CALL_TYPE, invite.type.wire)
    intent.putExtra(EXTRA_CALL_TRANSPORT, invite.transport.name)
    intent.putExtra(EXTRA_CALL_CREATED_AT_MS, invite.createdAtMs)
  }

  private fun hasPostNotificationPermission(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
      appContext,
      Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
  }

  private fun postNotification(
    title: String,
    body: String,
    safeChatId: String?,
    isSilent: Boolean,
  ) {
    try {
      if (!hasPostNotificationPermission()) return
      ensureChannel()

      val intent = Intent(appContext, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
          Intent.FLAG_ACTIVITY_CLEAR_TOP or
          Intent.FLAG_ACTIVITY_SINGLE_TOP
        if (safeChatId != null) {
          putExtra("open_chat_id", safeChatId)
        }
      }
      val notificationId = (safeChatId ?: title).hashCode()
      val pendingIntent = PendingIntent.getActivity(
        appContext,
        notificationId,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
      val channelId = if (isSilent) CHANNEL_ID_SILENT else CHANNEL_ID
      val priority = if (isSilent) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH

      val builder = NotificationCompat.Builder(appContext, channelId)
        .setSmallIcon(R.drawable.ic_notification_mio)
        .setColor(ContextCompat.getColor(appContext, R.color.mio_blush_rose))
        .setContentTitle(title.ifBlank { MioCopy.APP_NAME })
        .setContentText(body.ifBlank { MioCopy.NOTIFICATION_NEW_MESSAGE_BODY })
        .setPriority(priority)
        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)

      if (isSilent) {
        builder
          .setSilent(true)
          .setOnlyAlertOnce(true)
      } else {
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        builder
          .setSound(soundUri)
          .setDefaults(NotificationCompat.DEFAULT_VIBRATE or NotificationCompat.DEFAULT_LIGHTS)
      }

      NotificationManagerCompat.from(appContext).notify(notificationId, builder.build())
    } catch (_: SecurityException) {
      Log.w(TAG, "Notification permission unavailable.")
    } catch (e: Exception) {
      Log.w(TAG, "Could not show notification: ${e.javaClass.simpleName}")
    }
  }

  fun onChatOpened(chatId: String?) {
    val safeChatId = chatId?.takeIf { MioConstants.SAFE_ID_REGEX.matches(it) } ?: return
    synchronized(notifyLock) {
      prefs.edit()
        .putInt("$KEY_POPUP_COUNT_PREFIX$safeChatId", 0)
        .apply()
    }
    runCatching {
      NotificationManagerCompat.from(appContext).cancel(safeChatId.hashCode())
    }
  }

  fun getPopupCount(chatId: String): Int {
    val safeChatId = chatId.takeIf { MioConstants.SAFE_ID_REGEX.matches(it) } ?: return 0
    synchronized(notifyLock) {
      return prefs.getInt("$KEY_POPUP_COUNT_PREFIX$safeChatId", 0)
    }
  }

  fun cancelAll() {
    synchronized(notifyLock) {
      prefs.edit().clear().apply()
    }
    runCatching {
      NotificationManagerCompat.from(appContext).cancelAll()
    }
  }

  companion object {
    const val CHANNEL_ID = "chat_messages"
    const val CHANNEL_ID_SILENT = "chat_messages_silent"
    const val CHANNEL_INCOMING_CALLS = "incoming_calls"
    const val CHANNEL_ONGOING_CALLS = "ongoing_calls"

    const val ONGOING_CALL_NOTIFICATION_ID = 42_001
    const val INCOMING_CALL_NOTIFICATION_ID = 42_002

    const val ACTION_INCOMING_CALL = "com.example.action.UI_INCOMING_CALL"
    const val ACTION_ANSWER_CALL = "com.example.action.UI_ANSWER_CALL"
    const val ACTION_OPEN_ACTIVE_CALL = "com.example.action.UI_OPEN_ACTIVE_CALL"

    const val EXTRA_OPEN_ACTIVE_CALL = "extra_open_active_call"
    const val EXTRA_CALL_ID = "extra_call_id"
    const val EXTRA_CALL_CHAT_ID = "extra_call_chat_id"
    const val EXTRA_CALLER_ID = "extra_caller_id"
    const val EXTRA_CALLER_NAME = "extra_caller_name"
    const val EXTRA_CALLER_USERNAME = "extra_caller_username"
    const val EXTRA_CALL_TYPE = "extra_call_type"
    const val EXTRA_CALL_TRANSPORT = "extra_call_transport"
    const val EXTRA_CALL_CREATED_AT_MS = "extra_call_created_at_ms"

    private const val TAG = "MioNotify"
    private const val PREFS_NAME = "mio_notify_state"
    private const val KEY_POPUP_COUNT_PREFIX = "popup_count_"
    private const val KEY_LAST_MSG_ID_PREFIX = "last_msg_id_"
    private const val KEY_LAST_MSG_TS_PREFIX = "last_msg_ts_"
    private const val KEY_LAST_FCM_WALL_PREFIX = "last_fcm_wall_"
    private const val KEY_ACTIVE_INCOMING_CALL_ID = "active_incoming_call_id"
    private const val CROSS_SOURCE_DEDUP_MS = 4_000L
    private val notifyLock = Any()

    fun resolveSenderTitle(displayName: String?, username: String?): String {
      val cleanDisplay = displayName?.trim()?.takeIf { it.isNotBlank() }
      val cleanUsername = username?.trim()?.removePrefix("@")?.takeIf { it.isNotBlank() }
      return cleanDisplay ?: cleanUsername ?: MioCopy.APP_NAME
    }
  }
}
