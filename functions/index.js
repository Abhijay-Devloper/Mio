const { onDocumentCreated, onDocumentWritten } = require("firebase-functions/v2/firestore");
const { onValueWritten } = require("firebase-functions/v2/database");
const { onRequest } = require("firebase-functions/v2/https");
const admin = require("firebase-admin");
const { getFirestore } = require("firebase-admin/firestore");
const { getDatabase } = require("firebase-admin/database");

admin.initializeApp();

const DATABASE_ID =
  process.env.FIRESTORE_DATABASE_ID ||
  "ai-studio-android-mio-5e3fa961-277f-49f2-a353-460e76153863";

const SAFE_ID_REGEX = /^[a-zA-Z0-9_-]{1,128}$/;

const DEFAULT_RTDB_URL =
  "https://gen-lang-client-0609526912-default-rtdb.asia-southeast1.firebasedatabase.app";

const DEFAULT_RTDB_INSTANCE =
  process.env.FIREBASE_DATABASE_INSTANCE ||
  "gen-lang-client-0609526912-default-rtdb";

const DEFAULT_FUNCTION_REGION = "asia-southeast1";

const CHAT_FCM_TTL_MS = 28 * 24 * 60 * 60 * 1000;
const CALL_FCM_TTL_MS = 45 * 1000;

let turnCredentialsCache = null;
let turnCredentialsExpiry = 0;

function resolveNotificationBody() {
  return "Sent you a message !";
}

function getRtdbInstance() {
  const url = process.env.FIREBASE_DATABASE_URL || DEFAULT_RTDB_URL;
  return getDatabase(url.trim());
}

function isUserNotificationsAllowed(userSnap, { isCall = false } = {}) {
  if (!userSnap || !userSnap.exists) return false;
  if (userSnap.get("notificationsEnabled") === false) return false;
  if (isCall && userSnap.get("callNotificationsEnabled") === false) return false;
  const muteAllUntilMs = Number(userSnap.get("muteAllUntilMs") || 0);
  if (muteAllUntilMs > Date.now()) return false;
  return true;
}

async function clearStaleFcmTokenIfUnregistered(db, uid, error) {
  const code = error && error.code ? String(error.code) : "unknown";
  if (
    code === "messaging/registration-token-not-registered" ||
    code === "messaging/invalid-registration-token"
  ) {
    await db.collection("users").doc(uid).update({
      fcmToken: "",
    }).catch(() => {});
    return true;
  }
  return false;
}

async function getOrFetchTurnCredentials() {
  const now = Date.now();
  if (turnCredentialsCache && now < turnCredentialsExpiry) {
    return turnCredentialsCache;
  }

  const apiKey = (process.env.METERED_API_KEY || "").trim();
  const rawDomain = (process.env.METERED_APP_DOMAIN || "mio.metered.live").trim();
  const domain = rawDomain.replace(/^https?:\/\//i, "").replace(/\/+$/, "");

  if (!apiKey || apiKey === "METERED_API_KEY_DEFAULT_VALUE") {
    console.warn("METERED_API_KEY is not configured on backend environment");
    return [];
  }

  const url = `https://${domain}/api/v1/turn/credentials?apiKey=${apiKey}`;
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`Metered API returned status ${response.status}`);
  }

  const rawJson = await response.json();
  if (!Array.isArray(rawJson)) {
    return [];
  }

  const filtered = rawJson.map((item) => ({
    urls: item.urls,
    username: item.username || "",
    credential: item.credential || "",
  }));

  turnCredentialsCache = filtered;
  turnCredentialsExpiry = now + 15 * 60 * 1000;

  try {
    const rtdb = getRtdbInstance();
    await rtdb.ref("turnCredentials").set({
      updatedAt: admin.database.ServerValue.TIMESTAMP,
      servers: filtered,
    });
  } catch (err) {
    console.warn("Could not mirror turnCredentials to RTDB:", err && err.message ? err.message : err);
  }

  return filtered;
}

async function syncChatDocMembershipToRtdb(chatId, afterData, beforeData) {
  const source = afterData || beforeData;
  if (!source || !Array.isArray(source.participantUids) || source.participantUids.length !== 2) {
    return;
  }
  const [uidA, uidB] = source.participantUids.map((u) => String(u || ""));
  if (!SAFE_ID_REGEX.test(uidA) || !SAFE_ID_REGEX.test(uidB) || uidA === uidB) {
    return;
  }

  const isActiveAccepted =
    Boolean(afterData) &&
    afterData.status === "accepted" &&
    (!Array.isArray(afterData.blockedBy) || afterData.blockedBy.length === 0);

  const rtdb = getRtdbInstance();
  const updates = {};
  if (isActiveAccepted) {
    updates[`/acceptedPeers/${uidA}/${uidB}`] = true;
    updates[`/acceptedPeers/${uidB}/${uidA}`] = true;
    updates[`/chatMembers/${chatId}/${uidA}`] = true;
    updates[`/chatMembers/${chatId}/${uidB}`] = true;
  } else {
    updates[`/acceptedPeers/${uidA}/${uidB}`] = null;
    updates[`/acceptedPeers/${uidB}/${uidA}`] = null;
    updates[`/chatMembers/${chatId}/${uidA}`] = null;
    updates[`/chatMembers/${chatId}/${uidB}`] = null;
    updates[`/typing/${chatId}/${uidA}`] = null;
    updates[`/typing/${chatId}/${uidB}`] = null;
  }

  await rtdb.ref().update(updates);
}

/**
 * Dispatches incoming chat notifications with both an Android system notification payload
 * (so OEM battery-restricted / closed apps still render the alert via Play Services) AND
 * high-priority data payload (so foreground sessions suppress active chat & sync relay docs).
 */
exports.onNewChatMessage = onDocumentCreated(
  {
    document: "chats/{chatId}/messages/{messageId}",
    database: DATABASE_ID,
  },
  async (event) => {
    const snapshot = event.data;
    if (!snapshot) return;

    const message = snapshot.data() || {};
    const chatId = String(message.chatId || event.params.chatId || "");
    const messageId = String(event.params.messageId || "");
    const senderId = String(message.senderUid || message.senderId || "");
    const receiverId = String(message.recipientUid || message.receiverId || "");

    if (
      !SAFE_ID_REGEX.test(chatId) ||
      !SAFE_ID_REGEX.test(messageId) ||
      !SAFE_ID_REGEX.test(senderId) ||
      !SAFE_ID_REGEX.test(receiverId)
    ) {
      return;
    }
    if (senderId === receiverId) {
      return;
    }

    const db = getFirestore(DATABASE_ID);
    const [receiverSnap, chatSnap, senderSnap] = await Promise.all([
      db.collection("users").doc(receiverId).get(),
      db.collection("chats").doc(chatId).get(),
      db.collection("users").doc(senderId).get(),
    ]);

    if (chatSnap.exists) {
      const chatData = chatSnap.data();
      if (
        chatData &&
        chatData.status === "accepted" &&
        (!Array.isArray(chatData.blockedBy) || chatData.blockedBy.length === 0)
      ) {
        await syncChatDocMembershipToRtdb(chatId, chatData, null).catch((err) => {
          console.warn("RTDB membership sync on message skipped", err && err.code ? err.code : err);
        });
      }
    }

    if (!isUserNotificationsAllowed(receiverSnap, { isCall: false })) {
      return;
    }

    const fcmToken = receiverSnap.get("fcmToken");
    if (typeof fcmToken !== "string" || fcmToken.trim().length === 0) {
      return;
    }

    if (chatSnap.exists) {
      const blockedBy = chatSnap.get("blockedBy");
      if (Array.isArray(blockedBy) && blockedBy.length > 0) {
        return;
      }
    }

    const participantNames = chatSnap.exists ? chatSnap.get("participantDisplayNames") : null;
    const participantUsernames = chatSnap.exists ? chatSnap.get("participantUsernames") : null;
    const profileDisplayName =
      senderSnap.exists && typeof senderSnap.get("displayName") === "string"
        ? senderSnap.get("displayName").trim()
        : "";
    const chatDisplayName =
      participantNames && typeof participantNames[senderId] === "string"
        ? participantNames[senderId].trim()
        : "";
    const senderUsername =
      (participantUsernames && typeof participantUsernames[senderId] === "string" && participantUsernames[senderId].trim()) ||
      (senderSnap.exists && typeof senderSnap.get("username") === "string" ? senderSnap.get("username").trim() : "");
    const senderDisplayName = profileDisplayName || chatDisplayName;

    const title = (senderDisplayName || senderUsername || "Mio").slice(0, 60);
    const body = resolveNotificationBody();

    const fcmMessage = {
      token: fcmToken.trim(),
      notification: {
        title,
        body,
      },
      android: {
        priority: "high",
        ttl: CHAT_FCM_TTL_MS,
        notification: {
          channelId: "chat_messages",
          tag: chatId,
          icon: "ic_notification_mio",
          color: "#FE8492",
          sound: "default",
          defaultVibrateTimings: true,
          notificationPriority: "PRIORITY_MAX",
        },
      },
      data: {
        type: "new_message",
        chatId,
        open_chat_id: chatId,
        messageId,
        senderId,
        senderDisplayName: senderDisplayName.slice(0, 60),
        senderUsername: senderUsername.slice(0, 32),
        senderName: title,
        messageText: body,
        sentAtMs: String(Date.now()),
      },
    };

    try {
      await admin.messaging().send(fcmMessage);
    } catch (error) {
      if (await clearStaleFcmTokenIfUnregistered(db, receiverId, error)) {
        return;
      }
      const code = error && error.code ? String(error.code) : "unknown";
      console.error("Failed to dispatch FCM notification", { code, chatId });
      throw error;
    }
  }
);

async function dispatchCallPushNotification({ userId, callId, afterData, beforeData, transport }) {
  if (!SAFE_ID_REGEX.test(userId) || !SAFE_ID_REGEX.test(callId)) return;

  const db = getFirestore(DATABASE_ID);
  const receiverSnap = await db.collection("users").doc(userId).get();
  if (!receiverSnap.exists) return;

  const fcmToken = receiverSnap.get("fcmToken");
  if (typeof fcmToken !== "string" || fcmToken.trim().length === 0) {
    return;
  }

  const isRinging = Boolean(afterData) && afterData.status === "ringing";
  const wasRinging = Boolean(beforeData) && beforeData.status === "ringing";

  if (isRinging) {
    if (!isUserNotificationsAllowed(receiverSnap, { isCall: true })) {
      return;
    }

    const callerId = String(afterData.callerId || "");
    const chatId = String(afterData.chatId || "");
    if (!SAFE_ID_REGEX.test(callerId) || callerId === userId) return;

    const callerName = String(afterData.callerName || afterData.callerUsername || "Mio").trim().slice(0, 60);
    const callerUsername = String(afterData.callerUsername || "").trim().slice(0, 32);
    const callType = afterData.type === "video" ? "video" : "audio";
    const createdAtMs =
      typeof afterData.createdAt === "number"
        ? String(afterData.createdAt)
        : String(Date.now());

    const fcmCallMessage = {
      token: fcmToken.trim(),
      android: {
        priority: "high",
        ttl: CALL_FCM_TTL_MS,
      },
      data: {
        type: "incoming_call",
        callId,
        chatId,
        callerId,
        callerName,
        callerUsername,
        callType,
        transport,
        createdAtMs,
        sentAtMs: String(Date.now()),
      },
    };

    try {
      await admin.messaging().send(fcmCallMessage);
    } catch (error) {
      if (await clearStaleFcmTokenIfUnregistered(db, userId, error)) return;
      const code = error && error.code ? String(error.code) : "unknown";
      console.error("Failed to dispatch incoming call FCM", { code, callId });
    }
    return;
  }

  // Cancel any ringing notification when invite is deleted or moves away from ringing
  const shouldCancel = !afterData || (wasRinging && afterData.status !== "ringing");
  if (shouldCancel) {
    const fcmCancelMessage = {
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
    };
    try {
      await admin.messaging().send(fcmCancelMessage);
    } catch (error) {
      await clearStaleFcmTokenIfUnregistered(db, userId, error);
    }
  }
}

/**
 * High-priority FCM trigger for incoming WebRTC calls signaled over RTDB.
 */
exports.onIncomingCallRtdb = onValueWritten(
  {
    ref: "/incomingCalls/{userId}/{callId}",
    instance: DEFAULT_RTDB_INSTANCE,
    region: DEFAULT_FUNCTION_REGION,
  },
  async (event) => {
    const userId = String(event.params.userId || "");
    const callId = String(event.params.callId || "");
    const afterData = event.data && event.data.after ? event.data.after.val() : null;
    const beforeData = event.data && event.data.before ? event.data.before.val() : null;

    await dispatchCallPushNotification({
      userId,
      callId,
      afterData,
      beforeData,
      transport: "RTDB",
    });
  }
);

/**
 * High-priority FCM trigger for incoming WebRTC calls signaled via Firestore fallback.
 */
exports.onIncomingCallFirestore = onDocumentWritten(
  {
    document: "users/{userId}/incomingCalls/{callId}",
    database: DATABASE_ID,
  },
  async (event) => {
    const userId = String(event.params.userId || "");
    const callId = String(event.params.callId || "");
    const afterData = event.data && event.data.after && event.data.after.exists
      ? event.data.after.data()
      : null;
    const beforeData = event.data && event.data.before && event.data.before.exists
      ? event.data.before.data()
      : null;

    await dispatchCallPushNotification({
      userId,
      callId,
      afterData,
      beforeData,
      transport: "FIRESTORE",
    });
  }
);

/**
 * Mirrors accepted, unblocked 1:1 chat membership into RTDB (/acceptedPeers & /chatMembers)
 * so RTDB security rules can strictly authorize /status/{uid} and /typing/{chatId}/{uid}
 * reads without allowing clients to forge membership.
 */
exports.syncChatMembershipToRtdb = onDocumentWritten(
  {
    document: "chats/{chatId}",
    database: DATABASE_ID,
  },
  async (event) => {
    const chatId = String(event.params.chatId || "");
    if (!SAFE_ID_REGEX.test(chatId)) return;

    const afterData = event.data && event.data.after && event.data.after.exists
      ? event.data.after.data()
      : null;
    const beforeData = event.data && event.data.before && event.data.before.exists
      ? event.data.before.data()
      : null;

    await syncChatDocMembershipToRtdb(chatId, afterData, beforeData);
  }
);

/**
 * HTTPS endpoint to provide ephemeral TURN credentials to authenticated Firebase users.
 * The master Metered API key stays safely in backend environment variables.
 */
exports.getTurnCredentials = onRequest(
  {
    region: DEFAULT_FUNCTION_REGION,
    cors: true,
  },
  async (req, res) => {
    if (req.method !== "GET" && req.method !== "POST") {
      res.status(405).json({ error: "Method Not Allowed" });
      return;
    }

    const authHeader = req.headers.authorization || "";
    if (!authHeader.startsWith("Bearer ")) {
      res.status(401).json({ error: "Unauthorized: Missing Bearer token" });
      return;
    }

    const token = authHeader.split("Bearer ")[1].trim();
    try {
      await admin.auth().verifyIdToken(token);
    } catch (err) {
      res.status(401).json({ error: "Unauthorized: Invalid Firebase ID token" });
      return;
    }

    try {
      const servers = await getOrFetchTurnCredentials();
      res.status(200).json({ servers });
    } catch (err) {
      console.error("Failed to fetch TURN credentials on backend:", err);
      res.status(500).json({ error: "Failed to fetch TURN credentials" });
    }
  }
);
