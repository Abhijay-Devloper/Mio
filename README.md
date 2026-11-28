# Mio — Private Chat for Couples (Android)

Mio is a romantic, end-to-end encrypted 1:1 Android messaging app built with Kotlin, Jetpack Compose, Material Design 3, Room, Android Keystore, and Firebase (Google Sign-In, Cloud Firestore, Firebase Realtime Database, and FCM).

## Phase 1 Features

- **Google Sign-In & Unique Handles**: Credential Manager (`GetSignInWithGoogleOption`) authentication, mandatory 18+ confirmation (timestamp only, no birth date), and atomic create-only `@username` claims (`^[a-z0-9_]{3,20}$`) with reserved handle blocking.
- **Exact Username Connect**: Look up a partner strictly by their exact `@username` (no browsing or user enumeration), send chat requests, and accept or decline invitations across multiple 1:1 conversations.
- **End-to-End Encrypted 1:1 Chat & Relay Cleanup**: Device-local ECDH (`secp256r1`) + SHA-256 + AES-256-GCM encryption via `CryptoService` with fresh 12-byte random nonces per message. Private keys are wrapped by an `AndroidKeyStore` AES-256-GCM master key. History is stored locally in Room (`mio_local_history.db`); Firestore acts as a short-term relay where the recipient device receives via high-priority data-only FCM (`onNewChatMessage`), decrypts & saves locally, writes a `"delivered"` ack, and deletes the relay doc.
- **Presence & Typing Indicators**: Live online status lives in Firebase Realtime Database (`/status/{uid}`), while throttled typing indicators sync directly through Firestore (`chats/{chatId}.typingBy.<uid>`) with an RTDB overlay and automatic 6-second stale expiry — working reliably even on the Firebase Spark free plan.
- **Message Status Receipts**: Shows `Sending...`, `Sent`, `Delivered`, `Read`, and `Not sent` in compact text on the latest outgoing message and on any earlier outgoing messages not yet in the `Read` state.
- **Swipe-to-Reply, Reactions & Delete**: Horizontal drag gesture to quote any message, emoji reactions, delete for me / everyone, and per-chat local history clearing.
- **Most Important Person (MIP)**: One MIP per user inside an accepted, unblocked chat, delivered as an animated crimson wax-seal letter with a 24-hour quiet cooldown after unlinking.
- **Mood Cats**: Custom Canvas-drawn mood cats (`happy`, `sad`, `tired`, `bored`, `angry`) with distinct haptic waveforms and optional `res/raw/cat_<mood>` sound playback (falls back cleanly when audio files are omitted).
- **Safety & Account Controls**: Block/unblock, report with reason + optional excerpt, plain-language Privacy Policy & Terms (`Draft for legal review`), and two-step in-app account deletion.

## Setup & Build

1. Configure Firebase (`app/google-services.json`, `app/src/main/res/values/firebase_applet_config.xml`, and optional `FIREBASE_DATABASE_URL` in `.env` if your RTDB URL differs from `https://<project-id>-default-rtdb.firebaseio.com`).
2. Deploy security rules:
   ```bash
   firebase deploy --only firestore:rules,database
   ```
3. Deploy `vercel-backend/` to Vercel (works on Firebase Spark free plan without Blaze) with `FIREBASE_SERVICE_ACCOUNT_JSON` set in Vercel Environment Variables, then add `BACKEND_PUSH_URL=https://<your-vercel-domain>/api/notify` (and optionally `BACKEND_TURN_FUNCTION_URL=https://<your-vercel-domain>/api/turn`) in the **Secrets panel in AI Studio** (or `.env`).
4. Add optional cat audio assets to `app/src/main/res/raw/` named `cat_happy`, `cat_sad`, `cat_tired`, `cat_bored`, `cat_angry` (`.ogg` or `.mp3`). Missing files degrade safely without errors.
5. Run Firestore security rules tests:
   ```bash
   FIRESTORE_EMULATOR_HOST="127.0.0.1:8085" node --test firestore.test.js
   ```
5. Run unit & Robolectric tests:
   ```bash
   gradle :app:testDebugUnitTest
   ```
6. Build APK (release builds enable R8 shrinking/obfuscation and log stripping):
   ```bash
   gradle :app:assembleDebug
   ```
