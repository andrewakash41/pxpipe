/** Safety (report/block), privacy (consent, export, deletion), Family Mode and verification. */
import { createHash, randomBytes, randomInt } from "node:crypto";
import { getAuth } from "firebase-admin/auth";
import { defineString } from "firebase-functions/params";
import { logger } from "firebase-functions";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { z } from "zod";
import { app, callableOptions, db, FieldValue, isEmulator, pairId, parse, rateLimit, requireUid } from "../lib/firebase.js";
import { ConsentPurpose, FamilyPermission, type Profile, ReportReason } from "../lib/model.js";

const DAY = 86_400_000;
const UserId = z.object({ userId: z.string().min(1).max(128) });

// ------------------------------------------------------------------------------------- safety

export const reportUser = onCall(callableOptions, async (req) => {
  const uid = requireUid(req);
  const { userId, reason, details, attachRecentMessages } = parse(UserId.extend({
    reason: ReportReason, details: z.string().max(1000).default(""), attachRecentMessages: z.boolean().default(false),
  }), req.data);
  if (userId === uid) throw new HttpsError("invalid-argument", "You can't report yourself.");
  await rateLimit(uid, "report", 20, DAY);
  const now = Date.now();
  const ref = db.collection("reports").doc();
  await ref.set({
    reporterId: uid, reportedId: userId, reason, details, createdAt: now,
    // IT Rules 2021: acknowledge within 24 h, resolve within 15 days.
    acknowledgeBy: now + DAY, resolveBy: now + 15 * DAY,
    status: "Received — we'll acknowledge within 24 hours",
  });
  if (attachRecentMessages) {
    // The reporter chooses to share recent messages from their own conversation as evidence.
    const match = db.doc(`matches/${pairId(uid, userId)}`);
    if ((await match.get()).exists) {
      const msgs = await match.collection("messages").orderBy("sentAt", "desc").limit(30).get();
      const batch = db.batch();
      msgs.docs.forEach((m) => batch.set(ref.collection("evidence").doc(m.id), m.data()));
      await batch.commit();
    }
  }
  await db.doc(`users/${userId}`).set({ riskSignals: { reportsAgainst: FieldValue.increment(1) } }, { merge: true });
  return { id: ref.id };
});

export const blockUser = onCall(callableOptions, async (req) => {
  const uid = requireUid(req);
  const { userId } = parse(UserId, req.data);
  const batch = db.batch();
  batch.set(db.doc(`users/${uid}/blocks/${userId}`), { at: Date.now() });
  batch.set(db.doc(`users/${userId}/blockedBy/${uid}`), { at: Date.now() });
  const match = db.doc(`matches/${pairId(uid, userId)}`);
  if ((await match.get()).exists) batch.update(match, { closed: true, closedAt: Date.now(), contactShared: { [uid]: false, [userId]: false } });
  for (const id of [`${uid}_${userId}`, `${userId}_${uid}`]) {
    const i = await db.doc(`interests/${id}`).get();
    if (i.exists && i.get("status") === "PENDING") batch.update(i.ref, { status: "WITHDRAWN" });
  }
  await batch.commit();
  return { ok: true };
});

export const unblockUser = onCall(callableOptions, async (req) => {
  const uid = requireUid(req);
  const { userId } = parse(UserId, req.data);
  const batch = db.batch();
  batch.delete(db.doc(`users/${uid}/blocks/${userId}`));
  batch.delete(db.doc(`users/${userId}/blockedBy/${uid}`));
  await batch.commit();
  return { ok: true };
});

// ------------------------------------------------------------------------------------ privacy

export const updateConsent = onCall(callableOptions, async (req) => {
  const uid = requireUid(req);
  const { purpose, granted } = parse(z.object({ purpose: ConsentPurpose, granted: z.boolean() }), req.data);
  if (purpose === "ESSENTIAL") throw new HttpsError("invalid-argument", "Essential processing can't be toggled; delete your account instead.");
  const now = Date.now();
  const batch = db.batch();
  batch.set(db.doc(`users/${uid}`), { consents: { [purpose]: { granted, updatedAt: now } } }, { merge: true });
  // DPDP: keep an auditable history of consent changes.
  batch.set(db.collection(`users/${uid}/consentLog`).doc(), { purpose, granted, at: now, ip: req.rawRequest.ip ?? null });
  await batch.commit();
  return { ok: true };
});

export const exportMyData = onCall(callableOptions, async (req) => {
  const uid = requireUid(req);
  await rateLimit(uid, "export", 3, DAY);
  const user = (await db.doc(`users/${uid}`).get()).data() ?? {};
  const [sent, received, matches, reports, consentLog, family, blocks] = await Promise.all([
    db.collection("interests").where("fromId", "==", uid).get(),
    db.collection("interests").where("toId", "==", uid).get(),
    db.collection("matches").where("members", "array-contains", uid).get(),
    db.collection("reports").where("reporterId", "==", uid).get(),
    db.collection(`users/${uid}/consentLog`).get(),
    db.collection(`users/${uid}/familyManagers`).get(),
    db.collection(`users/${uid}/blocks`).get(),
  ]);
  const conversations = await Promise.all(matches.docs.map(async (m) => ({
    matchId: m.id,
    createdAt: m.get("createdAt"),
    // Only the member's own messages: the other person's words are their personal data.
    myMessages: (await m.ref.collection("messages").where("senderId", "==", uid).get()).docs.map((d) => d.data()),
  })));
  const { riskSignals: _internal, ...userData } = user;
  const out = {
    exportedAt: new Date().toISOString(),
    account: userData,
    interestsSent: sent.docs.map((d) => d.data()),
    interestsReceived: received.docs.map((d) => ({ ...d.data(), fromId: "hidden" })),
    conversations,
    reports: reports.docs.map((d) => d.data()),
    consentHistory: consentLog.docs.map((d) => d.data()),
    familyManagers: family.docs.map((d) => ({ ...d.data(), inviteCode: undefined })),
    blocked: blocks.docs.map((d) => d.id),
  };
  return { json: JSON.stringify(out, null, 2) };
});

async function setHidden(uid: string, hidden: boolean) {
  const userRef = db.doc(`users/${uid}`);
  const profile = (await userRef.get()).get("profile") as Profile | undefined;
  if (!profile) return;
  const visibility = hidden ? "HIDDEN" : "EVERYONE";
  await Promise.all([
    userRef.update({ "profile.visibility": visibility }),
    db.doc(`profiles/${uid}`).update({ visibility }),
  ]);
}

export const requestAccountDeletion = onCall(callableOptions, async (req) => {
  const uid = requireUid(req);
  await db.doc(`users/${uid}`).set({ deletionRequestedAt: Date.now() }, { merge: true });
  await setHidden(uid, true);
  return { deletesAt: Date.now() + 30 * DAY };
});

export const cancelAccountDeletion = onCall(callableOptions, async (req) => {
  const uid = requireUid(req);
  await db.doc(`users/${uid}`).update({ deletionRequestedAt: FieldValue.delete() });
  await setHidden(uid, false);
  return { ok: true };
});

// ------------------------------------------------------------------------------------- family

const hashCode = (code: string) => createHash("sha256").update(code.toUpperCase()).digest("hex");

export const inviteFamilyManager = onCall(callableOptions, async (req) => {
  const uid = requireUid(req);
  const { name, relation, permissions } = parse(z.object({
    name: z.string().trim().min(1).max(40), relation: z.string().max(20), permissions: z.array(FamilyPermission).min(1).max(4),
  }), req.data);
  await rateLimit(uid, "family_invite", 5, DAY);
  const code = Array.from(randomBytes(8), (b) => "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"[b % 32]).join("");
  const ref = db.collection(`users/${uid}/familyManagers`).doc();
  const manager = { name, relation, permissions, inviteCode: code, accepted: false, createdAt: Date.now() };
  const batch = db.batch();
  batch.set(ref, manager);
  // Only a hash of the code is indexed; the code itself is visible to the member alone.
  batch.set(db.doc(`familyInvites/${hashCode(code)}`), { memberUid: uid, managerId: ref.id, expiresAt: Date.now() + 2 * DAY });
  batch.set(db.collection(`users/${uid}/familyActivity`).doc(), { managerName: name, action: "was invited", at: Date.now() });
  await batch.commit();
  return { manager: { id: ref.id, ...manager } };
});

/** Called by the family member's own account to accept an invite code. */
export const acceptFamilyInvite = onCall(callableOptions, async (req) => {
  const uid = requireUid(req);
  const { code } = parse(z.object({ code: z.string().length(8) }), req.data);
  await rateLimit(uid, "family_accept", 10, DAY);
  const inviteRef = db.doc(`familyInvites/${hashCode(code)}`);
  return db.runTransaction(async (tx) => {
    const invite = await tx.get(inviteRef);
    if (!invite.exists || (invite.get("expiresAt") as number) < Date.now()) throw new HttpsError("not-found", "This code is invalid or has expired.");
    const memberUid = invite.get("memberUid") as string;
    if (memberUid === uid) throw new HttpsError("invalid-argument", "You can't manage your own profile this way.");
    const managerRef = db.doc(`users/${memberUid}/familyManagers/${invite.get("managerId")}`);
    const manager = await tx.get(managerRef);
    tx.update(managerRef, { accepted: true, managerUid: uid, inviteCode: FieldValue.delete() });
    tx.set(db.collection(`users/${memberUid}/familyActivity`).doc(), { managerName: manager.get("name"), action: "accepted your invitation", at: Date.now() });
    tx.delete(inviteRef);
    return { memberUid };
  });
});

export const updateFamilyManager = onCall(callableOptions, async (req) => {
  const uid = requireUid(req);
  const { managerId, permissions } = parse(z.object({ managerId: z.string().max(128), permissions: z.array(FamilyPermission).max(4) }), req.data);
  await db.doc(`users/${uid}/familyManagers/${managerId}`).update({ permissions });
  return { ok: true };
});

export const removeFamilyManager = onCall(callableOptions, async (req) => {
  const uid = requireUid(req);
  const { managerId } = parse(z.object({ managerId: z.string().max(128) }), req.data);
  await db.doc(`users/${uid}/familyManagers/${managerId}`).delete();
  return { ok: true };
});

// ------------------------------------------------------------------------------- verification

/** Set to the vendor's name once a liveness / face-match vendor is integrated. */
const VERIFICATION_VENDOR = defineString("VERIFICATION_VENDOR", { default: "" });
/** DigiLocker / Aadhaar-offline authorization URL, once partner registration is complete. */
const GOV_ID_AUTH_URL = defineString("GOV_ID_AUTH_URL", { default: "" });

async function setVerification(uid: string, field: "selfie" | "governmentId" | "workEmail") {
  await Promise.all([
    db.doc(`users/${uid}`).update({ [`profile.verification.${field}`]: true }),
    db.doc(`profiles/${uid}`).update({ [`verification.${field}`]: true }),
  ]);
  if (field !== "workEmail") {
    const auth = getAuth(app);
    const claims = (await auth.getUser(uid)).customClaims ?? {};
    await auth.setCustomUserClaims(uid, { ...claims, verified: true });
  }
}

export const submitSelfieCheck = onCall(callableOptions, async (req) => {
  const uid = requireUid(req);
  const { challengePassed } = parse(z.object({ challengePassed: z.boolean(), vendorSessionId: z.string().max(200).optional() }), req.data);
  await rateLimit(uid, "selfie", 5, DAY);
  if (!VERIFICATION_VENDOR.value()) {
    // Without a vendor we only accept the self-attested challenge in the local emulator.
    if (!isEmulator) throw new HttpsError("failed-precondition", "Selfie verification is not configured yet.");
  } else {
    // Integration point: confirm vendorSessionId with the liveness/face-match vendor API
    // (including virtual-camera / injection-attack detection) before trusting the result.
    throw new HttpsError("unimplemented", "Vendor verification hook not implemented.");
  }
  if (!challengePassed) throw new HttpsError("failed-precondition", "We couldn't confirm the gestures. Try again in good light.");
  await setVerification(uid, "selfie");
  return { ok: true };
});

export const startGovIdVerification = onCall(callableOptions, async (req) => {
  const uid = requireUid(req);
  const url = GOV_ID_AUTH_URL.value();
  if (url) {
    const state = randomBytes(16).toString("hex");
    await db.doc(`users/${uid}/private/govIdState`).set({ state, at: Date.now() });
    return { url: `${url}${url.includes("?") ? "&" : "?"}state=${state}`, message: "Continue in DigiLocker to share a verified ID. We'll store only the result." };
  }
  if (isEmulator) {
    await setVerification(uid, "governmentId");
    return { message: "Emulator: government ID verification simulated." };
  }
  throw new HttpsError("failed-precondition", "Government ID verification is coming soon.");
});

const PERSONAL_DOMAINS = new Set(["gmail.com", "yahoo.com", "yahoo.co.in", "outlook.com", "hotmail.com", "rediffmail.com", "icloud.com", "proton.me", "protonmail.com"]);
const hashOtp = (uid: string, code: string, salt: string) => createHash("sha256").update(`${uid}:${code}:${salt}`).digest("hex");

export const sendWorkEmailCode = onCall(callableOptions, async (req) => {
  const uid = requireUid(req);
  const { email } = parse(z.object({ email: z.email().max(120) }), req.data);
  const domain = email.split("@")[1]!.toLowerCase();
  if (PERSONAL_DOMAINS.has(domain)) throw new HttpsError("invalid-argument", "Please use your work email, not a personal one.");
  await rateLimit(uid, "work_email", 5, DAY);
  const code = String(randomInt(100000, 1000000));
  const salt = randomBytes(8).toString("hex");
  await db.doc(`users/${uid}/private/workEmail`).set({ domain, hash: hashOtp(uid, code, salt), salt, attempts: 0, expiresAt: Date.now() + 10 * 60_000 });
  // Integration point: send `code` with a transactional email provider. Never log the code in production.
  if (isEmulator) logger.info("work_email_code_emulator", { code });
  return { ok: true };
});

export const confirmWorkEmail = onCall(callableOptions, async (req) => {
  const uid = requireUid(req);
  const { code } = parse(z.object({ code: z.string().regex(/^\d{6}$/) }), req.data);
  const ref = db.doc(`users/${uid}/private/workEmail`);
  const ok = await db.runTransaction(async (tx) => {
    const s = await tx.get(ref);
    if (!s.exists || (s.get("expiresAt") as number) < Date.now()) throw new HttpsError("failed-precondition", "Request a new code.");
    if ((s.get("attempts") as number) >= 5) throw new HttpsError("resource-exhausted", "Too many attempts. Request a new code.");
    const match = hashOtp(uid, code, s.get("salt") as string) === s.get("hash");
    if (match) tx.delete(ref); else tx.update(ref, { attempts: FieldValue.increment(1) });
    return match;
  });
  if (!ok) throw new HttpsError("invalid-argument", "That code didn't match.");
  await setVerification(uid, "workEmail");
  return { ok: true };
});
