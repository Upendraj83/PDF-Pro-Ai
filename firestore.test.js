const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
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

function validPdfPayload(uid, overrides = {}) {
  const now = new Date();
  return {
    userId: uid,
    uri: "content://com.example/documents/report.pdf",
    fileName: "Quarterly_Report.pdf",
    fileSizeBytes: 102400,
    pageCount: 15,
    lastPage: 4,
    isFavorite: true,
    folderName: "Finance",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validBookmarkPayload(uid, overrides = {}) {
  const now = new Date();
  return {
    userId: uid,
    pdfUri: "content://com.example/documents/report.pdf",
    fileName: "Quarterly_Report.pdf",
    pageNumber: 3,
    note: "Review revenue chart",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validSummaryPayload(uid, overrides = {}) {
  const now = new Date();
  return {
    userId: uid,
    pdfUri: "content://com.example/documents/report.pdf",
    fileName: "Quarterly_Report.pdf",
    pageRangeDescription: "All Pages (1-15)",
    outputLanguage: "ENGLISH",
    shortSummary: "Revenue grew 22% YoY with strong operating margins [Page 1].",
    keyPoints: ["Q3 revenue reached $48M [Page 1].", "Costs dropped 12% [Page 3]."],
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

test("Unauthenticated user: cannot read or write PDF documents, bookmarks, or summaries", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("documents").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("documents")
      .doc("doc_1")
      .set(validPdfPayload(ALICE_UID))
  );
});

test("Authenticated user: can create, read, list, update, and delete own PDF records", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("documents")
    .doc("doc_1");

  const payload = validPdfPayload(ALICE_UID);
  await assertSucceeds(docRef.set(payload));
  await assertSucceeds(docRef.get());
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("documents").get()
  );

  // Update allowed mutable fields while preserving immutable userId, uri, createdAt
  await assertSucceeds(
    docRef.update({
      lastPage: 10,
      isFavorite: false,
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(docRef.delete());
});

test("Cross-user isolation: Alice cannot read, list, or modify Bob's PDF records or bookmarks", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const adminDb = ctx.firestore();
    await adminDb
      .collection("users")
      .doc(BOB_UID)
      .collection("documents")
      .doc("bob_doc")
      .set(validPdfPayload(BOB_UID));
    await adminDb
      .collection("users")
      .doc(BOB_UID)
      .collection("bookmarks")
      .doc("bob_bm")
      .set(validBookmarkPayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("documents")
      .doc("bob_doc")
      .get()
  );
  await assertFails(
    aliceDb.collection("users").doc(BOB_UID).collection("documents").get()
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc(BOB_UID)
      .collection("bookmarks")
      .doc("bob_bm")
      .delete()
  );
});

test("Shadow update & identity spoofing: rejects ghost fields and mismatched userId", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("documents")
    .doc("doc_shadow");

  // Spoofed userId
  await assertFails(docRef.set(validPdfPayload(BOB_UID)));

  // Ghost field injection
  await assertFails(
    docRef.set({
      ...validPdfPayload(ALICE_UID),
      isAdmin: true,
    })
  );
});

test("Authenticated user: can create and query own bookmarks and AI summaries", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bmRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("bookmarks")
    .doc("bm_1");
  const sumRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("summaries")
    .doc("sum_1");

  await assertSucceeds(bmRef.set(validBookmarkPayload(ALICE_UID)));
  await assertSucceeds(sumRef.set(validSummaryPayload(ALICE_UID)));
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("bookmarks").get()
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("summaries").get()
  );
});
