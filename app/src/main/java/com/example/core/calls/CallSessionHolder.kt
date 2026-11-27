package com.example.core.calls

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Application-scoped holder for the active user's WebRtcCallManager so ongoing calls
 * survive Activity recreation, leaving a chat screen, or switching to another app
 * while MioCallForegroundService keeps the call alive.
 */
object CallSessionHolder {
  private val lock = Any()

  @Volatile
  private var activeUid: String? = null

  @Volatile
  var activeManager: WebRtcCallManager? = null
    private set

  private val _isCallMinimized = MutableStateFlow(false)
  val isCallMinimized: StateFlow<Boolean> = _isCallMinimized.asStateFlow()

  fun getOrCreate(context: Context, uid: String): WebRtcCallManager {
    synchronized(lock) {
      val existing = activeManager
      if (existing != null && activeUid == uid) {
        return existing
      }
      existing?.dispose()
      _isCallMinimized.value = false
      val created = WebRtcCallManager(context.applicationContext)
      activeUid = uid
      activeManager = created
      return created
    }
  }

  fun setMinimized(minimized: Boolean) {
    _isCallMinimized.value = minimized
  }

  fun disposeSession(uid: String? = null) {
    synchronized(lock) {
      if (uid != null && activeUid != uid) return
      activeManager?.dispose()
      activeManager = null
      activeUid = null
      _isCallMinimized.value = false
    }
  }
}
