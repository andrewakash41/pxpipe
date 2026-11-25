/**
 * Server-side domain model: zod schemas that mirror app/src/main/java/com/vachan/app/core/model/*.kt.
 * Enum values and field names must stay in sync with the Kotlin @Serializable classes, because the
 * app sends and receives these shapes as JSON.
 *
 * Validation philosophy: anything a client sends is untrusted. Schemas strip unknown keys (so forged
 * trust fields such as `verification`, `memberCode` or a photo `url` are silently dropped), bound
 * every string and list, and reject unknown enum values.
 */
import { z } from "zod";

// ------------------------------------------------------------------------------------- enums

export const Gender = z.enum(["FEMALE", "MALE"]);
export type Gender = z.infer<typeof Gender>;

export const MaritalStatus = z.enum(["NEVER_MARRIED", "DIVORCED", "AWAITING_DIVORCE", "WIDOWED", "ANNULLED"]);
export const Religion = z.enum(["HINDU", "MUSLIM", "CHRISTIAN", "SIKH", "JAIN", "BUDDHIST", "PARSI", "JEWISH", "SPIRITUAL", "NO_RELIGION", "OTHER"]);
export const Diet = z.enum(["VEGETARIAN", "EGGETARIAN", "NON_VEGETARIAN", "VEGAN", "JAIN"]);
export const Habit = z.enum(["NEVER", "OCCASIONALLY", "REGULARLY"]);
export const FamilyType = z.enum(["JOINT", "NUCLEAR", "FLEXIBLE"]);
export const ManagedBy = z.enum(["SELF", "PARENT", "SIBLING", "RELATIVE", "FRIEND"]);
export const EducationLevel = z.enum(["HIGH_SCHOOL", "DIPLOMA", "BACHELORS", "MASTERS", "PROFESSIONAL", "DOCTORATE"]);
export type EducationLevel = z.infer<typeof EducationLevel>;
export const IncomeBand = z.enum(["UNDISCLOSED", "UPTO_5L", "L5_10", "L10_20", "L20_35", "L35_50", "L50_100", "ABOVE_1CR"]);
export const PhotoVisibility = z.enum(["ALL_MEMBERS", "VERIFIED_MEMBERS", "AFTER_ACCEPT"]);
export const ProfileVisibility = z.enum(["EVERYONE", "VERIFIED_ONLY", "HIDDEN"]);
export type ProfileVisibility = z.infer<typeof ProfileVisibility>;
export const ActivityBucket = z.enum(["TODAY", "THIS_WEEK", "THIS_MONTH", "EARLIER"]);
export const ValueQuestion = z.enum([
  "LIVING_ARRANGEMENT", "CAREERS", "CHILDREN", "RELOCATION", "FAITH", "FINANCES",
  "HOUSEHOLD", "PARENTS_ROLE", "SOCIAL_LIFE", "CONFLICT", "MENTAL_HEALTH", "SPENDING",
]);
export const PreferenceKey = z.enum([
  "AGE", "HEIGHT", "MARITAL_STATUS", "RELIGION", "MOTHER_TONGUE", "DIET", "EDUCATION", "DISTANCE",
  "COUNTRY", "SMOKING", "DRINKING", "CHILDREN",
]);

export const ConsentPurpose = z.enum(["ESSENTIAL", "AI_ASSISTANCE", "ANALYTICS", "MARKETING"]);
export type ConsentPurpose = z.infer<typeof ConsentPurpose>;
export const ReportReason = z.enum([
  "SCAM", "FAKE_PROFILE", "HARASSMENT", "INAPPROPRIATE", "ALREADY_MARRIED", "UNDERAGE", "OFF_PLATFORM_PRESSURE", "OTHER",
]);
export type ReportReason = z.infer<typeof ReportReason>;
export const FamilyPermission = z.enum(["VIEW_MATCHES", "SHORTLIST", "SUGGEST", "EDIT_PROFILE"]);
export type FamilyPermission = z.infer<typeof FamilyPermission>;

/** Same ordering as EducationLevel.rank in Kotlin (professional degrees rank with master's). */
export const EDUCATION_RANK: Record<EducationLevel, number> = {
  HIGH_SCHOOL: 0, DIPLOMA: 1, BACHELORS: 2, MASTERS: 3, PROFESSIONAL: 3, DOCTORATE: 4,
};

/** Legal minimum marriageable age in India (Prohibition of Child Marriage Act, 2006). */
export function minimumMarriageAge(gender: Gender): number {
  return gender === "FEMALE" ? 18 : 21;
}

// ---------------------------------------------------------------------------------- helpers

const text = (max: number) => z.string().trim().max(max);
const shortList = (max: number, itemMax: number) => z.array(text(itemMax).min(1)).max(max);
/** Lists are deduplicated so `Set<String>` on the client round-trips cleanly. */
const set = <T extends z.ZodType>(item: T, max: number) =>
  z.array(item).max(max).transform((xs) => [...new Set(xs)] as z.output<T>[]);

// ------------------------------------------------------------------------------------ shapes

export const Location = z.object({
  city: text(60).min(1),
  state: text(60).default(""),
  country: text(60).min(1).default("India"),
  /** City-centroid coordinates only; precise locations are never collected. */
  lat: z.number().min(-90).max(90),
  lng: z.number().min(-180).max(180),
});

export const ValueAnswer = z.object({
  answer: z.number().int().min(0).max(4),
  importance: z.number().int().min(0).max(3),
});
export type ValueAnswer = z.infer<typeof ValueAnswer>;

/**
 * A photo reference as the client may send it: an opaque id (the object lives at
 * `photos/{uid}/{id}.jpg` in Storage) and an avatar seed. Any `url` is dropped — the server alone
 * issues short-lived signed URLs — so a profile can never point viewers at an external tracker.
 */
export const PhotoInput = z.object({
  id: z.string().regex(/^[A-Za-z0-9_-]{1,64}$/),
  seed: z.number().int().default(0),
});
export interface Photo { id: string; seed: number; url?: string | null }

export const PartnerPreferences = z.object({
  minAge: z.number().int().min(18).max(100).default(21),
  maxAge: z.number().int().min(18).max(100).default(40),
  minHeightCm: z.number().int().min(100).max(250).nullish(),
  maxHeightCm: z.number().int().min(100).max(250).nullish(),
  maritalStatuses: set(MaritalStatus, 5).default(["NEVER_MARRIED"]),
  religions: set(Religion, 11).default([]),
  motherTongues: set(text(30).min(1), 15).default([]),
  diets: set(Diet, 5).default([]),
  minEducation: EducationLevel.nullish(),
  maxDistanceKm: z.number().int().min(1).max(20_000).nullish(),
  countries: set(text(60).min(1), 15).default([]),
  acceptsSmoking: z.boolean().default(true),
  acceptsDrinking: z.boolean().default(true),
  acceptsChildren: z.boolean().default(true),
  mustHaves: set(PreferenceKey, 12).default([]),
}).refine((p) => p.minAge <= p.maxAge, { message: "Minimum age must not exceed maximum age", path: ["minAge"] })
  .refine((p) => p.minHeightCm == null || p.maxHeightCm == null || p.minHeightCm <= p.maxHeightCm, {
    message: "Minimum height must not exceed maximum height", path: ["minHeightCm"],
  });

/** Everything a member may set about themselves. Trust fields are deliberately absent. */
export const ProfileInput = z.object({
  firstName: text(40).min(1),
  lastInitial: text(2).default(""),
  gender: Gender,
  age: z.number().int().min(18).max(100),
  heightCm: z.number().int().min(120).max(230),
  location: Location,
  religion: Religion.nullish(),
  motherTongue: text(30).min(1),
  community: text(60).nullish(),
  showCommunity: z.boolean().default(false),
  maritalStatus: MaritalStatus.default("NEVER_MARRIED"),
  hasChildren: z.boolean().default(false),
  education: EducationLevel,
  educationField: text(80).default(""),
  profession: text(80).min(1),
  incomeBand: IncomeBand.default("UNDISCLOSED"),
  showIncome: z.boolean().default(false),
  diet: Diet,
  smoking: Habit.default("NEVER"),
  drinking: Habit.default("NEVER"),
  familyType: FamilyType.default("FLEXIBLE"),
  managedBy: ManagedBy.default("SELF"),
  about: z.string().trim().max(1200).default(""),
  interests: set(text(40).min(1), 12).default([]),
  languages: set(text(30).min(1), 10).default([]),
  values: z.partialRecord(ValueQuestion, ValueAnswer).default({}),
  openToRelocate: z.boolean().default(false),
  residencyStatus: text(60).nullish(),
  photos: z.array(PhotoInput).max(6).default([]),
  photoVisibility: PhotoVisibility.default("VERIFIED_MEMBERS"),
  visibility: ProfileVisibility.default("EVERYONE"),
  preferences: PartnerPreferences,
});
export type ProfileInput = z.infer<typeof ProfileInput>;

export interface Verification { phone: boolean; selfie: boolean; governmentId: boolean; workEmail: boolean }

/** A stored profile: the member's input plus server-owned trust fields. */
export type Profile = Omit<ProfileInput, "photos"> & {
  id: string;
  photos: Photo[];
  verification: Verification;
  memberCode: string;
  responseRate: number | null;
  activity: z.infer<typeof ActivityBucket>;
};

/**
 * The card other members can be shown (stored at `profiles/{uid}`, readable only through Cloud
 * Functions). Optional sensitive fields are blanked unless the member chose to show them.
 */
export function publicCard(p: Profile): Profile {
  return {
    ...p,
    community: p.showCommunity ? (p.community ?? null) : null,
    incomeBand: p.showIncome ? p.incomeBand : "UNDISCLOSED",
    // Signed URLs are minted per viewer; never persist one in the card.
    photos: p.photos.map(({ id, seed }) => ({ id, seed })),
  };
}

// ------------------------------------------------------------------------------------ search

export const SearchFilters = z.object({
  minAge: z.number().int().min(18).max(100).nullish(),
  maxAge: z.number().int().min(18).max(100).nullish(),
  cities: shortList(10, 60).default([]),
  religions: z.array(Religion).max(11).default([]),
  motherTongues: shortList(10, 30).default([]),
  diets: z.array(Diet).max(5).default([]),
  professions: shortList(10, 50).default([]),
  minEducation: EducationLevel.nullish(),
  maritalStatuses: z.array(MaritalStatus).max(5).default([]),
  openToRelocate: z.boolean().nullish(),
  livesAbroad: z.boolean().nullish(),
  verifiedOnly: z.boolean().default(false),
  minHeightCm: z.number().int().min(100).max(250).nullish(),
  keywords: shortList(5, 30).default([]),
});
export type SearchFilters = z.infer<typeof SearchFilters>;
