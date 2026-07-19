# External review request: "Emersion Chess" (Android, pre-launch)

You are a senior Android engineer, application-security reviewer, and
open-source-licensing specialist performing a pre-launch review. Assume the
authors are competent; skip praise and generic advice. Every finding must
carry: severity (BLOCKER / HIGH / MED / LOW), the concrete risk, and a
specific fix. Cite exact files/paths when you can read them.

**Honesty rule: the repository below is public. If you
cannot fetch it, SAY SO and review strictly from the facts in this document —
do not invent repository contents.** Key facts, rules, and design decisions
are embedded below precisely so a paper review is still substantive.

## Project identity
- App: **Emersion Chess** (Play title: "Emersion Chess: Stockfish")
- applicationId: `io.github.emersionplay.chess` (code namespace `com.chessapp` — deliberate AGP split)
- Repo: https://github.com/emersionplay/emersion-chess (public at launch; AUDIT.md holds the full engineering history — read it first if accessible)
- License: **GPLv3** app; bundles **Stockfish 18** (GPLv3) and **Colin Burnett's piece set** (GPLv2+, sourced from the lichess repo); THIRD_PARTY_LICENSES.md + full LICENSE text present. Distribution: Google Play (AAB) + source public.

## Stack & architecture
- Kotlin + Jetpack Compose, single Activity; minSdk 24 / targetSdk 35; Room (saved games), DataStore (settings). No Google Play Services, no Firebase SDK.
- **Own rules engine** (move generation, legality, SAN, clocks, all draw rules; perft-verified) — Stockfish is opponent-only, never arbiter.
- Opponents are pluggable: built-in minimax+alpha-beta (4 tiers) and **bundled Stockfish 18 via UCI** — arm64 `jniLibs` (install-time exec dir; the only W^X-legal path on API 29+), >1MB size-guard with silent fallback to the built-in engine elsewhere; skill map 3/8/14/20; default-ON where present.
- Pieces render from Cburnett geometry transpiled to Compose paths (constant-pool strings parsed at load; verbatim colors: black = #000 fill + #FFF inner detail).
- Size (measured in CI, deliberate decision on file): release APK ≈ 74.6 MB, AAB ≈ 76.3 MB; engine = ~96% of payload (~109 MB on-disk after extraction). Positioning: professional/engine-first; falsifier: retrofit Play on-demand delivery if size-driven abandonment appears.

## Online mode (review this hardest)
- **Friend matches by 6-char room code only — no matchmaking by design.** Firestore via REST (X-HTTP-Method-Override PATCH), anonymous auth via Identity Toolkit (uid persisted; refresh keeps uid). Both clients validate every move with the identical local rules engine; optimistic concurrency via `updateTime` preconditions; polling ≈ 2.5 s.
- Config ships baked into builds: **projectId `emersion-chess-online`**, Web API key `AIzaSyDQtMoGOaw096ORR_0RQO9G4jD6HDKLC5w` — these are public-by-design identifiers (they ship in every APK); security is intended to live entirely in the rules below. **Out of bounds for this review and absent from this document: signing keystore, its passwords, and any access tokens.**
- Firestore rules, verbatim:

```
rules_version = '2';
// Room-code friendly games (REST client, free tier). Writers are restricted to
// the two participants; structure is shape-checked. Full chess-legality is
// enforced client-side by both players' identical validators (see
// RestOnlineRepository.kt) — adequate for invite-by-code casual play. A future
// ranked mode should switch to the Cloud-Functions authority in functions/.
service cloud.firestore {
  match /databases/{database}/documents {
    match /games/{code} {
      allow get: if request.auth != null;
      allow list: if false;

      allow create: if request.auth != null
        && request.resource.data.whiteUid == request.auth.uid
        && request.resource.data.blackUid == ""
        && request.resource.data.moves.size() == 0
        && request.resource.data.status == "ONGOING";

      allow update: if request.auth != null && (
        // joining: the single transition blackUid "" -> me, nothing else moves
        (resource.data.blackUid == ""
          && request.resource.data.blackUid == request.auth.uid
          && request.resource.data.whiteUid == resource.data.whiteUid
          && request.resource.data.moves == resource.data.moves
          && request.resource.data.status == resource.data.status)
        ||
        // playing: participants only; seats immutable; history append-only
        (request.auth.uid in [resource.data.whiteUid, resource.data.blackUid]
          && request.resource.data.whiteUid == resource.data.whiteUid
          && request.resource.data.blackUid == resource.data.blackUid
          && request.resource.data.moves.size() >= resource.data.moves.size())
      );

      allow delete: if false;
    }
  }
}
```

## Test & release engineering
- Phone-only development: GitHub Actions is the sole build path. Per push: 45 JUnit + lint → signed release APK/AAB with a **self-verifying log chain** (keystore + key-password verifiers, apksigner cert print CN=Gambit Chess, size report, online-defaults print) → separate job: 47 instrumented E2E on an x86 emulator (built-in engine as opponent) + release crash gate (R8 build cold-boot + monkey).
- Known test-isolation compromise to critique: persisted DataStore settings cross test boundaries; current mitigation is a suite rule (coordinate-tapping tests self-pin seat+difficulty). Propose the better isolation if you see one.

## Specific questions (answer each)
1. **Rules security:** join-race abuse, seat spoofing, winner/status forgery, resign/draw integrity, replay/append-only gaps, code-space guessability (31^6 with list disabled), quota-burn griefing. What breaks first, and the minimal rule change?
2. **Licensing:** any GPLv3 compliance gap for Play distribution (source offer, license texts, artwork attribution, Stockfish notice)? Anything Play policy adds on top?
3. **Native engine approach:** risks of the jniLibs-exec + size-guard fallback across OEMs/ABIs; anything better than bundling given W^X?
4. **REST-instead-of-SDK:** failure modes we've likely missed (token refresh edge cases, clock skew, precondition races, offline behavior).
5. **Play readiness:** Data-safety form answers implied by "anonymous auth, no analytics, no ads, no PII"; any listing/policy landmines for a GPL app bundling Stockfish.
6. **Compose/perf:** the piece renderer parses ~100 KB of path strings at class load and draws multi-part paths per piece per frame — measurable concerns and idiomatic fixes?
7. **UX heuristics** on the attached screenshots [OWNER: attach current screenshots]: anything a chess-native user would find off?

## Output format
Severity-ranked findings table, then a "Top 5 before launch" list, then per-question answers. Mark every assumption you could not verify.
