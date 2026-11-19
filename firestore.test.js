const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const OWNER_UID = "owner_honest_001";
const OTHER_UID = "intruder_999";

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

function pastDate(offsetMs = 2000) {
  return new Date(Date.now() - offsetMs);
}

test("Unauthenticated access is denied across all owner collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection(`users/${OWNER_UID}/accounts`).get());
  await assertFails(unauthDb.collection(`users/${OWNER_UID}/settings`).get());
  await assertFails(unauthDb.collection(`users/${OWNER_UID}/commands`).get());
  await assertFails(unauthDb.collection(`users/${OWNER_UID}/mediaJobs`).get());
  await assertFails(unauthDb.collection(`users/${OWNER_UID}/platforms`).get());
  await assertFails(unauthDb.collection(`users/${OWNER_UID}/events`).get());
  await assertFails(unauthDb.collection(`users/${OWNER_UID}/auditLogs`).get());
});

test("Cross-user isolation blocks other authenticated users from reading or writing", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  const otherDb = testEnv.authenticatedContext(OTHER_UID).firestore();
  const now = pastDate();

  await assertSucceeds(
    ownerDb.collection(`users/${OWNER_UID}/settings`).doc("owner_policy").set({
      ownerUid: OWNER_UID,
      globalFleetState: "ONLINE",
      maintenanceMessage: "Maintenance active",
      allowNewLinkRequests: true,
      defaultDailyQuota: 250,
      maxMediaSizeMb: 16,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(otherDb.collection(`users/${OWNER_UID}/settings`).doc("owner_policy").get());
  await assertFails(otherDb.collection(`users/${OWNER_UID}/accounts`).get());
  await assertFails(
    otherDb.collection(`users/${OWNER_UID}/settings`).doc("owner_policy").update({
      globalFleetState: "PAUSED_ALL",
      updatedAt: pastDate(500),
    })
  );
});

test("ConnectedAccount create, partial admin update, and shadow-field rejection", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  const created = pastDate(5000);

  // Valid Kotlin-mirrored create payload (NO @DocumentId `id` field)
  await assertSucceeds(
    ownerDb.collection(`users/${OWNER_UID}/accounts`).doc("acc_honest_1").set({
      ownerUid: OWNER_UID,
      targetType: "PERSONAL_INBOX",
      countryCode: "+263",
      phoneNumber: "780549558",
      e164Number: "+263780549558",
      displayName: "Honest Tech Support",
      pairingCode: "HBP8-4K2M",
      sessionFingerprint: "a1b2c3d4e5f67890",
      pairingExpiresAtMs: Date.now() + 900000,
      connectionStatus: "AWAITING_CODE",
      operatingMode: "FULL_AI_MEDIA",
      customPhoneNumberId: "",
      role: "VIP",
      botEnabled: true,
      autoReplyEnabled: true,
      mediaDownloadEnabled: true,
      webSearchEnabled: true,
      groupDispatchEnabled: true,
      blockedCommands: [],
      dailyQuota: 500,
      messagesUsedToday: 0,
      lastActiveAtMs: Date.now(),
      createdAt: created,
      updatedAt: created,
    })
  );

  // Valid partial admin update
  await assertSucceeds(
    ownerDb.collection(`users/${OWNER_UID}/accounts`).doc("acc_honest_1").update({
      connectionStatus: "LIVE",
      role: "VIP",
      blockedCommands: [".video"],
      updatedAt: pastDate(1000),
    })
  );

  // Reject ghost `id` field
  await assertFails(
    ownerDb.collection(`users/${OWNER_UID}/accounts`).doc("acc_honest_2").set({
      id: "acc_honest_2",
      ownerUid: OWNER_UID,
      targetType: "PERSONAL_INBOX",
      countryCode: "+263",
      phoneNumber: "780549558",
      e164Number: "+263780549558",
      displayName: "Ghost",
      pairingCode: "HBP8-9999",
      sessionFingerprint: "a1b2c3d4e5f67890",
      pairingExpiresAtMs: Date.now() + 900000,
      connectionStatus: "AWAITING_CODE",
      operatingMode: "FULL_AI_MEDIA",
      customPhoneNumberId: "",
      role: "STANDARD",
      botEnabled: true,
      autoReplyEnabled: true,
      mediaDownloadEnabled: true,
      webSearchEnabled: true,
      groupDispatchEnabled: true,
      blockedCommands: [],
      dailyQuota: 100,
      messagesUsedToday: 0,
      lastActiveAtMs: Date.now(),
      createdAt: created,
      updatedAt: created,
    })
  );

  // Reject immutable createdAt mutation
  await assertFails(
    ownerDb.collection(`users/${OWNER_UID}/accounts`).doc("acc_honest_1").update({
      createdAt: pastDate(100),
      updatedAt: pastDate(100),
    })
  );
});

test("Commands, MediaJobs, Platforms, Events, and immutable AuditLogs parity", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  const now = pastDate(3000);

  await assertSucceeds(
    ownerDb.collection(`users/${OWNER_UID}/commands`).doc("cmd_play").set({
      ownerUid: OWNER_UID,
      trigger: ".play",
      title: "Play Music & Mixes",
      description: "Searches social platforms and uploads audio to WhatsApp.",
      category: "MEDIA_AUDIO",
      promptTemplate: "Search music track: {{query}}",
      usesWebSearch: true,
      commandEnabled: true,
      executionCount: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    ownerDb.collection(`users/${OWNER_UID}/mediaJobs`).doc("job_1").set({
      ownerUid: OWNER_UID,
      accountId: "acc_honest_1",
      targetLabel: "+263780549558",
      targetType: "PERSONAL_INBOX",
      commandTrigger: ".play dj onilo mix",
      query: "dj onilo mix",
      sourcePlatform: "YOUTUBE",
      mediaType: "AUDIO",
      title: "DJ Onilo Mix",
      sourceUrl: "https://example.com/mix.mp3",
      localCachePath: "",
      fileSizeBytes: 0,
      whatsappMediaId: "",
      status: "SEARCHING",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    ownerDb.collection(`users/${OWNER_UID}/mediaJobs`).doc("job_1").update({
      status: "DELIVERED",
      whatsappMediaId: "wa_media_987",
      fileSizeBytes: 4200123,
      updatedAt: pastDate(500),
    })
  );

  await assertSucceeds(
    ownerDb.collection(`users/${OWNER_UID}/platforms`).doc("plat_wa").set({
      ownerUid: OWNER_UID,
      platformKey: "WHATSAPP_CLOUD",
      name: "WhatsApp Business Cloud API",
      status: "CONFIGURED",
      endpointOrHandle: "https://graph.facebook.com/v26.0",
      autoSyncEnabled: true,
      webhookSecretHash: "sha256_demo_hash",
      lastHeartbeatAtMs: Date.now(),
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    ownerDb.collection(`users/${OWNER_UID}/events`).doc("evt_1").set({
      ownerUid: OWNER_UID,
      accountId: "acc_honest_1",
      e164Number: "+263780549558",
      commandTrigger: ".play",
      platform: "YOUTUBE",
      promptPreview: ".play dj onilo mix",
      responsePreview: "Uploaded audio to inbox",
      latencyMs: 840,
      status: "DELIVERED",
      createdAt: now,
    })
  );

  await assertSucceeds(
    ownerDb.collection(`users/${OWNER_UID}/auditLogs`).doc("log_1").set({
      ownerUid: OWNER_UID,
      actionType: "APPROVE_LINK",
      targetAccountId: "acc_honest_1",
      targetLabel: "+263780549558",
      details: "Owner verified pairing code HBP8-4K2M",
      createdAt: now,
    })
  );

  // AuditLogs are immutable: updates & deletes must fail
  await assertFails(
    ownerDb.collection(`users/${OWNER_UID}/auditLogs`).doc("log_1").update({
      details: "Tampered",
    })
  );
  await assertFails(ownerDb.collection(`users/${OWNER_UID}/auditLogs`).doc("log_1").delete());

  // Repository-aligned queries
  await assertSucceeds(ownerDb.collection(`users/${OWNER_UID}/accounts`).get());
  await assertSucceeds(ownerDb.collection(`users/${OWNER_UID}/auditLogs`).orderBy("createdAt", "desc").get());
});

test("Pro Bot Automation Policy, WebPair Account, Command Aliases & WebPair Bridge Platform", async () => {
  const ownerDb = testEnv.authenticatedContext(OWNER_UID).firestore();
  const now = pastDate(2000);

  // Extended OwnerPolicy update on existing policy doc
  await assertSucceeds(
    ownerDb.collection(`users/${OWNER_UID}/settings`).doc("owner_policy").set({
      ownerUid: OWNER_UID,
      globalFleetState: "ONLINE",
      maintenanceMessage: "Maintenance",
      allowNewLinkRequests: true,
      defaultDailyQuota: 250,
      maxMediaSizeMb: 16,
      botName: "Honest Bot Pro",
      commandPrefix: ".",
      botMode: "public",
      webPairBridgeUrl: "https://my-bridge.example.com",
      sessionId: "HONEST~SESSION123",
      readMessages: true,
      alwaysOnline: true,
      autoTyping: "inbox",
      autoRecording: "off",
      autoReact: false,
      ownerReact: true,
      autoViewStatus: true,
      autoLikeStatus: false,
      autoDownloader: true,
      antiLinkMode: "delete",
      antiDeletePath: "inbox",
      antiEditPath: "all",
      antiCallEnabled: true,
      callRejectMessage: "Calls disabled",
      antiStatusEnabled: true,
      welcomeEnabled: true,
      goodbyeEnabled: true,
      welcomeMessage: "@user joined @group",
      goodbyeMessage: "@user left @group",
      sudoNumbers: ["+263780549558"],
      bannedNumbers: [],
      createdAt: now,
      updatedAt: now,
    })
  );

  // Invalid botMode must be rejected
  await assertFails(
    ownerDb.collection(`users/${OWNER_UID}/settings`).doc("owner_policy").update({
      botMode: "hacked_mode",
      updatedAt: pastDate(500),
    })
  );

  // WebPair ConnectedAccount + Command Aliases + WEBPAIR_BRIDGE platform
  await assertSucceeds(
    ownerDb.collection(`users/${OWNER_UID}/accounts`).doc("acc_webpair_1").set({
      ownerUid: OWNER_UID,
      targetType: "PERSONAL_INBOX",
      countryCode: "+263",
      phoneNumber: "780549558",
      e164Number: "+263780549558",
      displayName: "WebPair Session",
      pairingCode: "HBP4-WXYZ",
      sessionFingerprint: "abcdef1234567890",
      pairingExpiresAtMs: Date.now() + 900000,
      connectionStatus: "LIVE",
      operatingMode: "FULL_AI_MEDIA",
      customPhoneNumberId: "",
      role: "VIP",
      botEnabled: true,
      autoReplyEnabled: true,
      mediaDownloadEnabled: true,
      webSearchEnabled: true,
      groupDispatchEnabled: true,
      blockedCommands: [],
      dailyQuota: 1000,
      messagesUsedToday: 0,
      lastActiveAtMs: Date.now(),
      linkingMethod: "WEB_PAIR",
      sessionId: "HONEST~SESSION123",
      antiLinkEnabled: true,
      antiDeleteEnabled: true,
      welcomeEnabled: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    ownerDb.collection(`users/${OWNER_UID}/commands`).doc("cmd_menu").set({
      ownerUid: OWNER_UID,
      trigger: ".menu",
      title: "Bot Command Menu",
      description: "Shows all active commands and fleet mode.",
      category: "UTILITY",
      promptTemplate: "{{query}}",
      usesWebSearch: false,
      commandEnabled: true,
      executionCount: 0,
      aliases: [".help", ".list"],
      ownerOnly: false,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    ownerDb.collection(`users/${OWNER_UID}/platforms`).doc("plat_webpair").set({
      ownerUid: OWNER_UID,
      platformKey: "WEBPAIR_BRIDGE",
      name: "WebPair & Baileys Bridge",
      status: "CONFIGURED",
      endpointOrHandle: "https://my-bridge.example.com",
      autoSyncEnabled: true,
      webhookSecretHash: "webpair-bridge",
      lastHeartbeatAtMs: Date.now(),
      createdAt: now,
      updatedAt: now,
    })
  );
});
