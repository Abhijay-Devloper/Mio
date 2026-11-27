package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import com.example.core.config.MioConstants
import com.google.firebase.Firebase
import com.google.firebase.app
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.PeerConnection
import java.util.concurrent.TimeUnit

interface IceServerProvider {
  suspend fun buildIceServers(): List<PeerConnection.IceServer>
}

/**
 * Configures direct Peer-to-Peer (P2P) Google STUN servers first for lowest latency,
 * and fetches ephemeral TURN credentials from the secure backend (Firebase RTDB / Cloud Functions).
 *
 * Master API keys are strictly kept on the backend to prevent reverse engineering or key leakage.
 */
class MeteredIceServerProvider(
  private val databaseProvider: (() -> FirebaseDatabase?)? = null,
  private val authProvider: (() -> FirebaseAuth?)? = null,
  private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(MioConstants.TURN_FETCH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    .readTimeout(MioConstants.TURN_FETCH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    .callTimeout(MioConstants.TURN_FETCH_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    .build(),
) : IceServerProvider {

  override suspend fun buildIceServers(): List<PeerConnection.IceServer> {
    val servers = mutableListOf<PeerConnection.IceServer>()
    MioConstants.GOOGLE_STUN_URLS.forEach { stunUrl ->
      servers += PeerConnection.IceServer.builder(stunUrl).createIceServer()
    }
    MioConstants.EXTRA_STUN_URLS.forEach { stunUrl ->
      servers += PeerConnection.IceServer.builder(stunUrl).createIceServer()
    }

    // 1. Try reading cached ephemeral TURN servers from Realtime Database (/turnCredentials/servers)
    val rtdbServers = kotlinx.coroutines.withTimeoutOrNull(MioConstants.TURN_FETCH_TIMEOUT_MS) {
      fetchTurnFromRtdb()
    }.orEmpty()
    if (rtdbServers.isNotEmpty()) {
      servers.addAll(rtdbServers)
      return servers
    }

    // 2. Query the authenticated backend Cloud Function proxy (which fetches and caches ephemeral credentials)
    val backendServers = kotlinx.coroutines.withTimeoutOrNull(MioConstants.TURN_FETCH_TIMEOUT_MS) {
      fetchTurnFromCloudFunction()
    }.orEmpty()
    if (backendServers.isNotEmpty()) {
      servers.addAll(backendServers)
      return servers
    }

    return servers
  }

  private suspend fun fetchTurnFromRtdb(): List<PeerConnection.IceServer> = withContext(Dispatchers.IO) {
    runCatching {
      val db = databaseProvider?.invoke() ?: resolveDefaultDatabase() ?: return@withContext emptyList()
      val snapshot = db.getReference(PATH_TURN_CREDENTIALS).child("servers").get().await()
      if (!snapshot.exists()) return@withContext emptyList()
      parseRtdbSnapshotToIceServers(snapshot)
    }.onFailure { err ->
      Log.w(TAG, "RTDB TURN credentials read skipped: ${err.javaClass.simpleName}")
    }.getOrDefault(emptyList())
  }

  private suspend fun fetchTurnFromCloudFunction(): List<PeerConnection.IceServer> = withContext(Dispatchers.IO) {
    runCatching {
      val auth = authProvider?.invoke() ?: runCatching { Firebase.auth }.getOrNull()
      val idToken = auth?.currentUser?.getIdToken(false)?.await()?.token
      if (idToken.isNullOrBlank()) return@withContext emptyList()

      val functionUrl = readBuildConfigField("BACKEND_TURN_FUNCTION_URL")
        ?.takeIf { it.isNotBlank() }
        ?: MioConstants.DEFAULT_BACKEND_TURN_URL

      val request = Request.Builder()
        .url(functionUrl)
        .header("Authorization", "Bearer $idToken")
        .get()
        .build()

      client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
          Log.w(TAG, "Backend TURN function HTTP ${response.code}; continuing with STUN")
          return@withContext emptyList()
        }
        val body = response.body?.string().orEmpty()
        if (body.isBlank()) return@withContext emptyList()
        parseIceServerJson(body)
      }
    }.onFailure { err ->
      Log.w(TAG, "Backend TURN function call skipped (${err.javaClass.simpleName}); continuing with STUN")
    }.getOrDefault(emptyList())
  }

  internal fun parseRtdbSnapshotToIceServers(snapshot: DataSnapshot): List<PeerConnection.IceServer> {
    val googleStunSet = MioConstants.GOOGLE_STUN_URLS.toSet()
    val parsed = mutableListOf<PeerConnection.IceServer>()
    for (child in snapshot.children) {
      val urlsValue = child.child("urls").value
      val urlList = when (urlsValue) {
        is String -> listOf(urlsValue)
        is List<*> -> urlsValue.filterIsInstance<String>()
        else -> emptyList()
      }.filter { url ->
        val lower = url.lowercase()
        (lower.startsWith("stun:") || lower.startsWith("turn:") || lower.startsWith("turns:")) &&
          url !in googleStunSet
      }
      if (urlList.isEmpty()) continue
      val username = child.child("username").getValue(String::class.java).orEmpty()
      val credential = child.child("credential").getValue(String::class.java).orEmpty()
      val builder = PeerConnection.IceServer.builder(urlList)
      if (username.isNotBlank()) builder.setUsername(username)
      if (credential.isNotBlank()) builder.setPassword(credential)
      parsed += builder.createIceServer()
    }
    return parsed
  }

  internal fun parseIceServerJson(rawJson: String): List<PeerConnection.IceServer> {
    val trimmed = rawJson.trim()
    val array = if (trimmed.startsWith("{")) {
      val obj = JSONObject(trimmed)
      obj.optJSONArray("servers") ?: JSONArray()
    } else if (trimmed.startsWith("[")) {
      JSONArray(trimmed)
    } else {
      JSONArray()
    }
    val googleStunSet = MioConstants.GOOGLE_STUN_URLS.toSet()
    val parsed = mutableListOf<PeerConnection.IceServer>()
    for (i in 0 until array.length()) {
      val item = array.optJSONObject(i) ?: continue
      val urlsValue = item.opt("urls") ?: continue
      val urlList = when (urlsValue) {
        is String -> listOf(urlsValue)
        is JSONArray -> buildList {
          for (j in 0 until urlsValue.length()) {
            urlsValue.optString(j).takeIf { it.isNotBlank() }?.let(::add)
          }
        }
        else -> emptyList()
      }.filter { url ->
        val lower = url.lowercase()
        (lower.startsWith("stun:") || lower.startsWith("turn:") || lower.startsWith("turns:")) &&
          url !in googleStunSet
      }

      if (urlList.isEmpty()) continue
      val username = item.optString("username", "")
      val credential = item.optString("credential", "")
      val builder = PeerConnection.IceServer.builder(urlList)
      if (username.isNotBlank()) builder.setUsername(username)
      if (credential.isNotBlank()) builder.setPassword(credential)
      parsed += builder.createIceServer()
    }
    return parsed
  }

  private fun resolveDefaultDatabase(): FirebaseDatabase? {
    return runCatching {
      val app = Firebase.app
      val explicitUrl = app.options.databaseUrl?.takeIf { it.isNotBlank() }
        ?: MioConstants.DEFAULT_RTDB_URL
      FirebaseDatabase.getInstance(explicitUrl)
    }.getOrNull()
  }

  private fun readBuildConfigField(name: String): String? {
    return runCatching {
      BuildConfig::class.java.getField(name).get(null) as? String
    }.getOrNull()?.trim()
  }

  private companion object {
    const val TAG = "MioIce"
    const val PATH_TURN_CREDENTIALS = "turnCredentials"
  }
}
