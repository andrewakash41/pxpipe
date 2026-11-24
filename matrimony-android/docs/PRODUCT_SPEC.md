# Vachan — product specification

*Vachan* (वचन, "a promise") is a working title for a privacy-first, AI-assisted matrimony
app for Indian users at home and abroad. The name is a placeholder and easy to change.

Every feature below is traced to a gap found in [RESEARCH.md](RESEARCH.md). Status legend:
**✅ built** (in this repo, works in Demo mode), **🔌 integration point** (code path and
interface exist; needs a vendor/partner account), **🗺 roadmap**.

---

## 1. Positioning

> **Verified people. Serious intent. Your data stays yours.**

Three promises users can see in the product:

1. **Trust first.** Verification is free for everyone and done *before* any payment. Scam
   defence is visible, not hidden.
2. **Fair by design.** Chat is free once both people say yes. Premium adds convenience, never
   access to a person. No sales calls, ever. No hidden auto-renewal.
3. **Private by default.** We collect the minimum, explain every AI use, and let people delete
   everything in two taps.

Target segments (from research): urban professionals 25–35 who feel swipe-fatigued; families
who want to help; Tier 2/3 vernacular users; remarriage seekers; NRIs and Gulf workers; late
marriers (29+).

## 2. Core user journeys

1. **Sign up** → phone OTP → choose language → read a plain-language privacy notice → pick
   consents per purpose (AI matchmaking, analytics, marketing are separately optional) →
   confirm *intent to marry* and minimum legal age (21 men / 18 women) as the MeitY advisory
   requires.
2. **Build profile** (self, or by a parent/sibling via Family Mode) → basics → background
   (religion, mother tongue, community are *optional* and can be hidden) → education and
   career → lifestyle → family → **Values Compass** questionnaire → partner preferences →
   AI-assisted bio → photos with privacy level.
3. **Verify** → selfie liveness → government ID (DigiLocker / Aadhaar offline) → optional work
   email. Badges appear on the profile.
4. **Discover** → a small set of **Daily Picks** (default 8) ranked by reciprocal
   compatibility, each with a transparent "why you match" breakdown → send interest with an
   optional note.
5. **Connect** → mutual interest unlocks chat (free) with Safety Copilot, icebreakers and
   translation → share phone number only via explicit, revocable consent → "Meet safely" plan
   with a trusted-contact check-in.
6. **Close the loop** → unanswered interests expire after 7 days (the sender is told), and
   declining takes one tap with a kind, AI-drafted note → hide or delete the profile when engaged.

## 3. Feature list

### 3.1 Trust and verification

| Feature | Why (gap) | Status |
|---|---|---|
| Phone OTP sign-up (Firebase Auth) | Baseline identity, required by advisory | ✅ |
| Intent-to-marry + correct-information declaration, minimum-age gate (21 M / 18 F) | MeitY advisory; Prohibition of Child Marriage Act | ✅ |
| **Free** selfie liveness with randomised gesture challenge | Fake profiles; "verification only after payment" complaint | ✅ UI flow + 🔌 vendor liveness/face-match (with injection-attack detection) |
| Government ID via DigiLocker / Aadhaar offline (QR / verifiable credential). Only the *result* is stored (name match, age band, gender), never the Aadhaar number | Fake profiles; legal requirement to verify ID | 🔌 needs UIDAI/DigiLocker partner registration + legal review |
| Work-email verification | Professionals want proof of employer | ✅ UI, 🔌 email OTP sender |
| AI image forensics on every photo upload (AI-generated / face-swap / stock-photo detection) | Deepfake profiles defeat reverse image search | 🔌 provider interface in backend |
| Profile consistency check (age vs education timeline, profession vs income band) → *flags for human review*, never auto-ban | Catch fabricated personas | ✅ backend heuristics, AI assist optional |
| "Verified-only" mode: only verified members can see or contact me | Women's safety gap | ✅ |
| Trust badges (not a numeric score) | Explainable trust | ✅ |
| Response-rate badge ("usually replies") | Ghosting | ✅ UI (Demo data); 🗺 nightly server computation |

### 3.2 AI-assisted matchmaking and recommendations

All AI runs **server-side** behind a provider-agnostic interface (Claude, OpenAI, Gemini or a
self-hosted OpenAI-compatible model such as vLLM/Ollama). **Ranking is deterministic code we
own; the LLM explains, drafts and parses — it never decides who is shown.** See §5.

| Feature | What it does | Status |
|---|---|---|
| **Reciprocal compatibility engine** | Scores A→B *and* B→A (does each fit the other's preferences?) and combines them with a harmonic mean, so a match only ranks high if it works for both people. Hard filters (age, marital status, distance, must-haves) then weighted soft factors: values, lifestyle, family, education/career, interests, language, location, relocation openness | ✅ (Kotlin on device for Demo mode, TypeScript on server) |
| **Values Compass** | 12 questions on what actually breaks marriages: finances, joint vs nuclear family, career after marriage, relocation, children, faith practice, diet, gender roles, mental-health openness, conflict style. Weighted by how much the user says each matters | ✅ |
| **"Why you match" insights** | Structured factor breakdown (shown always) + an AI-written, warm 2–3 sentence summary and 3 gentle "things to talk about" (only when the user has opted in to AI) | ✅ |
| **Daily Picks** | 8 curated introductions a day instead of an endless feed (swipe fatigue). Diversified so they aren't all clones | ✅ |
| **Learns from behaviour** | Implicit feedback (interest sent / declined / ignored, dwell time) nudges per-user factor weights within bounds, with an explainable "we noticed you prefer…" card the user can accept or reset | ✅ (on-device learner, server mirror 🗺) |
| **Natural-language search** | "Vegetarian doctor in Pune or Mumbai, 27–31, open to moving abroad" → AI turns it into structured filters shown as editable chips → deterministic search. Falls back to a rule-based parser when AI is off | ✅ |
| **AI profile coach + bio writer** | Drafts or polishes a bio in the user's language from their answers. Never invents facts, and flags contact details (which are not allowed in bios) | ✅ |
| **Icebreakers** | 3 conversation starters from shared interests/values (no private data) | ✅ |
| **Kind decline drafts** | One tap sends a respectful "not a match" message | ✅ |
| **Chat translation** | Hindi ↔ Tamil ↔ English etc. for cross-regional matches and families | ✅ (AI provider) |
| **Ask Vachan (assistant)** | Helps refine preferences ("you keep passing on profiles outside Bengaluru — tighten distance?"), explains features, gives safety guidance. Guardrails: can't reveal other users' private data and can't contact anyone | ✅ |
| **Family brief** | A watermarked, contact-free one-page summary of a match to share with parents | 🗺 |
| Horoscope matching (opt-in, off by default) | Some families want it; many users don't | 🗺 partner integration |

### 3.3 Safety

| Feature | Why | Status |
|---|---|---|
| **Safety Copilot in chat (on-device)** | Detects scam scripts *on the phone* (money/UPI/bank requests, "customs/parcel fee", investment/crypto/trading tips, gift cards, pressure to move to WhatsApp/Telegram early, urgency + emotional manipulation, requests for intimate photos, sharing OTPs) and shows a calm, specific warning to the recipient. Messages are never sent to a server for this | ✅ |
| Outgoing-message nudge | Warns before you share a phone number, UPI ID, address or OTP in chat | ✅ |
| One-tap report & block, with categories; blocking hides both people from each other everywhere | Advisory: encourage fraud reporting | ✅ |
| Server-side pattern detection (no LLM): same text sent to many users, burst messaging, new account + money keywords → account risk score → step-up verification or human review | Organised fraud rings | ✅ |
| **Meet safely** | Plan a first meeting in a public place, share time/place with a trusted contact, check-in timer, SOS shortcut (112) | ✅ |
| Women-first controls | Verified-only contact, photo blur until accepted, no contact from people without photos (enforced server-side) | ✅ |
| Grievance officer + 24 h acknowledge / 15-day resolution SLA tracking | IT Rules 2021 | ✅ (info + report status) |

### 3.4 Privacy

| Feature | Status |
|---|---|
| **Data minimisation:** caste/community, income and horoscope are optional, hidden by default, and never required for matching | ✅ |
| Exact DOB, full name, exact location and phone are never shown: profiles show age, first name + initial, city | ✅ |
| **Photo privacy levels:** visible to all members / verified members / only after I accept interest. Blurred previews otherwise | ✅ |
| **Viewer watermark:** every full photo is rendered with the viewer's member code, a deterrent against leaks and misuse | ✅ (client overlay; server-side burn-in 🗺) |
| Photo upload pipeline: client upload, EXIF/GPS stripping, AI-image forensics, derivatives | 🔌 storage rules + signed-URL serving ✅; client upload and processing 🗺 |
| **Screenshot and screen-recording blocking** (`FLAG_SECURE`) across the app | ✅ |
| Phone number is never exposed: share it only through a consent button, and revoke it at any time | ✅ |
| App lock (biometric / device credential) with auto-lock timeout, built for shared family phones | ✅ |
| Private notifications (no names or message text on the lock screen) | ✅ setting; 🗺 push notifications (FCM) |
| Encrypted on-device storage (Tink AEAD with Android Keystore key); no backups of app data; chat cache in memory only | ✅ |
| Incognito: browse without appearing in "who viewed me"; hide profile any time | ✅ |
| **Consent dashboard (DPDP):** per-purpose toggles with history; AI processing can be turned off entirely | ✅ |
| Download my data (JSON export) and delete my account (30-day grace, then hard delete; legally required IP log kept 1 year, separately and access-restricted) | ✅ |
| Profiles can't be scraped: no list queries on profiles, server-mediated discovery with rate limits, random non-sequential IDs (lesson from the "Dil Ka Rishta" API leak) | ✅ |
| AI privacy: the AI never receives names, phone numbers, photos, exact location or chat history unless the feature needs it (translation of a single message); PII is redacted before any call; providers configured for zero/limited retention | ✅ |
| End-to-end encrypted chat | 🗺 phase 2 (see [SECURITY_PRIVACY.md](SECURITY_PRIVACY.md) for the trade-off with safety scanning) |

### 3.5 Family Mode

Parents or siblings can be **invited** as co-managers with scoped permissions: *view matches*,
*shortlist*, *suggest a profile*, *edit profile sections*. They can never chat as the member,
see the member's chats, or accept an interest on the member's behalf. The member can remove a
co-manager at any time, and every co-manager action is shown in an activity log. This bridges
"77% self-managed" with "7 in 10 value parental guidance". **✅ member-side UI, invite codes,
permissions and activity log (server: invite / accept / update / remove); 🗺 the co-manager's own app
screens.**

### 3.6 Inclusion and reach

- UI in English (✅). Hindi, Tamil, Telugu, Marathi, Bengali, Kannada, Malayalam, Gujarati and
  Punjabi next (🗺 — screen strings still need extracting into resources; the locale config and
  Hindi app name are in place). AI bio writing and translation already work in all of them (✅ via
  provider).
- Remarriage-friendly: divorced/widowed/awaiting-divorce statuses with respectful copy;
  "has children" and "open to partner with children" fields (✅).
- NRI: country, residency/visa status, time zone shown in chat (✅ fields).
- Accessibility: TalkBack labels, 48 dp touch targets, dynamic type (✅); voice input for
  bios (🗺).

## 4. Monetisation (designed against the complaints)

| Free (forever) | Vachan Plus (convenience) |
|---|---|
| Full profile, all verification, 8 Daily Picks/day, send 5 interests/day, **unlimited chat after mutual interest**, safety features, AI insights within a fair-use limit | 20 Daily Picks, more interests/day, advanced filters, more AI search and translation, incognito, see who viewed you, profile boost (labelled "promoted" to others) |

Rules: prices shown in INR, inclusive of GST; monthly or 3-month plans; **auto-renew off by
default**; renewal reminder 3 days before; cancel in-app in two taps; pro-rata refund within 7
days; billing through Google Play (with UPI where the user-choice billing programme allows).
**No sales calls**: we never phone users to sell. *(Billing integration: 🗺.)*

## 5. AI architecture

```
 Android app ── callable (App Check + Auth) ──► Cloud Functions (asia-south1)
                                                 │
                         ┌───────────────────────┼─────────────────────────┐
                         ▼                       ▼                         ▼
                 Matching engine          AI gateway (ai/)           Safety engine
                 (deterministic,          • consent check             (rules, rate limits,
                  explainable)            • quota per user/day          risk scores)
                                          • PII redaction
                                          • prompt templates
                                          • output schema validation
                                          • provider adapter ──► Claude | OpenAI | Gemini | self-hosted
```

Principles:

1. **Consent-gated.** No AI call happens for a user who hasn't opted in to "AI matchmaking
   assistance". Every feature works without AI (with deterministic fallbacks).
2. **Minimised inputs.** Prompts get structured, de-identified features ("Person A: 29,
   software engineer, vegetarian, values: …") rather than raw profiles. A redactor strips
   phones, emails, URLs, UPI IDs, Aadhaar/PAN-like numbers and social handles from any free
   text before it leaves our servers.
3. **Untrusted text stays data.** User text is wrapped in delimiters and the system prompt
   says to treat it as data, not instructions (prompt-injection defence). Outputs are
   validated against a schema, length-capped and redacted again.
4. **The LLM doesn't rank people.** Scores come from auditable code, so no hidden bias is
   introduced by a model, and fairness checks can be run on the engine.
5. **Swappable providers.** `AI_PROVIDER=anthropic|openai|gemini|openai_compatible|mock`, with
   keys in Secret Manager. Defaults: Anthropic `claude-opus-5-5` at low effort for short
   tasks, with server-side refusal fallback enabled; each provider's model is configurable
   per deployment.
6. **Budgeted.** Per-user daily quotas per feature (free vs Plus), global circuit breaker,
   and a deterministic fallback on any AI error so the app never breaks.

## 6. Non-functional requirements

- **Data residency:** Firestore, Storage and Functions in `asia-south1` (Mumbai).
- **Performance:** cold start < 2 s on a mid-range phone; Daily Picks < 1.5 s p95.
- **Availability:** app degrades gracefully offline (cached own profile; queued actions).
- **Security:** OWASP MASVS L2 target. See [SECURITY_PRIVACY.md](SECURITY_PRIVACY.md).
- **Compliance:** DPDP Act + Rules (notice, consent, rights, breach reporting within 72 h to
  the Board), IT Rules 2021 (grievance officer), MeitY matrimonial advisory.

## 7. Roadmap

| Phase | Scope |
|---|---|
| **MVP (this repo)** | Everything marked ✅ above; Demo mode with on-device fake backend; Firebase backend with strict rules and AI gateway |
| **Phase 2 (launch)** | Vendor liveness + face match, DigiLocker integration, Play Billing, push notifications, image forensics provider, 8 more languages, in-app voice/video calls that hide numbers, E2EE chat, admin moderation console |
| **Phase 3 (growth)** | Family brief, group video meetups ("community evenings"), opt-in horoscope, verified education via DigiLocker, NRI time-zone matching, voice-first onboarding for Tier 2/3 |
| **Phase 4** | Learned ranking (two-tower model) trained only on consented, de-identified interaction data, with fairness audits; on-device small language model for icebreakers/bio when hardware allows |
