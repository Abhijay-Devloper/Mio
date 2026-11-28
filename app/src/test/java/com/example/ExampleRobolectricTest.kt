package com.example

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.example.core.config.CatMood
import com.example.core.crypto.DeviceKeyProtector
import com.example.core.crypto.EcdhAesCryptoService
import com.example.core.feedback.ActiveChatTracker
import com.example.core.feedback.MioNotificationHelper
import com.example.core.feedback.MoodFeedbackManager
import com.example.data.remote.MeteredIceServerProvider
import org.robolectric.Shadows
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  private class InMemoryTestProtector : DeviceKeyProtector {
    private val keys = mutableMapOf<String, ByteArray>()

    override fun wrap(alias: String, plainKeyBytes: ByteArray): String {
      keys.putIfAbsent(alias, ByteArray(plainKeyBytes.size) { idx -> (idx * 31 + 7).toByte() })
      val masked = ByteArray(plainKeyBytes.size) { idx ->
        (plainKeyBytes[idx].toInt() xor 0x5A).toByte()
      }
      return "ak2:testiv:${Base64.getEncoder().encodeToString(masked)}"
    }

    override fun unwrap(alias: String, wrappedPayload: String): ByteArray {
      val b64 = wrappedPayload.substringAfter("ak2:testiv:")
      val masked = Base64.getDecoder().decode(b64)
      return ByteArray(masked.size) { idx ->
        (masked[idx].toInt() xor 0x5A).toByte()
      }
    }

    override fun deleteKey(alias: String) {
      keys.remove(alias)
    }
  }

  @Test
  fun `read app name from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Mio", appName)
  }

  @Test
  fun `ecdh aes crypto encrypts decrypts and isolates wrong peer key`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val aliceCrypto = EcdhAesCryptoService(context, InMemoryTestProtector())
    val bobCrypto = EcdhAesCryptoService(context, InMemoryTestProtector())
    val eveCrypto = EcdhAesCryptoService(context, InMemoryTestProtector())

    val alicePub = aliceCrypto.ensureKeyPair("alice_test_uid")
    val bobPub = bobCrypto.ensureKeyPair("bob_test_uid")
    eveCrypto.ensureKeyPair("eve_test_uid")

    val secretText = "Goodnight, my favorite person"
    val encrypted1 = aliceCrypto.encrypt(secretText, bobPub, "alice_test_uid")
    val encrypted2 = aliceCrypto.encrypt(secretText, bobPub, "alice_test_uid")

    // Fresh random IV/nonce per message produces distinct ciphertexts
    assertNotEquals(encrypted1.nonce, encrypted2.nonce)
    assertNotEquals(encrypted1.ciphertext, encrypted2.ciphertext)
    assertTrue(!encrypted1.ciphertext.contains("Goodnight"))

    val decryptedByBob = bobCrypto.decrypt(
      ciphertextBase64 = encrypted1.ciphertext,
      nonceBase64 = encrypted1.nonce,
      peerPublicKeyBase64 = alicePub,
      myUid = "bob_test_uid",
    )
    assertEquals(secretText, decryptedByBob)

    try {
      eveCrypto.decrypt(
        ciphertextBase64 = encrypted1.ciphertext,
        nonceBase64 = encrypted1.nonce,
        peerPublicKeyBase64 = alicePub,
        myUid = "eve_test_uid",
      )
      fail("Expected decryption with wrong private key to fail")
    } catch (_: Exception) {
      // Expected AEAD authentication failure
    } finally {
      aliceCrypto.clearKeys("alice_test_uid")
      bobCrypto.clearKeys("bob_test_uid")
      eveCrypto.clearKeys("eve_test_uid")
    }
  }

  @Test
  fun `missing mood cat audio files never crash feedback manager`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val manager = MoodFeedbackManager(context)
    for (mood in CatMood.entries) {
      manager.triggerMoodFeedback(mood = mood, hapticsEnabled = true, soundsEnabled = true)
    }
    manager.playLightTick()
  }

  @Test
  fun `firestore exception maps to debug code in debug and friendly message in release`() {
    val permDenied = com.google.firebase.firestore.FirebaseFirestoreException(
      "PERMISSION_DENIED at /databases/secret/documents/chats/abc",
      com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED,
    )
    val debugMsg = com.example.data.remote.toHumanMessage(permDenied, "Fallback", includeDebugCode = true)
    assertTrue(!debugMsg.contains("/databases"))
    assertEquals("That action isn't available right now. [PERMISSION_DENIED]", debugMsg)

    val releaseMsg = com.example.data.remote.toHumanMessage(permDenied, "Fallback", includeDebugCode = false)
    assertTrue(!releaseMsg.contains("PERMISSION_DENIED"))
    assertTrue(!releaseMsg.contains("/databases"))
    assertEquals("That action isn't available right now.", releaseMsg)
  }

  @Test
  fun `opening intro 16x9 video raw resource is bundled and readable`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.resources.openRawResourceFd(R.raw.mio_intro).use { afd ->
      assertTrue(afd.length > 0L)
    }
  }

  @Test
  fun `metered ice server parser preserves stun p2p priority and turn credentials`() = runBlocking {
    val provider = MeteredIceServerProvider()
    val meteredJson = """
      {
        "servers": [
          {"urls": "stun:stun.relay.metered.ca:80"},
          {"urls": "turn:global.relay.metered.ca:80", "username": "u1", "credential": "p1"},
          {"urls": ["turns:global.relay.metered.ca:443?transport=tcp"], "username": "u1", "credential": "p1"}
        ]
      }
    """.trimIndent()
    val parsed = provider.parseIceServerJson(meteredJson)
    assertEquals(3, parsed.size)
    assertEquals("u1", parsed[1].username)
    assertEquals("p1", parsed[1].password)
    assertTrue(parsed[2].urls.contains("turns:global.relay.metered.ca:443?transport=tcp"))

    val built = provider.buildIceServers()
    assertTrue(built.size >= 2)
    assertTrue(built[0].urls.contains("stun:stun.l.google.com:19302"))
    assertTrue(built[1].urls.contains("stun:stun1.l.google.com:19302"))
  }

  @Test
  fun `notifications pop up 4 times then go silent and suppress active chat`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val shadowNm = Shadows.shadowOf(nm)

    val helper = MioNotificationHelper(app)
    helper.cancelAll()

    try {
      // Suppress when actively viewing the chat in foreground
      ActiveChatTracker.currentChatId = "chat_ab"
      ActiveChatTracker.isAppInForeground = true
      helper.showIncomingChatMessage(
        chatId = "chat_ab",
        senderDisplayName = "Partner B",
        senderUsername = "partner_b",
        messageId = "msg_active_1",
        messageTimestampMs = 100L,
        isFromFcm = true,
      )
      assertEquals(0, helper.getPopupCount("chat_ab"))
      assertEquals(0, shadowNm.allNotifications.size)

      // Different user (User C) pops up 4 times, then 5th sits silently on silent channel
      for (i in 1..4) {
        helper.showIncomingChatMessage(
          chatId = "chat_ac",
          senderDisplayName = "",
          senderUsername = "user_c",
          messageId = "msg_c_$i",
          messageTimestampMs = i * 1_000L,
          isFromFcm = true,
        )
        assertEquals(i, helper.getPopupCount("chat_ac"))
        val latest = shadowNm.getNotification("chat_ac".hashCode())
        assertEquals(MioNotificationHelper.CHANNEL_ID, latest.channelId)
        assertEquals("user_c", latest.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("Sent you a message !", latest.extras.getString(Notification.EXTRA_TEXT))
      }

      helper.showIncomingChatMessage(
        chatId = "chat_ac",
        senderDisplayName = "",
        senderUsername = "user_c",
        messageId = "msg_c_5",
        messageTimestampMs = 5_000L,
        isFromFcm = true,
      )
      assertEquals(5, helper.getPopupCount("chat_ac"))
      val silentNotif = shadowNm.getNotification("chat_ac".hashCode())
      assertEquals(MioNotificationHelper.CHANNEL_ID_SILENT, silentNotif.channelId)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        assertTrue((silentNotif.flags and NotificationCompat.FLAG_ONLY_ALERT_ONCE) != 0)
      }

      // Opening chat resets popup counter back to 0
      helper.onChatOpened("chat_ac")
      assertEquals(0, helper.getPopupCount("chat_ac"))
    } finally {
      ActiveChatTracker.currentChatId = null
      ActiveChatTracker.isAppInForeground = false
      helper.cancelAll()
    }
  }

  @Test
  fun `incoming call notification posts insistent CallStyle alert and cancels cleanly`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    Shadows.shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val shadowNm = Shadows.shadowOf(nm)

    val helper = MioNotificationHelper(app)
    helper.cancelAll()

    try {
      val invite = com.example.data.model.IncomingCallInvite(
        callId = "call_123",
        chatId = "chat_ab",
        callerId = "caller_1",
        callerName = "Elena",
        callerUsername = "elena",
        type = com.example.data.model.CallType.VIDEO,
        status = com.example.data.model.CallStatus.RINGING,
        createdAtMs = 1_700_000_000_000L,
        transport = com.example.data.model.CallTransport.RTDB,
      )
      helper.showIncomingCallNotification(invite)

      val notif = shadowNm.getNotification(MioNotificationHelper.INCOMING_CALL_NOTIFICATION_ID)
      assertEquals(MioNotificationHelper.CHANNEL_INCOMING_CALLS, notif.channelId)
      assertEquals(Notification.CATEGORY_CALL, notif.category)
      assertTrue((notif.flags and Notification.FLAG_INSISTENT) != 0)
      assertTrue((notif.flags and Notification.FLAG_ONGOING_EVENT) != 0)
      assertTrue(notif.fullScreenIntent != null)

      helper.cancelIncomingCallNotification("call_123")
      assertEquals(null, shadowNm.getNotification(MioNotificationHelper.INCOMING_CALL_NOTIFICATION_ID))
    } finally {
      helper.cancelAll()
    }
  }

  @Test
  fun `ongoing call notification configures silent CallStyle chronometer notification`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val helper = MioNotificationHelper(app)
    val connectedAt = System.currentTimeMillis() - 5_000L

    val notification = helper.buildOngoingCallNotification(
      peerName = "Elena",
      statusLabel = "Connected",
      isConnected = true,
      connectedAtMs = connectedAt,
      isVideo = false,
    )

    assertEquals(MioNotificationHelper.CHANNEL_ONGOING_CALLS, notification.channelId)
    assertEquals(Notification.CATEGORY_CALL, notification.category)
    assertTrue((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0)
    assertTrue(notification.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
  }

  @Test
  fun `call session holder tracks minimized state across screens`() {
    com.example.core.calls.CallSessionHolder.setMinimized(false)
    assertEquals(false, com.example.core.calls.CallSessionHolder.isCallMinimized.value)

    com.example.core.calls.CallSessionHolder.setMinimized(true)
    assertEquals(true, com.example.core.calls.CallSessionHolder.isCallMinimized.value)

    com.example.core.calls.CallSessionHolder.setMinimized(false)
    assertEquals(false, com.example.core.calls.CallSessionHolder.isCallMinimized.value)
  }

  @Test
  fun `chat message channel sets public lockscreen visibility and push relay safely no-ops without url`() = runBlocking {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val helper = MioNotificationHelper(app)
    helper.ensureChannel()
    val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      val chatChannel = nm.getNotificationChannel(MioNotificationHelper.CHANNEL_ID)
      assertEquals(Notification.VISIBILITY_PUBLIC, chatChannel.lockscreenVisibility)
    }

    val relay = com.example.data.remote.OkHttpPushNotificationRelay(authProvider = { null })
    relay.notifyNewMessage("chat_1", "msg_1", "user_2", "Elena", "elena")
    relay.notifyIncomingCall("call_1", "chat_1", "user_2", "Elena", "elena", com.example.data.model.CallType.AUDIO)
    relay.notifyCallEnded("call_1", "user_2")
  }
}
