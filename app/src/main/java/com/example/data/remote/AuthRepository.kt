package com.example.data.remote

import android.util.Log
import com.example.core.config.MioConstants
import com.example.core.crypto.CryptoService
import com.example.data.local.LocalMessageDao
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

interface AuthService {
  fun observeUserProfile(uid: String): Flow<UserProfile?>
  suspend fun claimUsernameAndCreateProfile(
    uid: String,
    rawUsername: String,
    displayName: String,
    ageConfirmed: Boolean,
  ): Result<UserProfile>
  suspend fun updateDisplayName(uid: String, username: String, displayName: String): Result<Unit>
  suspend fun syncPublicKeyIfNeeded(profile: UserProfile)
  suspend fun deleteAccount(uid: String, username: String): Result<Unit>
}

class FirebaseAuthRepository(
  private val db: FirebaseFirestore,
  private val cryptoService: CryptoService,
  private val localMessageDao: LocalMessageDao,
  private val auth: FirebaseAuth = Firebase.auth,
) : AuthService {

  private fun requireUserId(): String {
    return auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing account.")
  }

  override fun observeUserProfile(uid: String): Flow<UserProfile?> = flow {
    if (uid.isBlank()) {
      emit(null)
      return@flow
    }
    val path = "users/$uid"
    emitAll(
      db.collection("users").document(uid)
        .snapshots()
        .map { snapshot ->
          if (!snapshot.exists()) null
          else snapshot.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        }
        .catch { error ->
          val root = unwrapFirestoreError(error)
          if (root == null) throw error
          if (auth.currentUser?.uid == uid) {
            handleFirestoreError(root, OperationType.GET, path)
          }
          throw error
        }
    )
  }

  override suspend fun claimUsernameAndCreateProfile(
    uid: String,
    rawUsername: String,
    displayName: String,
    ageConfirmed: Boolean,
  ): Result<UserProfile> {
    val path = "users/$uid"
    return try {
      val signedInUid = requireUserId()
      check(signedInUid == uid) { "Signed-in user mismatch." }
      val normalized = rawUsername.trim().lowercase()
      require(MioConstants.USERNAME_REGEX.matches(normalized)) {
        "Username must be 3–20 lowercase letters, numbers, or underscores."
      }
      require(normalized !in MioConstants.RESERVED_USERNAMES) {
        "That @username is reserved. Please choose another."
      }
      val cleanDisplay = displayName.trim()
      require(cleanDisplay.length in 1..MioConstants.MAX_DISPLAY_NAME_LENGTH) {
        "Display name must be 1–${MioConstants.MAX_DISPLAY_NAME_LENGTH} characters."
      }
      require(ageConfirmed) { "You must confirm you are 18 or older to join Mio." }

      val publicKey = cryptoService.ensureKeyPair(uid)
      val userRef = db.collection("users").document(uid)
      val usernameRef = db.collection("usernames").document(normalized)

      db.runTransaction { tx ->
        val existingUsername = tx.get(usernameRef)
        if (existingUsername.exists()) {
          throw IllegalStateException("That @username is already taken.")
        }
        val existingUser = tx.get(userRef)
        if (existingUser.exists()) {
          throw IllegalStateException("Profile already exists for this account.")
        }

        val usernamePayload = mapOf(
          "uid" to uid,
          "username" to normalized,
          "displayName" to cleanDisplay,
          "publicKey" to publicKey,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
        val userPayload = mapOf(
          "uid" to uid,
          "username" to normalized,
          "displayName" to cleanDisplay,
          "publicKey" to publicKey,
          "ageConfirmed" to true,
          "ageConfirmedAt" to FieldValue.serverTimestamp(),
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp(),
        )
        tx.set(usernameRef, usernamePayload)
        tx.set(userRef, userPayload)
      }.await()

      val created = userRef.get().await()
        .toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        ?: UserProfile(
          uid = uid,
          username = normalized,
          displayName = cleanDisplay,
          publicKey = publicKey,
          ageConfirmed = true,
        )
      Result.success(created)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, path)
      Result.failure(e)
    }
  }

  override suspend fun updateDisplayName(
    uid: String,
    username: String,
    displayName: String,
  ): Result<Unit> {
    val path = "users/$uid"
    return try {
      val signedInUid = requireUserId()
      check(signedInUid == uid) { "Signed-in user mismatch." }
      val cleanDisplay = displayName.trim()
      require(cleanDisplay.length in 1..MioConstants.MAX_DISPLAY_NAME_LENGTH) {
        "Display name must be 1–${MioConstants.MAX_DISPLAY_NAME_LENGTH} characters."
      }

      // /usernames is create-only per security rules; update private profile + participant display names
      db.collection("users").document(uid)
        .update(
          mapOf(
            "displayName" to cleanDisplay,
            "updatedAt" to FieldValue.serverTimestamp(),
          )
        ).await()

      val chatsSnap = db.collection("chats")
        .whereArrayContains("participantUids", uid)
        .limit(MioConstants.CHATS_QUERY_LIMIT)
        .get()
        .await()
      for (chatDoc in chatsSnap.documents) {
        runCatching {
          chatDoc.reference.update(
            mapOf(
              "participantDisplayNames.$uid" to cleanDisplay,
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          ).await()
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

  override suspend fun syncPublicKeyIfNeeded(profile: UserProfile) {
    try {
      val uid = auth.currentUser?.uid ?: return
      if (uid != profile.uid) return
      val localPubKey = cryptoService.ensureKeyPair(uid)
      if (localPubKey != profile.publicKey) {
        db.collection("users").document(uid)
          .update(
            mapOf(
              "publicKey" to localPubKey,
              "updatedAt" to FieldValue.serverTimestamp(),
            )
          ).await()

        val chatsSnap = db.collection("chats")
          .whereArrayContains("participantUids", uid)
          .limit(MioConstants.CHATS_QUERY_LIMIT)
          .get()
          .await()
        for (chatDoc in chatsSnap.documents) {
          runCatching {
            chatDoc.reference.update(
              mapOf(
                "participantPublicKeys.$uid" to localPubKey,
                "updatedAt" to FieldValue.serverTimestamp(),
              )
            ).await()
          }
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "Public key sync skipped: ${e.javaClass.simpleName}")
    }
  }

  override suspend fun deleteAccount(uid: String, username: String): Result<Unit> {
    val path = "users/$uid"
    return try {
      val user = auth.currentUser ?: throw IllegalStateException("Not signed in.")
      check(user.uid == uid) { "Signed-in user mismatch." }

      // Require recent authentication BEFORE destroying Firestore/local state
      val lastSignInMs = user.metadata?.lastSignInTimestamp ?: 0L
      if (lastSignInMs > 0L && (System.currentTimeMillis() - lastSignInMs) > RECENT_AUTH_WINDOW_MS) {
        throw IllegalStateException(
          "For your security, please sign out and sign back in right before deleting your account."
        )
      }

      // 1. Delete user blocks
      val blocksSnap = db.collection("users").document(uid).collection("blocks")
        .limit(MioConstants.CHATS_QUERY_LIMIT)
        .get()
        .await()
      for (doc in blocksSnap.documents) {
        doc.reference.delete().await()
      }

      // 2. Delete relay message docs in user's chat threads, then delete thread docs
      val chatsSnap = db.collection("chats")
        .whereArrayContains("participantUids", uid)
        .limit(MioConstants.CHATS_QUERY_LIMIT)
        .get()
        .await()
      for (chatDoc in chatsSnap.documents) {
        val messagesRef = chatDoc.reference.collection("messages")
        while (true) {
          val msgBatch = messagesRef
            .whereArrayContains("participantUids", uid)
            .limit(DELETE_BATCH_SIZE)
            .get()
            .await()
          if (msgBatch.isEmpty) break
          val batch = db.batch()
          for (msgDoc in msgBatch.documents) {
            batch.delete(msgDoc.reference)
          }
          batch.commit().await()
        }
        chatDoc.reference.delete().await()
      }

      // 3. Delete username claim and private profile
      if (username.isNotBlank()) {
        db.collection("usernames").document(username).delete().await()
      }
      db.collection("users").document(uid).delete().await()

      // 4. Wipe local Room messages & Android Keystore-wrapped device encryption keys
      localMessageDao.deleteAllForUser(uid)
      cryptoService.clearKeys(uid)

      // 5. Delete Firebase Auth account
      user.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, path)
      Result.failure(e)
    }
  }

  private companion object {
    const val TAG = "AuthRepo"
    const val DELETE_BATCH_SIZE = 100L
    const val RECENT_AUTH_WINDOW_MS = 5 * 60_000L
  }
}
