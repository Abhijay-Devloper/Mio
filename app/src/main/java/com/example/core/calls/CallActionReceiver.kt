package com.example.core.calls

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.R
import com.example.core.config.MioConstants
import com.example.core.feedback.MioNotificationHelper
import com.example.data.model.CallStatus
import com.example.data.model.CallTransport
import com.example.data.remote.RtdbCallSignalingRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles background notification actions for calls (Decline incoming call, Hang up active call)
 * without forcing the UI into the foreground.
 */
class CallActionReceiver : BroadcastReceiver() {

  override fun onReceive(context: Context, intent: Intent) {
    val appContext = context.applicationContext
    when (intent.action) {
      ACTION_DECLINE_CALL -> {
        val callId = intent.getStringExtra(EXTRA_CALL_ID)
          ?.takeIf { MioConstants.SAFE_ID_REGEX.matches(it) } ?: return
        val chatId = intent.getStringExtra(EXTRA_CHAT_ID)
          ?.takeIf { MioConstants.SAFE_ID_REGEX.matches(it) }.orEmpty()
        val transportWire = intent.getStringExtra(EXTRA_CALL_TRANSPORT).orEmpty()
        val transport = if (transportWire == CallTransport.FIRESTORE.name) {
          CallTransport.FIRESTORE
        } else {
          CallTransport.RTDB
        }

        MioNotificationHelper(appContext).cancelIncomingCallNotification(callId)
        val activeManager = CallSessionHolder.activeManager
        val currentUid = Firebase.auth.currentUser?.uid
        val activeInvite = activeManager?.incomingInvite?.value
        if (activeManager != null && activeInvite != null && activeInvite.callId == callId && !currentUid.isNullOrBlank()) {
          activeManager.declineCall(activeInvite, currentUid)
          return
        }

        if (currentUid.isNullOrBlank()) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
          try {
            val repo = RtdbCallSignalingRepository(
              firestoreProvider = {
                FirebaseFirestore.getInstance(appContext.getString(R.string.firestore_database_id))
              },
            )
            repo.registerIncomingCallContext(callId, chatId, transport)
            repo.updateCallStatus(callId, currentUid, CallStatus.DECLINED)
            repo.removeIncomingInvite(currentUid, callId)
          } finally {
            pendingResult.finish()
          }
        }
      }
      ACTION_HANGUP_CALL -> {
        CallSessionHolder.activeManager?.endCall(CallStatus.ENDED)
        MioCallForegroundService.stop(appContext)
      }
    }
  }

  companion object {
    const val ACTION_DECLINE_CALL = "com.example.action.CALL_DECLINE"
    const val ACTION_HANGUP_CALL = "com.example.action.CALL_HANGUP"
    const val EXTRA_CALL_ID = "extra_call_id"
    const val EXTRA_CHAT_ID = "extra_chat_id"
    const val EXTRA_CALL_TRANSPORT = "extra_call_transport"
  }
}
