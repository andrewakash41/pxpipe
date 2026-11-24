/**
 * Server-side safety heuristics. No language model is used here, and message content is never
 * stored for this: fan-out detection keeps only a one-way hash of normalised text for 24 hours.
 * Content-level scam detection for chats runs on the device (Safety Copilot).
 */
import { createHash } from "node:crypto";
import { findPii } from "../ai/redact.js";

const RULES: [string, number, RegExp[]][] = [
  ["money_request", 40, [
    /\b(send|transfer|lend|give|need|pay|deposit)\b.{0,30}\b(money|rs\.?|₹|rupees|inr|amount|funds|payment|loan|dollars|usd|lakh|thousand|\d{3,})/i,
    /\b(paise|paisa|rupaye)\b.{0,25}\b(bhej|bhejo|bhejdo|chahiye|de do|dedo|transfer)/i,
  ]],
  ["customs_parcel", 45, [/\b(customs?|clearance|parcel|package|courier|consignment)\b.{0,60}\b(fee|charge|duty|tax|pay|release|stuck|held)/i]],
  ["investment", 45, [
    /\b(crypto|bitcoin|usdt|forex|binary option|trading app|trading platform|investment platform)\b/i,
    /\b(guaranteed|assured|double)\b.{0,20}\b(return|profit|money|income)/i,
  ]],
  ["gift_card", 35, [/\b(gift ?card|google play card|amazon (pay )?card|itunes card|recharge code)\b/i]],
  ["otp_request", 50, [/\b(share|send|tell|forward|give)\b.{0,25}\b(otp|verification code|one.?time password)/i]],
  ["off_platform", 15, [/\b(whatsapp|telegram|signal app|snapchat|wechat)\b/i]],
  ["intimate_request", 40, [/\b(send|share)\b.{0,20}\b(nude|naked|private|intimate)\b.{0,15}\b(pic|pics|photo|photos|video)/i]],
];

export interface TextRisk { score: number; signals: string[] }

export function assessText(text: string): TextRisk {
  const signals = RULES.filter(([, , res]) => res.some((r) => r.test(text))).map(([name]) => name);
  let score = RULES.filter(([n]) => signals.includes(n)).reduce((s, [, w]) => s + w, 0);
  if (findPii(text).some((f) => f.kind === "UPI" || f.kind === "CARD")) {
    signals.push("payment_details");
    score += 30;
  }
  return { score, signals };
}

/** Normalises text so trivially varied copies of a scam script hash the same. */
export function fingerprint(text: string): string {
  const normalised = text.toLowerCase().replace(/\d+/g, "#").replace(/[^\p{L}#]+/gu, " ").trim();
  return createHash("sha256").update(normalised).digest("hex");
}

/** Same text sent to this many distinct conversations within 24 hours triggers review. */
export const FAN_OUT_THRESHOLD = 8;

export interface AccountSignals {
  accountAgeDays: number;
  verified: boolean;
  reportsAgainst: number;
  fanOutHits: number;
  highRiskNotes: number;
  deviceSignals: string[];
}

/**
 * Account risk 0..100. Thresholds drive *step-up* actions (require selfie verification, rate
 * limits, human review) — never automatic bans.
 */
export function accountRisk(s: AccountSignals): number {
  let r = 0;
  if (s.accountAgeDays < 3) r += 10;
  if (!s.verified) r += 15;
  r += Math.min(40, s.reportsAgainst * 12);
  r += Math.min(30, s.fanOutHits * 10);
  r += Math.min(20, s.highRiskNotes * 10);
  if (s.deviceSignals.includes("EMULATOR") || s.deviceSignals.includes("ROOT_BINARY")) r += 5;
  return Math.min(100, r);
}

export type RiskAction = "none" | "require_verification" | "limit_and_review";

export function riskAction(score: number): RiskAction {
  if (score >= 60) return "limit_and_review";
  if (score >= 35) return "require_verification";
  return "none";
}
