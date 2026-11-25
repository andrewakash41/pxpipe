/**
 * Shared Firebase Admin wiring and the guard rails every callable uses: authentication, input
 * validation, rate limits, block lists, and the Firestore-backed consent and quota stores the AI
 * gateway depends on.
 */
import { getApps, initializeApp } from "firebase-admin/app";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { type CallableOptions, type CallableRequest, HttpsError } from "firebase-functions/v2/https";
import type { z } from "zod";
import type { ConsentChecker, QuotaStore } from "../ai/gateway.js";
import type { Profile } from "./model.js";

export const app = getApps()[0] ?? initializeApp();
export const db = getFirestore(app);
// Optional schema fields that a member left out arrive as `undefined`; never write those.
db.settings({ ignoreUndefinedProperties: true });
export { FieldValue };

export const REGION = "asia-south1";
export const isEmulator = process.env.FUNCTIONS_EMULATOR === "true";

/**
 * Every callable runs in Mumbai and, outside the emulator, only accepts requests carrying a valid
 * App Check token (Play Integrity), so scripts and modified apps can't call the API directly.
 */
export const callableOptions: CallableOptions = {
  region: REGION,
  enforceAppCheck: !isEmulator,
  maxInstances: 20,
  timeoutSeconds: 30,
};

export function requireUid(req: CallableRequest): string {
  const uid = req.auth?.uid;
  if (!uid) throw new HttpsError("unauthenticated", "Please sign in again.");
  return uid;
}

/** Validates untrusted input. Error messages name the field but never echo the value back. */
export function parse<T extends z.ZodType>(schema: T, data: unknown): z.output<T> {
  const result = schema.safeParse(data ?? {});
  if (!result.success) {
    const issue = result.error.issues[0];
    const where = issue?.path.length ? ` (${issue.path.join(".")})` : "";
    throw new HttpsError("invalid-argument", `Some details look wrong${where}. Please check and try again.`);
  }
  return result.data;
}

/** Stable id for a pair of members, independent of order. */
export const pairId = (a: string, b: string): string => [a, b].sort().join("_");

/**
 * Fixed-window rate limit per member and action, stored in `rateLimits` (expired by a TTL policy).
 * Throws `resource-exhausted` once the member has used up the window.
 */
export async function rateLimit(uid: string, action: string, max: number, windowMs: number, message?: string): Promise<void> {
  const ref = db.doc(`rateLimits/${uid}_${action}`);
  const allowed = await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const now = Date.now();
    const start = (snap.get("windowStart") as number | undefined) ?? 0;
    const count = (snap.get("count") as number | undefined) ?? 0;
    if (!snap.exists || now - start >= windowMs) {
      tx.set(ref, { count: 1, windowStart: now, expireAt: new Date(now + windowMs) });
      return true;
    }
    if (count >= max) return false;
    tx.update(ref, { count: count + 1 });
    return true;
  });
  if (!allowed) throw new HttpsError("resource-exhausted", message ?? "You've reached the limit for this for now. Please try again later.");
}

/** The caller's own full profile. */
export async function myProfile(uid: string): Promise<Profile> {
  const profile = (await db.doc(`users/${uid}`).get()).get("profile") as Profile | undefined;
  if (!profile) throw new HttpsError("failed-precondition", "Create your profile first.");
  return profile;
}

/** Another member's public card, or null if it doesn't exist. */
export async function publicProfile(id: string): Promise<Profile | null> {
  const snap = await db.doc(`profiles/${id}`).get();
  return snap.exists ? (snap.data() as Profile) : null;
}

/** Everyone this member blocked *and* everyone who blocked them: blocking hides both ways. */
export async function blockedSet(uid: string): Promise<Set<string>> {
  const [blocks, blockedBy] = await Promise.all([
    db.collection(`users/${uid}/blocks`).select().get(),
    db.collection(`users/${uid}/blockedBy`).select().get(),
  ]);
  return new Set([...blocks.docs.map((d) => d.id), ...blockedBy.docs.map((d) => d.id)]);
}

/** AI runs only for members who opted in to the AI purpose (DPDP: purpose-specific consent). */
export class FirestoreConsent implements ConsentChecker {
  async hasAiConsent(uid: string): Promise<boolean> {
    const snap = await db.doc(`users/${uid}`).get();
    return snap.get("consents.AI_ASSISTANCE.granted") === true;
  }
}

/** Per-member, per-task daily AI quota (India calendar day), expired by a TTL policy on `aiUsage`. */
export class FirestoreQuota implements QuotaStore {
  async consume(uid: string, task: string, dailyLimit: number): Promise<boolean> {
    const day = new Date(Date.now() + 5.5 * 3_600_000).toISOString().slice(0, 10);
    const ref = db.doc(`aiUsage/${uid}_${task}_${day}`);
    return db.runTransaction(async (tx) => {
      const used = ((await tx.get(ref)).get("count") as number | undefined) ?? 0;
      if (used >= dailyLimit) return false;
      tx.set(ref, { count: used + 1, expireAt: new Date(Date.now() + 2 * 86_400_000) }, { merge: true });
      return true;
    });
  }
}
