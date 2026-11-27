package com.example.core.calls

import android.Manifest
import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.core.config.MioCopy
import com.example.core.feedback.MioNotificationHelper
import com.example.data.model.CallStatus
import com.example.data.model.CallType

/**
 * Keeps voice and video WebRTC calls alive when the user navigates away from the chat,
 * switches to other apps, or locks the screen — matching WhatsApp/Instagram background
 * call behavior while adhering to Android 14+ foreground service type rules.
 */
class MioCallForegroundService : Service() {

  private var wakeLock: PowerManager.WakeLock? = null
  private var wifiLock: WifiManager.WifiLock? = null
  private var isForegroundStarted = false

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
      ACTION_END_CALL -> {
        CallSessionHolder.activeManager?.endCall(CallStatus.ENDED)
        stopSelfCleanly()
        return START_NOT_STICKY
      }
      ACTION_START_OR_UPDATE -> {
        val activeCall = CallSessionHolder.activeManager?.activeCall?.value
        val peerName = activeCall?.peerName
          ?: intent.getStringExtra(EXTRA_PEER_NAME)?.takeIf { it.isNotBlank() }
          ?: MioCopy.APP_NAME
        val isVideo = activeCall?.let { it.type == CallType.VIDEO && it.isCameraEnabled }
          ?: intent.getBooleanExtra(EXTRA_IS_VIDEO, false)
        val isConnected = activeCall?.status == CallStatus.CONNECTED
        val connectedAtMs = activeCall?.connectedAtMs ?: 0L
        val statusLabel = when (activeCall?.status) {
          CallStatus.RINGING -> MioCopy.CALL_STATUS_RINGING
          CallStatus.CONNECTING -> MioCopy.CALL_STATUS_CONNECTING
          CallStatus.CONNECTED -> MioCopy.CALL_BANNER_CONNECTED
          else -> MioCopy.CALL_BANNER_CONNECTING
        }

        val fgsTypes = resolveAllowedFgsTypes(includeCamera = isVideo)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && fgsTypes == 0) {
          Log.w(TAG, "Missing microphone permission for call foreground service")
          stopSelfCleanly()
          return START_NOT_STICKY
        }

        val notification = MioNotificationHelper(applicationContext).buildOngoingCallNotification(
          peerName = peerName,
          statusLabel = statusLabel,
          isConnected = isConnected,
          connectedAtMs = connectedAtMs,
          isVideo = isVideo,
        )

        val started = runCatching {
          ServiceCompat.startForeground(
            this,
            MioNotificationHelper.ONGOING_CALL_NOTIFICATION_ID,
            notification,
            fgsTypes,
          )
          isForegroundStarted = true
        }.onFailure { err ->
          Log.w(TAG, "Unable to enter foreground for call: ${err.javaClass.simpleName}")
        }.isSuccess

        if (!started) {
          stopSelfCleanly()
          return START_NOT_STICKY
        }

        acquireCallLocks()
      }
      else -> {
        if (CallSessionHolder.activeManager?.activeCall?.value == null) {
          stopSelfCleanly()
        }
      }
    }
    return START_NOT_STICKY
  }

  override fun onDestroy() {
    releaseCallLocks()
    isForegroundStarted = false
    runCatching {
      NotificationManagerCompat.from(applicationContext)
        .cancel(MioNotificationHelper.ONGOING_CALL_NOTIFICATION_ID)
    }
    super.onDestroy()
  }

  private fun stopSelfCleanly() {
    releaseCallLocks()
    if (isForegroundStarted) {
      runCatching { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE) }
      isForegroundStarted = false
    }
    stopSelf()
  }

  private fun resolveAllowedFgsTypes(includeCamera: Boolean): Int {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
      return 0
    }
    var types = 0
    val micGranted = ContextCompat.checkSelfPermission(
      this,
      Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED
    if (micGranted) {
      types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
    }
    val camGranted = ContextCompat.checkSelfPermission(
      this,
      Manifest.permission.CAMERA,
    ) == PackageManager.PERMISSION_GRANTED
    if (includeCamera && camGranted) {
      types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
    }
    return types
  }

  @SuppressLint("WakelockTimeout")
  private fun acquireCallLocks() {
    runCatching {
      if (wakeLock?.isHeld != true) {
        val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)?.apply {
          setReferenceCounted(false)
          acquire(MAX_CALL_LOCK_DURATION_MS)
        }
      }
    }
    runCatching {
      if (wifiLock?.isHeld != true) {
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
          WifiManager.WIFI_MODE_FULL_LOW_LATENCY
        } else {
          @Suppress("DEPRECATION")
          WifiManager.WIFI_MODE_FULL_HIGH_PERF
        }
        wifiLock = wm?.createWifiLock(mode, WIFI_LOCK_TAG)?.apply {
          setReferenceCounted(false)
          acquire()
        }
      }
    }
  }

  private fun releaseCallLocks() {
    runCatching {
      if (wakeLock?.isHeld == true) {
        wakeLock?.release()
      }
    }
    wakeLock = null
    runCatching {
      if (wifiLock?.isHeld == true) {
        wifiLock?.release()
      }
    }
    wifiLock = null
  }

  companion object {
    const val ACTION_START_OR_UPDATE = "com.example.action.CALL_FGS_START_OR_UPDATE"
    const val ACTION_END_CALL = "com.example.action.CALL_FGS_END"
    const val EXTRA_PEER_NAME = "extra_peer_name"
    const val EXTRA_IS_VIDEO = "extra_is_video"

    private const val TAG = "MioCallFgs"
    private const val WAKE_LOCK_TAG = "Mio:ActiveCallWakeLock"
    private const val WIFI_LOCK_TAG = "Mio:ActiveCallWifiLock"
    private const val MAX_CALL_LOCK_DURATION_MS = 4 * 60 * 60 * 1000L

    fun startOrUpdate(context: Context, peerName: String, isVideo: Boolean) {
      val appCtx = context.applicationContext
      val intent = Intent(appCtx, MioCallForegroundService::class.java).apply {
        action = ACTION_START_OR_UPDATE
        putExtra(EXTRA_PEER_NAME, peerName)
        putExtra(EXTRA_IS_VIDEO, isVideo)
      }
      runCatching {
        ContextCompat.startForegroundService(appCtx, intent)
      }.onFailure { err ->
        Log.w(TAG, "startForegroundService failed: ${err.javaClass.simpleName}")
      }
    }

    fun stop(context: Context) {
      val appCtx = context.applicationContext
      runCatching {
        appCtx.stopService(Intent(appCtx, MioCallForegroundService::class.java))
      }
      runCatching {
        NotificationManagerCompat.from(appCtx)
          .cancel(MioNotificationHelper.ONGOING_CALL_NOTIFICATION_ID)
      }
    }
  }
}
