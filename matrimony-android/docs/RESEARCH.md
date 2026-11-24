# Market research: Indian online matrimony (November 2026)

This document records what we found about the Indian matrimony market, what users of the
incumbent apps complain about, and where the market is heading. Every product decision in
[PRODUCT_SPEC.md](PRODUCT_SPEC.md) traces back to a finding here.

> **Source quality note.** Much of the public data on this market is published by companies
> with a stake in it (platform "reports", vendor blogs, app-store copy). User reviews on
> Trustpilot / Play Store are self-selected and skew negative. We flag the weaker numbers
> below, and treat them as *direction*, not precise figures.

---

## 1. Market size and trajectory

| Metric | Figure | Source / confidence |
|---|---|---|
| Online matrimony revenue, India | ~INR 1,200–1,400 crore (2025), expected to roughly double by 2030 (≈10–12% CAGR) | Redseer, Dec 2025 (via secondary write-up) — medium |
| Combined online dating + matrimony market | ~9–10% CAGR | Redseer (secondary) — medium |
| Offline matchmaking (bureaus, pandits, community) | INR 4,500–5,000 crore, growing 4–5% | Redseer (secondary) — medium |
| Digital share of the addressable matchmaking opportunity | ~30% in 2025 → 40%+ by 2030 | Redseer (secondary) — medium |
| Leaders | Shaadi.com and BharatMatrimony lead, then Jeevansathi; heavy user overlap (people keep profiles on several apps) | Redseer (secondary) — medium |

**Takeaway:** the online segment is still the *smaller* part of the market. The bigger
opportunity is moving the 70% of matchmaking that still happens offline (relatives, bureaus,
community networks) online, which means winning the trust of **families**, not just singles.

## 2. How Indian partner search is changing

From Jeevansathi's *The Big Shift* (Feb 2026; platform data 2016–2025 + survey of 30,000+
users — company-published, not a national sample):

- **Later marriages:** the median age at which users start searching rose from 27 to 29.
- **Caste is loosening as a hard filter:** strict caste preference fell from 91% (2016) to
  54% (2025), and to 49% in metros. Caveat: a softer preference doesn't automatically mean more
  inter-caste marriages.
- **Remarriage is growing:** remarriage seekers rose from 11% to 16% of users (+43% relative).
- **Self-managed profiles:** 77% of profiles are now managed by the person themselves (67% in
  2016), but **~7 in 10 still say parental guidance is important**.

Other signals:

- Jeevansathi's 2025 survey (21,000+ respondents): 47% of men vs 29% of women prioritise
  romance; 40% of singles are open to moving abroad for the right partner.
- Aisle's industry report (3,400 urban singles): 97% of women prioritise commitment vs 80% of
  men; 55% have seen couples marry after meeting online. (Commissioned by a dating app.)
- **Gen Z** favours shorter courtships (6–12 months) and *lifestyle* compatibility; millennials
  favour longer courtships and shared beliefs. ~80% of Gen Z couples reportedly prioritise a
  conversation about mental health before marriage.
- **Swipe fatigue:** Redseer reports 75%+ of Gen Z users on dating apps report "swiping
  fatigue", and one large swipe app saw a 7% YoY drop in monthly paying users (Q3 2025). Users
  want *intentional*, low-volume, high-quality introductions.
- Only ~9–12% of couples say they met through matrimonial sites (WedMeGood survey), so there
  is plenty of room to grow, and the incumbents are not delighting people.

## 3. What competitors offer today

| Player | Notable features | Gaps users point to |
|---|---|---|
| **BharatMatrimony / Matrimony.com** (incl. community sites, Jodii) | "MIMA" AI daily match recommendations; interest-based matching; ID-verified badge for paid "Prime" members; paid background verification; AI chatbot (customer service, FY26); AI astrology product; **Jodii** — vernacular app (10 languages) for non-graduates and blue-collar workers, incl. Gulf NRIs | Verification tied to payment; spam profiles on free tier; aggressive upsell calls; 2020 alleged data leak (SQL injection) incl. phone numbers and caste |
| **Shaadi.com** | Blue-tick selfie/ID verification; photo visibility "contacted & accepted only"; hide phone number; video/voice calls that don't reveal the number ("Shaadi Meet", premium to initiate); "Shaadi Live" group video events; profile hide up to 30 days | Payment requested before verification; fake-profile complaints; inactive/duplicate/parent-managed profiles |
| **Jeevansathi** (Info Edge) | "Shield" verified matches; privacy settings; reporting | Fake profiles reported; sales calls; repeated KYC prompts; auto-renew/refund disputes |
| **Betterhalf** | 16-trait personality test + ML compatibility; AI fraud detection; photo/ID verification; targets urban professionals | Reviewers say results are no better than other sites |
| **Sangam, community apps** | Community-based matching, Tier 2/3 reach | Little product innovation |
| **Global dating apps** (signal of where UX is going) | Hinge: AI conversation starters, AI *prompt feedback*; Tinder: "Chemistry" Q&A-based AI matching, AI photo selection; Bumble: "Deception Detector" (fake-account blocking), "AI-assisted photo" badge | Casual-dating positioning; little for families |

## 4. What users complain about (the gaps)

Synthesised from Trustpilot, Play Store, ConsumerComplaints and PissedConsumer reviews,
news coverage and comparison write-ups:

1. **Fake, inactive and scam profiles.** The #1 complaint on every major platform. Users
   report being sent profiles that look fake and being told "the other side is not responding".
2. **Pay-to-talk paywalls and bait-and-switch.** Paying to see a profile that then won't open;
   buying a pack and still not seeing a single phone number; "unlimited" messages capped at
   50/day after payment; hidden auto-renewal; refunds refused.
3. **Verification behind a paywall.** "They take money first and then raise verification. Why
   aren't profiles verified *before* payment is accepted?"
4. **Relentless sales calls.** Calls from relationship managers from different numbers even
   after repeatedly saying no; marking them as spam doesn't help.
5. **Ghosting / no response.** Interests go unanswered for weeks; there's no closure.
6. **Privacy.** Phone numbers, photos, caste and income are highly sensitive. Breaches have
   happened (BharatMatrimony 2020 alleged leak; WedMeGood 2021, 1.3M accounts; a "Dil Ka Rishta"
   API flaw that let profiles be enumerated sequentially, exposing photos, caste, income).
7. **Weak filters and poor relevance.** Filters that don't work; recommendations that ignore
   stated preferences; pop-ups pushing upgrades.
8. **No women-specific safety tooling.** We found no women-focused safety mode on the leading
   apps beyond photo/number hiding.

## 5. Safety and fraud landscape

- **Matrimonial fraud is organised and growing.** Police in Mandsaur (MP) busted a racket that
  allegedly cheated people of Rs 11+ crore in 100+ cases via matrimonial sites. Delhi Police
  arrested a foreign national + partner targeting women with "customs charges", gifts,
  "emergency expenses" and travel-fee requests. One legal commentary cites a **206% YoY rise**
  in matrimonial fraud on the National Cybercrime Reporting Portal with ₹500 crore+ losses,
  mostly Tier 2/3 women (unverified figure; no primary source given).
- **Common scam scripts** (to detect): fake NRI/army/doctor persona → fast emotional intimacy →
  move to WhatsApp/Telegram → "parcel stuck at customs, pay the fee" / medical emergency /
  visa fee / crypto or stock "investment opportunity" / gift cards / sextortion after a video
  call.
- **Deepfakes and AI-generated faces** defeat reverse image search, and simple liveness checks
  can be fooled by face-swap on a virtual webcam. Recommended defence is *layered and
  risk-based*: image forensics on every upload, step-up liveness (with injection-attack
  detection) for flagged accounts, and asking for live, randomised gestures.

## 6. Regulation that shapes the product

| Rule | What it means for us |
|---|---|
| **DPDP Act 2023 + DPDP Rules 2025** (notified Nov 2025) | Consent must be free, specific, informed and withdrawable per purpose; clear notices; breach reporting; data-principal rights (access, correction, erasure, grievance). Consent-manager registration opens ~Nov 2026; substantive obligations apply from **~May 2027**. We build to the full standard now. |
| **MeitY advisory for online matrimonial websites (June 2016)** | Matrimonial sites are intermediaries under the IT Act. They should: verify identity/address using legally verifiable documents; confirm the user's *intent to marry* and that information is correct; state that the site is **for matrimony only, not dating**; keep the profile creator's IP for one year after deletion; name a grievance officer; encourage fraud reporting. |
| **IT (Intermediary) Rules 2021** | Publish grievance officer contact details; acknowledge complaints within 24 h and resolve within 15 days. |
| **Aadhaar** (Good Governance amendment Jan 2025; Offline Verification Amendment Regulations Dec 2025) | Private entities may use online Aadhaar authentication only after ministry/UIDAI approval. Offline verification (QR / verifiable credential, optional offline face match) has its own registration (OVSE) for app-based flows. **Never store the Aadhaar number**; store only the verification result. Legal review is needed before launch. |
| **Prohibition of Child Marriage Act** | Minimum marriageable age is 21 (men) and 18 (women): enforce at sign-up. |

## 7. Future scenario (2026–2030)

1. **AI becomes table stakes** for recommendations, but users are sceptical of "black box"
   matching. *Explainable* compatibility will differentiate.
2. **Trust is the product.** Free, universal verification and visible anti-scam work will
   matter more than catalogue size, especially as deepfakes get cheaper.
3. **Vernacular + Tier 2/3 growth** (400M smartphone users comfortable only in Indian
   languages): voice-first, regional-language UX and cross-language chat translation.
4. **Family-in-the-loop, not family-in-control:** the self-managed share is rising, but
   families still matter. Products that let parents help *with consent and scoped
   permissions* bridge the offline/online gap.
5. **Underserved segments:** remarriage (16% and rising), late marriers (29+), NRIs and Gulf
   workers, professionals with little time, people with disabilities.
6. **Regulatory tightening:** DPDP enforcement from 2027 will punish data-hungry apps, and
   privacy-by-design becomes a competitive advantage.
7. **Fatigue with volume:** fewer, better, curated daily introductions beat infinite swiping.

## 8. Opportunity summary → product principles

| Gap | Our answer |
|---|---|
| Fake profiles | Free verification for everyone (phone OTP → selfie liveness → government ID via DigiLocker/Aadhaar-offline), AI image-forensics, and a "verified-only" mode |
| Pay-to-talk paywall, bait-and-switch | **Chat is free after mutual interest.** Premium is for convenience only. Plain pricing, no hidden auto-renew, in-app cancel |
| Sales calls | **No-call promise**: we never phone users for sales |
| Ghosting | Interests expire after 7 days with a kind, AI-drafted decline; response-rate badge |
| Privacy breaches | Data minimisation, sensitive fields optional, photo controls, viewer-watermarked photos, screenshot blocking, number never exposed, encrypted on-device storage, strict server rules, no sequential IDs |
| Scams | On-device chat Safety Copilot (money/UPI/crypto/customs/off-platform pressure detection) + "Meet safely" check-ins + one-tap report |
| Poor relevance | Two-way (reciprocal) compatibility engine with transparent "why you match" explanations, values-based questionnaire, natural-language search |
| Families excluded | Family Mode: invited co-managers with scoped permissions; the member always has the final say |
| Language barriers | Regional-language UI, AI bio writing in any language, chat translation |

---

### Sources

- [Redseer — India dating & matrimony market](https://redseer.com/articles/whos-winning-in-indias-dating-matrimony-market-and-where-are-the-opportunities-for-platforms-and-investors/)
- [Strategy Boffins — India's matrimonial market 2025](https://www.strategyboffins.com/start_up_strategy/indian-matrimonial-market/)
- [Sharekhan Education — The Indian matrimony market landscape has changed](https://sharekhaneducation.com/blog/the-indian-matrimony-market-landscape-has-changed/)
- [BW Businessworld — India's online dating market to hit Rs 2,500 Cr by 2030](https://www.businessworld.in/article/india-s-online-dating-market-to-hit-rs-2-500-cr-by-2030-report-584327)
- [Market Research Future — India online dating market](https://www.marketresearchfuture.com/reports/india-online-dating-market-60938)
- [ANI — Jeevansathi report: marriage timelines shift, remarriage rises](https://aninews.in/news/business/marriage-timelines-shift-remarriage-rises-jeevansathi-report-reveals-how-india-is-redefining-partner-search-and-marriage20260213175710/)
- [The Tribune — Median marriage age rises to 29, remarriage seekers up 43%](https://www.tribuneindia.com/news/top-headlines/marriage-median-age-rises-to-29-remarriage-seekers-up-43-per-cent-in-last-decade-report/)
- [ThePrint — Jeevansathi report on caste](https://theprint.in/feature/indians-marry-within-caste-jeevansathi-report/2855931)
- [Deccan Chronicle — Jeevansathi Modern Matchmaking Report 2025](https://www.deccanchronicle.com/lifestyle/relationship/men-want-love-women-seek-compatibility-jeevansathis-modern-matchmaking-report-1861069)
- [Aisle — Future of serious dating in India (PDF)](https://www.aisle.co/industry_report.pdf)
- [Global Dating Insights — 97% of Indian women prioritise commitment](https://www.globaldatinginsights.com/featured/study-97-of-indian-women-prioritize-commitment-over-casual-dating/)
- [WedMeGood — 30% of Indian couples find love online](https://www.wedmegood.com/blog/30-of-indian-couples-find-love-online-wedmegood-survey-reveals/)
- [SheThePeople — Gen Z couples and mental-health conversations](https://www.shethepeople.tv/lifestyle/genz-couples-marriage-conversation-mental-health-7781463)
- [Deccan Chronicle — How Gen Z is redefining matrimony](https://www.deccanchronicle.com/lifestyle/coffee-careers-compatibility-how-gen-z-is-redefining-matrimony-1876004)
- [Trustpilot — Shaadi.com reviews](https://www.trustpilot.com/review/shaadi.com)
- [Trustpilot — Jeevansathi reviews](https://uk.trustpilot.com/review/www.jeevansathi.com)
- [Trustpilot — BharatMatrimony reviews](https://www.trustpilot.com/review/bharatmatrimony.com)
- [PissedConsumer — Bharat Matrimony reviews](https://bharat-matrimony.pissedconsumer.com/review.html)
- [ConsumerComplaints.in — Telugu Matrimony](https://www.consumercomplaints.in/bycompany/telugu-matrimony-a254303.html)
- [Garam Masala Dating — BharatMatrimony vs Shaadi vs Jeevansathi](https://garammasaladating.com/journal/bharatmatrimony-vs-shaadi-vs-jeevansathi)
- [Google Play — BharatMatrimony](https://play.google.com/store/apps/details?id=com.bharatmatrimony&hl=en_IN)
- [Multibagg — Matrimony.com Q3 FY26](https://www.multibagg.ai/market-pulse/articles/matrimony-q3-fy26-earnings-92122)
- [Multibagg — Matrimony.com Q2 FY26](https://www.multibagg.ai/deep-dive/blogs/89044-matrimony-q2-fy26-analysis_MATRIMONY_Q2_FY26_MBEQU579)
- [Matrimony.com newsroom — Jodii launch](https://www.matrimony.com/newsroom/jodii-a-vernacular-matrimony-app-in-tamil-launched-to-help-millions-of-common-people-find-their-life)
- [DT Next — Jodii targets non-graduate workers in the Gulf](https://www.dtnext.in/news/business/jodii-matchmaking-app-targets-non-grad-indian-workers-in-gulf)
- [Shaadi.com support — Blue tick](https://support.shaadi.com/support/solutions/articles/48000953832-i-see-a-blue-tick-mark-next-to-most-of-the-profiles-what-is-this-icon-for-)
- [Shaadi.com support — Shaadi Meet](https://support.shaadi.com/support/solutions/articles/48001159195-what-is-shaadi-meet-how-does-it-work-)
- [Shaadi.com support — Hiding your profile](https://support.shaadi.com/support/solutions/articles/48000755467-i-want-to-hide-or-delete-my-profile)
- [The Tribune — Shaadi.com anti-scam efforts](https://www.tribuneindia.com/news/impact-feature/how-shaadi-com-is-making-online-matrimony-safer-with-proactive-anti-scam-efforts-576166)
- [YourStory — Betterhalf AI matrimony](https://yourstory.com/2022/06/betterhalf-corporate-partners-ai-matrimony-app-match-wedding)
- [DatingScout — Betterhalf review](https://www.datingscout.com/betterhalf/review)
- [Axios — Tinder wants to fix dating apps with AI](https://www.axios.com/2026/03/12/tinder-ai-features-hinge-bumble)
- [SwipeStats — Hinge vs Bumble vs Tinder 2026](https://www.swipestats.io/blog/hinge-vs-bumble-vs-tinder)
- [The Hawk — MP police bust Rs 11-cr matrimonial fraud racket](https://www.thehawk.in/news/india/mp-police-bust-rs-11-cr-matrimonial-cyber-fraud-racket-six-arrested)
- [Daily Pioneer — Delhi Police bust matrimonial cyber fraud racket](https://dailypioneer.com/news/matrimonial-fraud-racket-busted-in-delhi-foreigner-partner-arrested)
- [Record of Law — Matrimonial cyber fraud in India](https://recordoflaw.in/the-matrimonial-cyber-fraud-in-india-legal-framework-and-emerging-trends-2/)
- [Cyble — Alleged BharatMatrimony data for sale](https://cyble.com/blog/alleged-user-data-of-bharat-matrimony-for-sale-on-darkweb/)
- [Have I Been Pwned — WedMeGood breach](https://haveibeenpwned.com/breach/WedMeGood)
- [TechJuice — Dil Ka Rishta API data breach](https://www.techjuice.pk/dil-ka-rishta-app-hit-by-serious-api-data-breach/)
- [Sumsub — Detecting romance and dating scams](https://sumsub.com/blog/romance-scams/)
- [Vaarhaft — Detecting AI-generated dating profile pictures](https://www.vaarhaft.com/resources/blog/detect-ai-generated-profile-pictures-online-dating)
- [Facia — How deepfakes are transforming dating apps](https://facia.ai/blog/how-deepfakes-are-transforming-dating-apps-and-why-we-must-act-now/)
- [Onluxy — How scammers bypass dating-app verification](https://www.onluxy.com/Online-Dating-Knowledge-Base/Online-Dating-Safety-Center/how-scammers-bypass-dating-app-verification-2025-2/)
- [PIB — DPDP Rules 2025 press release](https://www.pib.gov.in/PressReleasePage.aspx?PRID=2190655&reg=48&lang=2)
- [Wikipedia — DPDP Rules 2025](https://en.wikipedia.org/wiki/Digital_Personal_Data_Protection_Rules,_2025)
- [Lexology — DPDP Rules 2025: consent, security and governance](https://www.lexology.com/library/detail.aspx?g=7e3af947-10aa-4712-bc1e-54179a613409)
- [Scrut — DPDP Rules implementation checklist](https://www.scrut.io/post/dpdp-rules)
- [The Quint — "No dating, only marriage": govt advisory to matrimonial sites](https://www.thequint.com/news/india/no-dating-only-marriage-govt-tells-matrimonial-websites)
- [DNA — Intent-to-marry agreement for matrimonial sites](https://www.dnaindia.com/india/report-intent-to-marry-agreement-must-to-seek-matches-online-government-2224623)
- [Governance Now — ID and address proofs for matrimonial websites](https://www.governancenow.com/news/regular-story/identity-address-proofs-mandatory-using-matrimonial-websites)
- [PRS India — IT Rules 2021 key features](https://prsindia.org/billtrack/the-information-technology-intermediary-guidelines-and-digital-media-ethics-code-rules-2021)
- [Legal500 — Aadhaar Offline Verification Amendment Regulations 2025](https://www.legal500.com/intelligence/india/compliance/aadhaar-authentication-and-offline-verification-amendment-regulations-2025-strengthening-india's-digital-identity-framework)
- [Vinod Kothari — Aadhaar offline face matching](https://vinodkothari.com/2025/12/a-new-way-to-verify-aadhaar-offline-introduction-of-face-matching/)
- [Ikigai Law — Aadhaar authentication amendment rules](https://www.ikigailaw.com/article/658/aadhaar-authentication-and-offline-verification-amendment-rules)
- [Campaign India — Indian singles prioritise compatibility over tradition](https://campaignindia.in/article/indian-singles-prioritise-compatibility-over-tradition-study/500783)
