/**
 * Server-side PII redaction (mirror of app core/safety/PiiDetector.kt). Applied to any free text
 * before it reaches an AI provider, and again to everything a provider returns.
 */
type Kind = "EMAIL" | "UPI" | "URL" | "OTP" | "PHONE" | "AADHAAR" | "CARD" | "PAN" | "HANDLE" | "PINCODE";

const TOKENS: Record<Kind, string> = {
  EMAIL: "[EMAIL]", UPI: "[UPI]", URL: "[LINK]", OTP: "[OTP]", PHONE: "[PHONE]", AADHAAR: "[ID_NUMBER]",
  CARD: "[ACCOUNT]", PAN: "[ID_NUMBER]", HANDLE: "[HANDLE]", PINCODE: "[PINCODE]",
};

// Order matters: more specific patterns first; earlier matches win overlapping spans.
const PATTERNS: [Kind, RegExp][] = [
  ["EMAIL", /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/g],
  ["UPI", /\b[A-Za-z0-9._-]{2,256}@(?:ok[a-z]+|ybl|ibl|axl|paytm|upi|apl|yapl|axisbank|icici|sbi|hdfcbank|kotak|barodampay|aubank|jupiteraxis|fam|freecharge|slice|naviaxis|[a-z]{3,10})\b/gi],
  ["URL", /\b(?:https?:\/\/|www\.)\S+|\b[a-z0-9-]+\.(?:com|in|net|org|io|app|me|link|ly|xyz|apk)(?:\/\S*)?\b/gi],
  ["OTP", /\b(?:otp|verification code|passcode|code is)\b[^\d]{0,12}\d{4,8}\b/gi],
  ["PHONE", /(?:\+|\b00)91[\s-]?[6-9]\d{4}[\s-]?\d{5}\b/g],
  ["AADHAAR", /\b[2-9]\d{3}[\s-]?\d{4}[\s-]?\d{4}\b/g],
  ["CARD", /\b(?:\d[\s-]?){13,19}\b/g],
  ["PHONE", /(?<![\d+])0?[6-9]\d{4}[\s-]?\d{5}(?!\d)/g],
  ["PAN", /\b[A-Z]{5}\d{4}[A-Z]\b/g],
  ["HANDLE", /(?:^|\s)@[A-Za-z0-9_.]{3,30}\b|\b(?:insta|instagram|ig|snap|snapchat|fb|telegram|tg)\s*(?:id|handle)?\s*[:-]?\s*@?[A-Za-z0-9_.]{3,30}\b/gi],
  ["PINCODE", /\b[1-9]\d{2}\s?\d{3}\b/g],
];

export interface Finding { kind: Kind; start: number; end: number }

export function findPii(text: string): Finding[] {
  const taken: Finding[] = [];
  for (const [kind, re] of PATTERNS) {
    for (const m of text.matchAll(re)) {
      const start = m.index ?? 0;
      const end = start + m[0].length;
      if (taken.some((t) => t.start < end && start < t.end)) continue;
      taken.push({ kind, start, end });
    }
  }
  return taken.sort((a, b) => a.start - b.start);
}

export function redact(text: string): string {
  const findings = findPii(text);
  if (!findings.length) return text;
  let out = "";
  let cursor = 0;
  for (const f of findings) {
    if (f.start < cursor) continue;
    out += text.slice(cursor, f.start) + TOKENS[f.kind];
    cursor = f.end;
  }
  return out + text.slice(cursor);
}

export function containsContactDetails(text: string): boolean {
  return findPii(text).some((f) => ["PHONE", "EMAIL", "UPI", "HANDLE", "URL"].includes(f.kind));
}

/** Recursively redacts every string in a JSON-like value. */
export function redactDeep<T>(value: T): T {
  if (typeof value === "string") return redact(value) as T;
  if (Array.isArray(value)) return value.map((v) => redactDeep(v)) as T;
  if (value && typeof value === "object") {
    return Object.fromEntries(Object.entries(value as Record<string, unknown>).map(([k, v]) => [k, redactDeep(v)])) as T;
  }
  return value;
}

/**
 * Wraps untrusted text so the model treats it as data. Angle brackets are neutralised so the text
 * cannot close the fence and smuggle instructions outside it.
 */
export function fence(text: string): string {
  return `<user_text>${text.replace(/</g, "‹").replace(/>/g, "›")}</user_text>`;
}
