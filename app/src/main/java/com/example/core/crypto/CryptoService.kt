package com.example.core.crypto

data class EncryptedPayload(
  val ciphertext: String,
  val nonce: String,
)

/**
 * Clean abstraction for Mio end-to-end encryption so cryptographic primitives
 * can be swapped or upgraded (e.g., multi-device key backup) in later phases.
 */
interface CryptoService {
  suspend fun ensureKeyPair(uid: String): String
  suspend fun getPublicKeyBase64(uid: String): String?
  suspend fun getKeyFingerprint(uid: String): String
  suspend fun clearKeys(uid: String)
  suspend fun encrypt(
    plaintext: String,
    recipientPublicKeyBase64: String,
    myUid: String,
  ): EncryptedPayload
  suspend fun decrypt(
    ciphertextBase64: String,
    nonceBase64: String,
    peerPublicKeyBase64: String,
    myUid: String,
  ): String
}
