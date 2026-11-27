# Mio Architecture

## Layers

- **`core/config`**: Design constants & rate limits (`MioConstants`), `FeatureFlags`, `MoodRegistry` (`CatMood`), and centralized copy (`MioCopy`).
- **`core/crypto`**: `CryptoService` interface and `EcdhAesCryptoService` implementation using Android's `EC` (`secp256r1`) key agreement + `AES/GCM/NoPadding` with a fresh 12-byte random IV per message. Private keys are wrapped by `AndroidKeystoreProtector` (`AndroidKeyStore` AES-256-GCM master key) before storage in app-private `DataStore` (`mio_device_keys`).
- **`core/feedback`**: `MoodFeedbackManager` (guarded `Vibrator` waveforms + optional `res/raw/cat_*` playback), `MioNotificationHelper` (high-importance `"chat_messages"` channel with sound & vibration), and `MioFirebaseMessagingService` (FCM token refresh, data-only push handling, local decrypt & Room persistence, `"delivered"` ack, and relay doc deletion).
- **`data/local`**: Room database (`MioDatabase`, `LocalMessageEntity`, `LocalMessageDao`) storing decrypted message history on this device, plus `MioPreferences` for user toggles (including `showOnlineStatus`).
- **`data/remote`**: Repository interfaces (`AuthService`, `ChatService`, `MipService`, `PresenceService`) and Firebase implementations (`FirebaseAuthRepository`, `FirebaseChatRepository`, `FirebaseMipRepository` on named Firestore `R.string.firestore_database_id`; `RtdbPresenceRepository` on Firebase Realtime Database), plus sanitized error mapping (`FirestoreErrorHandler`).
- **`functions/`**: Firebase Cloud Functions in `functions/index.js`:
  - `onNewChatMessage` (`onDocumentCreated` on `chats/{chatId}/messages/{messageId}`): sends a high-priority data-only FCM push to the recipient's `fcmToken`.
  - `syncChatMembershipToRtdb` (`onDocumentWritten` on `chats/{chatId}`): mirrors accepted, unblocked 1:1 chat membership into RTDB `/acceptedPeers` and `/chatMembers` for RTDB security rule enforcement.
- **`ui/*`**: Pure Jetpack Compose screens (`AuthScreen`, `OnboardingScreen`, `ChatListScreen`, `ChatDetailScreen`, `SettingsScreen`, `LegalScreen`) driven by `StateFlow<UiState<T>>` ViewModels and Mio palette tokens (`Color.kt`, `MioTokens.kt`, `Type.kt`, `Theme.kt`). The Settings module (`SettingsScreen`, `SettingsSections`, `SettingsDialogs`) provides a full 9-section suite (Account, Privacy, Blocked users, Chats, Calls, MIP & Mood Cats, Notifications, Security, Help & About) accessible from the Profile section's custom horizontal-sliders icon.

## Firestore Data Model (Messaging & Accounts)

- `/usernames/{username}` — Exact-match lookup only (`allow list: if false`, `allow update: if false`). Create-only by owner with reserved-name enforcement; deleted only on account deletion.
- `/users/{userId}` — Private owner-only profile storing `ageConfirmed`, `ageConfirmedAt`, `publicKey`, optional `fcmToken`, and MIP cooldown/partner state.
- `/users/{userId}/blocks/{blockedUid}` — Per-user block records.
- `/chats/{chatId}` — 1:1 thread metadata (`participantUids`, `status`, `blockedBy`, `unreadCounts`, encrypted `lastMessage*` preview + `lastMessageStatus`, and encrypted `mip*` letter state). No typing or presence fields are written here.
- `/chats/{chatId}/messages/{messageId}` — Short-term E2EE message relay docs (`ciphertext`, `nonce`, `type`, optional `moodId`, `status`, `deletedFor`, `deletedForEveryone`, `reactions`, `expiresAt`), deleted right after the recipient persists the decrypted message locally and writes the `"delivered"` ack.
- `/reports/{reportId}` — Write-once safety reports (`allow read, update, delete: if false`) validated for participant membership and reporter-chosen excerpt length.

## Firebase Realtime Database Data Model (Presence & Typing Only)

- `/status/{uid}` — `{ state: "online" | "offline", lastActive: ServerValue.TIMESTAMP }`. Uses RTDB `onDisconnect()` to automatically flip to `"offline"` with server timestamp on connection drop/kill. Omitted/removed completely when the user disables Active Status (`showOnlineStatus = false`).
- `/typing/{chatId}/{uid}` — `boolean`, throttled to at most once every 3s, cleared after 5s of inactivity, armed with `onDisconnect().setValue(false)`, and auto-cleared on the listener side after 6s stale timeout.
- `/acceptedPeers/{uidA}/{uidB}` & `/chatMembers/{chatId}/{uid}` — Server-only indexes maintained by `syncChatMembershipToRtdb` (`".read": false, ".write": false` for clients).

## Security Rules Summary

- **Firestore (`firestore.rules`)**: Default-deny catch-all at root; no user listing; participant-scoped access; blocked-user denial; `typingBy` excluded from `Action 2` updates so typing writes cannot hit Firestore; recipient/sender relay delete.
- **RTDB (`database.rules.json`)**: Default-deny catch-all at root; `/status/$uid` writable only by `auth.uid === $uid` and readable only by self or an accepted unblocked peer (`/acceptedPeers`); `/typing/$chatId/$uid` readable and writable only by `chatMembers` (with `$uid === auth.uid` for writes).

## Phase 2+ Extension Seams

`FeatureFlags` and `MessageContent` sealed interfaces reserve clean extension points for voice notes, photo messages, audio/video calls, scheduled/disappearing messages, shared calendar, and encrypted multi-device key backup.
