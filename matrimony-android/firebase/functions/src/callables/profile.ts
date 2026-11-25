import { randomInt } from "node:crypto";
import { getStorage } from "firebase-admin/storage";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { z } from "zod";
import { containsContactDetails, redact } from "../ai/redact.js";
import { app, callableOptions, db, FieldValue, parse, requireUid } from "../lib/firebase.js";
import { minimumMarriageAge, type Profile, ProfileInput, publicCard } from "../lib/model.js";
import { assessText } from "../safety/scam.js";

async function uniqueMemberCode(): Promise<string> {
  for (let i = 0; i < 5; i++) {
    const code = `VCH-${randomInt(100000, 999999)}`;
    const ref = db.doc(`memberCodes/${code}`);
    const created = await db.runTransaction(async (tx) => {
      if ((await tx.get(ref)).exists) return false;
      tx.create(ref, { at: FieldValue.serverTimestamp() });
      return true;
    });
    if (created) return code;
  }
  throw new HttpsError("internal", "Please try again.");
}

/**
 * Keeps only photo entries whose image was really uploaded to the member's own Storage folder,
 * so a client can't claim a photo it never uploaded (which would bypass other members'
 * "require a profile photo" setting).
 */
async function uploadedPhotos(uid: string, photos: { id: string; seed: number }[]) {
  const bucket = getStorage(app).bucket();
  const kept = await Promise.all(photos.map(async (p) => {
    try {
      const [exists] = await bucket.file(`photos/${uid}/${p.id}.jpg`).exists();
      return exists ? p : null;
    } catch {
      return null;
    }
  }));
  return kept.filter((p): p is { id: string; seed: number } => p !== null);
}

/**
 * Validates and publishes the member's profile. The client can never set trust fields
 * (verification badges, member code, response rate): those come from the server's copy.
 */
export const publishProfile = onCall(callableOptions, async (req) => {
  const uid = requireUid(req);
  const input = parse(z.object({ profile: ProfileInput }), req.data).profile;

  if (input.age < minimumMarriageAge(input.gender)) {
    throw new HttpsError("failed-precondition", `Members must be at least ${minimumMarriageAge(input.gender)} (legal marriageable age).`);
  }
  if (input.preferences.minAge < minimumMarriageAge(input.gender === "FEMALE" ? "MALE" : "FEMALE")) {
    input.preferences.minAge = minimumMarriageAge(input.gender === "FEMALE" ? "MALE" : "FEMALE");
  }

  const userRef = db.doc(`users/${uid}`);
  const existing = (await userRef.get()).get("profile") as Profile | undefined;

  const bioRisk = assessText(input.about);
  const about = redact(input.about);
  const flagged = containsContactDetails(input.about) || bioRisk.score >= 40;

  const profile: Profile = {
    ...input,
    id: uid,
    about,
    interests: input.interests.map(redact),
    photos: await uploadedPhotos(uid, input.photos),
    verification: existing?.verification ?? { phone: true, selfie: false, governmentId: false, workEmail: false },
    memberCode: existing?.memberCode ?? (await uniqueMemberCode()),
    responseRate: existing?.responseRate ?? null,
    activity: "TODAY",
  };

  const batch = db.batch();
  batch.set(userRef, { profile, updatedAt: FieldValue.serverTimestamp() }, { merge: true });
  batch.set(db.doc(`profiles/${uid}`), { ...publicCard(profile), updatedAt: Date.now() });
  // MeitY matrimonial advisory: retain the profile creator's IP for one year (restricted collection).
  batch.set(db.doc(`ipLog/${uid}`), {
    lastIp: req.rawRequest.ip ?? null,
    lastSeenAt: FieldValue.serverTimestamp(),
    ...(existing ? {} : { firstIp: req.rawRequest.ip ?? null, firstSeenAt: FieldValue.serverTimestamp() }),
  }, { merge: true });
  if (flagged) {
    batch.set(db.collection("moderationQueue").doc(), { type: "bio", uid, signals: bioRisk.signals, at: FieldValue.serverTimestamp() });
  }
  await batch.commit();
  return { profile };
});

/** Lets the server enforce the member's contact rules (who may send them interests). */
export const updatePrivacySettings = onCall(callableOptions, async (req) => {
  const uid = requireUid(req);
  const s = parse(z.object({
    verifiedOnlyContact: z.boolean(),
    requirePhotoToContact: z.boolean(),
    incognito: z.boolean(),
  }), req.data);
  await db.doc(`users/${uid}`).set({ privacy: s }, { merge: true });
  return { ok: true };
});
