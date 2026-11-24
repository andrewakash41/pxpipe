import { describe, expect, it } from "vitest";
import { dietCompatibility, isEligible, matchesFilters, rankDailyPicks, score } from "../src/matching/engine.js";
import { chennai, man, woman } from "./fixtures.js";

const ctx = { blocked: new Set<string>(), viewerVerified: true };

describe("compatibility engine (mirror of the Kotlin engine)", () => {
  it("scores a well-matched pair highly and symmetrically", () => {
    const a = woman();
    const b = man();
    expect(score(a, b).score).toBeGreaterThanOrEqual(80);
    expect(score(a, b).score).toBe(score(b, a).score);
    expect(score(a, b).sharedInterests).toEqual(["Cooking", "Music", "Travel"]);
  });

  it("penalises one-sided fit (reciprocity)", () => {
    const a = woman();
    const oneSided = man({ preferences: { ...man().preferences, minAge: 20, maxAge: 22 } });
    expect(score(a, oneSided).score).toBeLessThan(score(a, man()).score - 10);
    expect(score(a, oneSided).youFitThem).toBeLessThan(score(a, oneSided).theyFitYou);
  });

  it("applies must-haves as hard filters in both directions", () => {
    const strict = woman({ preferences: { ...woman().preferences, religions: ["SIKH"], mustHaves: ["RELIGION"] } });
    expect(isEligible(strict, man(), ctx)).toBe(false);
    expect(isEligible(man(), strict, ctx)).toBe(false);
  });

  it("respects gender, visibility, verification and blocks", () => {
    const a = woman();
    expect(isEligible(a, woman({ id: "w2" }), ctx)).toBe(false);
    expect(isEligible(a, man({ visibility: "HIDDEN" }), ctx)).toBe(false);
    expect(isEligible(a, man({ visibility: "VERIFIED_ONLY" }), { ...ctx, viewerVerified: false })).toBe(false);
    expect(isEligible(a, man(), { ...ctx, blocked: new Set(["m1"]) })).toBe(false);
  });

  it("surfaces strongly held opposing values as frictions", () => {
    const a = woman({ values: { CHILDREN: { answer: 0, importance: 3 }, CAREERS: { answer: 4, importance: 2 }, FAITH: { answer: 2, importance: 1 } } });
    const b = man({ values: { CHILDREN: { answer: 4, importance: 3 }, CAREERS: { answer: 4, importance: 2 }, FAITH: { answer: 2, importance: 1 } } });
    expect(score(a, b).frictions.join(" ")).toContain("children");
  });

  it("treats the vegetarian family as close", () => {
    expect(dietCompatibility("VEGETARIAN", "JAIN")).toBeGreaterThan(0.8);
    expect(dietCompatibility("JAIN", "NON_VEGETARIAN")).toBeLessThan(0.3);
  });

  it("builds diversified, capped daily picks", () => {
    const viewer = woman();
    const pool = Array.from({ length: 12 }, (_, i) => man({ id: `d${i}`, profession: i < 10 ? "Doctor" : "Architect", location: i % 2 ? chennai : man().location }));
    const picks = rankDailyPicks(viewer, pool, { ...ctx, exclude: new Set(["d0"]) }, 4);
    expect(picks).toHaveLength(4);
    expect(picks.map((p) => p.id)).not.toContain("d0");
    expect(picks.some((p) => p.profession === "Architect")).toBe(true);
  });

  it("filters search results deterministically", () => {
    const f = { cities: ["Pune"], religions: [], motherTongues: [], diets: ["VEGETARIAN"], professions: ["doctor"], maritalStatuses: [], verifiedOnly: true, keywords: [], minAge: 28, maxAge: 32 } as const;
    expect(matchesFilters(man(), f as never)).toBe(true);
    expect(matchesFilters(man({ profession: "Architect" }), f as never)).toBe(false);
    expect(matchesFilters(man({ verification: { phone: true, selfie: false, governmentId: false, workEmail: false } }), f as never)).toBe(false);
  });
});
