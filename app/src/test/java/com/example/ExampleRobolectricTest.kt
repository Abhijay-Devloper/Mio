package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.config.CatMood
import com.example.core.crypto.DeviceKeyProtector
import com.example.core.crypto.EcdhAesCryptoService
import com.example.core.feedback.MoodFeedbackManager
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
}
