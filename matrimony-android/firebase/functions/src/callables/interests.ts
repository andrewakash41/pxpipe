import { getAuth } from "firebase-admin/auth";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { z } from "zod";
import { redact } from "../ai/redact.js";
import { app, blockedSet, callableOptions, db, FieldValue, myProfile, pairId, parse, publicProfile, rateLimit, requireUid } from "../lib/firebase.js";
import { isEligible } from "../matching/engine.js";
import { assessText } from "../safety/scam.js";

const DAY = 86_400_000;
export const INTEREST_TTL_MS = 7 * DAY;
const FREE_DAILY_INTERESTS = 5;
const PLUS_DAILY_INTERESTS = 20;

export const sendInterest = onCall(callableOptions, async (req) => {
  const uid = requireUid(req);
  const { toId, note } = parse(z.object({ toId: z.string().min(1).max(128), note: z.string().max(300).nullish() }), req.data);
  const userSnap = await db.doc(`users/${uid}`).get();
  const plus = userSnap.get("plan") === "PLUS";
  await rateLimit(uid, "interest", plus ? PLUS_DAILY_INTERESTS : FREE_DAILY_INTERESTS, DAY);

  const [me, them, blocked] = await Promise.all([myProfile(uid), publicProfile(toId), blockedSet(uid)]);
  if (!them || !isEligible(me, them, { blocked, viewerVerified: me.verification.selfie || me.verification.governmentId })) {
    throw new HttpsError("failed-precondition", "You can't send an interest to this member.");
  }
  const theirPrivacy = (await db.doc(`users/${toId}`).get()).get("privacy") as { verifiedOnlyContact?: boolean; requirePhotoToContact?: boolean } | undefined;
  if (theirPrivacy?.verifiedOnlyContact && !(me.verification.selfie || me.verification.governmentId)) {
    throw new HttpsError("failed-precondition", `${them.firstName} accepts interests from verified members only. Verification is free.`);
  }
  if (theirPrivacy?.requirePhotoToContact && me.photos.length === 0) {
    throw new HttpsError("failed-precondition", `${them.firstName} accepts interests only from profiles with a photo.`);
  }

  let cleanNote = note ? redact(note.trim()) : null;
  if (note && assessText(note).score >= 40) {
    // Don't deliver risky notes; keep a non-content record for the risk score.
    cleanNote = null;
    await db.doc(`users/${uid}`).set({ riskSignals: { highRiskNotes: FieldValue.increment(1) } }, { merge: true });
  }

  const ref = db.doc(`interests/${uid}_${toId}`);
  const now = Date.now();
  const interest = { fromId: uid, toId, note: cleanNote, status: "PENDING", createdAt: now, expiresAt: now + INTEREST_TTL_MS, declineMessage: null };
  await db.runTransaction(async (tx) => {
    const existing = await tx.get(ref);
    if (existing.exists && existing.get("status") === "PENDING") throw new HttpsError("already-exists", "You've already sent an interest.");
    tx.set(ref, interest);
  });
  return { interest: { id: ref.id, ...interest } };
});

export const respondToInterest = onCall(callableOptions, async (req) => {
  const uid = requireUid(req);
  const { interestId, action, message } = parse(z.object({
    interestId: z.string().min(1).max(300),
    action: z.enum(["accept", "decline", "withdraw"]),
    message: z.string().max(400).nullish(),
  }), req.data);
  const ref = db.doc(`interests/${interestId}`);

  return db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    if (!snap.exists) throw new HttpsError("not-found", "This interest no longer exists.");
    const i = snap.data() as { fromId: string; toId: string; status: string; expiresAt: number };
    if (i.status !== "PENDING") throw new HttpsError("failed-precondition", "This interest was already answered.");
    if (action === "withdraw") {
      if (i.fromId !== uid) throw new HttpsError("permission-denied", "Not allowed.");
      tx.update(ref, { status: "WITHDRAWN" });
      return { ok: true };
    }
    if (i.toId !== uid) throw new HttpsError("permission-denied", "Not allowed.");
    if (Date.now() > i.expiresAt) {
      tx.update(ref, { status: "EXPIRED" });
      throw new HttpsError("failed-precondition", "This interest has expired.");
    }
    if (action === "decline") {
      tx.update(ref, { status: "DECLINED", declineMessage: message ? redact(message) : null, respondedAt: Date.now() });
      return { ok: true };
    }
    const matchId = pairId(i.fromId, i.toId);
    tx.update(ref, { status: "ACCEPTED", respondedAt: Date.now() });
    tx.set(db.doc(`matches/${matchId}`), {
      members: [i.fromId, i.toId].sort(),
      createdAt: Date.now(),
      contactShared: { [i.fromId]: false, [i.toId]: false },
      lastReadAt: {},
      closed: false,
    }, { merge: true });
    return { matchId };
  });
});

async function requireMember(matchId: string, uid: string) {
  const snap = await db.doc(`matches/${matchId}`).get();
  const members = snap.get("members") as string[] | undefined;
  if (!snap.exists || !members?.includes(uid)) throw new HttpsError("permission-denied", "Not allowed.");
  return { snap, other: members.find((m) => m !== uid)! };
}

export const setContactSharing = onCall(callableOptions, async (req) => {
  const uid = requireUid(req);
  const { matchId, shared } = parse(z.object({ matchId: z.string().max(300), shared: z.boolean() }), req.data);
  const { snap } = await requireMember(matchId, uid);
  if (snap.get("closed") === true) throw new HttpsError("failed-precondition", "This conversation is closed.");
  await snap.ref.update({ [`contactShared.${uid}`]: shared });
  await snap.ref.collection("messages").add({
    senderId: "system", system: true, sentAt: Date.now(),
    text: shared ? "A phone number was shared in this conversation." : "Phone number sharing was turned off.",
  });
  return { ok: true };
});

/** Returns the other member's number only if they chose to share it (and can revoke any time). */
export const getSharedContact = onCall(callableOptions, async (req) => {
  const uid = requireUid(req);
  const { matchId } = parse(z.object({ matchId: z.string().max(300) }), req.data);
  const { snap, other } = await requireMember(matchId, uid);
  if (snap.get("closed") === true || snap.get(`contactShared.${other}`) !== true) return { phone: null };
  await rateLimit(uid, "contact_reveal", 30, DAY);
  const user = await getAuth(app).getUser(other);
  return { phone: user.phoneNumber ?? null };
});

export const closeMatch = onCall(callableOptions, async (req) => {
  const uid = requireUid(req);
  const { matchId } = parse(z.object({ matchId: z.string().max(300) }), req.data);
  const { snap } = await requireMember(matchId, uid);
  await snap.ref.update({ closed: true, closedAt: Date.now(), [`contactShared.${uid}`]: false });
  return { ok: true };
});
