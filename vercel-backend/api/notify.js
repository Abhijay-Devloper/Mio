const admin = require("firebase-admin");
const { getFirestore } = require("firebase-admin/firestore");

const SAFE_ID_REGEX = /^[a-zA-Z0-9_-]{1,128}$/;
const DATABASE_ID =
  process.env.FIRESTORE_DATABASE_ID ||
  "ai-studio-android-mio-5e3fa961-277f-49f2-a353-460e76153863";

const CHAT_FCM_TTL_MS = 28 * 24 * 60 * 60 * 1000;
const CALL_FCM_TTL_MS = 45 * 1000;

function ensureFirebaseAdmin() {
  if (admin.apps.length > 0) return;

  const rawJson = (process.env.FIREBASE_SERVICE_ACCOUNT_JSON || "").trim();
  if (!rawJson) {
    throw new Error("Missing FIREBASE_SERVICE_ACCOUNT_JSON environment variable");
  }

  const serviceAccount = JSON.parse(rawJson);
  admin.initializeApp({
    credential: admin.credential.cert(serviceAccount),
  });
}

function isUserNotificationsAllowed(userSnap, { isCall = false } = {}) {
  if (!userSnap || !userSnap.exists) return false;
  if (userSnap.get("notificationsEnabled") === false) return false;
  if (isCall && userSnap.get("callNotificationsEnabled") === false) return false;
  const muteAllUntilMs = Number(userSnap.get("muteAllUntilMs") || 0);
  if (muteAllUntilMs > Date.now()) return false;
  return true;
}

async function clearStaleTokenIfUnregistered(db, uid, error) {
  const code = error && error.code ? String(error.code) : "";
  if (
    code === "messaging/registration-token-not-registered" ||
    code === "messaging/invalid-registration-token"
  ) {
    await db.collection("users").doc(uid).update({ fcmToken: "" }).catch(() => {});
    return true;
  }
  return false;
}

module.exports = async function handler(req, res) {
  if (req.method !== "POST") {
    res.status(405).json({ error: "Method Not Allowed" });
    return;
  }

  try {
    ensureFirebaseAdmin();
  } catch (err) {
    console.error("Admin init error:", err.message);
    res.status(500).json({ error: "Server configuration error" });
    return;
  }

  const authHeader = req.headers.authorization || "";
  if (!authHeader.startsWith("Bearer ")) {
    res.status(401).json({ error: "Unauthorized" });
    return;
  }

  const idToken = authHeader.slice(7).trim();
  let senderUid;
  try {
    const decoded = await admin.auth().verifyIdToken(idToken);
    senderUid = decoded.uid;
  } catch (_err) {
    res.status(401).json({ error: "Invalid Firebase ID token" });
    return;
  }

  const body = typeof req.body === "string" ? JSON.parse(req.body || "{}") : req.body || {};
  const eventType = String(body.type || "");
  const db = getFirestore(DATABASE_ID);

  if (eventType === "new_message") {
    const chatId = String(body.chatId || "");
    const messageId = String(body.messageId || "");
    const recipientUid = String(body.recipientUid || "");

    if (
      !SAFE_ID_REGEX.test(chatId) ||
      !SAFE_ID_REGEX.test(messageId) ||
      !SAFE_ID_REGEX.test(recipientUid) ||
      recipientUid === senderUid
    ) {
      res.status(400).json({ error: "Invalid message payload" });
      return;
    }

    const [chatSnap, receiverSnap, senderSnap] = await Promise.all([
      db.collection("chats").doc(chatId).get(),
      db.collection("users").doc(recipientUid).get(),
      db.collection("users").doc(senderUid).get(),
    ]);

    if (!chatSnap.exists) {
      res.status(404).json({ error: "Chat not found" });
      return;
    }

    const participants = chatSnap.get("participantUids") || [];
    const blockedBy = chatSnap.get("blockedBy") || [];
    if (
      !Array.isArray(participants) ||
      !participants.includes(senderUid) ||
      !participants.includes(recipientUid) ||
      chatSnap.get("status") !== "accepted" ||
      (Array.isArray(blockedBy) && blockedBy.length > 0)
    ) {
      res.status(403).json({ error: "Not permitted" });
      return;
    }

    if (!isUserNotificationsAllowed(receiverSnap, { isCall: false })) {
      res.status(200).json({ skipped: true, reason: "muted_or_disabled" });
      return;
    }

    const fcmToken = receiverSnap.get("fcmToken");
    if (typeof fcmToken !== "string" || !fcmToken.trim()) {
      res.status(200).json({ skipped: true, reason: "no_token" });
      return;
    }

    const senderDisplayName = (
      (senderSnap.exists && senderSnap.get("displayName")) ||
      body.senderDisplayName ||
      ""
    )
      .toString()
      .trim()
      .slice(0, 60);
    const senderUsername = (
      (senderSnap.exists && senderSnap.get("username")) ||
      body.senderUsername ||
      ""
    )
      .toString()
      .trim()
      .slice(0, 32);
    const title = senderDisplayName || senderUsername || "Mio";

    // High-priority data-only FCM wakes MioFirebaseMessagingService even when the screen is off,
    // allowing immediate heads-up notification AND automatic "Delivered" receipt + relay cleanup.
    const fcmMessage = {
      token: fcmToken.trim(),
      android: {
        priority: "high",
        ttl: CHAT_FCM_TTL_MS,
      },
      data: {
        type: "new_message",
        chatId,
        open_chat_id: chatId,
        messageId,
        senderId: senderUid,
        senderDisplayName,
        senderUsername,
        senderName: title,
        sentAtMs: String(Date.now()),
      },
    };

    try {
      await admin.messaging().send(fcmMessage);
      res.status(200).json({ sent: true });
    } catch (err) {
      await clearStaleTokenIfUnregistered(db, recipientUid, err);
      res.status(502).json({ error: "FCM send failed", code: err && err.code ? err.code : "unknown" });
    }
    return;
  }

  if (eventType === "incoming_call") {
    const callId = String(body.callId || "");
    const chatId = String(body.chatId || "");
    const receiverUid = String(body.receiverUid || "");
    const callType = body.callType === "video" ? "video" : "audio";

    if (
      !SAFE_ID_REGEX.test(callId) ||
      !SAFE_ID_REGEX.test(chatId) ||
      !SAFE_ID_REGEX.test(receiverUid) ||
      receiverUid === senderUid
    ) {
      res.status(400).json({ error: "Invalid call payload" });
      return;
    }

    const [chatSnap, receiverSnap, senderSnap] = await Promise.all([
      db.collection("chats").doc(chatId).get(),
      db.collection("users").doc(receiverUid).get(),
      db.collection("users").doc(senderUid).get(),
    ]);

    if (!chatSnap.exists) {
      res.status(404).json({ error: "Chat not found" });
      return;
    }

    const participants = chatSnap.get("participantUids") || [];
    const blockedBy = chatSnap.get("blockedBy") || [];
    if (
      !Array.isArray(participants) ||
      !participants.includes(senderUid) ||
      !participants.includes(receiverUid) ||
      chatSnap.get("status") !== "accepted" ||
      (Array.isArray(blockedBy) && blockedBy.length > 0)
    ) {
      res.status(403).json({ error: "Not permitted" });
      return;
    }

    if (!isUserNotificationsAllowed(receiverSnap, { isCall: true })) {
      res.status(200).json({ skipped: true, reason: "calls_muted_or_disabled" });
      return;
    }

    const fcmToken = receiverSnap.get("fcmToken");
    if (typeof fcmToken !== "string" || !fcmToken.trim()) {
      res.status(200).json({ skipped: true, reason: "no_token" });
      return;
    }

    const callerName = (
      (senderSnap.exists && senderSnap.get("displayName")) ||
      body.callerName ||
      body.callerUsername ||
      "Mio"
    )
      .toString()
      .trim()
      .slice(0, 60);
    const callerUsername = (
      (senderSnap.exists && senderSnap.get("username")) ||
      body.callerUsername ||
      ""
    )
      .toString()
      .trim()
      .slice(0, 32);

    try {
      await admin.messaging().send({
        token: fcmToken.trim(),
        android: {
          priority: "high",
          ttl: CALL_FCM_TTL_MS,
        },
        data: {
          type: "incoming_call",
          callId,
          chatId,
          callerId: senderUid,
          callerName,
          callerUsername,
          callType,
          transport: "RTDB",
          createdAtMs: String(Date.now()),
          sentAtMs: String(Date.now()),
        },
      });
      res.status(200).json({ sent: true });
    } catch (err) {
      await clearStaleTokenIfUnregistered(db, receiverUid, err);
      res.status(502).json({ error: "FCM call send failed", code: err && err.code ? err.code : "unknown" });
    }
    return;
  }

  if (eventType === "call_ended") {
    const callId = String(body.callId || "");
    const receiverUid = String(body.receiverUid || "");

    if (!SAFE_ID_REGEX.test(callId) || !SAFE_ID_REGEX.test(receiverUid)) {
      res.status(400).json({ error: "Invalid call_ended payload" });
      return;
    }

    const receiverSnap = await db.collection("users").doc(receiverUid).get();
    const fcmToken = receiverSnap.exists ? receiverSnap.get("fcmToken") : "";
    if (typeof fcmToken !== "string" || !fcmToken.trim()) {
      res.status(200).json({ skipped: true });
      return;
    }

    try {
      await admin.messaging().send({
        token: fcmToken.trim(),
        android: {
          priority: "high",
          ttl: 30 * 1000,
        },
        data: {
          type: "call_ended",
          callId,
          sentAtMs: String(Date.now()),
        },
      });
      res.status(200).json({ sent: true });
    } catch (err) {
      await clearStaleTokenIfUnregistered(db, receiverUid, err);
      res.status(200).json({ sent: false });
    }
    return;
  }

  res.status(400).json({ error: "Unsupported event type" });
};
