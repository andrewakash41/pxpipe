import type { Profile } from "../src/lib/model.js";

const values = {
  LIVING_ARRANGEMENT: { answer: 3, importance: 2 },
  CAREERS: { answer: 4, importance: 2 },
  CHILDREN: { answer: 1, importance: 2 },
  FINANCES: { answer: 2, importance: 2 },
  HOUSEHOLD: { answer: 4, importance: 2 },
  PARENTS_ROLE: { answer: 2, importance: 2 },
};

export const pune = { city: "Pune", state: "Maharashtra", country: "India", lat: 18.52, lng: 73.86 };
export const chennai = { city: "Chennai", state: "Tamil Nadu", country: "India", lat: 13.08, lng: 80.27 };

const base = {
  heightCm: 170, location: pune, religion: "HINDU", motherTongue: "Marathi", community: "Example", showCommunity: false,
  maritalStatus: "NEVER_MARRIED", hasChildren: false, education: "MASTERS", educationField: "Computer Science",
  incomeBand: "L20_35", showIncome: false, diet: "VEGETARIAN", smoking: "NEVER", drinking: "NEVER", familyType: "NUCLEAR",
  managedBy: "SELF", about: "I love trekking and cooking.", languages: ["English", "Hindi"], values, openToRelocate: false,
  residencyStatus: null, photos: [], photoVisibility: "VERIFIED_MEMBERS", visibility: "EVERYONE", memberCode: "VCH-100001",
  responseRate: 0.8, activity: "THIS_WEEK",
} as const;

export function woman(over: Partial<Profile> = {}): Profile {
  return {
    ...base, id: "w1", firstName: "Ananya", lastInitial: "K", gender: "FEMALE", age: 28, profession: "Software engineer",
    interests: ["Travel", "Music", "Cooking", "Reading"],
    verification: { phone: true, selfie: true, governmentId: false, workEmail: false },
    preferences: { minAge: 28, maxAge: 34, maritalStatuses: ["NEVER_MARRIED"], religions: [], motherTongues: [], diets: [], countries: [], acceptsSmoking: true, acceptsDrinking: true, acceptsChildren: true, mustHaves: [] },
    ...over,
  } as Profile;
}

export function man(over: Partial<Profile> = {}): Profile {
  return {
    ...base, id: "m1", firstName: "Rohan", lastInitial: "D", gender: "MALE", age: 30, heightCm: 178, profession: "Doctor",
    interests: ["Travel", "Music", "Cooking", "Cricket"],
    verification: { phone: true, selfie: false, governmentId: true, workEmail: false },
    preferences: { minAge: 25, maxAge: 30, maritalStatuses: ["NEVER_MARRIED"], religions: [], motherTongues: [], diets: [], countries: [], acceptsSmoking: true, acceptsDrinking: true, acceptsChildren: true, mustHaves: [] },
    ...over,
  } as Profile;
}
