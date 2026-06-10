# End-to-End Static Audit — 5 Iterations

Environment note: no Android SDK/emulator available, so this is a rigorous STATIC
audit (reading code, tracing flows, checking variables/indexes/state). It cannot
catch runtime/render bugs. Findings labelled by confidence: [BUG] certain logic
error, [RISK] likely problem needs runtime confirm, [NIT] minor.

## Iteration 1 — Data & state layer, ViewModel orchestration

[BUG] ViewModel.undo() line 312: uses `playerColor` not `controllingColor`. In
  pass-and-play this double-undo logic is wrong — it always tries to land on
  playerColor's turn (White), so undoing Black's move behaves incorrectly.

[BUG] autoSave() line 300: hardcodes `vsAi = true` even for pass-and-play games.
  Saved pass-and-play games will be mislabelled "vs Computer" in the list.

[BUG] resume() line 421: always restores into a vs-AI-shaped game and calls
  maybeTriggerAi(). A resumed pass-and-play game would suddenly get an AI move.
  Also: resume() does not restore the clock (clock state is never persisted to
  Room — see GameRepository), so a resumed timed game starts the clock fresh.

[RISK] onAppPaused/persistClock line 414-418: persistClock() calls clock.snapshot()
  but THROWS AWAY the result — nothing is persisted. Comment claims "rides along
  with the game autosave" but GameRepository.save() never stores clock state.
  So process-death clock restore does NOT actually work end-to-end despite the
  ChessClock supporting it. The unit test proves the clock CAN restore; the app
  never wires it.

[BUG] newGame() line 329: calls startClockLoop() which assigns clockLoopJob, but
  the OLD loop from the previous game is never cancelled. Repeated New Game calls
  leak coroutine loops; multiple loops mutate _state concurrently.

[BUG] offerDraw() AI-decline path line 365-368: sets nothing and re-snapshots, so
  pressing "Offer draw" vs AI that declines gives NO user feedback at all — looks
  like a dead button. Needs a transient "declined" signal.

[NIT] cueFor() applies the move a second time (board.apply) purely to detect check;
  minor wasted work, not a bug.

[RISK] init{} and newGame() both can call startClockLoop, and onAppResumed too;
  combined with the no-cancel bug, multiple concurrent clock loops are likely.

## Iteration 2 — Navigation flow, persistence wiring

[BUG] AppNav resume (line ~78): onResume always builds Screen.Game with
  passAndPlay=false and difficulty=MEDIUM regardless of the saved game's actual
  type. SavedGame DOES store vsAi + difficulty, but AppNav ignores them on resume.
  Result: resuming a pass-and-play game spawns an AI; resuming a HARD game plays at
  MEDIUM. The data is there; the wiring drops it.

[BUG] GameRepository.save() computes `result` ONLY from engine.status(). It has no
  knowledge of resignation / draw-agreement / timeout (those live in the ViewModel's
  GameResult). So a resigned or drawn-by-agreement or lost-on-time game is saved as
  "*" (ongoing) in the list. The ResultEvaluator exists but isn't threaded into save.
  FIX: pass the GameResult token from ViewModel into save().

[BUG] No clock persistence in SavedGame schema at all (no time columns). Combined
  with Iter-1 persistClock no-op: timed games cannot truly resume with correct
  clocks. Either add columns or document that resume restarts the clock.

[NIT] AppNav imports LocalContext but never uses it. Dead import (harmless, lint).

[NIT] SavedGamesScreen has a delete query in DAO (deleteById) but no swipe/delete
  affordance in the UI — saved games can never be removed by the user.

[RISK] autoSave runs on every move (and AI reply) creating a Room write per ply.
  Fine functionally, but each writes updatedAt=now, so list ordering churns. Also
  every game (even a 2-move abandoned one) persists forever with no cleanup.

## Iteration 3 — UI rendering, board geometry, animation

[VERIFIED OK] Board tap/render geometry is consistent (tap and render are exact
  inverses) for both flipped and non-flipped. Square color parity correct (a1 dark).
  Coordinate label positions correct (ranks on file-a column, files on rank-1 row).

[BUG] Animation double-draw. In commitMove(): state.animating is set, THEN
  delay(ANIM_MS), THEN engine.makeMove(). So during the slide, state.board still has
  the moving piece on anim.from. BoardCanvas draws all board pieces (including the
  one still on anim.from) AND the interpolated sliding piece. The "skip" only skips
  anim.to (empty pre-move). Net effect: the piece is rendered TWICE during the slide
  — a ghost stays on the origin square while the copy slides. 
  FIX: skip drawing the piece on anim.from (origin), not anim.to. i.e.
  `if (anim != null && sq == anim.from) continue`.

[RISK] Animation timing vs state. progress is driven by animateFloatAsState keyed on
  anim != null. When the move applies and snapshot(animating=null) fires, progress
  animates back 0->... actually target flips to 0f, so the just-placed piece could
  briefly re-slide backward. Needs runtime confirm but the from/to interpolation on a
  null->set->null transition is fragile.

[RISK] Captured-tray pieces (Iter from earlier) draw at 20dp with PieceRenderer; the
  inline Canvas width = captured.size*16dp but pieces drawn at i*s*0.8 spacing with
  s=20dp -> 16dp pitch, matches. OK, but if >~18 captured pieces (impossible in chess,
  max 15) no overflow. Fine.

[NIT] Coordinate label fontSize uses cell*0.16f/2.625f — the /2.625 px->sp hack
  assumes ~mdpi density. On other densities label size will be off. Use sp directly.

[NIT] Same /2.625f density hack appears in PieceRenderer-adjacent text. Cosmetic.

## Iteration 4 — Settings ↔ behavior wiring, engine selection

[BUG] DEAD SETTING: boardFlipped. SettingsScreen toggles it and it persists, but
  ChessScreen is always called as ChessScreen(vm) with flipped defaulting false.
  AppNav never reads settings.boardFlipped. The flip-board toggle does nothing.

[BUG] DEAD SETTING: useStockfish. Toggle persists but nothing constructs
  StockfishEngine from it; AppNav always uses NativeEngine. Toggle is inert.
  (Also Stockfish binaries aren't bundled, so even if wired it would need the
  installer + asset — but the toggle should at least attempt + fall back.)

[BUG] DEAD SETTING: darkBoard. Persisted, never read anywhere in UI. Inert toggle.

[BUG] DISCONNECTED SETTING: settings.difficulty. The Settings difficulty picker
  writes DataStore, but gameplay uses game.difficulty chosen on the Home screen.
  Changing difficulty in Settings has no effect on the next game started from Home
  tiles other than the "Play vs Computer" primary tile (which hardcodes MEDIUM).
  Two sources of truth for difficulty, not reconciled.

[VERIFIED OK] showLegalMoves IS wired (selectIfOwn checks settings.showLegalMoves).
[VERIFIED OK] soundEnabled / hapticsEnabled ARE wired (passed to sound.play()).
[VERIFIED OK] clockMinutes / clockIncrementSeconds ARE wired (init + newGame read them).

[BUG] Settings changes don't apply to an in-progress game. ViewModel reads settings
  once via settings.first() in init and never re-collects. Toggling sound/showMoves
  mid-game has no effect until a new VM is built. (Acceptable for some, but flip/
  difficulty especially feel broken.)

[BUG] "Play vs Computer" primary Home tile hardcodes ChessAI.Difficulty.MEDIUM
  (onPlayAi(MEDIUM, WHITE)) ignoring any prior difficulty choice; only the small
  difficulty row buttons pass a real choice.

## Iteration 5 — Edge cases, scenarios, navigation, lifecycle, resources

[BUG] NO BACK NAVIGATION FROM GAME. ChessScreen has no back/home affordance, and
  AppNav's state-based nav doesn't integrate the system back stack (no BackHandler).
  Once in a game the user is stranded — system Back likely exits the whole app
  instead of returning Home. Settings/SavedGames have onBack; Game does not.
  This is the most user-visible flow bug.

[BUG] "Play as Black" is unreachable. playerColor is plumbed end-to-end and the VM
  correctly auto-moves the AI first when playerColor==BLACK, but HomeScreen only ever
  passes Color.WHITE. The feature exists but no UI exposes it.

[BUG] System-back inside Settings/SavedGames: onBack is wired to a button, but
  pressing the hardware/gesture Back is not handled — same missing BackHandler issue.
  Likely exits app from any sub-screen.

[RISK] SoundManager: pool.load() is async. The first move can call pool.play()
  before the sample finishes loading, so the very first sound may be silently
  dropped. Minor; consider setOnLoadCompleteListener or preload gating.

[BUG] OnlineRepository / online multiplayer is fully unreferenced by UI. onPlayOnline
  in HomeScreen is an empty lambda. The entire online stack (repo, Cloud Functions
  client) is dead code from the app's perspective — no lobby, no matchmaking screen.
  (Known/expected per prior turns, but logging for completeness.)

[BUG] onPuzzles is an empty lambda too — puzzle engine + PuzzleBank exist, no screen.

[RISK] Concurrency: maybeTriggerAi launches a coroutine that mutates _state after
  delay(ANIM_MS); if the user hits "New game" during AI thinking, the old coroutine
  (capturing the old engine reference) may still call engine.makeMove on the NEW
  engine (field reassigned, not captured) -> a stray AI move on the fresh game, or
  state thrash. No cancellation of in-flight AI when engine is swapped.

[RISK] Same for the clock loop and autoSave coroutines on New Game (see Iter-1 leak).

[VERIFIED OK] MainActivity theme wiring fine. PieceRenderer math (45x45 viewbox
  scale) consistent. AI negamax/clock/result/material all unit-verified earlier.

[NIT] difficultyLabel default "MEDIUM" but ChessAI.Difficulty enum names are
  EASY/MEDIUM/HARD/EXPERT — label uses .name so consistent; fine.

## FIXES APPLIED THIS SESSION (logic-verifiable)

[FIXED] Animation double-draw — now skips the ORIGIN square during the slide.
[FIXED] Resume context loss — SavedGamesScreen passes the full SavedGame; AppNav
        restores vsAi (pass-and-play) + difficulty from the saved record.
[FIXED] Dead boardFlipped setting — AppNav now passes flipped = boardFlipped &&
        playerColor==BLACK into ChessScreen.
[FIXED] Disconnected difficulty — pass-and-play and (Home primary tile path via
        settings) now read settings.difficulty; resume uses saved difficulty.
[FIXED] No back navigation — added BackHandler in AppNav (system back -> Home) and
        a "‹ Home" button on the game screen; ChessScreen gained onBack param.
[FIXED] undo() pass-and-play bug — double-undo now guarded by isVsAi.
[FIXED] autoSave hardcoded vsAi=true — now records real isVsAi + null difficulty
        for pass-and-play, and passes the authoritative GameResult token.
[FIXED] GameRepository.save — accepts resultToken so resign/draw/timeout games save
        with the correct result instead of "*". (token logic unit-checked)
[FIXED] Clock-loop leak — startClockLoop cancels any existing job first.
[FIXED] Draw-offer-declined gave no feedback — added transient drawDeclined state +
        dialog + clearDrawDeclined().

## STILL OPEN (need Android Studio / product decisions)

[OPEN] useStockfish + darkBoard remain dead toggles (Stockfish needs bundled binary
       + installer wiring; darkBoard needs a second board palette). Recommend hiding
       these toggles until implemented, to avoid lying to the user.
[OPEN] "Play as Black" still not exposed on Home (VM supports it; needs a UI choice).
[OPEN] Clock state still not persisted to Room across process death (needs schema
       columns: whiteMillis, blackMillis, activeColor, running; + thread through save/
       load). Backgrounding within a session is handled; full process death is not.
[OPEN] Settings changes don't apply to an in-progress game (VM reads settings once).
[OPEN] In-flight AI coroutine not cancelled on New Game (RISK of stray move/thrash).
[OPEN] No saved-game delete affordance in UI (DAO supports it).
[OPEN] Online + Puzzles screens unbuilt (onPlayOnline/onPuzzles are empty lambdas).
[OPEN] First sound may drop if played before SoundPool finishes async load.
[OPEN] EVERYTHING UI: still uncompiled — no Android SDK here. The fixes above are
       logic-level; a Gradle build is required to confirm Compose correctness.

## ROUND 2 — Iterations 6-8 (executable behavioral tests)

[VERIFIED OK] AI: legal move across 1800 random positions; finds mate-in-1 at all
  4 difficulties; prefers mate over hanging queen; deterministic; avoids stalemate
  in KQvK (played Qf6).
[VERIFIED OK] FEN: round-trips 6 positions incl. ep target, no-castling, high
  counters; ep set/clear; castling rights drop per rook; halfmove reset on pawn/
  capture; fullmove increments after black; underpromotion UCI; SAN disambiguation
  (Nce2).
[VERIFIED OK] Clock: blitz increment accumulation; flag is terminal (press after
  flag no-op); snapshot/restore mid-turn exact; reset clears flag; resign precedes
  timeout in ResultEvaluator.

[RISK-CONFIRMED] ChessClock.press(mover) does not validate that `mover` is the
  active side. Wrong-side press (W active, press(BLACK)) banked White's elapsed but
  left active=WHITE and produced W=8000/B=10000 — internally inconsistent (active
  side got charged but turn didn't pass). Not reachable through current ViewModel
  (it always presses the moving piece's color, which equals the active side), but
  it's a latent footgun if any future caller presses incorrectly.
  RECOMMENDATION: make press() ignore a press whose color != activeColor, or assert.

## ROUND 2 — Iteration 9 (online/parsing adversarial) + FIXES

[BUG-FOUND+FIXED] Move.fromUci silently accepted over-length strings ("e2e4e5" ->
  parsed as e2e4, ignoring trailing chars) in BOTH Kotlin and the TS Cloud Function
  port. On the online security boundary this means sloppy/garbage client input was
  coerced into a move. FIXED: require length 4 or 5 in both; reject otherwise.

[BUG-FOUND+FIXED] Square.parse / parseSquare did not range-check file/rank, so "e9"
  or "i1" could produce out-of-range indices. FIXED: require file,rank in 0..7 in
  both Kotlin and TS.

[BUG-FOUND+FIXED] fromUci allowed promotion to KING ("e7e8k") because 'k' is a valid
  PieceType char. UCI only permits q/r/b/n. FIXED: explicit when/switch in both
  Kotlin and TS (TS was already correct).

[BUG-FOUND+FIXED] OnlineGameValidator history replay (Kotlin line 56 + TS line 107)
  called fromUci UNGUARDED. Now that fromUci throws on bad input, a tampered move
  history would crash the validator/Cloud Function instead of returning IllegalMove.
  FIXED: wrapped history replay in runCatching/try in both.

[LATENT-FIXED] ChessClock.press(mover) now ignores a press whose color != activeColor
  (was internally inconsistent on wrong-side press; not reachable via current VM).

[VERIFIED] After all hardening: 29/29 unit tests pass (added UciParsingTest, 5 cases);
  Kotlin↔TS engine parity re-confirmed 0 mismatches across 9,747 positions; TS
  production build (firebase functions) compiles clean; king-promotion rejected both
  sides.

ROUND 2 NET: 4 real bugs found + fixed (all on the online/input-handling boundary),
1 latent hardened. The verified core (movegen, clock, result, material, notation)
showed no new defects under adversarial AI, FEN, and clock stress testing.

## FINAL PROJECT REVIEW (completeness pass)

[CRITICAL-FOUND+FIXED] Gradle wrapper was ENTIRELY MISSING (gradlew, gradlew.bat,
  gradle-wrapper.jar, gradle-wrapper.properties). Every build instruction said
  "./gradlew ..." but the script did not exist — the project could not be built
  from CLI or CI at all. FIXED: installed official Gradle 8.10.2 wrapper (scripts +
  jar from the gradle/gradle v8.10.2 tag).

[FOUND+FIXED] app/proguard-rules.pro was referenced by build.gradle.kts but missing
  — assembleRelease would fail. FIXED: added with Room/Firestore keep rules.

[FOUND+FIXED] No launcher icon (manifest had no android:icon; no mipmap resources).
  FIXED: generated brass-knight icons at all 5 densities from the app's own piece
  geometry; manifest now references @mipmap/ic_launcher.

[FOUND+FIXED] No CI path for a user without Android Studio. FIXED: added
  .github/workflows/build.yml — runs the 29 unit tests, builds the debug APK, and
  uploads it as a downloadable artifact (works from a phone via github.com).

[VERIFIED] Cross-reference audit: every vm.X() call in ChessScreen has a matching
  ViewModel method; every state.X field read is defined in BoardUiState; HomeScreen
  callback signatures match AppNav; functions package.json test script matches its
  file; firebase.json paths correct; AGP 8.6/Kotlin 2.0.20/KSP/Gradle 8.10.2 are a
  compatible version set. Framework-free layer compiles clean; 29/29 tests pass.

[KNOWN-OPEN, unchanged] Compose UI still needs its first real compile (now possible
  via the CI workflow); Stockfish binaries not bundled; useStockfish/darkBoard
  toggles inert; play-as-Black not exposed; clock not persisted across process
  death; Online/Puzzles screens unbuilt; google-services.json required before
  enabling the Firebase plugin.

## ROUNDS 3–7: FIVE PROGRESSIVELY DEEPER TEST ROUNDS (all executable)

ROUND 3 — Deepest rule edges:
  CPW Position 5 perft to d4 (2,103,487) and Position 6 to d4 (3,894,594) exact.
  En-passant-pin (ep capture exposing own king along rank) correctly ILLEGAL; ep
  legal without the pin. Castling rights removed when rook is CAPTURED at home.
  No castling in check; castle-through-attacked-square blocked (only O-O-O legal).
  Double check -> only king moves. Classic a-file stalemate detected. King-capture
  invariant: zero king-capture moves in 93,777 legal moves from reachable positions.
  (One test failure was MY malformed FEN — side to move could capture king — not
  an engine defect.)

ROUND 4 — Search-tree validation:
  Alpha-beta pruned search proven SCORE-IDENTICAL to unpruned minimax at depth 3 on
  4 positions (strongest pruning-correctness guarantee). KRvK mate delivered within
  2 moves at depth 4. Mate-speed folding verified (depth-4 still plays the immediate
  mate). EXPERT depth-5 completes a busy middlegame in 1,235ms on server CPU —
  [UX NOTE] phone-class CPUs will be slower; "Thinking…" state covers it but EXPERT
  may feel sluggish on low-end devices.

ROUND 5 — ViewModel state-machine simulation (headless mirror over real domain):
  Full vs-AI game loop: human always back on move after AI reply (to checkmate).
  Pass-and-play alternation + out-of-turn rejection. Resign-by-controlling-side.
  Capture-promotion two-step (defer -> block other moves -> choose piece). Undo
  rolls back both plies to exact pre-move FEN. Timeout -> TIMEOUT result, no moves
  after flag. (Two test failures were MY chess error — pawn pushing through a
  blocker; engine's correct rejection exposed the bad test.)

ROUND 6 — Cross-system integration chains:
  save(FEN+PGN+UCI) -> load-by-replay -> continue: lockstep across 40 random games
  (FEN, PGN, status all identical; post-resume play stays identical). Online
  validator FEN matched local engine on all 360 moves pushed through it. Result
  tokens correct for mate and resignation. Clock snapshot==live across the save
  boundary.

ROUND 7 — Property-based fuzzing (23,791 positions / 400 games):
  INV-1 exactly one king/side everywhere. INV-2 FEN idempotent everywhere.
  INV-3 ep target only on rank 3/6 and empty. INV-4 no pawn on rank 1/8.
  INV-5 material monotone (<=1 capture/ply). INV-6 legal ⊆ pseudo-legal.
  INV-7 clock fuzz (300 random press/pause/resume/jump/snapshot sequences): no
  negative time, no flag resurrection, no snapshot drift.

NET RESULT ROUNDS 3–7: ZERO engine defects found. Three test-authoring errors of
mine were exposed by correct engine behavior. One UX advisory (EXPERT latency on
phones). Standing suite remains 29/29.

## CI TESTING ROUND (via GitHub Actions) — the round that was impossible locally

Run on real toolchain (commit e226298), both jobs GREEN:

UNIT + LINT (build job):
- 41 unit tests (12 classes) PASS under real Gradle/JUnit — includes CPW pos5/6
  perft, alpha-beta==minimax proof, property fuzzing, integration lockstep.
- Android Lint found 2 REAL ERRORS invisible to JVM testing: StockfishEngine used
  Process.destroyForcibly + timed waitFor (API 26) against minSdk 24 — would CRASH
  on Android 7.x devices. FIXED (API-24-safe destroy()); lint errors now 0.
  Remaining lint warnings: dependency-version notices only (non-blocking).

EMULATOR UI TESTS (ui-smoke job) — first true end-to-end runtime verification:
- Pixel 5 profile, Android 10 (API 29) x86_64 emulator, headless, KVM.
- 8/8 instrumented Compose tests PASS on-device:
  homeScreenRenders, startGameShowsBoardAndStatus, playE4_engineAccepts_andAiReplies,
  backButtonReturnsHome, settingsScreenOpensAndReturns, homeScreenRendersBrand,
  settingsNavigationWorks, savedGamesScreenOpens.
- This proves on a real device: app launches, Compose renders, navigation works,
  the board accepts a tapped move (e2-e4), and the AI replies in the live UI.

CI-DEBUGGING LESSONS (3 iterations, all diagnosed via ci-logs/ci-logs-ui branches
since Actions step logs are on blob storage unreachable from the authoring env):
1. emulator-runner action failed opaquely before running its script → replaced
   with manually scripted emulator steps so every line is capturable.
2. Manual script bugs found via captured log: avdmanager/emulator AVD-home
   mismatch (fixed by pinning ANDROID_AVD_HOME), adb missing from PATH, and the
   emulator being SIGPIPE-killed by piping its output through head (fixed by
   redirecting to emu.log).
3. Boot completed in ~15s once fixed; connectedDebugAndroidTest ran in 1m27s.

## FULL E2E SCENARIO ROUND (emulator, all clicks) — 20/20 PASS

Commit 63321cd, Android 10 emulator, every test via real screen interaction:

GAMEPLAY: illegal-target tap clears selection and pipeline recovers; undo rolls
back both plies (button disables at start) and redo re-applies; pass-and-play
alternates both colors through real board taps.
GAME ENDINGS: resign confirm dialog -> "Black wins / by resignation" -> New game
resets; resign Cancel keeps the game live; draw offer vs AI accepted when level
("Draw / by agreement"); pass-and-play draw offer Decline continues play, Accept
ends it; scholar's-mate played move-by-move on the board ends with
"White wins / by checkmate" dialog.
PROMOTION: 9-move pass-and-play line to a7xb8 opens the "Promote to" picker;
tapping the rook option produces SAN "axb8=R" (underpromotion verified on-device).
PERSISTENCE: play vs AI, navigate Home -> Saved Games, tap the saved row, history
replays ("e4" restored) on the board.
NAVIGATION/CONFIG: all four difficulty buttons start games; Settings shows all
controls (one test-authoring fix: Section headers render uppercased).

One iteration needed: 19/20 first run; sole failure was the test expecting
"Clock" where the UI renders "CLOCK". No app defects found in this round.
Testability additions: testTags on promotion options and saved-game rows.

## BACKLOG ROUND (post-E2E): 4 fixes shipped, all verified on-device (24/24)

FIXED + E2E-VERIFIED (commit a52e50a):
1. Inert "Use Stockfish" toggle hidden from Settings (was misleading users);
   test asserts its absence. DataStore field retained for easy re-enable.
2. "Play as Black" picker on Home, wired to both AI entry points. On-device test:
   AI (White) opens on its own, human replies 1...e5 as Black.
3. In-flight AI cancellation on New Game: aiJob.cancel() + gameGeneration guard,
   covering the bestMove window AND both 160ms animation windows (AI + human
   commitMove). On-device race test: start EXPERT, play e4, hit New game during
   "Thinking…", wait 5s — no stray move lands, fresh game intact.
4. Delete button on saved-game rows (repo.delete existed; UI was missing).
   On-device test: row count decreases.

REGRESSION CAUGHT BY THE SUITE: the new Play-as row overflowed the non-scrollable
Home column, pushing the Settings tile off a Pixel-5-sized screen — 5 tests failed
(every one involving the Settings entry). Fixed by compacting the header (logo
96->72dp, spacers trimmed) and making Home vertically scrollable. This is exactly
the class of runtime layout bug static review cannot catch; the E2E investment
paid for itself within one round.

STILL OPEN: settings don't apply mid-game; clock not persisted across process
death; Online/Puzzles screens; Stockfish bundling decision; darkBoard field unused.
