package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
  CREATE("create"),
  UPDATE("update"),
  DELETE("delete"),
  LIST("list"),
  GET("get"),
  WRITE("write"),
}

/**
 * Logs structured metadata for Firestore failures without PII (no email), message plaintext, or keys.
 */
fun handleFirestoreError(exception: Throwable, operationType: OperationType, path: String?): String {
  val causes = generateSequence(exception) { it.cause }
  val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
    ?: causes.firstOrNull { it !is CancellationException }
    ?: exception

  val currentUser = Firebase.auth.currentUser
  val providerIds = currentUser?.providerData?.map { it.providerId } ?: emptyList()

  val authInfoJson = JSONObject().apply {
    put("userId", currentUser?.uid)
    put("providers", JSONArray(providerIds))
  }

  val errorCode = (rootError as? FirebaseFirestoreException)?.code?.name ?: rootError.javaClass.simpleName

  val errorInfoJson = JSONObject().apply {
    put("errorCode", errorCode)
    put("operationType", operationType.value)
    put("path", path)
    put("authInfo", authInfoJson)
  }

  val jsonString = errorInfoJson.toString()
  Log.e("FirestoreError", "Firestore Error: $jsonString")
  return jsonString
}

fun unwrapFirestoreError(error: Throwable): Throwable? {
  val firestoreError = generateSequence(error) { it.cause }
    .filterIsInstance<FirebaseFirestoreException>()
    .firstOrNull()
  if (firestoreError == null && error is CancellationException) return null
  return firestoreError ?: error
}

/**
 * Converts internal/Firebase exceptions into warm, plain-language user messages.
 * In debug builds only, appends the Firebase error code (e.g. [PERMISSION_DENIED]).
 * Never exposes stack traces, message content, keys, or Firestore paths.
 */
fun toHumanMessage(
  error: Throwable,
  fallback: String,
  includeDebugCode: Boolean = BuildConfig.DEBUG,
): String {
  val causes = generateSequence(error) { it.cause }.toList()
  val authRecent = causes.filterIsInstance<FirebaseAuthRecentLoginRequiredException>().firstOrNull()
  if (authRecent != null) {
    val msg = "For your security, please sign out and sign back in right before deleting your account."
    return if (includeDebugCode) "$msg [${authRecent.errorCode}]" else msg
  }
  val firestore = causes.filterIsInstance<FirebaseFirestoreException>().firstOrNull()
  if (firestore != null) {
    val friendly = when (firestore.code) {
      FirebaseFirestoreException.Code.PERMISSION_DENIED ->
        "That action isn't available right now."
      FirebaseFirestoreException.Code.UNAVAILABLE,
      FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
        "Connection looks offline or slow. Please try again."
      FirebaseFirestoreException.Code.NOT_FOUND ->
        "This item could not be found."
      FirebaseFirestoreException.Code.ABORTED ->
        "Something changed at the same moment. Please try again."
      FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED ->
        "Too many requests right now. Please wait a moment and try again."
      else -> fallback
    }
    return if (includeDebugCode) "$friendly [${firestore.code.name}]" else friendly
  }
  if (causes.any { it is FirebaseNetworkException }) {
    val msg = "You appear to be offline. Please check your connection and try again."
    return if (includeDebugCode) "$msg [NETWORK_ERROR]" else msg
  }
  val authEx = causes.filterIsInstance<FirebaseAuthException>().firstOrNull()
  if (authEx != null) {
    return if (includeDebugCode) "$fallback [${authEx.errorCode}]" else fallback
  }
  val validation = causes.firstOrNull {
    it is IllegalArgumentException || it is IllegalStateException
  }
  val msg = validation?.message?.trim()
  if (!msg.isNullOrBlank() && msg.length <= 160 && !msg.contains("Firebase", ignoreCase = true) && !msg.contains("Exception")) {
    return msg
  }
  return fallback
}
