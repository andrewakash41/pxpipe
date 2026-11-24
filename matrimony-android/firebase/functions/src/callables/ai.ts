import { defineSecret, defineString } from "firebase-functions/params";
import { logger } from "firebase-functions";
import { HttpsError, onCall } from "firebase-functions/v2/https";
import { z } from "zod";
import { AiGateway, AiUnavailable, createProvider } from "../ai/gateway.js";
import {
  assistantTask, draftBioTask, icebreakersTask, kindDeclineTask, matchInsightTask, parseSearchTask, translateTask,
} from "../ai/tasks.js";
import { blockedSet, callableOptions, FirestoreConsent, FirestoreQuota, myProfile, pairId, parse, publicProfile, requireUid, db } from "../lib/firebase.js";
import { type Profile, ProfileInput } from "../lib/model.js";
import { score } from "../matching/engine.js";

// Provider-agnostic configuration. Switch providers without code changes:
//   AI_PROVIDER = anthropic | openai | gemini | openai_compatible | mock
//   AI_MODEL    = provider model id (defaults to claude-opus-5-5 for anthropic; required for others)
//   AI_BASE_URL = for openai_compatible (vLLM / Ollama / gateway), optional override for others
//   AI_API_KEY  = Secret Manager secret holding the provider key
export const AI_PROVIDER = defineString("AI_PROVIDER", { default: "anthropic" });
export const AI_MODEL = defineString("AI_MODEL", { default: "" });
export const AI_BASE_URL = defineString("AI_BASE_URL", { default: "" });
export const AI_API_KEY = defineSecret("AI_API_KEY");

let cached: { key: string; gateway: AiGateway } | undefined;

function gateway(): AiGateway {
  const cfg = { provider: AI_PROVIDER.value(), model: AI_MODEL.value() || undefined, baseUrl: AI_BASE_URL.value() || undefined, apiKey: AI_API_KEY.value() || undefined };
  const key = `${cfg.provider}|${cfg.model}|${cfg.baseUrl}`;
  if (!cached || cached.key !== key) {
    cached = { key, gateway: new AiGateway(createProvider(cfg), new FirestoreConsent(), new FirestoreQuota(), logger) };
  }
  return cached.gateway;
}

const aiOptions = { ...callableOptions, secrets: [AI_API_KEY], timeoutSeconds: 60, memory: "512MiB" as const };

async function guarded<T>(fn: () => Promise<T>): Promise<T> {
  try {
    return await fn();
  } catch (e) {
    if (e instanceof AiUnavailable) {
      // The app falls back to its on-device templates when it receives these.
      if (e.reason === "no_consent") throw new HttpsError("failed-precondition", e.message);
      if (e.reason === "quota") throw new HttpsError("resource-exhausted", e.message);
      throw new HttpsError("unavailable", e.message);
    }
    throw e;
  }
}

/** The other member, only if the caller is allowed to see them. */
async function visibleOther(uid: string, otherId: string): Promise<Profile> {
  const [other, blocked] = await Promise.all([publicProfile(otherId), blockedSet(uid)]);
  if (!other || blocked.has(otherId)) throw new HttpsError("not-found", "This profile isn't available.");
  if (other.visibility === "HIDDEN" && !(await db.doc(`matches/${pairId(uid, otherId)}`).get()).exists) {
    throw new HttpsError("not-found", "This profile isn't available.");
  }
  return other;
}

const OtherId = z.object({ otherId: z.string().min(1).max(128) });

export const aiMatchInsight = onCall(aiOptions, async (req) => {
  const uid = requireUid(req);
  const { otherId } = parse(OtherId, req.data);
  const [me, other] = await Promise.all([myProfile(uid), visibleOther(uid, otherId)]);
  const out = await guarded(() => gateway().run(uid, matchInsightTask, { me, other, c: score(me, other) }));
  return { ...out, generatedByAi: true };
});

export const aiIcebreakers = onCall(aiOptions, async (req) => {
  const uid = requireUid(req);
  const { otherId } = parse(OtherId, req.data);
  const [me, other] = await Promise.all([myProfile(uid), visibleOther(uid, otherId)]);
  return guarded(() => gateway().run(uid, icebreakersTask, { me, other, c: score(me, other) }));
});

export const aiDraftBio = onCall(aiOptions, async (req) => {
  const uid = requireUid(req);
  const { profile, tone, language } = parse(z.object({
    profile: ProfileInput, tone: z.string().max(20), language: z.string().max(40),
  }), req.data);
  const draft = { ...profile, id: uid, verification: { phone: true, selfie: false, governmentId: false, workEmail: false }, memberCode: "", activity: "TODAY" } as Profile;
  return guarded(() => gateway().run(uid, draftBioTask, { me: draft, tone, language }));
});

export const aiParseSearch = onCall(aiOptions, async (req) => {
  const uid = requireUid(req);
  const { query } = parse(z.object({ query: z.string().min(1).max(200) }), req.data);
  const filters = await guarded(() => gateway().run(uid, parseSearchTask, { query }));
  return { filters };
});

export const aiKindDecline = onCall(aiOptions, async (req) => {
  const uid = requireUid(req);
  // The other person's name is deliberately not sent to the model.
  const lang = ((await db.doc(`users/${uid}`).get()).get("language") as string | undefined) ?? "English";
  return guarded(() => gateway().run(uid, kindDeclineTask, { language: lang }));
});

export const aiTranslate = onCall(aiOptions, async (req) => {
  const uid = requireUid(req);
  const { text, targetLanguage } = parse(z.object({ text: z.string().min(1).max(2000), targetLanguage: z.string().max(40) }), req.data);
  return guarded(() => gateway().run(uid, translateTask, { text, target: targetLanguage }));
});

export const aiAssistant = onCall(aiOptions, async (req) => {
  const uid = requireUid(req);
  const { history } = parse(z.object({
    history: z.array(z.object({ role: z.enum(["user", "assistant"]), text: z.string().max(1500) })).min(1).max(12),
  }), req.data);
  const me = await myProfile(uid);
  // Non-identifying context only: shape of preferences and profile completeness.
  const context = [
    `preferred age span ${me.preferences.maxAge - me.preferences.minAge} years`,
    `${me.preferences.mustHaves.length} must-have preferences`,
    `values answered ${Object.keys(me.values).length}/12`,
    `bio length ${me.about.length} characters`,
    `verified: ${me.verification.selfie || me.verification.governmentId}`,
  ].join(", ");
  return guarded(() => gateway().run(uid, assistantTask, { history, context }));
});
