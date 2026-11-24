# Security and privacy design

Matrimony profiles combine some of the most sensitive data a person has: face, phone,
religion, caste, income, family details, health and marital history. A leak here can cause
real harm (harassment, blackmail, discrimination, fraud). This document records the threat
model and the controls built into the app and the backend.

## 1. Threat model

| # | Threat | Actor | Impact |
|---|---|---|---|
| T1 | Scraping or enumerating profiles via the API (cf. "Dil Ka Rishta" sequential-ID leak) | Data brokers, scammers | Mass PII exposure |
| T2 | Romance / matrimonial scam (customs fee, investment, emergency) | Organised fraud rings | Financial loss, emotional harm |
| T3 | Fake profile with stolen or AI-generated photos | Scammers, catfishers | Fraud, impersonation |
| T4 | Photo misuse (screenshots, morphing, deepfake blackmail) | Malicious viewers | Harassment, sextortion |
| T5 | Stalking: finding home/work from profile details | Rejected suitors, ex-partners | Physical safety |
| T6 | Account takeover (SIM swap, OTP phishing) | Fraudsters | Impersonation |
| T7 | Snooping on a shared family phone | Relatives | Loss of autonomy, outing of choices |
| T8 | Server-side breach (SQLi, misconfigured rules, leaked keys) | External attackers | Mass PII exposure |
| T9 | Insider misuse (staff browsing profiles) | Employees, vendors | Privacy violation |
| T10 | AI-specific: prompt injection via bios/messages, PII leaking to AI vendors, model output with harmful content | Users, vendors | Privacy violation, unsafe output |
| T11 | Discrimination through opaque ranking | Platform itself | Unfair outcomes |
| T12 | Abuse of Family Mode (coercion by relatives) | Family members | Loss of autonomy |

## 2. Controls

### 2.1 Mobile app (Android)

| Control | Threats | Where |
|---|---|---|
| `FLAG_SECURE` on all windows: blocks screenshots, screen recording, and previews in Recents | T4, T7 | `core/security/SecureWindow.kt` |
| Viewer watermark overlay on every full-size photo (viewer member code + date) | T4 | `ui/components/ProfilePhoto.kt` |
| Tapjacking protection: touches are filtered when the window is obscured | T6 | `MainActivity` |
| App lock: BiometricPrompt (strong biometric or device credential), auto-lock after a configurable time in background | T7 | `core/security/AppLockManager.kt` |
| Private notifications: no names or message text on the lock screen | T7 | Notification channel config |
| Encrypted local storage: Tink AEAD (AES-256-GCM) with keyset wrapped by an Android Keystore master key | T7, T8 | `core/security/SecureStore.kt` |
| No cloud/device backups of app data (`allowBackup=false`, data-extraction rules exclude everything) | T7, T8 | Manifest, `xml/data_extraction_rules.xml` |
| Firestore cache in memory only (no on-disk persistence of others' profiles or chats) | T7 | `FirebaseModule` |
| Network: cleartext disabled; only system CAs trusted in release | T8 | `xml/network_security_config.xml` |
| Firebase App Check with Play Integrity: only the genuine app can call the backend | T1, T8 | `VachanApp` |
| Root / emulator / debugger signals are reported as *risk signals* (they don't lock users out) | T2, T6 | `core/security/DeviceIntegrity.kt` |
| R8 minification and obfuscation on release; no secrets in the APK | T8 | `app/build.gradle.kts` |
| EXIF/GPS metadata stripped by re-encoding before photo upload | T5 | Photo pipeline (server also strips) |
| On-device Safety Copilot scans incoming chat for scam scripts, nothing leaves the phone | T2 | `core/safety/ScamDetector.kt` |
| Outgoing PII nudge (phone, UPI, address, OTP) | T2, T5 | `core/safety/PiiDetector.kt` |

### 2.2 Backend (Firebase, asia-south1)

| Control | Threats | Where |
|---|---|---|
| **Default-deny security rules**; every collection is explicitly allowed | T1, T8 | `firebase/firestore.rules` |
| **No `list` on profiles**: discovery only through Cloud Functions with per-user rate limits and preference/consent/block filtering. Firebase UIDs are random 28-character IDs, so nothing is sequential | T1 | rules + `functions/src/callables/discovery.ts` |
| Clients can never write trust fields (verification badges, risk scores, plan): public profiles are written only by the server after validation and moderation | T3, T8 | `publishProfile` callable |
| Interests, matches and contact sharing are server-mediated, with quotas | T1, T2 | `interests.ts` |
| Messages: only match members can read and write, the sender must be the caller, size limits apply, closed/blocked matches are read-only | T2, T8 | rules |
| Storage: originals are owner-only; derivatives are served through short-lived URLs after a permission check (photo privacy level, block status) | T4 | `firebase/storage.rules`, `photos.ts` |
| Server-side fraud heuristics: duplicate-message fan-out, burst rates, new account + money keywords → risk score → step-up verification or human review. Uses no LLM, so chat content never leaves our infrastructure for this | T2 | `functions/src/safety/` |
| Secrets (AI keys) in Secret Manager; never in code or the client | T8 | `defineSecret` |
| Admin access only through audited service accounts; staff tooling shows redacted data by default and logs every access | T9 | 🗺 admin console |
| Account deletion: 30-day grace (undo), then hard delete across Firestore and Storage; legally required IP log kept 1 year in a restricted collection | DPDP | `privacy.ts` |

### 2.3 AI gateway

| Control | Threats |
|---|---|
| Consent check before every AI call (purpose: "AI matchmaking assistance") | DPDP |
| **De-identification:** prompts use structured features and pseudonyms ("Person A"), never names, phone numbers, photos, exact DOB or location | T10 |
| **Redactor** removes phones (+91 / 10-digit), emails, URLs, UPI IDs, Aadhaar/PAN-like numbers, card numbers, social handles and pincodes from any free text, both before the call and on the output | T10 |
| Untrusted text is wrapped in `<user_text>` delimiters and the system prompt tells the model to treat it as data and ignore instructions inside it | T10 |
| Output validation: JSON schema (zod), length caps, a banned-content check; anything that fails falls back to deterministic output | T10 |
| Ranking stays in deterministic code (the LLM never chooses who is shown), and fairness metrics are computed on the engine | T11 |
| Provider configuration: zero/limited data-retention agreements where available; Indian or nearest region | T10 |
| Per-user daily quotas and a global circuit breaker | Abuse, cost |

### 2.4 Family Mode safeguards (T12)

Co-managers are *invited by the member*, permissions are scoped and visible, and every action
is logged and shown to the member. Co-managers can't read chats, can't accept or decline
interests, and can't see the member's private settings. The member can revoke access
instantly, and can do so discreetly (no notification to the co-manager until their next
visit).

## 3. End-to-end encryption: a deliberate trade-off

E2EE would stop us (and attackers who breach us) from reading chats. It also prevents
*server-side* scam detection on message content. Our approach:

- **MVP:** messages are encrypted in transit (TLS) and at rest (Google-managed keys) and
  access-controlled to the two members. Scam detection happens **on the device**. The server
  only sees metadata patterns (fan-out, rates) plus messages a user explicitly includes in a
  report.
- **Phase 2:** move to E2EE (X25519 + AES-GCM via Tink hybrid encryption, per-device keys,
  safety-number verification). The on-device Safety Copilot keeps working unchanged; reports
  attach the decrypted messages the reporter chooses. The `MessageCipher` seam in the data
  layer is where this plugs in.

## 4. DPDP Act / Rules compliance map

| Obligation | Implementation |
|---|---|
| Notice in clear, plain language, itemised by purpose | Consent screen at onboarding; Privacy Center → "What we collect and why" |
| Free, specific, informed, unambiguous consent; as easy to withdraw as to give | Per-purpose toggles (Essential / AI assistance / Product analytics / Marketing); history in `consentLog` |
| Purpose limitation and data minimisation | Optional sensitive fields; AI gets de-identified features only |
| Accuracy | User edits profile anytime; "information is correct" declaration |
| Storage limitation | Retention schedule: inactive accounts reminded at 12 months and deleted at 18; chats of closed matches deleted 90 days after closing (configurable) |
| Security safeguards | §2 above; encryption, access control, logging |
| Breach notification (Board + affected users) | Incident runbook 🗺; contact channel in app |
| Rights: access, correction, erasure, grievance, nominee | Download my data, edit profile, delete account, grievance officer contact, nominee field 🗺 |
| Children's data | Minimum-age gate (21 M / 18 F); no profiles of minors |
| Grievance redressal (IT Rules 2021) | Grievance officer contact; 24 h acknowledgement / 15-day resolution tracked on each report |

## 5. Open items before launch

1. Legal review of Aadhaar offline verification (OVSE registration) and DigiLocker partner terms.
2. Data Processing Agreements with each AI provider (retention, region, no training on our data).
3. Penetration test of rules and functions; App Check enforcement turned on in production.
4. Incident response runbook and DPO/grievance-officer appointment.
5. Fairness audit of the compatibility engine on synthetic and consented data.
