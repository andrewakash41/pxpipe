/**
 * Server mirror of app/src/main/java/com/vachan/app/core/matching/CompatibilityEngine.kt.
 * Deterministic, explainable, two-way. The server is the authority for who is shown;
 * the app recomputes the same factors to explain them.
 */
import type { Profile, SearchFilters } from "../lib/model.js";
import { EDUCATION_RANK } from "../lib/model.js";

export type Factor = "PREFERENCES" | "VALUES" | "LIFESTYLE" | "INTERESTS" | "FAMILY" | "LANGUAGE" | "LOCATION";

export const DEFAULT_WEIGHTS: Record<Factor, number> = {
  PREFERENCES: 0.35, VALUES: 0.25, LIFESTYLE: 0.12, INTERESTS: 0.10, FAMILY: 0.06, LANGUAGE: 0.06, LOCATION: 0.06,
};

export interface FactorScore { factor: Factor; score: number; detail: string }
export interface Compatibility {
  score: number;
  youFitThem: number;
  theyFitYou: number;
  factors: FactorScore[];
  frictions: string[];
  sharedInterests: string[];
}

const VALUE_TOPICS: Record<string, string> = {
  LIVING_ARRANGEMENT: "where you'll live", CAREERS: "careers after marriage", CHILDREN: "children",
  RELOCATION: "relocation", FAITH: "faith and rituals", FINANCES: "managing money", HOUSEHOLD: "sharing the household",
  PARENTS_ROLE: "family involvement", SOCIAL_LIFE: "weekends and social life", CONFLICT: "handling disagreements",
  MENTAL_HEALTH: "emotional wellbeing", SPENDING: "spending and saving",
};

const clamp = (x: number, lo: number, hi: number) => Math.min(hi, Math.max(lo, x));
export const harmonic = (x: number, y: number) => (x + y === 0 ? 0 : (2 * x * y) / (x + y));
const seeking = (p: Profile) => (p.gender === "FEMALE" ? "MALE" : "FEMALE");

export function distanceKm(a: Profile, b: Profile): number {
  const r = 6371;
  const toRad = (d: number) => (d * Math.PI) / 180;
  const dLat = toRad(b.location.lat - a.location.lat);
  const dLng = toRad(b.location.lng - a.location.lng);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(toRad(a.location.lat)) * Math.cos(toRad(b.location.lat)) * Math.sin(dLng / 2) ** 2;
  return 2 * r * Math.asin(Math.sqrt(h));
}

export function dietCompatibility(a: string, b: string): number {
  if (a === b) return 1;
  const veg = new Set(["VEGETARIAN", "VEGAN", "JAIN", "EGGETARIAN"]);
  if (veg.has(a) && veg.has(b)) return a === "EGGETARIAN" || b === "EGGETARIAN" ? 0.75 : 0.85;
  if ((a === "NON_VEGETARIAN" && b === "EGGETARIAN") || (b === "NON_VEGETARIAN" && a === "EGGETARIAN")) return 0.6;
  if ([a, b].some((d) => d === "JAIN" || d === "VEGAN")) return 0.2;
  return 0.4;
}

const habitTolerance = (h: string) => (h === "NEVER" ? 1 : h === "OCCASIONALLY" ? 0.4 : 0);

interface Dim { key: string; weight: number; score: number; miss: string }

function dimensions(seeker: Profile, c: Profile): Dim[] {
  const p = seeker.preferences;
  const dims: Dim[] = [];
  const age = c.age >= p.minAge && c.age <= p.maxAge ? 1
    : c.age < p.minAge ? Math.max(0, 1 - (p.minAge - c.age) / 5) : Math.max(0, 1 - (c.age - p.maxAge) / 5);
  dims.push({ key: "AGE", weight: 3, score: age, miss: `age ${c.age}` });
  if (p.minHeightCm != null || p.maxHeightCm != null) {
    const min = p.minHeightCm ?? 0;
    const max = p.maxHeightCm ?? 300;
    const s = c.heightCm >= min && c.heightCm <= max ? 1
      : c.heightCm < min ? Math.max(0, 1 - (min - c.heightCm) / 10) : Math.max(0, 1 - (c.heightCm - max) / 10);
    dims.push({ key: "HEIGHT", weight: 1, score: s, miss: "height" });
  }
  if (p.maritalStatuses.length) dims.push({ key: "MARITAL_STATUS", weight: 2, score: p.maritalStatuses.includes(c.maritalStatus) ? 1 : 0, miss: "marital status" });
  if (p.religions.length) dims.push({ key: "RELIGION", weight: 2, score: c.religion && p.religions.includes(c.religion) ? 1 : 0, miss: "religion" });
  if (p.motherTongues.length) {
    const t = new Set(p.motherTongues.map((x) => x.toLowerCase()));
    const s = t.has(c.motherTongue.toLowerCase()) ? 1 : c.languages.some((l) => t.has(l.toLowerCase())) ? 0.6 : 0;
    dims.push({ key: "MOTHER_TONGUE", weight: 1.5, score: s, miss: "mother tongue" });
  }
  if (p.diets.length) {
    const s = p.diets.includes(c.diet) ? 1 : Math.max(...p.diets.map((d) => dietCompatibility(d, c.diet)));
    dims.push({ key: "DIET", weight: 1.5, score: s, miss: "diet" });
  }
  if (p.minEducation) {
    const gap = EDUCATION_RANK[p.minEducation] - EDUCATION_RANK[c.education];
    dims.push({ key: "EDUCATION", weight: 1, score: gap <= 0 ? 1 : gap === 1 ? 0.5 : 0, miss: "education" });
  }
  if (p.maxDistanceKm != null) {
    const d = distanceKm(seeker, c);
    let s = d <= p.maxDistanceKm ? 1 : Math.max(0, 1 - (d - p.maxDistanceKm) / p.maxDistanceKm);
    if (c.openToRelocate || seeker.openToRelocate) s = Math.max(s, 0.6);
    dims.push({ key: "DISTANCE", weight: 1.5, score: s, miss: `${Math.round(d)} km away` });
  }
  if (p.countries.length) {
    const inCountry = p.countries.some((x) => x.toLowerCase() === c.location.country.toLowerCase());
    dims.push({ key: "COUNTRY", weight: 1.5, score: inCountry ? 1 : c.openToRelocate ? 0.5 : 0, miss: "country" });
  }
  if (!p.acceptsSmoking) dims.push({ key: "SMOKING", weight: 1, score: habitTolerance(c.smoking), miss: "smoking" });
  if (!p.acceptsDrinking) dims.push({ key: "DRINKING", weight: 1, score: habitTolerance(c.drinking), miss: "drinking" });
  if (!p.acceptsChildren) dims.push({ key: "CHILDREN", weight: 2, score: c.hasChildren ? 0 : 1, miss: "has children" });
  return dims;
}

export function preferenceFit(seeker: Profile, c: Profile) {
  const dims = dimensions(seeker, c);
  const total = dims.reduce((s, d) => s + d.weight, 0);
  return {
    score: dims.reduce((s, d) => s + d.weight * d.score, 0) / total,
    matched: dims.filter((d) => d.score >= 0.99).length,
    total: dims.length,
    misses: dims.filter((d) => d.score < 0.5).map((d) => d.miss),
  };
}

function passesMustHaves(seeker: Profile, c: Profile): boolean {
  if (!seeker.preferences.mustHaves.length) return true;
  const byKey = new Map(dimensions(seeker, c).map((d) => [d.key, d.score]));
  return seeker.preferences.mustHaves.every((k) => (byKey.get(k) ?? 1) >= 0.99);
}

export interface PolicyContext {
  blocked: Set<string>;
  viewerVerified: boolean;
}

export function isEligible(viewer: Profile, c: Profile, ctx: PolicyContext): boolean {
  if (viewer.id === c.id || ctx.blocked.has(c.id)) return false;
  if (c.gender !== seeking(viewer) || viewer.gender !== seeking(c)) return false;
  if (c.visibility === "HIDDEN") return false;
  if (c.visibility === "VERIFIED_ONLY" && !ctx.viewerVerified) return false;
  return passesMustHaves(viewer, c) && passesMustHaves(c, viewer);
}

function shared(a: Profile, b: Profile): FactorScore[] {
  const out: FactorScore[] = [];
  // Values
  const qs = Object.keys(VALUE_TOPICS).filter((q) => a.values[q as keyof typeof a.values] && b.values[q as keyof typeof b.values]);
  if (qs.length >= 3) {
    let w = 0, s = 0, aligned = 0;
    for (const q of qs) {
      const x = a.values[q as keyof typeof a.values]!;
      const y = b.values[q as keyof typeof b.values]!;
      const wt = 0.25 + (x.importance + y.importance) / 2;
      if (Math.abs(x.answer - y.answer) <= 1) aligned++;
      s += wt * (1 - Math.abs(x.answer - y.answer) / 4);
      w += wt;
    }
    out.push({ factor: "VALUES", score: s / w, detail: `Aligned on ${aligned} of ${qs.length} life questions` });
  }
  // Lifestyle
  const order = ["NEVER", "OCCASIONALLY", "REGULARLY"];
  const smoke = 1 - Math.abs(order.indexOf(a.smoking) - order.indexOf(b.smoking)) / 2;
  const drink = 1 - Math.abs(order.indexOf(a.drinking) - order.indexOf(b.drinking)) / 2;
  out.push({ factor: "LIFESTYLE", score: dietCompatibility(a.diet, b.diet) * 0.5 + smoke * 0.25 + drink * 0.25, detail: a.diet === b.diet ? "Same food habits" : "Different everyday habits" });
  // Interests
  if (a.interests.length && b.interests.length) {
    const sa = new Set(a.interests);
    const common = b.interests.filter((i) => sa.has(i));
    const union = new Set([...a.interests, ...b.interests]).size;
    out.push({ factor: "INTERESTS", score: clamp(Math.min(1, common.length / 4) * 0.7 + (common.length / union) * 0.3, 0, 1), detail: common.length ? `Both enjoy ${common.slice(0, 3).join(", ")}` : "No shared interests yet" });
  }
  // Family
  const fam = a.familyType === b.familyType ? 1 : a.familyType === "FLEXIBLE" || b.familyType === "FLEXIBLE" ? 0.8 : 0.3;
  out.push({ factor: "FAMILY", score: fam, detail: a.familyType === b.familyType ? "Same family preference" : "Different family preferences" });
  // Language
  const la = new Set([...a.languages, a.motherTongue].map((x) => x.toLowerCase()));
  const lb = [...b.languages, b.motherTongue].map((x) => x.toLowerCase());
  const sameTongue = a.motherTongue.toLowerCase() === b.motherTongue.toLowerCase();
  out.push({ factor: "LANGUAGE", score: sameTongue ? 1 : lb.some((l) => la.has(l)) ? 0.8 : 0.3, detail: sameTongue ? "Same mother tongue" : "Shared language" });
  // Location
  const d = distanceKm(a, b);
  const sameCity = a.location.city.toLowerCase() === b.location.city.toLowerCase();
  let loc = sameCity ? 1 : d < 200 ? 0.8 : a.location.country.toLowerCase() === b.location.country.toLowerCase() ? 0.6 : 0.3;
  if (a.openToRelocate || b.openToRelocate) loc = Math.max(loc, 0.7);
  out.push({ factor: "LOCATION", score: loc, detail: sameCity ? "Same city" : `${Math.round(d)} km apart` });
  return out;
}

export function score(a: Profile, b: Profile, multipliers: Partial<Record<Factor, number>> = {}): Compatibility {
  const w = (f: Factor) => DEFAULT_WEIGHTS[f] * clamp(multipliers[f] ?? 1, 0.5, 2);
  const ab = preferenceFit(a, b);
  const ba = preferenceFit(b, a);
  const s = shared(a, b);
  const combine = (pref: number) => {
    const parts: [Factor, number][] = [["PREFERENCES", pref], ...s.map((x) => [x.factor, x.score] as [Factor, number])];
    const tw = parts.reduce((t, [f]) => t + w(f), 0);
    return parts.reduce((t, [f, v]) => t + w(f) * v, 0) / tw;
  };
  const frictions: string[] = [];
  for (const q of Object.keys(VALUE_TOPICS)) {
    const x = a.values[q as keyof typeof a.values];
    const y = b.values[q as keyof typeof b.values];
    if (x && y && Math.min(x.importance, y.importance) >= 2 && Math.abs(x.answer - y.answer) >= 3) {
      frictions.push(`Different views on ${VALUE_TOPICS[q]}`);
    }
  }
  const sa = new Set(a.interests);
  return {
    score: Math.round(clamp(harmonic(combine(ab.score), combine(ba.score)) * 100, 0, 100)),
    youFitThem: Math.round(ba.score * 100),
    theyFitYou: Math.round(ab.score * 100),
    factors: [{ factor: "PREFERENCES", score: harmonic(ab.score, ba.score), detail: `${ba.matched}/${ba.total} and ${ab.matched}/${ab.total} preferences met` }, ...s],
    frictions,
    sharedInterests: b.interests.filter((i) => sa.has(i)).sort(),
  };
}

export function rankDailyPicks(
  viewer: Profile,
  candidates: Profile[],
  ctx: PolicyContext & { exclude: Set<string> },
  count: number,
  multipliers: Partial<Record<Factor, number>> = {},
): Profile[] {
  const boost = (p: Profile) =>
    (p.verification.selfie || p.verification.governmentId ? 4 : 0) + (p.verification.governmentId ? 1 : 0) +
    (p.responseRate ?? 0) * 3 + ({ TODAY: 2, THIS_WEEK: 1, THIS_MONTH: 0, EARLIER: -3 } as const)[p.activity];
  const pool = candidates
    .filter((c) => !ctx.exclude.has(c.id) && isEligible(viewer, c, ctx))
    .map((c) => ({ p: c, rank: score(viewer, c, multipliers).score }))
    .filter((x) => x.rank >= 40)
    .map((x) => ({ ...x, rank: x.rank + boost(x.p) }));
  const picked: typeof pool = [];
  while (picked.length < count && pool.length) {
    let bestI = 0;
    let best = -Infinity;
    pool.forEach((x, i) => {
      const penalty = picked.filter((y) => y.p.profession.toLowerCase() === x.p.profession.toLowerCase()).length * 3 +
        picked.filter((y) => y.p.location.city.toLowerCase() === x.p.location.city.toLowerCase()).length * 1.5;
      if (x.rank - penalty > best) { best = x.rank - penalty; bestI = i; }
    });
    picked.push(pool.splice(bestI, 1)[0]!);
  }
  return picked.map((x) => x.p);
}

export function matchesFilters(p: Profile, f: SearchFilters): boolean {
  if (f.minAge != null && p.age < f.minAge) return false;
  if (f.maxAge != null && p.age > f.maxAge) return false;
  if (f.cities.length && !f.cities.some((c) => c.toLowerCase() === p.location.city.toLowerCase())) return false;
  if (f.religions.length && (!p.religion || !f.religions.includes(p.religion))) return false;
  if (f.motherTongues.length && !f.motherTongues.some((t) => t.toLowerCase() === p.motherTongue.toLowerCase())) return false;
  if (f.diets.length && !f.diets.includes(p.diet)) return false;
  if (f.professions.length && !f.professions.some((x) => p.profession.toLowerCase().includes(x.toLowerCase()))) return false;
  if (f.minEducation && EDUCATION_RANK[p.education] < EDUCATION_RANK[f.minEducation]) return false;
  if (f.maritalStatuses.length && !f.maritalStatuses.includes(p.maritalStatus)) return false;
  if (f.openToRelocate === true && !p.openToRelocate) return false;
  const abroad = p.location.country.toLowerCase() !== "india";
  if (f.livesAbroad === true && !abroad) return false;
  if (f.livesAbroad === false && abroad) return false;
  if (f.verifiedOnly && !(p.verification.selfie || p.verification.governmentId)) return false;
  if (f.minHeightCm != null && p.heightCm < f.minHeightCm) return false;
  if (f.keywords.length) {
    const hay = `${p.about} ${p.interests.join(" ")} ${p.profession}`.toLowerCase();
    if (!f.keywords.some((k) => hay.includes(k.toLowerCase()))) return false;
  }
  return true;
}
