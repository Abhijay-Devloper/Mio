# Mio Architecture

## Layers

- **`core/config`**: Design constants & rate limits (`MioConstants`), `FeatureFlags`, `MoodRegistry` (`CatMood`), and centralized copy (`MioCopy`).
- **`core/crypto`**: `CryptoService` interface and `EcdhAesCryptoService` implementation using Android's `EC` (`secp256r1`) key agreement + `AES/GCM/NoPadding` with a fresh 12-byte random IV per message. Private keys are wrapped by `AndroidKeystoreProtector` (`AndroidKeyStore` AES-256-GCM master key) before storage in app-private `DataStore` (`mio_device_keys`).
- **`core/feedback`**: `MoodFeedbackManager` (guarded `Vibrator` waveforms + optional `res/raw/cat_*` playback) and `MioNotificationHelper` (local alerts without plaintext content).
- **`data/local`**: Room database (`MioDatabase`, `LocalMessageEntity`, `LocalMessageDao`) storing decrypted message history on this device, plus `MioPreferences` for user toggles.
- **`data/remote`**: Repository interfaces (`AuthService`, `ChatService`, `MipService`) and Firebase implementations (`FirebaseAuthRepository`, `FirebaseChatRepository`, `FirebaseMipRepository`) connecting to the named Firestore database (`R.string.firestore_database_id`), plus sanitized error mapping (`FirestoreErrorHandler`).
- **`ui/*`**: Pure Jetpack Compose screens (`AuthScreen`, `OnboardingScreen`, `ChatListScreen`, `ChatDetailScreen`, `MipScreen`, `SettingsScreen`, `LegalScreen`) driven by `StateFlow<UiState<T>>` ViewModels and Mio palette tokens (`Color.kt`, `MioTokens.kt`, `Type.kt`, `Theme.kt`).

## Firestore Data Model

- `/usernames/{username}` — Exact-match lookup only (`allow list: if false`, `allow update: if false`). Create-only by owner with reserved-name enforcement; deleted only on account deletion.
- `/users/{userId}` — Private owner-only profile storing `ageConfirmed`, `ageConfirmedAt`, `publicKey`, and MIP cooldown/partner state.
- `/users/{userId}/blocks/{blockedUid}` — Per-user block records.
- `/chats/{chatId}` — 1:1 thread metadata (`participantUids`, `status`, `blockedBy`, `typingBy`, `unreadCounts`, encrypted `lastMessage*` preview, and encrypted `mip*` letter state).
- `/chats/{chatId}/messages/{messageId}` — E2EE message relay docs (`ciphertext`, `nonce`, `type`, optional `moodId`, `status`, `deletedFor`, `deletedForEveryone`, `reactions`, `expiresAt`).
- `/reports/{reportId}` — Write-once safety reports (`allow read, update, delete: if false`) validated for participant membership and reporter-chosen excerpt length.

## Security Rules Summary

- Default-deny catch-all at root (`allow read, write: if false`).
- No user listing (`allow list: if false` on `/usernames` and `/users`).
- Participant-scoped access (`request.auth.uid in resource.data.participantUids`) on `/chats` and `/chats/{chatId}/messages`.
- Blocked users cannot create chats, send messages, toggle reactions, or send MIP letters.
- Both sender and recipient can delete relay documents (enabling recipient post-decrypt delete and sender/account-deletion cleanup); no message content edits after write; reactions lock partner entries to caller UID (`reactions.diff(resource.data.reactions).affectedKeys().hasOnly([request.auth.uid])`).

## Phase 2+ Extension Seams

`FeatureFlags` and `MessageContent` sealed interfaces reserve clean extension points for voice notes, photo messages, audio/video calls, presence, scheduled/disappearing messages, shared calendar, FCM push notifications, and encrypted multi-device key backup.
