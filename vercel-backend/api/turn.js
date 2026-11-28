const admin = require("firebase-admin");

let turnCredentialsCache = null;
let turnCredentialsExpiry = 0;

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

module.exports = async function handler(req, res) {
  if (req.method !== "GET" && req.method !== "POST") {
    res.status(405).json({ error: "Method Not Allowed" });
    return;
  }

  try {
    ensureFirebaseAdmin();
  } catch (_err) {
    res.status(500).json({ error: "Server configuration error" });
    return;
  }

  const authHeader = req.headers.authorization || "";
  if (!authHeader.startsWith("Bearer ")) {
    res.status(401).json({ error: "Unauthorized" });
    return;
  }

  try {
    await admin.auth().verifyIdToken(authHeader.slice(7).trim());
  } catch (_err) {
    res.status(401).json({ error: "Invalid Firebase ID token" });
    return;
  }

  const now = Date.now();
  if (turnCredentialsCache && now < turnCredentialsExpiry) {
    res.status(200).json({ servers: turnCredentialsCache });
    return;
  }

  const apiKey = (process.env.METERED_API_KEY || "").trim();
  const domain = (process.env.METERED_APP_DOMAIN || "mio.metered.live")
    .trim()
    .replace(/^https?:\/\//i, "")
    .replace(/\/+$/, "");

  if (!apiKey) {
    res.status(200).json({ servers: [] });
    return;
  }

  try {
    const response = await fetch(`https://${domain}/api/v1/turn/credentials?apiKey=${apiKey}`);
    if (!response.ok) {
      res.status(502).json({ error: "Failed to fetch TURN credentials" });
      return;
    }
    const rawJson = await response.json();
    const servers = Array.isArray(rawJson)
      ? rawJson.map((item) => ({
          urls: item.urls,
          username: item.username || "",
          credential: item.credential || "",
        }))
      : [];
    turnCredentialsCache = servers;
    turnCredentialsExpiry = now + 15 * 60 * 1000;
    res.status(200).json({ servers });
  } catch (_err) {
    res.status(500).json({ error: "Failed to fetch TURN credentials" });
  }
};
