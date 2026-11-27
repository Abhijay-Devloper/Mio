package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import com.example.core.config.MioConstants
import com.example.data.model.PresenceStatus
import com.google.firebase.Firebase
import com.google.firebase.app
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

interface PresenceService {
  fun bindPresence(myUid: String, enabled: Boolean)
  suspend fun goOfflineAndClear(myUid: String, removeNode: Boolean = false)
  fun observeUserStatus(uid: String): Flow<PresenceStatus?>
  fun observeTyping(chatId: String, partnerUid: String): Flow<Boolean>
  suspend fun setTyping(chatId: String, myUid: String, typing: Boolean)
}

class RtdbPresenceRepository(
  private val databaseProvider: (() -> FirebaseDatabase?)? = null,
) : PresenceService {

  private var connectedRef: DatabaseReference? = null
  private var connectedListener: ValueEventListener? = null
  private var boundUid: String? = null
  private var presenceEnabled: Boolean = false
  @Volatile private var presenceOpSeq: Long = 0L

  private val database: FirebaseDatabase? by lazy {
    databaseProvider?.invoke() ?: resolveDefaultDatabase()
  }

  private fun resolveDefaultDatabase(): FirebaseDatabase? {
    return runCatching {
      val app = Firebase.app
      val buildConfigDbUrl = runCatching {
        BuildConfig::class.java.getField("FIREBASE_DATABASE_URL").get(null) as? String
      }.getOrNull()?.takeIf { it.isNotBlank() }
      val explicitUrl = app.options.databaseUrl?.takeIf { it.isNotBlank() }
        ?: buildConfigDbUrl
        ?: MioConstants.DEFAULT_RTDB_URL
      FirebaseDatabase.getInstance(explicitUrl)
    }.onFailure { err ->
      Log.w(TAG, "RTDB unavailable: ${err.javaClass.simpleName}")
    }.getOrNull()
  }

  override fun bindPresence(myUid: String, enabled: Boolean) {
    if (!MioConstants.SAFE_ID_REGEX.matches(myUid)) return
    val db = database ?: return

    val opId = ++presenceOpSeq
    detachConnectedListener()
    boundUid = myUid
    presenceEnabled = enabled

    val statusRef = db.getReference(PATH_STATUS).child(myUid)

    if (!enabled) {
      // Privacy toggle off: cancel onDisconnect and remove node so no status is published.
      runCatching {
        statusRef.onDisconnect().cancel()
        statusRef.removeValue()
      }
      return
    }

    val infoRef = db.getReference(PATH_INFO_CONNECTED)
    val listener = object : ValueEventListener {
      override fun onDataChange(snapshot: DataSnapshot) {
        val connected = snapshot.getValue(Boolean::class.java) == true
        if (!connected || !presenceEnabled || boundUid != myUid || presenceOpSeq != opId) return

        val offlinePayload = mapOf(
          FIELD_STATE to STATE_OFFLINE,
          FIELD_LAST_ACTIVE to ServerValue.TIMESTAMP,
        )
        val onlinePayload = mapOf(
          FIELD_STATE to STATE_ONLINE,
          FIELD_LAST_ACTIVE to ServerValue.TIMESTAMP,
        )

        // Arm server-side onDisconnect before announcing online so process kill flips to offline.
        statusRef.onDisconnect().setValue(offlinePayload)
          .addOnSuccessListener {
            if (presenceEnabled && boundUid == myUid && presenceOpSeq == opId) {
              statusRef.setValue(onlinePayload)
            }
          }
          .addOnFailureListener { err ->
            Log.w(TAG, "Presence onDisconnect failed: ${err.javaClass.simpleName}")
          }
      }

      override fun onCancelled(error: DatabaseError) {
        Log.w(TAG, "Presence connection listener cancelled: ${error.code}")
      }
    }

    connectedRef = infoRef
    connectedListener = listener
    runCatching {
      infoRef.addValueEventListener(listener)
    }.onFailure { err ->
      Log.w(TAG, "Presence bind failed: ${err.javaClass.simpleName}")
    }
  }

  override suspend fun goOfflineAndClear(myUid: String, removeNode: Boolean) {
    val opId = ++presenceOpSeq
    detachConnectedListener()
    presenceEnabled = false
    if (boundUid == myUid) {
      boundUid = null
    }
    if (!MioConstants.SAFE_ID_REGEX.matches(myUid)) return
    val db = database ?: return
    val statusRef = db.getReference(PATH_STATUS).child(myUid)
    try {
      runCatching { statusRef.onDisconnect().cancel().await() }
      if (presenceOpSeq != opId) return
      if (removeNode) {
        statusRef.removeValue().await()
      } else {
        statusRef.setValue(
          mapOf(
            FIELD_STATE to STATE_OFFLINE,
            FIELD_LAST_ACTIVE to ServerValue.TIMESTAMP,
          ),
        ).await()
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "Presence offline clear skipped: ${e.javaClass.simpleName}")
    }
  }

  override fun observeUserStatus(uid: String): Flow<PresenceStatus?> {
    if (!MioConstants.SAFE_ID_REGEX.matches(uid)) return flowOf(null)
    val db = database ?: return flowOf(null)

    return callbackFlow {
      val statusRef = db.getReference(PATH_STATUS).child(uid)
      var currentListener: ValueEventListener? = null
      var retryJob: Job? = null
      var retryAttempt = 0

      fun attachListener() {
        val listener = object : ValueEventListener {
          override fun onDataChange(snapshot: DataSnapshot) {
            retryAttempt = 0
            if (!snapshot.exists()) {
              trySend(null)
              return
            }
            val state = snapshot.child(FIELD_STATE).getValue(String::class.java)
            val lastActive = (snapshot.child(FIELD_LAST_ACTIVE).value as? Number)?.toLong() ?: 0L
            if (state == STATE_ONLINE || state == STATE_OFFLINE) {
              trySend(PresenceStatus(state = state, lastActiveMs = lastActive))
            } else {
              trySend(null)
            }
          }

          override fun onCancelled(error: DatabaseError) {
            Log.w(TAG, "observeUserStatus cancelled for $uid: ${error.code}")
            trySend(null)
            if (currentListener == this) {
              currentListener = null
            }
            if (error.code == DatabaseError.PERMISSION_DENIED && retryAttempt < MAX_LISTENER_RETRIES) {
              val backoffMs = LISTENER_RETRY_BASE_MS * (retryAttempt + 1)
              retryAttempt++
              retryJob?.cancel()
              retryJob = launch {
                delay(backoffMs)
                attachListener()
              }
            }
          }
        }
        currentListener = listener
        runCatching { statusRef.addValueEventListener(listener) }
          .onFailure { trySend(null) }
      }

      attachListener()

      awaitClose {
        retryJob?.cancel()
        currentListener?.let { listener ->
          runCatching { statusRef.removeEventListener(listener) }
        }
      }
    }
  }

  override fun observeTyping(chatId: String, partnerUid: String): Flow<Boolean> {
    if (!MioConstants.SAFE_ID_REGEX.matches(chatId) || !MioConstants.SAFE_ID_REGEX.matches(partnerUid)) {
      return flowOf(false)
    }
    val db = database ?: return flowOf(false)

    return callbackFlow {
      val typingRef = db.getReference(PATH_TYPING).child(chatId).child(partnerUid)
      var currentListener: ValueEventListener? = null
      var staleResetJob: Job? = null
      var retryJob: Job? = null
      var retryAttempt = 0

      fun attachListener() {
        val listener = object : ValueEventListener {
          override fun onDataChange(snapshot: DataSnapshot) {
            retryAttempt = 0
            val isTyping = snapshot.getValue(Boolean::class.java) == true
            staleResetJob?.cancel()
            trySend(isTyping)
            if (isTyping) {
              staleResetJob = launch {
                delay(MioConstants.TYPING_STALE_TIMEOUT_MS)
                trySend(false)
              }
            }
          }

          override fun onCancelled(error: DatabaseError) {
            Log.w(TAG, "observeTyping cancelled for $chatId: ${error.code}")
            staleResetJob?.cancel()
            trySend(false)
            if (currentListener == this) {
              currentListener = null
            }
            if (error.code == DatabaseError.PERMISSION_DENIED && retryAttempt < MAX_LISTENER_RETRIES) {
              val backoffMs = LISTENER_RETRY_BASE_MS * (retryAttempt + 1)
              retryAttempt++
              retryJob?.cancel()
              retryJob = launch {
                delay(backoffMs)
                attachListener()
              }
            }
          }
        }
        currentListener = listener
        runCatching { typingRef.addValueEventListener(listener) }
          .onFailure { trySend(false) }
      }

      attachListener()

      awaitClose {
        retryJob?.cancel()
        staleResetJob?.cancel()
        currentListener?.let { listener ->
          runCatching { typingRef.removeEventListener(listener) }
        }
      }
    }
  }

  override suspend fun setTyping(chatId: String, myUid: String, typing: Boolean) {
    if (!MioConstants.SAFE_ID_REGEX.matches(chatId) || !MioConstants.SAFE_ID_REGEX.matches(myUid)) {
      return
    }
    val db = database ?: return
    val typingRef = db.getReference(PATH_TYPING).child(chatId).child(myUid)
    try {
      if (typing) {
        runCatching { typingRef.onDisconnect().setValue(false).await() }
        typingRef.setValue(true).await()
      } else {
        runCatching { typingRef.onDisconnect().cancel().await() }
        typingRef.setValue(false).await()
      }
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      // Typing indicator updates are best-effort
    }
  }

  private fun detachConnectedListener() {
    val listener = connectedListener
    val ref = connectedRef
    if (listener != null && ref != null) {
      runCatching { ref.removeEventListener(listener) }
    }
    connectedListener = null
    connectedRef = null
  }

  private companion object {
    const val TAG = "RtdbPresence"
    const val PATH_STATUS = "status"
    const val PATH_TYPING = "typing"
    const val PATH_INFO_CONNECTED = ".info/connected"
    const val FIELD_STATE = "state"
    const val FIELD_LAST_ACTIVE = "lastActive"
    const val STATE_ONLINE = "online"
    const val STATE_OFFLINE = "offline"
    const val MAX_LISTENER_RETRIES = 4
    const val LISTENER_RETRY_BASE_MS = 1_500L
  }
}
