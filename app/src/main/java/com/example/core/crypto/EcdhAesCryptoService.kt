package com.example.core.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private val Context.cryptoDataStore by preferencesDataStore(name = "mio_device_keys")

/**
 * Wraps and unwraps device-local private key material using hardware/OS-backed key storage.
 */
interface DeviceKeyProtector {
  fun wrap(alias: String, plainKeyBytes: ByteArray): String
  fun unwrap(alias: String, wrappedPayload: String): ByteArray
  fun deleteKey(alias: String)
}

/**
 * Android Keystore (AES-256-GCM) protector so private keys are never stored in plaintext.
 */
class AndroidKeystoreProtector : DeviceKeyProtector {
  override fun wrap(alias: String, plainKeyBytes: ByteArray): String {
    val masterKey = getOrCreateMasterKey(alias)
    val cipher = Cipher.getInstance(AES_GCM_TRANSFORM)
    cipher.init(Cipher.ENCRYPT_MODE, masterKey)
    val iv = cipher.iv
    val ciphertext = cipher.doFinal(plainKeyBytes)
    val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
    val cipherB64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
    return "$WRAPPED_PREFIX$ivB64:$cipherB64"
  }

  override fun unwrap(alias: String, wrappedPayload: String): ByteArray {
    val body = wrappedPayload.removePrefix(WRAPPED_PREFIX)
    val parts = body.split(":", limit = 2)
    require(parts.size == 2) { "Corrupted wrapped key format." }
    val iv = Base64.decode(parts[0], Base64.NO_WRAP)
    val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
    val masterKey = getExistingMasterKey(alias)
      ?: throw IllegalStateException("Device Keystore master key unavailable.")
    val cipher = Cipher.getInstance(AES_GCM_TRANSFORM)
    cipher.init(Cipher.DECRYPT_MODE, masterKey, GCMParameterSpec(GCM_TAG_BITS, iv))
    return cipher.doFinal(ciphertext)
  }

  override fun deleteKey(alias: String) {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    if (keyStore.containsAlias(alias)) {
      keyStore.deleteEntry(alias)
    }
  }

  private fun getExistingMasterKey(alias: String): SecretKey? {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    return (keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey
  }

  private fun getOrCreateMasterKey(alias: String): SecretKey {
    getExistingMasterKey(alias)?.let { return it }
    val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
    val spec = KeyGenParameterSpec.Builder(
      alias,
      KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
    )
      .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
      .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
      .setKeySize(256)
      .build()
    keyGenerator.init(spec)
    return keyGenerator.generateKey()
  }

  private companion object {
    const val ANDROID_KEYSTORE = "AndroidKeyStore"
    const val AES_GCM_TRANSFORM = "AES/GCM/NoPadding"
    const val GCM_TAG_BITS = 128
    const val WRAPPED_PREFIX = "ak2:"
  }
}

/**
 * Device-local ECDH (secp256r1 / P-256) + SHA-256 shared-secret derivation + AES-256-GCM.
 * Private keys are wrapped with an Android Keystore AES-256-GCM master key before persistence
 * in app-private DataStore, and are never transmitted to Firestore, logs, or backups.
 */
class EcdhAesCryptoService(
  private val context: Context,
  private val keyProtector: DeviceKeyProtector = AndroidKeystoreProtector(),
) : CryptoService {

  private val secureRandom = SecureRandom()

  private fun privateKeyPref(uid: String) = stringPreferencesKey("priv_$uid")
  private fun publicKeyPref(uid: String) = stringPreferencesKey("pub_$uid")
  private fun keystoreAlias(uid: String) = "mio_ecdh_wrap_$uid"

  override suspend fun ensureKeyPair(uid: String): String = withContext(Dispatchers.Default) {
    require(uid.isNotBlank()) { "User UID required for key generation" }
    val prefs = context.cryptoDataStore.data.first()
    val existingPub = prefs[publicKeyPref(uid)]
    val existingPriv = prefs[privateKeyPref(uid)]
    if (!existingPub.isNullOrBlank() && !existingPriv.isNullOrBlank()) {
      // Ensure legacy unwrapped entries are transparently re-wrapped into Android Keystore
      if (!existingPriv.startsWith(WRAPPED_PREFIX)) {
        val rawBytes = Base64.decode(existingPriv, Base64.NO_WRAP)
        val wrapped = keyProtector.wrap(keystoreAlias(uid), rawBytes)
        context.cryptoDataStore.edit { mutable ->
          mutable[privateKeyPref(uid)] = wrapped
        }
      }
      return@withContext existingPub
    }

    val kpg = KeyPairGenerator.getInstance("EC")
    kpg.initialize(ECGenParameterSpec("secp256r1"), secureRandom)
    val keyPair = kpg.generateKeyPair()

    val pubB64 = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
    val wrappedPriv = keyProtector.wrap(keystoreAlias(uid), keyPair.private.encoded)

    context.cryptoDataStore.edit { mutable ->
      mutable[publicKeyPref(uid)] = pubB64
      mutable[privateKeyPref(uid)] = wrappedPriv
    }
    pubB64
  }

  override suspend fun getPublicKeyBase64(uid: String): String? = withContext(Dispatchers.IO) {
    context.cryptoDataStore.data.first()[publicKeyPref(uid)]
  }

  override suspend fun getKeyFingerprint(uid: String): String = withContext(Dispatchers.Default) {
    val pubB64 = getPublicKeyBase64(uid) ?: return@withContext "Pending key setup"
    val digest = MessageDigest.getInstance("SHA-256").digest(Base64.decode(pubB64, Base64.NO_WRAP))
    digest.take(8).joinToString(" ") { byte -> "%02X".format(byte) }
  }

  override suspend fun clearKeys(uid: String) {
    withContext(Dispatchers.IO) {
      context.cryptoDataStore.edit { mutable ->
        mutable.remove(privateKeyPref(uid))
        mutable.remove(publicKeyPref(uid))
      }
      runCatching { keyProtector.deleteKey(keystoreAlias(uid)) }
    }
  }

  override suspend fun encrypt(
    plaintext: String,
    recipientPublicKeyBase64: String,
    myUid: String,
  ): EncryptedPayload = withContext(Dispatchers.Default) {
    ensureKeyPair(myUid)
    val myPrivateKey = loadPrivateKey(myUid)
    val peerPublicKey = decodePublicKey(recipientPublicKeyBase64)
    val aesKey = deriveSharedAesKey(myPrivateKey, peerPublicKey)

    val nonce = ByteArray(GCM_IV_BYTES)
    secureRandom.nextBytes(nonce)

    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_BITS, nonce))
    val encryptedBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

    EncryptedPayload(
      ciphertext = Base64.encodeToString(encryptedBytes, Base64.NO_WRAP),
      nonce = Base64.encodeToString(nonce, Base64.NO_WRAP),
    )
  }

  override suspend fun decrypt(
    ciphertextBase64: String,
    nonceBase64: String,
    peerPublicKeyBase64: String,
    myUid: String,
  ): String = withContext(Dispatchers.Default) {
    val myPrivateKey = loadPrivateKey(myUid)
    val peerPublicKey = decodePublicKey(peerPublicKeyBase64)
    val aesKey = deriveSharedAesKey(myPrivateKey, peerPublicKey)

    val nonce = Base64.decode(nonceBase64, Base64.NO_WRAP)
    val cipherBytes = Base64.decode(ciphertextBase64, Base64.NO_WRAP)

    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(GCM_TAG_BITS, nonce))
    val plainBytes = cipher.doFinal(cipherBytes)
    String(plainBytes, Charsets.UTF_8)
  }

  private suspend fun loadPrivateKey(uid: String): PrivateKey {
    val stored = context.cryptoDataStore.data.first()[privateKeyPref(uid)]
      ?: throw IllegalStateException("Local encryption key not found on this device.")
    val keyBytes = if (stored.startsWith(WRAPPED_PREFIX)) {
      keyProtector.unwrap(keystoreAlias(uid), stored)
    } else {
      val raw = Base64.decode(stored, Base64.NO_WRAP)
      val wrapped = keyProtector.wrap(keystoreAlias(uid), raw)
      context.cryptoDataStore.edit { mutable ->
        mutable[privateKeyPref(uid)] = wrapped
      }
      raw
    }
    return KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(keyBytes))
  }

  private fun decodePublicKey(pubB64: String): PublicKey {
    val keyBytes = Base64.decode(pubB64, Base64.NO_WRAP)
    return KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(keyBytes))
  }

  private fun deriveSharedAesKey(myPrivateKey: PrivateKey, peerPublicKey: PublicKey): SecretKeySpec {
    val agreement = KeyAgreement.getInstance("ECDH")
    agreement.init(myPrivateKey)
    agreement.doPhase(peerPublicKey, true)
    val sharedSecret = agreement.generateSecret()
    val keyDigest = MessageDigest.getInstance("SHA-256").digest(sharedSecret)
    return SecretKeySpec(keyDigest, "AES")
  }

  private companion object {
    const val GCM_IV_BYTES = 12
    const val GCM_TAG_BITS = 128
    const val WRAPPED_PREFIX = "ak2:"
  }
}
