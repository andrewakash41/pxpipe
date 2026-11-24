import { readFileSync } from "node:fs";
import { assertFails, assertSucceeds, initializeTestEnvironment, type RulesTestEnvironment } from "@firebase/rules-unit-testing";
import { addDoc, collection, deleteDoc, doc, getDoc, getDocs, query, setDoc, updateDoc, where } from "firebase/firestore";
import { getBytes, ref, uploadBytes } from "firebase/storage";
import { afterAll, beforeAll, beforeEach, describe, it } from "vitest";

let env: RulesTestEnvironment;

beforeAll(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-vachan",
    firestore: { rules: readFileSync(new URL("../firestore.rules", import.meta.url), "utf8") },
    storage: { rules: readFileSync(new URL("../storage.rules", import.meta.url), "utf8") },
  });
});

afterAll(async () => { await env.cleanup(); });

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, "users/alice"), { profile: { firstName: "Alice" }, consents: {} });
    await setDoc(doc(db, "profiles/alice"), { firstName: "Alice", gender: "FEMALE" });
    await setDoc(doc(db, "profiles/bob"), { firstName: "Bob", gender: "MALE" });
    await setDoc(doc(db, "interests/bob_alice"), { fromId: "bob", toId: "alice", status: "PENDING" });
    await setDoc(doc(db, "matches/alice_bob"), { members: ["alice", "bob"], closed: false, lastReadAt: {}, contactShared: { alice: false, bob: false } });
    await setDoc(doc(db, "matches/alice_dan"), { members: ["alice", "dan"], closed: true, lastReadAt: {} });
    await setDoc(doc(db, "reports/r1"), { reporterId: "alice", reportedId: "mallory", reason: "SCAM" });
    await setDoc(doc(db, "aiUsage/alice_2026-11-24"), { tasks: { translate: 1 } });
    await setDoc(doc(db, "ipLog/alice"), { lastIp: "203.0.113.5" });
  });
});

const as = (uid: string) => env.authenticatedContext(uid).firestore();
const anon = () => env.unauthenticatedContext().firestore();
const message = (senderId: string, over: Record<string, unknown> = {}) => ({ senderId, text: "Namaste!", sentAt: Date.now(), ...over });

describe("profiles cannot be scraped", () => {
  it("a member can read only their own card directly", async () => {
    await assertSucceeds(getDoc(doc(as("alice"), "profiles/alice")));
    await assertFails(getDoc(doc(as("alice"), "profiles/bob")));
    await assertFails(getDoc(doc(anon(), "profiles/bob")));
  });

  it("nobody can list or query profiles, or write them", async () => {
    await assertFails(getDocs(collection(as("alice"), "profiles")));
    await assertFails(getDocs(query(collection(as("alice"), "profiles"), where("gender", "==", "MALE"))));
    await assertFails(setDoc(doc(as("alice"), "profiles/alice"), { verification: { selfie: true } }));
  });
});

describe("private account data", () => {
  it("is owner-read and server-write only", async () => {
    await assertSucceeds(getDoc(doc(as("alice"), "users/alice")));
    await assertFails(getDoc(doc(as("bob"), "users/alice")));
    await assertFails(updateDoc(doc(as("alice"), "users/alice"), { "profile.verification.selfie": true }));
  });

  it("shortlist accepts only a timestamp, only for the owner", async () => {
    await assertSucceeds(setDoc(doc(as("alice"), "users/alice/shortlist/bob"), { at: Date.now() }));
    await assertFails(setDoc(doc(as("alice"), "users/alice/shortlist/carol"), { at: Date.now(), note: "x" }));
    await assertFails(setDoc(doc(as("bob"), "users/alice/shortlist/bob"), { at: Date.now() }));
    await assertSucceeds(deleteDoc(doc(as("alice"), "users/alice/shortlist/bob")));
  });

  it("server-only collections are closed", async () => {
    await assertFails(getDoc(doc(as("alice"), "aiUsage/alice_2026-11-24")));
    await assertFails(getDoc(doc(as("alice"), "ipLog/alice")));
    await assertFails(setDoc(doc(as("alice"), "users/alice/blocks/bob"), { at: 1 }));
  });
});

describe("interests", () => {
  it("are readable by the two people involved only", async () => {
    await assertSucceeds(getDoc(doc(as("alice"), "interests/bob_alice")));
    await assertSucceeds(getDoc(doc(as("bob"), "interests/bob_alice")));
    await assertFails(getDoc(doc(as("carol"), "interests/bob_alice")));
  });

  it("can be queried only with a constraint on the caller", async () => {
    await assertSucceeds(getDocs(query(collection(as("alice"), "interests"), where("toId", "==", "alice"))));
    await assertFails(getDocs(collection(as("alice"), "interests")));
  });

  it("are created only by the server", async () => {
    await assertFails(setDoc(doc(as("alice"), "interests/alice_bob"), { fromId: "alice", toId: "bob", status: "ACCEPTED" }));
  });
});

describe("matches and messages", () => {
  it("only members can read a match", async () => {
    await assertSucceeds(getDoc(doc(as("bob"), "matches/alice_bob")));
    await assertFails(getDoc(doc(as("carol"), "matches/alice_bob")));
  });

  it("members can move only their own read marker", async () => {
    await assertSucceeds(updateDoc(doc(as("alice"), "matches/alice_bob"), { "lastReadAt.alice": Date.now() }));
    await assertFails(updateDoc(doc(as("alice"), "matches/alice_bob"), { "lastReadAt.bob": Date.now() }));
    await assertFails(updateDoc(doc(as("alice"), "matches/alice_bob"), { "contactShared.bob": true }));
    await assertFails(updateDoc(doc(as("alice"), "matches/alice_bob"), { closed: true }));
  });

  it("members can send well-formed messages as themselves", async () => {
    await assertSucceeds(addDoc(collection(as("alice"), "matches/alice_bob/messages"), message("alice")));
    await assertSucceeds(getDocs(collection(as("bob"), "matches/alice_bob/messages")));
  });

  it("rejects spoofed senders, extra fields, oversized text and stale timestamps", async () => {
    const msgs = collection(as("alice"), "matches/alice_bob/messages");
    await assertFails(addDoc(msgs, message("bob")));
    await assertFails(addDoc(msgs, message("alice", { system: true })));
    await assertFails(addDoc(msgs, message("alice", { text: "x".repeat(2001) })));
    await assertFails(addDoc(msgs, message("alice", { text: "" })));
    await assertFails(addDoc(msgs, message("alice", { sentAt: Date.now() - 3_600_000 })));
  });

  it("blocks outsiders and closed conversations", async () => {
    await assertFails(addDoc(collection(as("carol"), "matches/alice_bob/messages"), message("carol")));
    await assertFails(getDocs(collection(as("carol"), "matches/alice_bob/messages")));
    await assertFails(addDoc(collection(as("alice"), "matches/alice_dan/messages"), message("alice")));
  });

  it("messages are immutable", async () => {
    const created = await addDoc(collection(as("alice"), "matches/alice_bob/messages"), message("alice"));
    await assertFails(updateDoc(doc(as("alice"), created.path), { text: "edited" }));
    await assertFails(deleteDoc(doc(as("bob"), created.path)));
  });
});

describe("reports", () => {
  it("are visible to the reporter only and created by the server", async () => {
    await assertSucceeds(getDoc(doc(as("alice"), "reports/r1")));
    await assertFails(getDoc(doc(as("mallory"), "reports/r1")));
    await assertFails(setDoc(doc(as("alice"), "reports/r2"), { reporterId: "alice", reportedId: "x" }));
  });
});

describe("photo storage", () => {
  const jpeg = new Uint8Array([0xff, 0xd8, 0xff, 0xe0]);

  it("members upload small images into their own folder only", async () => {
    const storage = env.authenticatedContext("alice").storage();
    await assertSucceeds(uploadBytes(ref(storage, "photos/alice/p1.jpg"), jpeg, { contentType: "image/jpeg" }));
    await assertFails(uploadBytes(ref(storage, "photos/bob/p1.jpg"), jpeg, { contentType: "image/jpeg" }));
    await assertFails(uploadBytes(ref(storage, "photos/alice/p2.jpg"), jpeg, { contentType: "application/octet-stream" }));
    await assertFails(uploadBytes(ref(storage, "photos/alice/../evil.jpg"), jpeg, { contentType: "image/jpeg" }));
  });

  it("nobody reads another member's originals directly", async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await uploadBytes(ref(ctx.storage(), "photos/bob/p1.jpg"), jpeg, { contentType: "image/jpeg" });
    });
    await assertFails(getBytes(ref(env.authenticatedContext("alice").storage(), "photos/bob/p1.jpg")));
    await assertSucceeds(getBytes(ref(env.authenticatedContext("bob").storage(), "photos/bob/p1.jpg")));
  });
});
