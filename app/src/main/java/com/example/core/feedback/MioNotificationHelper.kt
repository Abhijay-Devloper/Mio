package com.example.core.feedback

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

/**
 * Local notification helper for incoming encrypted message alerts when permission is granted.
 * Privacy rule: notifications never include plaintext message previews or keys.
 */
class MioNotificationHelper(private val context: Context) {

  private val appContext = context.applicationContext

  fun ensureChannel() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        ?: return
      val channel = NotificationChannel(
        CHANNEL_ID,
        "Mio Messages",
        NotificationManager.IMPORTANCE_HIGH,
      ).apply {
        description = "Alerts when your partner sends a new encrypted note or mood."
      }
      manager.createNotificationChannel(channel)
    }
  }

  fun showNewMessageAlert(senderDisplayName: String, chatId: String) {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val granted = ContextCompat.checkSelfPermission(
          appContext,
          Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return
      }
      ensureChannel()
      val intent = Intent(appContext, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra("open_chat_id", chatId)
      }
      val pendingIntent = PendingIntent.getActivity(
        appContext,
        chatId.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
      )
      val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(senderDisplayName)
        .setContentText("Sent you a private message on Mio")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .setContentIntent(pendingIntent)
        .build()

      NotificationManagerCompat.from(appContext).notify(chatId.hashCode(), notification)
    } catch (e: SecurityException) {
      Log.w(TAG, "Notification permission unavailable.")
    } catch (e: Exception) {
      Log.w(TAG, "Could not show notification: ${e.javaClass.simpleName}")
    }
  }

  fun cancelAll() {
    runCatching {
      NotificationManagerCompat.from(appContext).cancelAll()
    }
  }

  private companion object {
    const val CHANNEL_ID = "mio_messages_channel"
    const val TAG = "MioNotify"
  }
}
