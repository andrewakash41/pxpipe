import { getAuth } from "firebase-admin/auth";
import { getStorage } from "firebase-admin/storage";
import { logger } from "firebase-functions";
import { onDocumentCreated } from "firebase-functions/v2/firestore";
import { onSchedule } from "firebase-functions/v2/scheduler";
import { app, db, FieldValue } from "../lib/firebase.js";
import { FAN_OUT_THRESHOLD, fingerprint } from "../safety/scam.js";

const DAY = 86_400_000;
const REGION = "asia-south1";

/**
 * On each new message: update the conversation summary and run fan-out detection
 * (the same text sent to many people is a hallmark of scam scripts). Only a one-way hash is kept,
 * and it expires after 24 hours via a Firestore TTL policy on `expireAt`.
 */
export const onMessageCreated = onDocumentCreated({ document: "matches/{matchId}/messages/{messageId}", region: REGION }, async (event) => {
  const msg = event.data?.data();
  if (!msg || msg.system === true) return;
  const { matchId } = event.params;
  const senderId = msg.senderId as string;
  const text = String(msg.text ?? "");

  await db.doc(`matches/${matchId}`).update({
    lastMessage: text.slice(0, 120),
    lastMessageAt: msg.sentAt ?? Date.now(),
    lastSenderId: senderId,
  });

  if (text.length < 40) return; // short greetings are naturally repeated
  const ref = db.doc(`fanout/${senderId}_${fingerprint(text)}`);
  const hits = await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const matches = new Set<string>((snap.get("matches") as string[] | undefined) ?? []);
    matches.add(matchId);
    tx.set(ref, { senderId, matches: [...matches], expireAt: new Date(Date.now() + DAY) });
    return matches.size;
  });
  if (hits === FAN_OUT_THRESHOLD) {
    await Promise.all([
      db.doc(`users/${senderId}`).set({ riskSignals: { fanOutHits: FieldValue.increment(1) } }, { merge: true }),
      db.collection("moderationQueue").add({ type: "fan_out", uid: senderId, conversations: hits, at: FieldValue.serverTimestamp() }),
    ]);
    logger.warn("fan_out_detected", { senderId, conversations: hits });
  }
});

/** Daily: expire unanswered interests (7 days) so nobody waits forever. */
export const expireInterests = onSchedule({ schedule: "every day 03:10", timeZone: "Asia/Kolkata", region: REGION }, async () => {
  const now = Date.now();
  let total = 0;
  for (;;) {
    const snap = await db.collection("interests").where("status", "==", "PENDING").where("expiresAt", "<", now).limit(400).get();
    if (snap.empty) break;
    const batch = db.batch();
    snap.docs.forEach((d) => batch.update(d.ref, { status: "EXPIRED" }));
    await batch.commit();
    total += snap.size;
  }
  logger.info("interests_expired", { total });
});

/**
 * Daily: hard-delete accounts 30 days after the member asked (DPDP right to erasure).
 * Removes profile, account, interests, conversations, photos and the auth user. The IP log
 * required by the MeitY advisory is kept separately for one year, then deleted by TTL.
 */
export const purgeDeletedAccounts = onSchedule({ schedule: "every day 02:40", timeZone: "Asia/Kolkata", region: REGION, timeoutSeconds: 540 }, async () => {
  const cutoff = Date.now() - 30 * DAY;
  const due = await db.collection("users").where("deletionRequestedAt", "<", cutoff).limit(50).get();
  for (const user of due.docs) {
    const uid = user.id;
    try {
      const [sent, received, matches] = await Promise.all([
        db.collection("interests").where("fromId", "==", uid).get(),
        db.collection("interests").where("toId", "==", uid).get(),
        db.collection("matches").where("members", "array-contains", uid).get(),
      ]);
      await Promise.all([...sent.docs, ...received.docs].map((d) => d.ref.delete()));
      await Promise.all(matches.docs.map((m) => db.recursiveDelete(m.ref)));
      await db.recursiveDelete(db.doc(`profiles/${uid}`));
      await db.recursiveDelete(user.ref);
      await getStorage(app).bucket().deleteFiles({ prefix: `photos/${uid}/` });
      await db.doc(`ipLog/${uid}`).set({ accountDeletedAt: Date.now(), expireAt: new Date(Date.now() + 365 * DAY) }, { merge: true });
      await getAuth(app).deleteUser(uid).catch(() => undefined);
      logger.info("account_purged", { uid });
    } catch (e) {
      logger.error("account_purge_failed", { uid, error: String(e) });
    }
  }
});
