const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_001";
const BOB_UID = "bob_uid_002";
const EVE_UID = "eve_uid_003";
const CHAT_ID = "alice_uid_001_bob_uid_002";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function pastDate(offsetMs = 5000) {
  return new Date(Date.now() - offsetMs);
}

function futureDate(offsetMs = 86400000) {
  return new Date(Date.now() + offsetMs);
}

async function seedAcceptedChat() {
  const created = pastDate(20000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("chats").doc(CHAT_ID).set({
      participantUids: [ALICE_UID, BOB_UID],
      initiatorUid: ALICE_UID,
      status: "accepted",
      participantUsernames: { [ALICE_UID]: "alice_m", [BOB_UID]: "bob_m" },
      participantDisplayNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      participantPublicKeys: {
        [ALICE_UID]: "alice_pub_key_base64_1234567890",
        [BOB_UID]: "bob_pub_key_base64_1234567890",
      },
      blockedBy: [],
      unreadCounts: { [ALICE_UID]: 0, [BOB_UID]: 0 },
      createdAt: created,
      updatedAt: created,
    });
  });
}

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("usernames").doc("alice_m").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("chats").where("participantUids", "array-contains", ALICE_UID).get());
  await assertFails(unauthDb.collection("reports").doc("rep_1").get());
});

test("Username lookup: create-only by owner, blocks reserved names and enumeration", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastDate();

  // Reserved handles fail
  await assertFails(
    aliceDb.collection("usernames").doc("admin").set({
      uid: ALICE_UID,
      username: "admin",
      displayName: "Admin",
      publicKey: "alice_pub_key_base64_1234567890",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Valid create succeeds
  await assertSucceeds(
    aliceDb.collection("usernames").doc("alice_m").set({
      uid: ALICE_UID,
      username: "alice_m",
      displayName: "Alice",
      publicKey: "alice_pub_key_base64_1234567890",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Update is denied (create-only)
  await assertFails(
    aliceDb.collection("usernames").doc("alice_m").update({
      displayName: "Alice Renamed",
      updatedAt: pastDate(),
    })
  );

  // Exact single-doc lookup works
  await assertSucceeds(bobDb.collection("usernames").doc("alice_m").get());
  // Listing usernames must fail
  await assertFails(bobDb.collection("usernames").get());
  // Non-owner cannot delete
  await assertFails(bobDb.collection("usernames").doc("alice_m").delete());
  // Owner can delete (for account deletion)
  await assertSucceeds(aliceDb.collection("usernames").doc("alice_m").delete());
});

test("Private user profile: owner-only access, 18+ required, no cross-user read", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = pastDate();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      uid: ALICE_UID,
      username: "alice_m",
      displayName: "Alice",
      publicKey: "alice_pub_key_base64_1234567890",
      ageConfirmed: true,
      ageConfirmedAt: now,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      fcmToken: "sample_fcm_token_value_12345",
      updatedAt: pastDate(),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      fcmToken: "x".repeat(513),
      updatedAt: pastDate(),
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      fcmToken: "hijacked_token",
      updatedAt: pastDate(),
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(aliceDb.collection("users").get());
});

test("Chat lifecycle: lookup, pre-check missing chat, invite, receiver accept, both read, message, receipts, reactions, relay delete", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();
  const now = pastDate(10000);

  // 1. Both users register handles & public keys
  await assertSucceeds(
    aliceDb.collection("usernames").doc("alice_m").set({
      uid: ALICE_UID,
      username: "alice_m",
      displayName: "Alice",
      publicKey: "alice_pub_key_base64_1234567890",
      createdAt: now,
      updatedAt: now,
    })
  );
  await assertSucceeds(
    bobDb.collection("usernames").doc("bob_m").set({
      uid: BOB_UID,
      username: "bob_m",
      displayName: "Bob",
      publicKey: "bob_pub_key_base64_1234567890",
      createdAt: now,
      updatedAt: now,
    })
  );

  // 2. Sender looks up receiver exact username + checks own blocklist + checks missing chat doc
  const bobLookup = await assertSucceeds(bobDb ? aliceDb.collection("usernames").doc("bob_m").get() : Promise.reject());
  if (!bobLookup.exists || !bobLookup.data().publicKey) {
    throw new Error("Expected receiver username & publicKey doc to be readable");
  }
  const blockCheck = await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("blocks").doc(BOB_UID).get()
  );
  if (blockCheck.exists) {
    throw new Error("Expected unblocked partner");
  }
  // Critical check: participant pre-create get on non-existent chat doc succeeds with exists === false,
  // while non-participant Eve probing the same non-existent chatId is still denied!
  const missingChatSnap = await assertSucceeds(aliceDb.collection("chats").doc(CHAT_ID).get());
  if (missingChatSnap.exists) {
    throw new Error("Expected chat to not exist yet");
  }
  await assertFails(eveDb.collection("chats").doc(CHAT_ID).get());

  // 3. Sender creates pending chat request
  await assertSucceeds(
    aliceDb.collection("chats").doc(CHAT_ID).set({
      participantUids: [ALICE_UID, BOB_UID],
      initiatorUid: ALICE_UID,
      status: "pending",
      participantUsernames: { [ALICE_UID]: "alice_m", [BOB_UID]: "bob_m" },
      participantDisplayNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      participantPublicKeys: {
        [ALICE_UID]: "alice_pub_key_base64_1234567890",
        [BOB_UID]: "bob_pub_key_base64_1234567890",
      },
      blockedBy: [],
      unreadCounts: { [ALICE_UID]: 0, [BOB_UID]: 0 },
      createdAt: now,
      updatedAt: now,
    })
  );

  // 4. Receiver sees incoming pending request in their chat list query
  const bobInboxBefore = await assertSucceeds(
    bobDb.collection("chats").where("participantUids", "array-contains", BOB_UID).get()
  );
  if (bobInboxBefore.size !== 1 || bobInboxBefore.docs[0].data().status !== "pending") {
    throw new Error("Expected receiver to see pending chat request");
  }

  // Initiator cannot self-accept
  await assertFails(
    aliceDb.collection("chats").doc(CHAT_ID).update({
      status: "accepted",
      updatedAt: pastDate(),
    })
  );

  // 5. Receiver accepts request
  await assertSucceeds(
    bobDb.collection("chats").doc(CHAT_ID).update({
      status: "accepted",
      updatedAt: pastDate(),
    })
  );

  // Non-participant Eve cannot read or list
  await assertFails(eveDb.collection("chats").doc(CHAT_ID).get());
  await assertFails(eveDb.collection("chats").get());

  // 6. Both sender and receiver see the accepted chat
  const aliceChats = await assertSucceeds(
    aliceDb.collection("chats").where("participantUids", "array-contains", ALICE_UID).get()
  );
  const bobChats = await assertSucceeds(
    bobDb.collection("chats").where("participantUids", "array-contains", BOB_UID).get()
  );
  if (aliceChats.size !== 1 || aliceChats.docs[0].data().status !== "accepted" ||
      bobChats.size !== 1 || bobChats.docs[0].data().status !== "accepted") {
    throw new Error("Expected both sender and receiver to see the accepted chat");
  }

  // Own typingBy.<uid> boolean updates succeed; touching partner entry or including updatedAt fails
  await assertSucceeds(
    aliceDb.collection("chats").doc(CHAT_ID).update({
      [`typingBy.${ALICE_UID}`]: true,
    })
  );
  await assertFails(
    aliceDb.collection("chats").doc(CHAT_ID).update({
      [`typingBy.${BOB_UID}`]: true,
    })
  );
  await assertFails(
    aliceDb.collection("chats").doc(CHAT_ID).update({
      typingBy: { [ALICE_UID]: true, [BOB_UID]: false },
      updatedAt: pastDate(),
    })
  );

  // Alice sends an encrypted message
  const msgRef = aliceDb.collection("chats").doc(CHAT_ID).collection("messages").doc("msg_1");
  await assertSucceeds(
    msgRef.set({
      chatId: CHAT_ID,
      senderUid: ALICE_UID,
      recipientUid: BOB_UID,
      participantUids: [ALICE_UID, BOB_UID],
      type: "mood",
      ciphertext: "base64ciphertext==",
      nonce: "base64nonce==",
      moodId: "happy",
      status: "sent",
      deletedFor: [],
      deletedForEveryone: false,
      reactions: {},
      createdAt: now,
      updatedAt: now,
      expiresAt: futureDate(),
    })
  );

  // Editing message ciphertext or moodId after write is denied
  await assertFails(
    msgRef.update({
      ciphertext: "tampered_ciphertext==",
      updatedAt: pastDate(),
    })
  );
  await assertFails(
    msgRef.update({
      moodId: "angry",
      updatedAt: pastDate(),
    })
  );

  // Eve cannot read or delete messages
  const eveMsgRef = eveDb.collection("chats").doc(CHAT_ID).collection("messages").doc("msg_1");
  await assertFails(eveMsgRef.get());
  await assertFails(eveMsgRef.delete());

  // Bob marks delivered then read
  const bobMsgRef = bobDb.collection("chats").doc(CHAT_ID).collection("messages").doc("msg_1");
  await assertSucceeds(bobMsgRef.update({ status: "delivered", updatedAt: pastDate() }));
  await assertSucceeds(bobMsgRef.update({ status: "read", updatedAt: pastDate() }));

  // Bob adds emoji reaction
  await assertSucceeds(
    bobMsgRef.update({
      reactions: { [BOB_UID]: "❤️" },
      updatedAt: pastDate(),
    })
  );

  // Bob cannot delete-for-everyone on Alice's message
  await assertFails(
    bobMsgRef.update({
      deletedForEveryone: true,
      ciphertext: "",
      nonce: "",
      updatedAt: pastDate(),
    })
  );

  // Alice (sender) can delete-for-everyone
  await assertSucceeds(
    msgRef.update({
      deletedForEveryone: true,
      ciphertext: "",
      nonce: "",
      updatedAt: pastDate(),
    })
  );

  // Both sender and recipient can delete relay doc
  const msg2Ref = aliceDb.collection("chats").doc(CHAT_ID).collection("messages").doc("msg_2");
  await assertSucceeds(
    msg2Ref.set({
      chatId: CHAT_ID,
      senderUid: ALICE_UID,
      recipientUid: BOB_UID,
      participantUids: [ALICE_UID, BOB_UID],
      type: "text",
      ciphertext: "base64ciphertext2==",
      nonce: "base64nonce2==",
      status: "sent",
      deletedFor: [],
      deletedForEveryone: false,
      reactions: {},
      createdAt: now,
      updatedAt: now,
      expiresAt: futureDate(),
    })
  );
  await assertSucceeds(msg2Ref.delete());
  await assertSucceeds(bobMsgRef.delete());
});

test("Blocked user cannot request chat, send messages, or send MIP letters", async () => {
  await seedAcceptedChat();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Bob adds Alice to his blocklist and updates chat blockedBy
  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).collection("blocks").doc(ALICE_UID).set({
      blockedUid: ALICE_UID,
      blockedUsername: "alice_m",
      createdAt: pastDate(),
    })
  );
  await assertSucceeds(
    bobDb.collection("chats").doc(CHAT_ID).update({
      blockedBy: [BOB_UID],
      updatedAt: pastDate(),
    })
  );

  // Alice cannot send message while blocked
  await assertFails(
    aliceDb.collection("chats").doc(CHAT_ID).collection("messages").doc("msg_blocked").set({
      chatId: CHAT_ID,
      senderUid: ALICE_UID,
      recipientUid: BOB_UID,
      participantUids: [ALICE_UID, BOB_UID],
      type: "text",
      ciphertext: "cipher==",
      nonce: "nonce==",
      status: "sent",
      deletedFor: [],
      deletedForEveryone: false,
      reactions: {},
      createdAt: pastDate(),
      updatedAt: pastDate(),
      expiresAt: futureDate(),
    })
  );

  // Alice cannot send MIP while blocked
  await assertFails(
    aliceDb.collection("chats").doc(CHAT_ID).update({
      mipStatus: "pending",
      mipSenderUid: ALICE_UID,
      mipCiphertext: "cipher==",
      mipNonce: "nonce==",
      mipUpdatedAt: pastDate(),
      updatedAt: pastDate(),
    })
  );

  // Blocked pair cannot create a new chat document
  await assertFails(
    aliceDb.collection("chats").doc("alt_chat").set({
      participantUids: [ALICE_UID, BOB_UID],
      initiatorUid: ALICE_UID,
      status: "pending",
      participantUsernames: { [ALICE_UID]: "alice_m", [BOB_UID]: "bob_m" },
      participantDisplayNames: { [ALICE_UID]: "Alice", [BOB_UID]: "Bob" },
      participantPublicKeys: {
        [ALICE_UID]: "alice_pub_key_base64_1234567890",
        [BOB_UID]: "bob_pub_key_base64_1234567890",
      },
      blockedBy: [],
      unreadCounts: { [ALICE_UID]: 0, [BOB_UID]: 0 },
      createdAt: pastDate(),
      updatedAt: pastDate(),
    })
  );
});

test("MIP cooldown enforced after unlink", async () => {
  await seedAcceptedChat();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("chats").doc(CHAT_ID).update({
      mipStatus: "pending",
      mipSenderUid: ALICE_UID,
      mipCiphertext: "mip_cipher==",
      mipNonce: "mip_nonce==",
      mipUpdatedAt: pastDate(5000),
      updatedAt: pastDate(5000),
    })
  );

  await assertSucceeds(
    bobDb.collection("chats").doc(CHAT_ID).update({
      mipStatus: "accepted",
      mipUpdatedAt: pastDate(3000),
      updatedAt: pastDate(3000),
    })
  );

  await assertSucceeds(
    aliceDb.collection("chats").doc(CHAT_ID).update({
      mipStatus: "unlinked",
      mipUpdatedAt: pastDate(1000),
      updatedAt: pastDate(1000),
    })
  );

  // Immediate re-seal within 24h must fail
  await assertFails(
    aliceDb.collection("chats").doc(CHAT_ID).update({
      mipStatus: "pending",
      mipSenderUid: ALICE_UID,
      mipCiphertext: "mip_cipher_2==",
      mipNonce: "mip_nonce_2==",
      mipUpdatedAt: pastDate(500),
      updatedAt: pastDate(500),
    })
  );
});

test("Abuse report: participant can create once, non-participant denied, immutable", async () => {
  await seedAcceptedChat();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const eveDb = testEnv.authenticatedContext(EVE_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("reports").doc("rep_1").set({
      reporterUid: ALICE_UID,
      reportedUid: BOB_UID,
      chatId: CHAT_ID,
      reason: "harassment",
      excerpt: "Unwanted message excerpt",
      createdAt: pastDate(),
    })
  );

  // Non-participant Eve cannot file a report on Alice/Bob's chat
  await assertFails(
    eveDb.collection("reports").doc("rep_eve").set({
      reporterUid: EVE_UID,
      reportedUid: BOB_UID,
      chatId: CHAT_ID,
      reason: "spam",
      createdAt: pastDate(),
    })
  );

  await assertFails(bobDb.collection("reports").doc("rep_1").get());
  await assertFails(aliceDb.collection("reports").doc("rep_1").update({ reason: "spam" }));
  await assertFails(aliceDb.collection("reports").doc("rep_1").delete());
});
