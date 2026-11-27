package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import com.example.core.config.MioConstants
import com.example.data.model.CallSessionSnapshot
import com.example.data.model.CallStatus
import com.example.data.model.CallTransport
import com.example.data.model.CallType
import com.example.data.model.IceCandidateSignal
import com.example.data.model.IncomingCallInvite
import com.example.data.model.SessionDescriptionSignal
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.app
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap

interface CallSignalingService {
  fun registerIncomingCallContext(callId: String, chatId: String, transport: CallTransport) {}
  fun observeIncomingCalls(myUid: String): Flow<IncomingCallInvite?>
  fun observeCallSession(callId: String): Flow<CallSessionSnapshot?>
  fun observeRemoteCandidates(callId: String, isCallerObservingReceiver: Boolean): Flow<IceCandidateSignal>
  suspend fun createOutgoingCall(
    callId: String,
    chatId: String,
    callerId: String,
    callerName: String,
    callerUsername: String,
    receiverId: String,
    type: CallType,
    offer: SessionDescriptionSignal,
  )
  suspend fun publishAnswer(callId: String, receiverId: String, answer: SessionDescriptionSignal)
  suspend fun pushIceCandidate(callId: String, isFromCaller: Boolean, candidate: IceCandidateSignal)
  suspend fun updateCallStatus(callId: String, receiverId: String?, status: CallStatus)
  suspend fun removeIncomingInvite(userId: String, callId: String)
  suspend fun cleanupCallNodes(callId: String, receiverId: String?)
  suspend fun armDisconnectCleanup(callId: String, receiverId: String?)
  suspend fun disarmDisconnectCleanup(callId: String, receiverId: String?)
}

/**
 * Handles WebRTC signaling (offers, answers, connecting/ringing states, and trickle ICE)
 * over Firebase Realtime Database (/calls/{callId}, /incomingCalls/{userId}/{callId}),
 * with automatic fallback to secured Firestore signaling when RTDB rules/functions
 * haven't mirrored chat membership yet.
 */
class RtdbCallSignalingRepository(
  private val databaseProvider: (() -> FirebaseDatabase?)? = null,
  private val firestoreProvider: (() -> FirebaseFirestore?)? = null,
) : CallSignalingService {

  private val callTransports = ConcurrentHashMap<String, CallTransport>()
  private val callChatIds = ConcurrentHashMap<String, String>()

  private val database: FirebaseDatabase? by lazy {
    databaseProvider?.invoke() ?: resolveDefaultDatabase()
  }

  private val firestore: FirebaseFirestore? by lazy {
    runCatching { firestoreProvider?.invoke() }.getOrNull()
  }

  override fun registerIncomingCallContext(callId: String, chatId: String, transport: CallTransport) {
    if (callId.isBlank()) return
    callTransports[callId] = transport
    if (chatId.isNotBlank()) {
      callChatIds[callId] = chatId
    }
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
      Log.w(TAG, "RTDB call signaling unavailable: ${err.javaClass.simpleName}")
    }.getOrNull()
  }

  override fun observeIncomingCalls(myUid: String): Flow<IncomingCallInvite?> {
    if (!MioConstants.SAFE_ID_REGEX.matches(myUid)) return flowOf(null)
    val rtdbFlow = observeRtdbIncomingCalls(myUid)
    val firestoreFlow = observeFirestoreIncomingCalls(myUid)
    return combine(rtdbFlow, firestoreFlow) { rtdbInvite, fsInvite ->
      val chosen = listOfNotNull(rtdbInvite, fsInvite).maxByOrNull { it.createdAtMs }
      if (chosen != null) {
        registerIncomingCallContext(chosen.callId, chosen.chatId, chosen.transport)
      }
      chosen
    }
  }

  private fun observeRtdbIncomingCalls(myUid: String): Flow<IncomingCallInvite?> {
    val db = database ?: return flowOf(null)
    return callbackFlow {
      val ref = db.getReference(PATH_INCOMING_CALLS).child(myUid)
      var currentListener: ValueEventListener? = null
      var retryJob: kotlinx.coroutines.Job? = null
      var retryAttempt = 0

      fun attach() {
        val listener = object : ValueEventListener {
          override fun onDataChange(snapshot: DataSnapshot) {
            retryAttempt = 0
            val now = System.currentTimeMillis()
            val activeInvite = snapshot.children.mapNotNull { child ->
              val callId = child.child("callId").getValue(String::class.java)
                ?: child.key.orEmpty()
              val callerId = child.child("callerId").getValue(String::class.java).orEmpty()
              val callerName = child.child("callerName").getValue(String::class.java).orEmpty()
              val callerUsername = child.child("callerUsername").getValue(String::class.java).orEmpty()
              val chatId = child.child("chatId").getValue(String::class.java).orEmpty()
              val type = CallType.fromWire(child.child("type").getValue(String::class.java))
              val status = CallStatus.fromWire(child.child("status").getValue(String::class.java))
              val createdAt = (child.child("createdAt").value as? Number)?.toLong() ?: 0L
              val notStale = createdAt == 0L || (now - createdAt) <= MioConstants.CALL_STALE_INVITE_MS
              if (
                callId.isNotBlank() &&
                callerId.isNotBlank() &&
                status == CallStatus.RINGING &&
                notStale
              ) {
                IncomingCallInvite(
                  callId = callId,
                  chatId = chatId,
                  callerId = callerId,
                  callerName = callerName.ifBlank { "Mio" },
                  callerUsername = callerUsername,
                  type = type,
                  status = status,
                  createdAtMs = createdAt,
                  transport = CallTransport.RTDB,
                )
              } else {
                null
              }
            }.maxByOrNull { it.createdAtMs }

            trySend(activeInvite)
          }

          override fun onCancelled(error: DatabaseError) {
            Log.w(TAG, "observeIncomingCalls(RTDB) cancelled: ${error.code}")
            trySend(null)
            if (currentListener == this) currentListener = null
            if (error.code == DatabaseError.PERMISSION_DENIED && retryAttempt < MAX_RETRIES) {
              val backoff = RETRY_BASE_MS * (retryAttempt + 1)
              retryAttempt++
              retryJob?.cancel()
              retryJob = launch {
                delay(backoff)
                attach()
              }
            }
          }
        }
        currentListener = listener
        runCatching { ref.addValueEventListener(listener) }.onFailure { trySend(null) }
      }

      attach()

      awaitClose {
        retryJob?.cancel()
        currentListener?.let { listener ->
          runCatching { ref.removeEventListener(listener) }
        }
      }
    }
  }

  private fun observeFirestoreIncomingCalls(myUid: String): Flow<IncomingCallInvite?> {
    val fs = firestore ?: return flowOf(null)
    return callbackFlow {
      val registration = runCatching {
        fs.collection(COL_USERS).document(myUid)
          .collection(PATH_INCOMING_CALLS)
          .limit(10L)
          .addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) {
              if (error != null) {
                Log.w(TAG, "observeIncomingCalls(FS) error: ${error.code}")
              }
              trySend(null)
              return@addSnapshotListener
            }
            val now = System.currentTimeMillis()
            val activeInvite = snapshot.documents.mapNotNull { doc ->
              val callId = doc.getString("callId") ?: doc.id
              val callerId = doc.getString("callerId").orEmpty()
              val callerName = doc.getString("callerName").orEmpty()
              val callerUsername = doc.getString("callerUsername").orEmpty()
              val chatId = doc.getString("chatId").orEmpty()
              val type = CallType.fromWire(doc.getString("type"))
              val status = CallStatus.fromWire(doc.getString("status"))
              val createdAtTs = doc.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
              val createdAt = createdAtTs?.toDate()?.time
                ?: (doc.get("createdAt") as? Number)?.toLong()
                ?: 0L
              val notStale = createdAt == 0L || (now - createdAt) <= MioConstants.CALL_STALE_INVITE_MS
              if (
                callId.isNotBlank() &&
                chatId.isNotBlank() &&
                callerId.isNotBlank() &&
                status == CallStatus.RINGING &&
                notStale
              ) {
                IncomingCallInvite(
                  callId = callId,
                  chatId = chatId,
                  callerId = callerId,
                  callerName = callerName.ifBlank { "Mio" },
                  callerUsername = callerUsername,
                  type = type,
                  status = status,
                  createdAtMs = createdAt,
                  transport = CallTransport.FIRESTORE,
                )
              } else {
                null
              }
            }.maxByOrNull { it.createdAtMs }

            trySend(activeInvite)
          }
      }.onFailure { trySend(null) }.getOrNull()

      awaitClose {
        registration?.remove()
      }
    }
  }

  override fun observeCallSession(callId: String): Flow<CallSessionSnapshot?> {
    if (!MioConstants.SAFE_ID_REGEX.matches(callId)) return flowOf(null)
    val transport = callTransports[callId] ?: CallTransport.RTDB
    return when (transport) {
      CallTransport.FIRESTORE -> observeFirestoreCallSession(callId)
      CallTransport.RTDB -> observeRtdbCallSession(callId)
    }
  }

  private fun observeRtdbCallSession(callId: String): Flow<CallSessionSnapshot?> {
    val db = database ?: return flowOf(null)
    return callbackFlow {
      val ref = db.getReference(PATH_CALLS).child(callId)
      val listener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
          if (!snapshot.exists()) {
            trySend(null)
            return
          }
          val callerId = snapshot.child("callerId").getValue(String::class.java).orEmpty()
          val receiverId = snapshot.child("receiverId").getValue(String::class.java).orEmpty()
          val type = CallType.fromWire(snapshot.child("type").getValue(String::class.java))
          val status = CallStatus.fromWire(snapshot.child("status").getValue(String::class.java))
          val offer = snapshot.child("offer").takeIf { it.exists() }?.let {
            SessionDescriptionSignal(
              type = it.child("type").getValue(String::class.java).orEmpty(),
              sdp = it.child("sdp").getValue(String::class.java).orEmpty(),
            )
          }?.takeIf { it.sdp.isNotBlank() }
          val answer = snapshot.child("answer").takeIf { it.exists() }?.let {
            SessionDescriptionSignal(
              type = it.child("type").getValue(String::class.java).orEmpty(),
              sdp = it.child("sdp").getValue(String::class.java).orEmpty(),
            )
          }?.takeIf { it.sdp.isNotBlank() }

          trySend(
            CallSessionSnapshot(
              callId = callId,
              callerId = callerId,
              receiverId = receiverId,
              type = type,
              status = status,
              offer = offer,
              answer = answer,
            ),
          )
        }

        override fun onCancelled(error: DatabaseError) {
          Log.w(TAG, "observeCallSession(RTDB) cancelled: ${error.code}")
          trySend(null)
        }
      }

      runCatching { ref.addValueEventListener(listener) }.onFailure { trySend(null) }

      awaitClose {
        runCatching { ref.removeEventListener(listener) }
      }
    }
  }

  private fun observeFirestoreCallSession(callId: String): Flow<CallSessionSnapshot?> {
    val fs = firestore ?: return flowOf(null)
    val chatId = callChatIds[callId]
    if (chatId.isNullOrBlank() || !MioConstants.SAFE_ID_REGEX.matches(chatId)) return flowOf(null)

    return callbackFlow {
      val docRef = fs.collection(COL_CHATS).document(chatId)
        .collection(PATH_CALLS).document(callId)
      val registration = runCatching {
        docRef.addSnapshotListener { snapshot, error ->
          if (error != null) {
            Log.w(TAG, "observeCallSession(FS) error: ${error.code}")
            trySend(null)
            return@addSnapshotListener
          }
          if (snapshot == null || !snapshot.exists()) {
            trySend(null)
            return@addSnapshotListener
          }
          val callerId = snapshot.getString("callerId").orEmpty()
          val receiverId = snapshot.getString("receiverId").orEmpty()
          val type = CallType.fromWire(snapshot.getString("type"))
          val status = CallStatus.fromWire(snapshot.getString("status"))
          val offerMap = snapshot.get("offer") as? Map<*, *>
          val offer = offerMap?.let {
            SessionDescriptionSignal(
              type = (it["type"] as? String).orEmpty(),
              sdp = (it["sdp"] as? String).orEmpty(),
            )
          }?.takeIf { it.sdp.isNotBlank() }
          val answerMap = snapshot.get("answer") as? Map<*, *>
          val answer = answerMap?.let {
            SessionDescriptionSignal(
              type = (it["type"] as? String).orEmpty(),
              sdp = (it["sdp"] as? String).orEmpty(),
            )
          }?.takeIf { it.sdp.isNotBlank() }

          trySend(
            CallSessionSnapshot(
              callId = callId,
              callerId = callerId,
              receiverId = receiverId,
              type = type,
              status = status,
              offer = offer,
              answer = answer,
            ),
          )
        }
      }.onFailure { trySend(null) }.getOrNull()

      awaitClose {
        registration?.remove()
      }
    }
  }

  override fun observeRemoteCandidates(
    callId: String,
    isCallerObservingReceiver: Boolean,
  ): Flow<IceCandidateSignal> {
    if (!MioConstants.SAFE_ID_REGEX.matches(callId)) return flowOf()
    val transport = callTransports[callId] ?: CallTransport.RTDB
    return when (transport) {
      CallTransport.FIRESTORE -> observeFirestoreRemoteCandidates(callId, isCallerObservingReceiver)
      CallTransport.RTDB -> observeRtdbRemoteCandidates(callId, isCallerObservingReceiver)
    }
  }

  private fun observeRtdbRemoteCandidates(
    callId: String,
    isCallerObservingReceiver: Boolean,
  ): Flow<IceCandidateSignal> {
    val db = database ?: return flowOf()
    val childNode = if (isCallerObservingReceiver) NODE_RECEIVER_CANDIDATES else NODE_CALLER_CANDIDATES

    return callbackFlow {
      val ref = db.getReference(PATH_CALLS).child(callId).child(childNode)
      val listener = object : ChildEventListener {
        override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
          val candidate = snapshot.child("candidate").getValue(String::class.java).orEmpty()
          val sdpMid = snapshot.child("sdpMid").getValue(String::class.java).orEmpty()
          val sdpMLineIndex = (snapshot.child("sdpMLineIndex").value as? Number)?.toInt() ?: 0
          if (candidate.isNotBlank()) {
            trySend(
              IceCandidateSignal(
                id = snapshot.key.orEmpty(),
                sdpMid = sdpMid,
                sdpMLineIndex = sdpMLineIndex,
                candidate = candidate,
              ),
            )
          }
        }

        override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) = Unit
        override fun onChildRemoved(snapshot: DataSnapshot) = Unit
        override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) = Unit
        override fun onCancelled(error: DatabaseError) {
          Log.w(TAG, "observeRemoteCandidates(RTDB) cancelled: ${error.code}")
        }
      }

      runCatching { ref.addChildEventListener(listener) }

      awaitClose {
        runCatching { ref.removeEventListener(listener) }
      }
    }
  }

  private fun observeFirestoreRemoteCandidates(
    callId: String,
    isCallerObservingReceiver: Boolean,
  ): Flow<IceCandidateSignal> {
    val fs = firestore ?: return flowOf()
    val chatId = callChatIds[callId]
    if (chatId.isNullOrBlank() || !MioConstants.SAFE_ID_REGEX.matches(chatId)) return flowOf()
    val childNode = if (isCallerObservingReceiver) NODE_RECEIVER_CANDIDATES else NODE_CALLER_CANDIDATES

    return callbackFlow {
      val colRef = fs.collection(COL_CHATS).document(chatId)
        .collection(PATH_CALLS).document(callId)
        .collection(childNode)
      val registration = runCatching {
        colRef.limit(MAX_CANDIDATES_QUERY).addSnapshotListener { snapshot, error ->
          if (error != null || snapshot == null) {
            if (error != null) {
              Log.w(TAG, "observeRemoteCandidates(FS) error: ${error.code}")
            }
            return@addSnapshotListener
          }
          for (change in snapshot.documentChanges) {
            if (change.type == DocumentChange.Type.ADDED) {
              val doc = change.document
              val candidate = doc.getString("candidate").orEmpty()
              val sdpMid = doc.getString("sdpMid").orEmpty()
              val sdpMLineIndex = (doc.get("sdpMLineIndex") as? Number)?.toInt() ?: 0
              if (candidate.isNotBlank()) {
                trySend(
                  IceCandidateSignal(
                    id = doc.id,
                    sdpMid = sdpMid,
                    sdpMLineIndex = sdpMLineIndex,
                    candidate = candidate,
                  ),
                )
              }
            }
          }
        }
      }.getOrNull()

      awaitClose {
        registration?.remove()
      }
    }
  }

  override suspend fun createOutgoingCall(
    callId: String,
    chatId: String,
    callerId: String,
    callerName: String,
    callerUsername: String,
    receiverId: String,
    type: CallType,
    offer: SessionDescriptionSignal,
  ) {
    if (chatId.isNotBlank()) {
      callChatIds[callId] = chatId
    }
    val cleanCallerName = callerName.trim().ifBlank { "Mio" }.take(60)
    val cleanUsername = callerUsername.trim().take(32)

    val db = database
    var rtdbError: Exception? = null
    if (db != null) {
      try {
        withTimeout(RTDB_CALL_WRITE_TIMEOUT_MS) {
          val callPayload = mapOf(
            "callerId" to callerId,
            "receiverId" to receiverId,
            "callerName" to cleanCallerName,
            "callerUsername" to cleanUsername,
            "chatId" to chatId,
            "type" to type.wire,
            "status" to CallStatus.RINGING.wire,
            "createdAt" to ServerValue.TIMESTAMP,
            "offer" to mapOf(
              "type" to offer.type,
              "sdp" to offer.sdp,
            ),
          )
          val invitePayload = mapOf(
            "callId" to callId,
            "chatId" to chatId,
            "callerId" to callerId,
            "callerName" to cleanCallerName,
            "callerUsername" to cleanUsername,
            "type" to type.wire,
            "status" to CallStatus.RINGING.wire,
            "createdAt" to ServerValue.TIMESTAMP,
          )
          db.getReference(PATH_CALLS).child(callId).setValue(callPayload).await()
          db.getReference(PATH_INCOMING_CALLS).child(receiverId).child(callId).setValue(invitePayload).await()
        }
        callTransports[callId] = CallTransport.RTDB
        return
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        rtdbError = e
        Log.w(TAG, "RTDB outgoing call failed (${e.javaClass.simpleName}); falling back to Firestore signaling")
        runCatching { db.getReference(PATH_CALLS).child(callId).removeValue().await() }
      }
    }

    val fs = firestore
      ?: throw (rtdbError ?: IllegalStateException("Call signaling backend is unavailable."))
    if (!MioConstants.SAFE_ID_REGEX.matches(chatId)) {
      throw IllegalArgumentException("Invalid conversation ID for call.")
    }

    val fsCallPayload = mapOf(
      "callerId" to callerId,
      "receiverId" to receiverId,
      "callerName" to cleanCallerName,
      "callerUsername" to cleanUsername,
      "chatId" to chatId,
      "type" to type.wire,
      "status" to CallStatus.RINGING.wire,
      "offer" to mapOf(
        "type" to offer.type,
        "sdp" to offer.sdp,
      ),
      "createdAt" to FieldValue.serverTimestamp(),
      "updatedAt" to FieldValue.serverTimestamp(),
    )
    val fsInvitePayload = mapOf(
      "callId" to callId,
      "chatId" to chatId,
      "callerId" to callerId,
      "callerName" to cleanCallerName,
      "callerUsername" to cleanUsername,
      "type" to type.wire,
      "status" to CallStatus.RINGING.wire,
      "createdAt" to FieldValue.serverTimestamp(),
    )

    val callDocRef = fs.collection(COL_CHATS).document(chatId).collection(PATH_CALLS).document(callId)
    val inviteDocRef = fs.collection(COL_USERS).document(receiverId).collection(PATH_INCOMING_CALLS).document(callId)
    val batch = fs.batch()
    batch.set(callDocRef, fsCallPayload)
    batch.set(inviteDocRef, fsInvitePayload)
    batch.commit().await()
    callTransports[callId] = CallTransport.FIRESTORE
  }

  override suspend fun publishAnswer(
    callId: String,
    receiverId: String,
    answer: SessionDescriptionSignal,
  ) {
    val transport = callTransports[callId] ?: CallTransport.RTDB
    if (transport == CallTransport.FIRESTORE) {
      val fs = firestore ?: throw IllegalStateException("Firestore signaling is unavailable.")
      val chatId = callChatIds[callId]
        ?: throw IllegalStateException("Missing chat reference for call.")
      val callDocRef = fs.collection(COL_CHATS).document(chatId).collection(PATH_CALLS).document(callId)
      callDocRef.update(
        mapOf(
          "answer" to mapOf(
            "type" to answer.type,
            "sdp" to answer.sdp,
          ),
          "status" to CallStatus.CONNECTING.wire,
          "updatedAt" to FieldValue.serverTimestamp(),
        ),
      ).await()
      runCatching {
        fs.collection(COL_USERS).document(receiverId)
          .collection(PATH_INCOMING_CALLS).document(callId).delete().await()
      }
      return
    }

    val db = database ?: throw IllegalStateException("Realtime Database is unavailable.")
    val callRef = db.getReference(PATH_CALLS).child(callId)
    callRef.updateChildren(
      mapOf(
        "answer" to mapOf(
          "type" to answer.type,
          "sdp" to answer.sdp,
        ),
        "status" to CallStatus.CONNECTING.wire,
      ),
    ).await()
    runCatching {
      db.getReference(PATH_INCOMING_CALLS).child(receiverId).child(callId).removeValue().await()
    }
  }

  override suspend fun pushIceCandidate(
    callId: String,
    isFromCaller: Boolean,
    candidate: IceCandidateSignal,
  ) {
    if (candidate.candidate.isBlank()) return
    val childNode = if (isFromCaller) NODE_CALLER_CANDIDATES else NODE_RECEIVER_CANDIDATES
    val transport = callTransports[callId] ?: CallTransport.RTDB
    if (transport == CallTransport.FIRESTORE) {
      val fs = firestore ?: return
      val chatId = callChatIds[callId] ?: return
      runCatching {
        fs.collection(COL_CHATS).document(chatId)
          .collection(PATH_CALLS).document(callId)
          .collection(childNode)
          .add(
            mapOf(
              "sdpMid" to candidate.sdpMid.take(128),
              "sdpMLineIndex" to candidate.sdpMLineIndex.coerceAtLeast(0),
              "candidate" to candidate.candidate.take(2048),
              "createdAt" to FieldValue.serverTimestamp(),
            ),
          ).await()
      }
      return
    }

    val db = database ?: return
    runCatching {
      db.getReference(PATH_CALLS).child(callId).child(childNode).push().setValue(
        mapOf(
          "sdpMid" to candidate.sdpMid,
          "sdpMLineIndex" to candidate.sdpMLineIndex,
          "candidate" to candidate.candidate,
        ),
      ).await()
    }
  }

  override suspend fun updateCallStatus(callId: String, receiverId: String?, status: CallStatus) {
    val transport = callTransports[callId] ?: CallTransport.RTDB
    if (transport == CallTransport.FIRESTORE) {
      val fs = firestore ?: return
      val chatId = callChatIds[callId] ?: return
      val callDocRef = fs.collection(COL_CHATS).document(chatId).collection(PATH_CALLS).document(callId)
      val updates = buildMap<String, Any> {
        put("status", status.wire)
        put("updatedAt", FieldValue.serverTimestamp())
        if (status.isTerminal) {
          put("endedAt", FieldValue.serverTimestamp())
        }
      }
      runCatching { callDocRef.update(updates).await() }
      if (status.isTerminal && !receiverId.isNullOrBlank()) {
        runCatching {
          fs.collection(COL_USERS).document(receiverId)
            .collection(PATH_INCOMING_CALLS).document(callId).delete().await()
        }
      }
      return
    }

    val db = database ?: return
    val callRef = db.getReference(PATH_CALLS).child(callId)
    runCatching { callRef.child("status").setValue(status.wire).await() }
    if (status.isTerminal) {
      runCatching { callRef.child("endedAt").setValue(ServerValue.TIMESTAMP).await() }
      if (!receiverId.isNullOrBlank()) {
        runCatching {
          db.getReference(PATH_INCOMING_CALLS).child(receiverId).child(callId).removeValue().await()
        }
      }
    }
  }

  override suspend fun removeIncomingInvite(userId: String, callId: String) {
    if (!MioConstants.SAFE_ID_REGEX.matches(userId) || !MioConstants.SAFE_ID_REGEX.matches(callId)) return
    database?.let { db ->
      runCatching {
        db.getReference(PATH_INCOMING_CALLS).child(userId).child(callId).removeValue().await()
      }
    }
    firestore?.let { fs ->
      runCatching {
        fs.collection(COL_USERS).document(userId)
          .collection(PATH_INCOMING_CALLS).document(callId).delete().await()
      }
    }
  }

  override suspend fun cleanupCallNodes(callId: String, receiverId: String?) {
    if (!MioConstants.SAFE_ID_REGEX.matches(callId)) return
    val transport = callTransports[callId] ?: CallTransport.RTDB
    disarmDisconnectCleanup(callId, receiverId)
    if (!receiverId.isNullOrBlank() && MioConstants.SAFE_ID_REGEX.matches(receiverId)) {
      removeIncomingInvite(receiverId, callId)
    }
    if (transport == CallTransport.FIRESTORE) {
      val fs = firestore
      val chatId = callChatIds[callId]
      if (fs != null && !chatId.isNullOrBlank()) {
        val callDocRef = fs.collection(COL_CHATS).document(chatId).collection(PATH_CALLS).document(callId)
        for (subCol in listOf(NODE_CALLER_CANDIDATES, NODE_RECEIVER_CANDIDATES)) {
          runCatching {
            val snap = callDocRef.collection(subCol).limit(MAX_CANDIDATES_QUERY).get().await()
            if (!snap.isEmpty) {
              val batch = fs.batch()
              snap.documents.forEach { batch.delete(it.reference) }
              batch.commit().await()
            }
          }
        }
        runCatching { callDocRef.delete().await() }
      }
    } else {
      database?.let { db ->
        runCatching { db.getReference(PATH_CALLS).child(callId).removeValue().await() }
      }
    }
    callTransports.remove(callId)
    callChatIds.remove(callId)
  }

  override suspend fun armDisconnectCleanup(callId: String, receiverId: String?) {
    if ((callTransports[callId] ?: CallTransport.RTDB) != CallTransport.RTDB) return
    val db = database ?: return
    if (!MioConstants.SAFE_ID_REGEX.matches(callId)) return
    runCatching {
      db.getReference(PATH_CALLS).child(callId).child("status")
        .onDisconnect().setValue(CallStatus.ENDED.wire).await()
    }
    if (!receiverId.isNullOrBlank() && MioConstants.SAFE_ID_REGEX.matches(receiverId)) {
      runCatching {
        db.getReference(PATH_INCOMING_CALLS).child(receiverId).child(callId)
          .onDisconnect().removeValue().await()
      }
    }
  }

  override suspend fun disarmDisconnectCleanup(callId: String, receiverId: String?) {
    if ((callTransports[callId] ?: CallTransport.RTDB) != CallTransport.RTDB) return
    val db = database ?: return
    if (!MioConstants.SAFE_ID_REGEX.matches(callId)) return
    runCatching {
      db.getReference(PATH_CALLS).child(callId).child("status").onDisconnect().cancel().await()
    }
    if (!receiverId.isNullOrBlank() && MioConstants.SAFE_ID_REGEX.matches(receiverId)) {
      runCatching {
        db.getReference(PATH_INCOMING_CALLS).child(receiverId).child(callId)
          .onDisconnect().cancel().await()
      }
    }
  }

  private companion object {
    const val TAG = "RtdbCallSignal"
    const val COL_USERS = "users"
    const val COL_CHATS = "chats"
    const val PATH_CALLS = "calls"
    const val PATH_INCOMING_CALLS = "incomingCalls"
    const val NODE_CALLER_CANDIDATES = "callerCandidates"
    const val NODE_RECEIVER_CANDIDATES = "receiverCandidates"
    const val MAX_RETRIES = 4
    const val RETRY_BASE_MS = 1_500L
    const val RTDB_CALL_WRITE_TIMEOUT_MS = 5_000L
    const val MAX_CANDIDATES_QUERY = 200L
  }
}
