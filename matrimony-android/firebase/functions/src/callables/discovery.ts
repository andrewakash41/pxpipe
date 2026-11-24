import { getStorage } from "firebase-admin/storage";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { z } from "zod";
import { app, blockedSet, callableOptions, db, myProfile, pairId, parse, publicProfile, rateLimit, requireUid } from "../lib/firebase.js";
import { type Profile, SearchFilters } from "../lib/model.js";
import { type Factor, isEligible, matchesFilters, rankDailyPicks, score } from "../matching/engine.js";

const CANDIDATE_POOL = 600;

/**
 * Candidate pool for a viewer. Discovery is server-mediated: clients cannot list or query
 * profiles directly (see firestore.rules), which prevents scraping and enumeration.
 * MVP: one bounded query + in-memory ranking. At scale this becomes a precomputed pool per
 * cohort (gender × age band × region) refreshed nightly.
 */
async function candidates(viewer: Profile): Promise<Profile[]> {
  const seeking = viewer.gender === "FEMALE" ? "MALE" : "FEMALE";
  const snap = await db.collection("profiles")
    .where("gender", "==", seeking)
    .where("visibility", "in", ["EVERYONE", "VERIFIED_ONLY"])
    .where("age", ">=", viewer.preferences.minAge - 3)
    .where("age", "<=", viewer.preferences.maxAge + 3)
    .limit(CANDIDATE_POOL)
    .get();
  return snap.docs.map((d) => d.data() as Profile);
}

async function interactedIds(uid: string): Promise<Set<string>> {
  const [sent, received, passed] = await Promise.all([
    db.collection("interests").where("fromId", "==", uid).select("toId").get(),
    db.collection("interests").where("toId", "==", uid).select("fromId").get(),
    db.collection(`users/${uid}/passed`).select().get(),
  ]);
  return new Set([
    ...sent.docs.map((d) => d.get("toId") as string),
    ...received.docs.map((d) => d.get("fromId") as string),
    ...passed.docs.map((d) => d.id),
  ]);
}

const isVerified = (p: Profile) => p.verification.selfie || p.verification.governmentId;

/** Photos are served only through short-lived signed URLs, and only when policy allows. */
async function withPhotoPolicy(target: Profile, viewer: Profile, accepted: boolean): Promise<Profile> {
  const allowed = target.photoVisibility === "ALL_MEMBERS" ||
    (target.photoVisibility === "VERIFIED_MEMBERS" && isVerified(viewer)) || accepted;
  const bucket = getStorage(app).bucket();
  const photos = await Promise.all(target.photos.map(async (ph) => {
    if (!allowed) return { id: ph.id, seed: ph.seed, url: null };
    try {
      const [url] = await bucket.file(`photos/${target.id}/${ph.id}.jpg`).getSignedUrl({
        version: "v4", action: "read", expires: Date.now() + 15 * 60 * 1000,
      });
      return { id: ph.id, seed: ph.seed, url };
    } catch {
      return { id: ph.id, seed: ph.seed, url: null };
    }
  }));
  return { ...target, photos };
}

async function isMatched(a: string, b: string): Promise<boolean> {
  return (await db.doc(`matches/${pairId(a, b)}`).get()).exists;
}

export const getDailyPicks = onCall(callableOptions, async (req) => {
  const uid = requireUid(req);
  const { count, weights } = parse(z.object({
    count: z.number().int().min(1).max(20).default(8),
    weights: z.record(z.string(), z.number()).default({}),
  }), req.data);
  await rateLimit(uid, "daily_picks", 60, 3_600_000);
  const viewer = await myProfile(uid);
  const [pool, blocked, exclude] = await Promise.all([candidates(viewer), blockedSet(uid), interactedIds(uid)]);
  const picks = rankDailyPicks(viewer, pool, { blocked, exclude, viewerVerified: isVerified(viewer) }, count, weights as Partial<Record<Factor, number>>);
  return { profiles: await Promise.all(picks.map((p) => withPhotoPolicy(p, viewer, false))) };
});

export const searchProfiles = onCall(callableOptions, async (req) => {
  const uid = requireUid(req);
  const { filters } = parse(z.object({ filters: SearchFilters }), req.data);
  await rateLimit(uid, "search", 60, 3_600_000);
  const viewer = await myProfile(uid);
  const [pool, blocked] = await Promise.all([candidates(viewer), blockedSet(uid)]);
  const ctx = { blocked, viewerVerified: isVerified(viewer) };
  const results = pool
    .filter((p) => matchesFilters(p, filters) && isEligible(viewer, p, ctx))
    .map((p) => ({ p, s: score(viewer, p).score }))
    .sort((a, b) => b.s - a.s)
    .slice(0, 50)
    .map((x) => x.p);
  return { profiles: await Promise.all(results.map((p) => withPhotoPolicy(p, viewer, false))) };
});

/** A single profile card, with visibility, block and photo rules applied. */
export const getProfileCard = onCall(callableOptions, async (req) => {
  const uid = requireUid(req);
  const { id } = parse(z.object({ id: z.string().min(1).max(128) }), req.data);
  await rateLimit(uid, "profile_card", 300, 3_600_000);
  const [viewer, target, blocked] = await Promise.all([myProfile(uid), publicProfile(id), blockedSet(uid)]);
  if (!target || blocked.has(id)) throw new HttpsError("not-found", "This profile isn't available.");
  const matched = await isMatched(uid, id);
  if (!matched) {
    if (target.visibility === "HIDDEN") throw new HttpsError("not-found", "This profile isn't available.");
    if (target.visibility === "VERIFIED_ONLY" && !isVerified(viewer)) throw new HttpsError("permission-denied", "Verify your profile to view this member.");
  }
  const incognito = (await db.doc(`users/${uid}`).get()).get("privacy.incognito") === true;
  if (!incognito) {
    await db.doc(`users/${id}/views/${uid}`).set({ at: Date.now(), expireAt: new Date(Date.now() + 30 * 86_400_000) });
  }
  return { profile: await withPhotoPolicy(target, viewer, matched) };
});

export const passProfile = onCall(callableOptions, async (req) => {
  const uid = requireUid(req);
  const { otherId } = parse(z.object({ otherId: z.string().min(1).max(128) }), req.data);
  await db.doc(`users/${uid}/passed/${otherId}`).set({ at: Date.now(), expireAt: new Date(Date.now() + 90 * 86_400_000) });
  return { ok: true };
});
