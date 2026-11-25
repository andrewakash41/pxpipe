# Aangan — a privacy-first, AI-assisted matrimony app for India

*Aangan* (आँगन) is the courtyard at the heart of an Indian home: open to the sky, closed to the
street. That's the promise — a private space where families begin, open only to the right people.
(The project's earlier working title was *Vachan*; code packages keep the `com.vachan` namespace.)

This folder is a self-contained project: a native Android app (Kotlin + Jetpack Compose) and a
Firebase backend in `asia-south1` (Mumbai), with a provider-agnostic AI layer. It lives in the
`pxpipe` repository but shares nothing with it.

> **Marriage-minded. Privacy-first.** Verified people, introductions that work both ways, and real
> safety — with your data kept yours.

| | |
|---|---|
| Why it exists, what users complain about, where the market is going | [docs/RESEARCH.md](docs/RESEARCH.md) |
| What we're building and why (every feature traced to a gap) | [docs/PRODUCT_SPEC.md](docs/PRODUCT_SPEC.md) |
| Threat model, controls, DPDP compliance map | [docs/SECURITY_PRIVACY.md](docs/SECURITY_PRIVACY.md) |

## Screenshots

Rendered with Robolectric from the real screens against the Demo backend (see [Testing](#testing)).
Demo members are fictional, so they show illustrated portraits rather than photos of real people.

| Welcome | Today | Profile | Safety Copilot |
|---|---|---|---|
| ![Welcome](docs/screenshots/01_welcome.png) | ![Today's introductions](docs/screenshots/05_today.png) | ![Profile and two-way match](docs/screenshots/06_profile.png) | ![Scam warning in chat](docs/screenshots/09_chat_safety_copilot.png) |

| Search | Values Compass | Privacy Center | Meet safely |
|---|---|---|---|
| ![Search](docs/screenshots/07_search.png) | ![Values Compass](docs/screenshots/04_values_compass.png) | ![Privacy Center](docs/screenshots/11_privacy_center.png) | ![Meet safely check-in](docs/screenshots/13_meet_safely.png) |

| Sign in | You | Interests | Dark mode |
|---|---|---|---|
| ![Sign in](docs/screenshots/02_sign_in.png) | ![You](docs/screenshots/10_you.png) | ![Interests](docs/screenshots/08_interests.png) | ![Today in dark mode](docs/screenshots/05b_today_dark.png) |

More: [consent](docs/screenshots/03_consent.png) · [Safety Center](docs/screenshots/12_safety_center.png) ·
[verification](docs/screenshots/14_verification.png) · [photos and bio](docs/screenshots/15_photos_and_bio.png).

## Design language

"Editorial warm", in the spirit of 2025–26 apps like Hinge, Airbnb and Arc:

- **Palette** — limewashed ivory canvas, warm ink for text and primary actions, **terracotta clay**
  only for intent (send interest, the brand), **sage** only for trust (verification), gold only for
  exceptional matches. Colour always means something. Full light and dark themes.
- **Type** — *Instrument Serif* for names and headlines, *Inter* for everything else. Both are SIL
  Open Font License fonts bundled in the APK (licences in `app/src/main/assets/licenses/`), so no
  font is fetched at runtime.
- **Layout** — photo-first portrait cards, a floating pill tab bar, hairline-bordered paper cards,
  iOS-style grouped settings, bottom sheets for focused tasks, segmented controls, skeleton loading.
- **The mark** — a home seen from above with an arched doorway into the courtyard, drawn in code
  (`ui/brand/Brand.kt`). Every user-facing brand string lives in that one file.

## What's in the box

**Matchmaking**
- **Reciprocal compatibility engine**: scores A→B *and* B→A and combines them with a harmonic
  mean, so a match ranks high only if it works for both people. Transparent factor breakdown
  ("why you might click"), plus frictions worth discussing early.
- **Values Compass**: 12 questions on what actually breaks marriages (living arrangement,
  careers, children, money, household roles, parents' role, mental health…), each weighted by
  how much it matters to the person.
- **Today**: a few diversified, two-way-compatible introductions a day instead of endless
  swiping. A bounded on-device learner adapts factor weights to behaviour and explains what it
  learned (with a reset).
- **Natural-language search**: "vegetarian doctor in Pune, 28–33, open to relocating" → editable
  filter chips → deterministic search.

**AI assistance** (consent-gated, off until you opt in, de-identified, never decides who is shown)
- Match insights, icebreakers, bio writer, smart search parsing, kind decline drafts, chat
  translation and the Aangan Guide assistant.
- One gateway, swappable providers: Anthropic (default `claude-opus-5-5`), OpenAI, Gemini, or a
  self-hosted OpenAI-compatible model (vLLM / Ollama). Offline templates take over whenever AI
  is off or unavailable.

**Safety**
- On-device **Safety Copilot** that recognises matrimonial scam scripts (customs-fee parcels,
  investment/crypto, emergencies, OTP requests, off-platform pressure — incl. Hinglish) and warns
  the reader. Outgoing nudges before sharing numbers, UPI IDs or OTPs.
- Free verification (selfie liveness flow, DigiLocker/Aadhaar-offline hook, work email),
  report/block (with confirmation), server-side fan-out detection.
- **Meet safely**: plan, SMS to a trusted contact, and a check-in timer that keeps running when
  you leave the screen or close the app, with a reminder notification that never names anyone.

**Privacy**
- Screenshot/recording blocking, faint viewer-watermarked photos, photo privacy levels (and no
  photo drawn at all where real blur isn't available), phone number shared only by consent
  (revocable), app lock that blocks touch, TalkBack and Back underneath it.
- Own photos: system photo picker (no storage permission), EXIF/GPS stripped by re-encoding,
  kept encrypted on device. Other members' photos are memory-only (no disk cache).
- Sign-out wipes everything of yours from a shared phone. Pause your profile without deleting.
- No client listing of profiles (anti-scraping), server-only trust fields, per-purpose consent
  (all optional purposes off by default) with history, in-app plain-language privacy notice,
  data export to a file you choose, 30-day deletion with hard purge.

**Built for India and the diaspora**: sign in with Indian or international numbers (UAE, US,
UK, Canada, Australia, Singapore, the Gulf and more); date of birth via a date picker with the
legal-age floor; resend-code timer; remarriage-friendly statuses.

**Fairness and honesty in the business model**: chat is free after mutual interest, verification
is free, no sales calls, no pay-to-see-contact.

## Project layout

```
matrimony-android/
├── app/                         Android app (single module, Kotlin + Compose, Hilt)
│   └── src/main/java/com/vachan/app/
│       ├── core/model/          Domain models (Profile, Values Compass, Interest, Consent…)
│       ├── core/matching/       CompatibilityEngine, Recommender, PreferenceLearner, QueryParser
│       ├── core/photos/         PhotoProcessor (metadata stripping), EncryptedPhotoStore
│       ├── core/safety/         ScamDetector (Safety Copilot), PiiDetector, check-in reminders
│       ├── core/security/       SecureStore (Tink), AppLockManager, SecureWindow, DeviceIntegrity
│       ├── data/                Repository interfaces + Demo and Firebase backends
│       ├── di/                  Hilt wiring (Demo vs Firebase chosen at build time)
│       └── ui/                  Theme, brand, components and Compose screens
├── firebase/
│   ├── firestore.rules          Default-deny access model
│   ├── storage.rules            Owner-only image uploads
│   ├── firestore.indexes.json   Indexes + TTL policies
│   ├── functions/               Cloud Functions (TypeScript): AI gateway, matching, safety, privacy
│   └── emulator-tests/          Security-rules and end-to-end tests against the emulators
└── docs/                        Research, product spec, security & privacy design
```

## Quick start (Demo mode — no backend needed)

Requirements: JDK 17+ (21 recommended), Android SDK with platform 37.

```bash
cd matrimony-android
./gradlew :app:installDebug        # or open the folder in Android Studio and Run
```

Without `app/google-services.json` the app builds in **Demo mode**: everything runs on the phone
against fictional members (illustrated portraits, no real photos). Sign in with any mobile
number; the code is **123456**. Two conversations are seeded after onboarding — one genuine and
one scripted scam persona, so you can see the Safety Copilot react to a "customs fee" request.

## Connecting the real backend (Firebase, Mumbai)

1. Create a Firebase project. Create Firestore and Storage in **`asia-south1`**.
2. Enable **Phone** sign-in, and **App Check** with Play Integrity for the Android app
   (`com.vachan.app`; debug builds use `com.vachan.app.debug` and the App Check debug provider).
3. Download `google-services.json` into `app/` (it's git-ignored). The build switches to the
   Firebase backend automatically.
4. Configure and deploy the backend:

   ```bash
   cd firebase
   cp .firebaserc.example .firebaserc        # set your project id
   (cd functions && npm ci)
   firebase functions:secrets:set AI_API_KEY # your provider key
   firebase deploy --only firestore,storage,functions
   ```

   The service account running functions needs `iam.serviceAccounts.signBlob` to issue signed
   photo URLs. TTL policies are declared in `firestore.indexes.json`.

### Choosing the AI provider

Set these in `firebase/functions/.env.<project-id>` (non-secret) — no code changes needed:

| `AI_PROVIDER` | `AI_MODEL` | `AI_BASE_URL` | Notes |
|---|---|---|---|
| `anthropic` (default) | optional (default `claude-opus-5-5`) | — | Official SDK, structured outputs, low effort, server-side refusal fallback |
| `openai` | required | optional | Chat Completions with strict JSON schema |
| `gemini` | required | optional | Generative Language API, key sent in a header |
| `openai_compatible` | required | required | Self-hosted vLLM / Ollama / LM Studio — keeps data in your VPC |
| `mock` | — | — | Deterministic, for the emulator and tests |

Whatever the provider: prompts never contain names, phone numbers, photos, exact birth dates or
locations; user text is redacted and fenced; outputs are schema-validated and redacted again;
every call is consent-gated and quota-limited. Sign a data-processing agreement with no training
on your data before going live.

## Testing

```bash
# Android: core logic (engine, safety, parser, learner), privacy behaviour (sign-out wipe,
# encrypted photos, EXIF stripping, visibility restore) and 16 screen renders
./gradlew :app:testDebugUnitTest
./gradlew :app:testDebugUnitTest --tests '*ScreenshotTest*' -Pscreenshots   # writes app/build/screenshots/*.png

# Cloud Functions unit tests (engine parity, redaction, scam heuristics, AI gateway, providers)
cd firebase/functions && npm ci && npm test

# Security rules + end-to-end flows against the Firebase emulators (needs Java)
cd firebase/emulator-tests && npm ci && npm test
```

The emulator scripts preload `scripts/loopback-direct.cjs` so firebase-tools can reach its own
local emulators when `HTTPS_PROXY` is set (it otherwise ignores `NO_PROXY`). It has no effect
without a proxy.

## Before launch

Honest list of what's designed and stubbed but not finished:

- **Vendor integrations**: selfie liveness + face match (with injection-attack detection),
  DigiLocker / Aadhaar offline verification (needs UIDAI/DigiLocker registration and legal
  review), transactional email for work-email codes, AI image forensics on photo uploads.
- **Photo derivatives**: server-side resizing and burned-in watermarks. On a new device your own
  photos show as a portrait until re-downloaded (others' photos load normally).
- **Age refresh**: profiles store age, not birth date, so ages need a yearly bump (store a private
  birth month server-side and refresh nightly).
- **Push notifications** (FCM) honouring the private-notifications setting.
- **Play Billing** for Aangan Plus (auto-renew off by default).
- **Localisation**: extract strings; ship Hindi first, then eight more Indian languages (AI bio
  help and translation already work in them).
- **E2EE chat** (phase 2; trade-offs in the security doc), admin moderation console, nightly
  response-rate computation, the co-manager's side of Family Mode.
- Penetration test, DPO/grievance officer appointment, incident-response runbook, fairness audit.

## Tech stack

Kotlin 2.4 · AGP 9.5 · Jetpack Compose (Material 3) · Hilt · Navigation (type-safe) · DataStore +
Tink · BiometricPrompt · Coil 3 (memory-only cache) · Instrument Serif + Inter · Firebase (Auth, Firestore, Storage, Functions, App Check) ·
TypeScript 7 · zod · `@anthropic-ai/sdk` · Vitest · Robolectric. Dependencies are pinned to
releases at least two weeks old, and the Gradle distribution is checksum-verified.
