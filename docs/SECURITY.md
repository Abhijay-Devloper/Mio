# Mio Security Overview

## Threat Model & Protections

1. **No User Enumeration**: `/usernames/{username}` permits single-document `get` only (`allow list: if false`). Lookups return the exact same neutral message whether a handle does not exist or has been blocked.
2. **End-to-End Message Confidentiality**: Message text and reply quotes are encrypted client-side with ECDH (`secp256r1`) + SHA-256 + AES-256-GCM and a fresh 12-byte random IV per message before leaving the device. Firestore stores only ciphertext, IV, and minimal routing metadata.
3. **Android Keystore Key Protection**: Device ECDH private keys are wrapped with a hardware/OS-backed `AndroidKeyStore` AES-256-GCM master key before persistence in app-private `DataStore`, excluded from cloud/ADB backups (`android:allowBackup="false"`), and purged on sign-out/account deletion.
4. **Zero Plaintext or PII in Logs, Alerts & Release Builds**: `handleFirestoreError` logs sanitized codes without email addresses, message bodies, or key material. `MioNotificationHelper` posts alerts without plaintext previews. Release builds enable R8 (`isMinifyEnabled = true`, `isShrinkResources = true`) and strip `Log.v/d/i` via `proguard-rules.pro`.
5. **Transport & Intent Validation**: `android:usesCleartextTraffic="false"` paired with `network_security_config.xml` enforces HTTPS/system trust anchors. Incoming notification extras (`open_chat_id`) and chat navigation parameters are validated against `MioConstants.SAFE_ID_REGEX`.
6. **Zero-Trust Firestore Rules**: Every collection enforces default-deny, field type/length/enum validation, server timestamps (`<= request.time`), immutable fields (`createdAt`, `senderUid`, `participantUids`), reserved username blocks, reaction ownership locking, and action-specific `affectedKeys().hasOnly(...)` guards.
7. **Block, Report & Rate-Limit Controls**: Blocking writes both to `/users/{uid}/blocks/{partnerUid}` and `blockedBy` on `/chats/{chatId}`. Security rules reject chat requests, messages, reactions, and MIP updates when blocked. Client cooldowns guard connects (2s), message sends (350ms), typing writes (3s throttle), reports (30s), and MIP relinking (24h).
8. **Full Account Deletion**: Requires recent Google authentication (5-minute window), then removes the user's `/chats/{chatId}/messages/*` relay subcollections, chat/MIP references, `/usernames/{handle}`, `/users/{uid}` profile & blocks, local Room history, Android Keystore keys, and Firebase Auth account.

## Known Phase 1 Limitations & Pre-Launch Human Actions

- **Device-Only Key & Message History**: Reinstalling the app or switching devices generates a new keypair; older device messages remain unreadable on a new device until Phase 2 encrypted key backup is added.
- **App Check**: Enable Play Integrity App Check in the Firebase Console before public Play Store release to verify client attestation.
- **Remote Push Notifications (FCM)**: Phase 1 provides local session alerts via `MioNotificationHelper`; background FCM push delivery requires a Cloud Function / FCM configuration in the Firebase Console.
- **Production Support Contact & Legal Review**: Replace the placeholder contact `privacy@mio.example` in `LegalScreen.kt` with a monitored domain email and have Privacy Policy & Terms (`Draft for legal review`) reviewed before public launch.
