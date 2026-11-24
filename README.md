# Mio — Private Chat for Couples (Android)

Mio is a romantic, end-to-end encrypted 1:1 Android messaging app built with Kotlin, Jetpack Compose, Material Design 3, Room, Android Keystore, and Firebase (Google Sign-In + Cloud Firestore).

## Phase 1 Features

- **Google Sign-In & Unique Handles**: Credential Manager (`GetSignInWithGoogleOption`) authentication, mandatory 18+ confirmation (timestamp only, no birth date), and atomic create-only `@username` claims (`^[a-z0-9_]{3,20}$`) with reserved handle blocking.
- **Exact Username Connect**: Look up a partner strictly by their exact `@username` (no browsing or user enumeration), send chat requests, and accept or decline invitations.
- **End-to-End Encrypted 1:1 Chat**: Device-local ECDH (`secp256r1`) + SHA-256 + AES-256-GCM encryption via `CryptoService` with fresh 12-byte random nonces per message. Private keys are wrapped by an `AndroidKeyStore` AES-256-GCM master key. History is stored locally in Room (`mio_local_history.db`); Firestore acts as an encrypted relay with client-side 30-day expiry cleanup.
- **Message Status Receipts**: Shows `Sending...`, `Sent`, `Delivered`, `Read`, and `Not sent` in compact text on the latest outgoing message and on any earlier outgoing messages not yet in the `Read` state.
- **Swipe-to-Reply**: Horizontal drag gesture on bubbles to quote any message; tapping a quote scrolls to the original message.
- **Emoji Reactions & Delete**: Long-press or double-tap any bubble to add/remove your own emoji reaction, copy text, delete for yourself, or delete for everyone.
- **Chat List**: Sorted latest message first with unread count badges, timestamps, typing indicators, and MIP badges.
- **Most Important Person (MIP)**: One MIP per user inside an accepted, unblocked chat, delivered as an animated crimson wax-seal letter with a 24-hour quiet cooldown after unlinking.
- **Mood Cats**: Custom Canvas-drawn mood cats (`happy`, `sad`, `tired`, `bored`, `angry`) with distinct haptic waveforms and optional `res/raw/cat_<mood>` sound playback (falls back cleanly when audio files are omitted).
- **Safety & Account Controls**: Block/unblock, report with reason + optional excerpt, plain-language Privacy Policy & Terms (`Draft for legal review`), and two-step in-app account deletion (purges profile, username claim, MIP links, chat message relay subcollections, local history, and Android Keystore keys).

## Setup & Build

1. Configure Firebase via AI Studio provisioning (`app/google-services.json` and `app/src/main/res/values/firebase_applet_config.xml`).
2. Add optional cat audio assets to `app/src/main/res/raw/` named `cat_happy`, `cat_sad`, `cat_tired`, `cat_bored`, `cat_angry` (`.ogg` or `.mp3`). Missing files degrade safely without errors.
3. Run Firestore security rules tests:
   ```bash
   FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js
   ```
4. Run unit & Robolectric tests:
   ```bash
   gradle :app:testDebugUnitTest
   ```
5. Build APK (release builds enable R8 shrinking/obfuscation and log stripping):
   ```bash
   gradle :app:assembleDebug
   ```
