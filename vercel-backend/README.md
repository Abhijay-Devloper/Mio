# Mio Vercel Backend (Free Firebase Spark Push + TURN Relay)

This standalone backend lets Mio deliver **high-priority FCM push notifications** (even when the recipient's screen is off or the app is closed) and **ephemeral TURN credentials** for free without upgrading Firebase to the Blaze plan.

## 1. Environment Variables in Vercel
In your Vercel Project → **Settings → Environment Variables**, add:

- `FIREBASE_SERVICE_ACCOUNT_JSON`: The full JSON contents downloaded from **Firebase Console → Project Settings → Service Accounts → Generate new private key**.
- `FIRESTORE_DATABASE_ID`: `ai-studio-android-mio-5e3fa961-277f-49f2-a353-460e76153863`
- `METERED_API_KEY` *(optional, for TURN relay calls)*: Your Metered API key.
- `METERED_APP_DOMAIN` *(optional)*: Your Metered domain (e.g. `mio.metered.live`).

## 2. Deploy to Vercel
Deploy the `vercel-backend` directory to Vercel (via GitHub import with Root Directory = `vercel-backend`, or `cd vercel-backend && vercel --prod`).

## 3. Connect Mio Android App (Secrets Panel in AI Studio)
In AI Studio **Secrets** (or `.env`), set:
```
BACKEND_PUSH_URL=https://YOUR-VERCEL-PROJECT.vercel.app/api/notify
BACKEND_TURN_FUNCTION_URL=https://YOUR-VERCEL-PROJECT.vercel.app/api/turn
```

## Why `android.priority = "high"` + `data`-only FCM is used
Sending high-priority `data`-only payloads (without a top-level `notification` object) causes Google Play Services to wake `MioFirebaseMessagingService.onMessageReceived` immediately even while the phone is locked/screen-off, so Mio:
1. Rings immediately with `CallStyle` for incoming calls or shows an `IMPORTANCE_HIGH` heads-up chat alert.
2. Decrypts the incoming message and sends the **Delivered (`✓✓`)** receipt right away without waiting for the user to open the app.
