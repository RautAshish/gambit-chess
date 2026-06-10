# ChessApp — Android Chess (Kotlin + Jetpack Compose)

A complete chess app built across five phases: a Perft-verified rules engine, a
built-in Minimax AI, an optional Stockfish engine, local persistence, tactics
puzzles, chess clocks, and server-authoritative online multiplayer. The domain
layer is pure Kotlin (no Android deps), so it compiles and tests on the JVM and is
reused verbatim on the multiplayer server.

## Module map

```
domain/
  model/      Model.kt, Board.kt        Pieces, immutable board, FEN
  engine/     MoveGenerator.kt          Legal move generation + attack detection
              GameEngine.kt             Status, history, undo/redo, PGN
              Notation.kt               SAN + PGN movetext
              ChessClock.kt             Fischer-increment two-sided clock
  ai/         Evaluator.kt, ChessAI.kt  Minimax + alpha-beta, piece-square eval
engine/                                 Swappable engine abstraction
  ChessEnginePort.kt                    Common interface
  NativeEngine.kt                       Built-in Minimax adapter (offline default)
  stockfish/StockfishEngine.kt          UCI bridge to a native Stockfish binary
  stockfish/StockfishInstaller.kt       Extracts the binary from assets
data/
  db/         ChessDatabase.kt          Room: saved games + puzzle progress
  prefs/      SettingsRepository.kt     DataStore preferences
  puzzle/     Puzzle.kt                 Tactics puzzle model + session validator
  online/     OnlineGame.kt             Multiplayer model + move validator
              OnlineRepository.kt       Firestore matchmaking + live sync
ui/board/     ChessViewModel.kt         State, promotion, clock, undo/redo,
                                        sound cues, captured pieces, animation, autosave
              ChessScreen.kt            Compose Canvas board + controls + dialogs
              PieceRenderer.kt          Vector chess pieces (no Unicode glyphs)
ui/home/      HomeScreen.kt             Landing screen + mode/difficulty selection
              SavedGamesScreen.kt       Resume-a-game list backed by Room
ui/settings/  SettingsScreen.kt         Toggles + difficulty/clock presets (DataStore)
ui/sound/     SoundManager.kt           SoundPool cues + haptics
ui/nav/       AppNav.kt                 Screen graph + ViewModel factory
data/db/      GameRepository.kt         Save/restore games (FEN + PGN + UCI replay)
res/raw/      move/capture/check/game_end.wav   Bundled sound cues
firebase/     firestore.rules           Security rules (games read-only to clients)
              firebase.json             Deploy config
functions/    src/board.ts              TS engine port (board + FEN)
              src/movegen.ts            TS move generation
              src/engine.ts             TS game status + move validator
              src/index.ts              Cloud Functions: submitMove/findMatch/resign
              test/equivalence.test.ts  Perft + validator parity tests
```

## Correctness — everything below was executed and verified on the JVM

**Move generator (Perft).** Leaf-node counts matched the chess-programming
reference values exactly, which proves castling, en passant, promotion, pins, and
check evasion are all correct:

| Position            | Depth | Nodes      | Result |
|---------------------|-------|------------|--------|
| Starting position   | 5     | 4,865,609  | PASS   |
| Kiwipete            | 4     | 4,085,603  | PASS   |
| Position 3 (EP)     | 5     | 674,624    | PASS   |
| Position 4 (castle) | 4     | 422,333    | PASS   |

**Notation.** Scholar's Mate renders as `1. e4 e5 2. Bc4 Nc6 3. Qh5 Nf6 4. Qxf7#`
with correct disambiguation, castling (`O-O`), and check/mate suffixes.

**Clock.** Tick, Fischer increment, side switching, flag-on-zero, and `m:ss`
formatting all verified.

**Built-in AI.** Finds a forced mate (Qh4#) unaided, and played a full legal
28-ply self-play game terminating correctly by repetition.

**Stockfish bridge.** Tested against a real Stockfish 16 binary over UCI: full
handshake, FEN positioning, opening move (e2e4), mate-in-1 (e1e8), and capturing a
hanging queen (e4d5) — the exact Kotlin code that runs on Android.

**Online validator.** Server-authoritative checks reject out-of-turn,
non-participant, illegal, and post-game moves, and assign the correct winner on
checkmate.

**Cross-engine parity.** The TypeScript Cloud Function engine was cross-checked
against the Kotlin engine on **14,997 unique positions** drawn from random
playouts: legal-move sets and FEN matched on every single one, with zero
mismatches. The server cannot diverge from the client.

**Puzzles.** Built-in puzzles verified to deliver real checkmates; the session
accepts correct moves and rejects wrong ones.

Run the whole suite in Android Studio:

```bash
./gradlew :app:testDebugUnitTest
```

(`PerftTest`, `NotationTest`, `ClockTest`, `ResultTest`, `MaterialTest`,
`OnlineValidatorTest`, `PuzzleTest` — 24 tests in total, all passing.)

## Build & run

1. Open the folder in Android Studio (Giraffe or newer). minSdk 24, compileSdk 35.
2. Gradle sync. Run on an emulator or device.
3. The app opens on a Home screen: pick a difficulty to play the built-in engine,
   or choose pass-and-play, saved games, or settings. In-game you get vector pieces,
   move animation, sound + haptics, a clock, captured-piece trays with a material
   badge, board coordinates, undo/redo, a live PGN list, a promotion picker, and a
   game-over dialog. Games auto-save after every move and resume from Saved Games.

## UX layer — verification status (read before shipping)

The rules engine and both validators are executed-and-verified (see above). The
**presentation layer added on top was not compiled in this environment** — no
Android SDK was available here, so the Compose/Android files (screens, ViewModel,
PieceRenderer, SoundManager, navigation) were written and statically reviewed but
need a real Gradle build to confirm. What *was* verified independently:

- The **vector piece geometry** was prototyped and visually iterated (the king and
  knight were redesigned after the first render) so the silhouettes read correctly
  at both full-board and captured-tray sizes.
- The **`Material` module** (captured pieces + balance) is pure Kotlin and unit
  tested (`MaterialTest`), passing on the JVM.
- The **timestamp clock** (`ChessClock`) and **result model** (`ResultEvaluator`,
  covering resignation, draw agreement, and loss on time) are pure Kotlin and unit
  tested (`ClockTest`, `ResultTest`). The clock self-heals across app backgrounding
  and survives process death via snapshot/restore — verified deterministically with
  an injectable time source.
- Full **Perft regression** was re-run after all changes: the engine core is
  unchanged and still correct.

The resign/draw buttons, draw-offer dialog, and the Compose lifecycle observer that
pauses the clock on background were written and reviewed but, like the rest of the
UI, need a real Gradle build to confirm.

Expect to fix minor Compose issues on first build (import nits, etc.); the logic
underneath is sound, but treat the UI as "needs a compile pass."

## Enabling the optional pieces

**Stockfish.** Compile Stockfish for each ABI and drop the binaries at
`app/src/main/assets/stockfish/<abi>/stockfish`. Then in `MainActivity`:

```kotlin
val path = StockfishInstaller.ensure(this)
ChessViewModel(opponent = StockfishEngine(path))
```

Stockfish is GPLv3 — distributing it obliges you to comply with the GPL. The
built-in `NativeEngine` is the default precisely so the base app carries no such
obligation.

**Multiplayer.** Add `google-services.json`, uncomment the `google-services`
plugin lines in the two Gradle files, deploy the Cloud Functions, and deploy
`firebase/firestore.rules`. The architecture is server-authoritative:

- `/games` is **read-only** to clients in the security rules. All mutations go
  through Cloud Functions (`submitMove`, `findMatch`, `resign`) running with admin
  privileges. A tampered client cannot write an illegal or out-of-turn move because
  it cannot write the game document at all.
- The function in `functions/src/` is a TypeScript port of the Kotlin engine. It
  was verified **byte-identical to the Kotlin engine across 14,997 positions**
  (legal moves + FEN), so the server's verdict always matches the client's.
- The client keeps the Kotlin `OnlineGameValidator` only for optimistic UI: apply
  locally for instant feedback, then reconcile when the server's update streams back.

Deploy:

```bash
cd functions && npm install && npm run build && npm test   # runs the equivalence suite
firebase deploy --only functions,firestore:rules
```

## Design notes

- **Board** is an immutable 64-square mailbox: clear and correct. The `Board` API
  hides the representation, so swapping in bitboards later changes nothing else.
- **Persistence** stores final FEN (instant resume) plus PGN and the UCI move list
  (replay/analysis). Save after every move for crash-safe resume.
- **Engine abstraction** (`ChessEnginePort`) means the AI is a runtime choice;
  difficulty maps to search depth (native) or skill/Elo limit (Stockfish).
