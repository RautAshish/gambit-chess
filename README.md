<p align="center">
  <img src="store/feature_graphic.png" alt="Emersion Chess" width="640"/>
</p>

<h1 align="center">Emersion Chess</h1>
<p align="center"><i>play. learn. repeat.</i></p>

**Emersion Chess** is a premium, offline-first chess app for Android: the
full-strength **Stockfish 18** engine on your phone, **137 machine-verified
tactics puzzles**, and **room-code online play** with friends — no ads, no
tracking, no account, ever.

It was also built in an unusual way: **entirely from a phone.** GitHub Actions
is the project's only computer — every feature, fix, and release in this
repository was written, tested, and shipped without a development machine.
The engineering history, including every bug and incident, is preserved
honestly in [AUDIT.md](AUDIT.md).

## Features

- **Stockfish 18 on-device** (arm64, Android 10+) — analysis-grade opposition,
  fully offline; other devices automatically use the capable built-in engine
- **Ten difficulty levels** (1 Beginner → 10 Maximum) riding the engines' full skill range, with a per-level lifetime record
- **137 tactics puzzles**, every one machine-verified by the engine itself:
  each mate-in-1 mates, each mate-in-2 forces mate against *all* defenses
- **Play a friend**: pass-and-play on one phone, or online by 6-letter room
  code — the server is baked in, nothing to set up
- Fischer clocks with increment, autosave & resume, undo/redo, live PGN,
  three board themes, the classic **Cburnett** piece set, sounds & haptics
- **Private by design**: no ads, analytics, trackers, or accounts
  ([privacy policy](PRIVACY.md)); online play uses anonymous identifiers only

## Get it

- **Google Play** — coming soon
- **Direct APK** — every green CI run publishes a signed `gambit-release-apk`
  artifact under [Actions](../../actions); testers can grab `gambit-debug-apk`

## How it's built (and proven)

Every push runs the full pipeline on GitHub Actions:

1. **45 JVM unit tests** + lint — perft-verified move generation (reference
   node counts at depth 4–5 across four standard positions), SAN/PGN, clocks,
   results, material, the online validator, and a full re-proof of all 137
   puzzles
2. A **signed release APK + AAB** with a self-verifying log chain: keystore &
   key-password verification, the release certificate printed via `apksigner`,
   a byte-exact size report, and confirmation the online defaults are baked
3. **47 instrumented E2E tests** on an emulator, plus a **release crash gate**
   that cold-boots the actual R8-minified build and monkey-tests it

Logs publish to the `ci-logs` / `ci-logs-ui` branches; R8 mappings ship as
artifacts for crash deobfuscation. Debug builds use a committed conventional
keystore so sideloaded updates always install cleanly.

## Architecture

- **Pure-Kotlin domain** (`domain/`) — immutable board, legal move generation,
  status/draw rules, SAN, clocks. No Android dependencies; unit-tested on the
  JVM.
- **Swappable engines** (`engine/`) — a built-in minimax+alpha-beta opponent
  (the offline default and CI's sparring partner) and a UCI bridge to the
  bundled Stockfish binary (`jniLibs`, size-guarded, gated to API 29+, with
  silent fallback).
- **Online** (`data/online/`) — Firestore **REST** + anonymous auth (no
  Firebase SDK, no Play Services). Both clients validate every move with the
  identical rules engine; the deployed
  [security rules](firebase/firestore.rules) enforce seats, turn order,
  one-ply appends, and outcome consistency as explicit transitions.
  `functions/` contains a server-authoritative TypeScript reference (engine
  cross-checked against Kotlin on 14,997 positions with zero divergence) for
  a future ranked mode — it is not required or deployed for friend play.
- **UI** (`ui/`) — Jetpack Compose throughout; pieces are the Cburnett SVGs
  transpiled to Compose paths and rendered verbatim.

## Building from source

**Android Studio:** open the project, sync, run. minSdk 24, compileSdk 35.

**The authentic way (no computer):** fork it — Actions builds everything.
Without secrets you get debug-signed builds and the in-app online setup flow;
add the `EMERSION_*` secrets described in [RELEASE.md](RELEASE.md) and
[SERVER_SETUP.md](SERVER_SETUP.md) for Play-signed releases with zero-setup
multiplayer baked in.

```bash
./gradlew :app:testDebugUnitTest      # 45 JVM tests
./gradlew :app:connectedDebugAndroidTest   # 47 E2E (device/emulator)
```

## License

**GPLv3** (see [LICENSE](LICENSE)). Bundles the **Stockfish** engine (GPLv3)
and **Colin M.L. Burnett's** chess piece artwork (GPLv2+) — full attributions
in [THIRD_PARTY_LICENSES.md](THIRD_PARTY_LICENSES.md).
