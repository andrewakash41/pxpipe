/**
 * End-to-end flow against the Functions + Firestore + Auth emulators, using the same JSON shapes
 * the Android client sends. Run with `npm run test:e2e`.
 */
import { initializeApp } from "firebase/app";
import { connectAuthEmulator, createUserWithEmailAndPassword, getAuth } from "firebase/auth";
import { addDoc, collection, connectFirestoreEmulator, doc, getDoc, getFirestore } from "firebase/firestore";
import { connectFunctionsEmulator, getFunctions, httpsCallable } from "firebase/functions";
import { describe, expect, it } from "vitest";

const PROJECT = "demo-vachan";

function client(name: string) {
  const app = initializeApp({ projectId: PROJECT, apiKey: "demo-key", authDomain: `${PROJECT}.firebaseapp.com` }, name);
  const auth = getAuth(app);
  connectAuthEmulator(auth, "http://127.0.0.1:9099", { disableWarnings: true });
  const db = getFirestore(app);
  connectFirestoreEmulator(db, "127.0.0.1", 8080);
  const functions = getFunctions(app, "asia-south1");
  connectFunctionsEmulator(functions, "127.0.0.1", 5001);
  const call = async <T = Record<string, unknown>>(fn: string, data: unknown = {}) => (await httpsCallable(functions, fn)(data)).data as T;
  return { auth, db, call };
}

const values = Object.fromEntries(
  ["LIVING_ARRANGEMENT", "CAREERS", "CHILDREN", "RELOCATION", "FAITH", "FINANCES", "HOUSEHOLD", "PARENTS_ROLE"].map((q, i) => [q, { answer: [3, 4, 1, 3, 2, 2, 4, 2][i], importance: 2 }]),
);

function profile(over: Record<string, unknown>) {
  return {
    id: "", firstName: "X", lastInitial: "K", gender: "FEMALE", age: 28, heightCm: 165,
    location: { city: "Pune", state: "Maharashtra", country: "India", lat: 18.52, lng: 73.86 },
    religion: "HINDU", motherTongue: "Marathi", community: "Secret", showCommunity: false, maritalStatus: "NEVER_MARRIED",
    hasChildren: false, education: "MASTERS", educationField: "Computer Science", profession: "Software engineer",
    incomeBand: "L20_35", showIncome: false, diet: "VEGETARIAN", smoking: "NEVER", drinking: "NEVER", familyType: "NUCLEAR",
    managedBy: "SELF", about: "I enjoy trekking, music and cooking for friends. Call me on 9876543210!",
    interests: ["Travel", "Music", "Cooking"], languages: ["English", "Hindi"], values, openToRelocate: false,
    residencyStatus: null, verification: { phone: true, selfie: true, governmentId: true, workEmail: true },
    photos: [{ id: "ph1", url: null, seed: 3 }], photoVisibility: "ALL_MEMBERS", visibility: "EVERYONE",
    responseRate: 1, activity: "TODAY", memberCode: "FORGED",
    preferences: { minAge: 26, maxAge: 34, maritalStatuses: ["NEVER_MARRIED"], religions: [], motherTongues: [], diets: [], countries: [], acceptsSmoking: true, acceptsDrinking: true, acceptsChildren: true, mustHaves: [] },
    ...over,
  };
}

const errorCode = async (p: Promise<unknown>) => p.then(() => "ok", (e: { code?: string }) => e.code ?? "unknown");

describe("Vachan backend end-to-end", () => {
  const alice = client("alice");
  const bob = client("bob");
  let aliceId = "";
  let bobId = "";
  let matchId = "";

  it("signs up and publishes profiles, ignoring forged trust fields and redacting bios", async () => {
    aliceId = (await createUserWithEmailAndPassword(alice.auth, `alice${Date.now()}@example.com`, "password123")).user.uid;
    bobId = (await createUserWithEmailAndPassword(bob.auth, `bob${Date.now()}@example.com`, "password123")).user.uid;

    const a = await alice.call<{ profile: Record<string, any> }>("publishProfile", { profile: profile({ firstName: "Alice" }) });
    expect(a.profile.id).toBe(aliceId);
    expect(a.profile.verification).toEqual({ phone: true, selfie: false, governmentId: false, workEmail: false });
    expect(a.profile.memberCode).toMatch(/^VCH-\d{6}$/);
    expect(a.profile.about).toContain("[PHONE]");

    await bob.call("publishProfile", { profile: profile({ firstName: "Bob", gender: "MALE", age: 30, profession: "Doctor", preferences: { ...profile({}).preferences, minAge: 24, maxAge: 30 } }) });

    // The public card hides optional sensitive fields.
    const card = (await getDoc(doc(bob.db, `profiles/${bobId}`))).data()!;
    expect(card.community).toBeNull();
    expect(card.incomeBand).toBe("UNDISCLOSED");
  });

  it("enforces the legal minimum age", async () => {
    const carol = client("carol");
    await createUserWithEmailAndPassword(carol.auth, `carol${Date.now()}@example.com`, "password123");
    expect(await errorCode(carol.call("publishProfile", { profile: profile({ firstName: "Carl", gender: "MALE", age: 19 }) }))).toBe("functions/failed-precondition");
  });

  it("recommends two-way compatible people and serves cards", async () => {
    const picks = await alice.call<{ profiles: { id: string }[] }>("getDailyPicks", { count: 8, weights: {} });
    expect(picks.profiles.map((p) => p.id)).toContain(bobId);
    const card = await alice.call<{ profile: { firstName: string } }>("getProfileCard", { id: bobId });
    expect(card.profile.firstName).toBe("Bob");
  });

  it("sends an interest (redacting contact details), rejects duplicates, and accepts", async () => {
    const sent = await alice.call<{ interest: { id: string; note: string } }>("sendInterest", { toId: bobId, note: "Loved your profile! WhatsApp 9876543210" });
    expect(sent.interest.note).toContain("[PHONE]");
    expect(await errorCode(alice.call("sendInterest", { toId: bobId, note: null }))).toBe("functions/already-exists");
    // Only the recipient can accept.
    expect(await errorCode(alice.call("respondToInterest", { interestId: sent.interest.id, action: "accept" }))).toBe("functions/permission-denied");
    matchId = (await bob.call<{ matchId: string }>("respondToInterest", { interestId: sent.interest.id, action: "accept" })).matchId;
    expect(matchId).toBe([aliceId, bobId].sort().join("_"));
  });

  it("lets matched members chat and updates the conversation summary", async () => {
    await addDoc(collection(alice.db, `matches/${matchId}/messages`), { senderId: aliceId, text: "Namaste Bob! How's your week going?", sentAt: Date.now() });
    let last: string | undefined;
    for (let i = 0; i < 30 && !last; i++) {
      await new Promise((r) => setTimeout(r, 300));
      last = (await getDoc(doc(bob.db, `matches/${matchId}`))).get("lastMessage");
    }
    expect(last).toContain("Namaste");
  });

  it("shares contact numbers only with consent", async () => {
    expect((await alice.call<{ phone: string | null }>("getSharedContact", { matchId })).phone).toBeNull();
    await bob.call("setContactSharing", { matchId, shared: true });
    // Email test accounts have no phone; the point is that the gate opened without error.
    expect(await errorCode(alice.call("getSharedContact", { matchId }))).toBe("ok");
  });

  it("gates AI on consent, then uses the configured provider", async () => {
    expect(await errorCode(alice.call("aiMatchInsight", { otherId: bobId }))).toBe("functions/failed-precondition");
    await alice.call("updateConsent", { purpose: "AI_ASSISTANCE", granted: true });
    const insight = await alice.call<{ summary: string; talkingPoints: string[]; generatedByAi: boolean }>("aiMatchInsight", { otherId: bobId });
    expect(insight.generatedByAi).toBe(true);
    expect(insight.summary.length).toBeGreaterThan(0);
    const parsed = await alice.call<{ filters: { verifiedOnly: boolean } }>("aiParseSearch", { query: "vegetarian doctor in Pune" });
    expect(parsed.filters).toHaveProperty("verifiedOnly");
  });

  it("accepts only known device-integrity signals", async () => {
    expect(await errorCode(alice.call("reportClientSignals", { deviceSignals: ["EMULATOR"] }))).toBe("ok");
    expect(await errorCode(alice.call("reportClientSignals", { deviceSignals: ["HACKED_BY_ME"] }))).toBe("functions/invalid-argument");
  });

  it("blocking hides people and closes the conversation", async () => {
    await bob.call("blockUser", { userId: aliceId });
    expect(await errorCode(alice.call("getProfileCard", { id: bobId }))).toBe("functions/not-found");
    expect(await errorCode(addDoc(collection(alice.db, `matches/${matchId}/messages`), { senderId: aliceId, text: "hello?", sentAt: Date.now() }))).toBe("permission-denied");
    const picks = await alice.call<{ profiles: { id: string }[] }>("getDailyPicks", { count: 8, weights: {} });
    expect(picks.profiles.map((p) => p.id)).not.toContain(bobId);
  });

  it("exports the member's own data only", async () => {
    const { json } = await alice.call<{ json: string }>("exportMyData");
    const data = JSON.parse(json);
    expect(data.account.profile.firstName).toBe("Alice");
    expect(data.account).not.toHaveProperty("riskSignals");
    expect(data.conversations[0].myMessages.every((m: { senderId: string }) => m.senderId === aliceId)).toBe(true);
  });

  it("schedules and cancels account deletion", async () => {
    const { deletesAt } = await alice.call<{ deletesAt: number }>("requestAccountDeletion");
    expect(deletesAt).toBeGreaterThan(Date.now() + 29 * 86_400_000);
    expect((await getDoc(doc(alice.db, `profiles/${aliceId}`))).get("visibility")).toBe("HIDDEN");
    await alice.call("cancelAccountDeletion");
    expect((await getDoc(doc(alice.db, `profiles/${aliceId}`))).get("visibility")).toBe("EVERYONE");
  });

  it("restores the member's own visibility choice when deletion is cancelled", async () => {
    await alice.call("publishProfile", { profile: profile({ firstName: "Alice", visibility: "VERIFIED_ONLY" }) });
    await alice.call("requestAccountDeletion");
    expect((await getDoc(doc(alice.db, `profiles/${aliceId}`))).get("visibility")).toBe("HIDDEN");
    await alice.call("cancelAccountDeletion");
    expect((await getDoc(doc(alice.db, `profiles/${aliceId}`))).get("visibility")).toBe("VERIFIED_ONLY");
  });

  it("keeps only photos that were really uploaded, and never a client-supplied URL", async () => {
    const { profile: p } = await alice.call<{ profile: { photos: Record<string, unknown>[] } }>("publishProfile", {
      profile: profile({ firstName: "Alice", photos: [{ id: "ph1", url: "https://tracker.example/p.gif", seed: 1 }] }),
    });
    // ph1 was never uploaded to Storage, so it doesn't count as a photo (or leak a URL).
    expect(p.photos).toEqual([]);
  });
});
