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

## PRODUCT REVIEW ROUND (21 observations) — shipped & verified 28/28 on-device

All 21 product-owner observations addressed at commit d8f9151 (details in commit
7725471 message). Verification: 41/41 unit + lint clean + 28/28 emulator E2E,
including 4 new regression tests written for the reported bugs:
undoAsBlack_aiRestartsPlay (#13), flipBoardForBlack_orientsAndAcceptsMoves (#10),
clockDescriptionShown (#7), gameOverDialog_viewBoardDismisses (#18).
The undo-as-black and flip-as-black tests pass, proving #13 fixed and #10 working
as wired. EASY verified: 120/120 legal picks, still finds mate-in-1.
Two test-authoring iterations were needed (rule-method import; scroll-clipped
board assert + exact-vs-substring status match) — no app defects in either.

PRODUCT DECISIONS LOGGED:
- #3 default board: Classic uses ivory/graphite rather than pure white/black
  (glare in long sessions); Walnut and Forest selectable.
- #6 confirmed real: instant AI replies were the issue; 550ms floor + 220ms slide.
- #16 handled as label "Gambit Chess" + ASO advice; store listing is out of code's reach.
- #1 Online/Puzzles: visible but "Coming soon" — roadmap without dead buttons.

## QA VERIFICATION MANDATE — 3 phases + 5 escalating rounds (final: 156e730)

PHASE 1 — CLAIM AUDIT: all 21 review fixes verified present and wired in code.
PHASE 2 — INTRODUCED-BUG HUNT: found and fixed
  Bug A (introduced): settings write mid-think wiped thinking/promotion state.
  Bug B (pre-existing): Undo/Redo usable after a decided game.
ROUND 1 (static+unit): 41/41, dead code removed (LIGHT_SQ, unused scale).
ROUND 2 (deep local fuzz): AI-vs-AI full games all 4 difficulties (every move
  legality-checked); 30x200 undo/redo storm ops vs lockstep replay; 40 random
  mid-game save/replays byte-identical; SAN uniqueness on 60 random positions.
ROUND 3 (on-device feature coverage): FOUND 2 REAL SHIPPED BUGS —
  (i) #9 was not actually fixed: rememberSaveable does not survive when-branch
      navigation on device; replaced with DataStore persistence.
  (ii) selection race: starting a game immediately after tapping a colour or
       difficulty chip used the stale persisted value (DataStore round-trip);
       fixed with local-immediate state + write-through persistence.
  Plus suite hardening: difficulty persistence (#20, by design) leaked EXPERT
  into later tests' 60s waits; AI-wait tests now pin Easy and restore.
  Final: 34/34 green.
ROUND 4 (adversarial): 40/40 first try — input storm during Expert think,
  double-tap deselect, random tap storms, dead-game tap immunity, activity
  recreation -> resumable from autosave, navigation thrash.
ROUND 5 (closed-loop): 42/42 first try — test drives its own GameEngine mirror,
  taps real squares, reads AI SANs back from the UI, matches each to exactly one
  legal mirror move across 10 plies (desync-impossible-by-construction);
  castling (O-O) and en passant (exd6) SAN-verified through the live UI.

Net: 2 introduced bugs + 2 shipped bugs found and fixed by the mandate.
Totals at HEAD: 41 unit + 42 instrumented, all green; lint clean.

## FEATURE ROUND: Stockfish + Puzzles + Online (final: 3745135, 46/46 + 45 unit)

STOCKFISH (offline, optional):
- CI fetches official sf_18 android-arm64 into jniLibs as libstockfish.so;
  executed from nativeLibraryDir (the only exec-allowed path on API 29+, the old
  assets->filesDir installer was rewritten); placeholder .so per other ABI so the
  APK installs everywhere, with a >1MB size guard so placeholders read as absent.
- UCI port PROVEN locally against a real Stockfish binary: legal bestmoves at
  skills 2/8/14/20 across positions, mate-in-1 found, "bestmove (none)" -> null,
  close() leaves no zombie (daemon reaper added after finding one).
- Engine chosen at game start: settings.useStockfish && binary present ->
  StockfishEngine with EASY/MED/HARD/EXPERT -> skill 3/8/14/20; else built-in.
  Settings row states availability honestly. GPLv3: THIRD_PARTY_LICENSES.md.

PUZZLES (offline): 137-puzzle bank MINED from engine self-play, every puzzle
machine-verified (mateIn1 mates; mateIn2 first move forces mate vs ALL replies)
and RE-PROVEN in CI by PuzzleBankTest on every build. Screen with hint/retry/
next, side-to-move-at-bottom orientation, progress persisted, board reuse.

ONLINE (free tier): room-code multiplayer over Firestore REST (no SDK, no
google-services.json, no paid Functions): anonymous auth with token refresh,
optimistic-concurrency writes (updateTime preconditions, race retry), BOTH
clients validate every state with the parity-proven validator + corrupt-history
detection; participant-scoped firestore.rules; lobby/waiting/game screens;
config in Settings; SERVER_SETUP.md (5-minute guide). The Cloud-Functions
server-authoritative variant remains in-repo for a future ranked mode.

CI iterations: 3 compile fixes (flow import, factory scope threading, malformed
when-blocks — one script abort initially masked the third), then 2 E2E fixes
(single-arg VM ctors for the default factory; obsolete hidden-toggle test
flipped to the ships-now policy; puzzle wrong-move test made order-independent).

## VETERAN 5-ROUND AUDIT (consolidated fix: this commit)

Five independent lenses, no fixes between rounds, one consolidated fix, full regression.

R1 static/packaging — INTERNET perm ✅, validator updatedAt bump ✅, status vocab ✅.
  FIXED: [F1 Med] adaptive icon lacked <monochrome> (Android 13 themed icons) — white
  silhouette layer added; [F2 Med] 3 Firebase SDK deps shipped for a dormant file —
  deps removed, file relocated to docs/reference/CloudFunctionsOnlineRepository.kt;
  [F4 Low] online draw line read "Draw · draw fifty move" — proper names now;
  [F6 Low] version stamped 2/"1.1". NOTED: allowBackup w/o extraction rules kept
  deliberately (anonymous identity continuity across reinstalls); i18n debt.
R2 executable proofs — ALL CLEAN: 200-game encode/decode round-trips, winnerUid
  null/"" symmetry, fool's-mate two-client sim, refusal paths, tamper detection
  (org.json shim built to execute the Android-only layer on JVM).
R3 UX walk — BackHandler ✅. FIXED: [F1 Low] "Online play" section now last (infra
  below gameplay); [F2 Med] Puzzle + Online had NO sound/haptic cues — wired with
  the same foreground gate as the local game (poll-adopted moves stay silent in
  background). NOTED: waiting room has no cancel (delete forbidden by rules —
  rules change scheduled with ranked mode); online draw offers = backlog.
R4 adversarial traces — busy/poll/main-thread serialization ✅. FIXED: [F1 Low]
  token-refresh failure silently signed up a NEW anonymous uid, orphaning the
  player from their own games mid-match — now surfaces a connection error and
  keeps identity; [F2 Med] online config was read once at VM birth (saving keys
  in Settings required process restart) — settings now collected live, repo swaps
  only in LOBBY. BONUS: found my own prior "message softening" edit had silently
  no-opped (unasserted replace) — re-applied; all future edits assert anchors.
R5 residual — google-services plugin correctly absent ✅, E2E suite healthy ✅.
  FIXED: [F1 Low] README claimed assets-extraction installer and server-
  authoritative online — both corrected.

Regression: local unit suite + JVM online-stack suite + full CI (45 unit, 46 E2E).

## USER FIELD-TEST ROUND (3 reports, all valid)
1. Puzzle board rotated 180 on a correct answer — orientation tracked the LIVE
   side-to-move instead of the solver's seat. Fixed: anchored to the starting
   position's mover for the puzzle's lifetime.
2. Online setup screen demanded Firebase knowledge + a repo file — hostile to
   store users. Fixed: builds accept a baked default server via two CI secrets
   (players get zero-setup online); Settings fields demoted to advanced
   override; copy rewritten player-first; E2E made valid in both build modes.
3. Knight logo read as a cat. Confirmed — and the home header shares
   PieceRenderer, so the board knights had the same problem. Redesigned
   (Staunton v5: deep blunt muzzle, low brow, smooth crest) after 3 rendered
   iterations judged at 432/120/44 px; applied to renderer, all launcher
   layers, and store art.

## ICON/PIECES ROUND 2 (user pushback: "refer to sources, don't build from scratch")
Correct call. Hand-rolled v5 read as a dolphin at 512px. Replaced ALL SIX piece
designs with the canonical Cburnett set (Wikipedia/Lichess pieces, GPLv2+,
fetched from the lichess repo — 45x45 grid, drop-in for our renderer). Built an
SVG->Kotlin transpiler (svgpathtools sampling, style/group fill inheritance —
two bugs caught pre-ship: hollow R/Q from group fills, hollow B from nested
groups). Launcher/store art re-rastered from the genuine knight via cairosvg.
App licensed GPLv3 for coherence with bundled GPL components.

## POST-CBURNETT VERIFICATION SWEEP (evidence-first, per user mandate)
Verified clean: all 12 piece renders proven pixel-level via local simulation of
the exact renderer semantics (roles/pad/stroke) — no hollow bodies or winding
defects; in-app URLs = 3 programmatic Google endpoints only (no clickable
links); doc links: both GitHub URLs return 200 (gnu.org/stockfishchess.org
syntactically valid, unverifiable from sandbox); shipped payload featherweight
(res 336K, largest file 61KB wav); no stray assets.
Found+fixed: Settings said "~15 min" for server setup vs "five minutes"
everywhere else (introduced in field-fix round); isShrinkResources was absent
(now on; release crash gate re-proves the shrunk build); CI now prints a SIZE
REPORT (engine/APK/AAB bytes) into the published log — prior engine-size line
was never captured, so no size was ever verified: now it is, every build.
Corrected record: local kotlinc did not survive container reset (earlier
"stale jar" attribution was wrong); unit regression authority = CI JUnit run.

## SIZE DECISION (owner call): option (a) — single full build, Stockfish bundled
Rationale: professional positioning makes the engine the headline, not the
size; 78MB APK / ~190MB on disk accepted for the target audience. Falsifier
on file: if post-launch metrics show size-driven abandonment, retrofit Play
on-demand delivery (option d). Follow-through: Stockfish now DEFAULT-ON where
the binary exists (fresh installs; availability-gated as before), listing
rewritten engine-first.

## SECURITY INCIDENT: keystore generated on a then-PUBLIC repo (contained)
The first generate-keystore run took passwords as workflow inputs; repo was
public, so the run page/log exposed both passwords and the artifact (the
keystore) was world-downloadable. Containment: run deleted by owner (my API
delete was 403 — the PAT deliberately lacks actions-write); repo made private;
workflow redesigned: hard-gates on repo privacy, generates random credentials
in-runner (no inputs, nothing logged), artifact carries ready-to-paste secret
lines. Owner regenerated cleanly and installed the 4 signing secrets. Root
cause was mine: the original design assumed a private repo without verifying;
the gate now enforces the assumption. Residual: delete the SECOND keystore run
after confirming signing, BEFORE any future flip back to public (artifacts
ride along with runs). Signing is now verified per-build in the published log
via keytool -printcert (CN=Gambit Chess vs CN=Android Debug).
Signing incident epilogue: first keystore-loaded build failed at packageRelease
("final block not properly padded"); verifier chain convicted GAMBIT_KEY_PASSWORD
specifically (store password + alias proven OK). Owner re-pasted the one secret;
this commit triggers the verification run expected to print the first
CN=Gambit Chess release certificate.
Correction to the epilogue: the owner's paste was CORRECT; root cause was my
generator — JDK-default PKCS12 silently ignores -keypass, so the emitted
GAMBIT_KEY_PASSWORD never matched the key's true password (== store password).
The 'verifier' passed vacuously (importkeystore ignores srckeypass on PKCS12).
Fixed: generator forces -storetype PKCS12 with one password; verifier now
asserts the equality that PKCS12 requires. Misdiagnosis owned.

## PERMANENT IDENTITY: applicationId = io.github.emersionplay.gambit
Chosen pre-first-upload (immutable after). Neutral org namespace (owner
privacy), GitHub-org convention, availability verified (github 404; no web
collisions — nearest: Emersion Systems billing platform, unrelated). Code
namespace remains com.chessapp (internal-only). Six refs renamed: gradle id,
2x shortcuts targetPackage, gate uninstall/launch/pidof; launch component now
explicit (relative .MainActivity would mis-resolve under the new package).

## IDENTITY: applicationId finalized pre-Play = io.github.emersionplay.gambit
Owner-chosen neutral org namespace ("emersionplay": GitHub 404-free, zero web
collisions on the exact compound). Code namespace remains com.chessapp (AGP
split, zero source churn); shortcuts targetPackage + all CI adb refs updated
(am start now fully-qualified since class package != appId). Old sideloaded
com.chessapp installs are a different app: uninstall manually; local saves
don't carry (free moment: never published).

## FIELD OBSERVATIONS ROUND 2 (5 reports)
1 VALID: About screen shipped the personal-name repo URL — now the org URL
  (github.com/emersionplay/gambit-chess; resolves after the pre-launch
  transfer, old URL auto-redirects meanwhile). Docs had no other refs.
3 VALID: black pieces used white-geometry-only; on dark squares bodies melted
  into the background, making identical knights look different. Fixed with the
  authentic Cburnett BLACK set (per-color part lists; absent-fill=black and
  <circle> elements handled in the transpiler) — 24-render proof on CLASSIC +
  WALNUT worst cases.
4 VALID: Settings/SavedGames said bare "Back" while game/puzzle said "Home".
  Rule adopted: destination-fixed screens name the destination ("‹ Home");
  Online keeps "‹ Back" (dynamic: lobby vs game). 4 E2E taps updated.
5 VALID: Settings "Difficulty" was a duplicate writer of the same preference
  as the Home selector — removed; Home is the single source of truth.
2 (question, not code): Firebase needs any Google account; no GitHub linkage.
Renderer daemon-crash fixed via constant-pool string geometry. E2E fallout of
the label change: my sweep grep had been truncated with head -4, so SmokeTest
and Round4 still tapped the removed "Back" — completed untruncated; lesson
logged (never head-limit a sweep you act on).

## REBRAND (pre-launch, owner-approved): Gambit Chess -> Emersion Chess
Driver: "Gambit" became contested in-category (live "Gambit: Chess Club" on
Play with near-identical pitch; Chess.com's new "Gambit" using Play-and-Learn
phrasing; gambit.ai). ASO: navigational capture ~100% vs leaked; title
"Emersion Chess: Stockfish" (25c) spends the freed chars on the highest-intent
pro keyword; exact-compound verified collision-free (only Steam's dormant
"Immersion Chess" VR title, absent from Play — fuzzy search bridges the
spelling both ways). appId leaf aligned while still free:
io.github.emersionplay.chess. Retained internal tokens by design: Theme.Gambit,
GambitUiTest, GAMBIT_* secret names, gambit-* CI artifact names, and the
existing signing cert's CN=Gambit Chess (immutable without a new key; invisible
to users). Repo transfer target: emersionplay/emersion-chess.
Supersedes earlier 'retain GAMBIT_* tokens' call (owner request): ALL secret
names, env vars, and keystore paths now EMERSION_*; future-generated keystores
use alias 'emersion'. The EXISTING keystore's alias remains baked as 'gambit'
(immutable without regeneration) — so the migrated EMERSION_KEY_ALIAS secret's
VALUE stays the string 'gambit'. Owner migration: add 4 EMERSION_* secrets
(same values), delete 4 GAMBIT_*; verifier chain self-certifies next push.

## IDENTITY FINAL (owner-delegated decision, closed): applicationId =
io.github.emersionplay.chess. Org segment carries the brand; leaf names the
product (com.spotify.music pattern). Gate-proven on-device pre-stamp.
## SECRET NAMING (supersedes earlier 'retained' note): all secret/env names
migrated GAMBIT_* -> EMERSION_* per owner. Signing key alias VALUE remains
"gambit" — baked inside the existing keystore, invisible everywhere, and
changing it would mean a new keystore for zero benefit. Owner migration: add
4 EMERSION_* signing secrets with existing values, delete GAMBIT_*.
Verification run 035478f: signing chain fully green (cert 36c4673f...). The
defaults proof-line caught the two EMERSION_ONLINE_ secret VALUES swapped
(printed an AIza string as project id) — guard now names it automatically.
Handover note: repository transfer to the emersionplay org severs the
personal fine-grained PAT by design (resource-owner scoping) — the built-in
revocation moment. Post-transfer verification is owner-run (Re-run latest
workflow; expect 'ONLINE DEFAULTS: baked (<project-id>)' and PLAY-SIGNED).
Launcher art upgraded to owner-supplied glossy hero render (Cburnett-derived
knight — GPL attribution already covers derivation; owner to confirm generator
usage rights before Play upload). Baked bezel stripped programmatically
(launchers/Play apply their own masks); art ships as the adaptive BACKGROUND
with transparent foreground; feathered margin keeps the knight in the 66dp
safe zone; monochrome themed-icon layer unchanged; legacy + Play-512 rebuilt.
Old flat foreground PNGs retained on disk (unreferenced) as fallback.
Hero-icon provenance confirmed: AI-rendered by owner from our knight as a
locked reference (rights: owner's generation; derivation: covered by existing
Cburnett GPL attribution). Brand spec + platform corrections banked in
store/BRAND.md; feature graphic unified onto the hero art.
Second deleteAll timeout in ~25 runs -> promoted from 'flake' to fragile test:
it never pinned seat/difficulty while DataStore persists across tests, so a
stray playAsBlack=true flips the board under its coordinate taps. Now
self-pins White+Easy (same order-independence class as the puzzle-test fix).
Theme decision (owner asked for honest take): interior stays flat/matte by
design — gloss belongs to marketing surfaces, not gameplay chrome; the board's
readability rule is load-bearing. One alignment taken from the hero art: all
in-app accent gold unified from brass C9A227 to the logo's E4B02A. Board
palettes untouched (gameplay-tuned).

## FIELD ROUND 3 (5 concerns, org era)
1 Black-piece inconsistency: crop-zoom evidence showed bone outline vanishing
  on light squares (bone~cream). Resolution per owner directive — do it like
  the reference apps, verbatim: black pieces now carry the set's DARK body
  outline (light = inner details only), and CLASSIC's dark square lifted
  5A5A54->75756B into the reference-board contrast gamut.
2 Online settings now hide behind "Use a custom server (advanced)" when the
  build ships a baked server — players see "Multiplayer is ready."
3 Home header knight renders in the hero gold gradient (flat geometry, brand
  palette) — the brand lockup zone is the one sanctioned gloss exception.
4 First-ever puzzle solve no longer jumps to the next puzzle with a stale
  Solved header (init heuristic replaced by explicit flag).
5 Header audit: Settings/Saved titles centered (nav pages) while play
  surfaces stay functional strips; Delete-all gold (page's primary action);
  Mute stays quiet by design; Online's dynamic "Back" stands.

## FIELD ROUNDS 4+5 (owner screenshots)
Headers (Online/Settings/Saved incl. the Delete-all shift report): my weight-
spacer 'centering' was the three-slot fallacy — it centers in LEFTOVER space,
so titles sat right of true center and moved when edge buttons changed. All
three rebuilt as overlay headers (Box): title pinned to SCREEN center,
back/action floating at CenterStart/CenterEnd, mutually immune.
Black pieces ('extra dark, bulky, different'): the softened tokens muted the
set's designed internal contrast, and the half-stroke rim added real dark
mass. Went fully verbatim like the references: #000 fill, #fff inner details,
#000 body stroke — Lichess-exact; white side untouched.
Puzzle void: content column was top-packed AND scrollable so it could never
claim the screen; scroll dropped (content fits by construction), header
pinned, board group weights+centers in remaining height.
Round 4/5 record CORRECTION: the interrupted pass had in fact completed ALL
five pieces (fb5dc27) including TopRow — my post-compaction verification
misread it because a grep -A2 window stopped short of a Box pushed down by a
2-line comment. The prior AUDIT note claiming a missed header was false and
the accompanying commit was audit-only. Lesson stacked on the truncation
scar: verification windows must exceed plausible drift.

## PROMOTE DIALOG (theme mismatch report)
Screenshot was a FOREST game; the "green" was the board scrim + board-relative
promo tiles (pal.dark/light), which made BLACK promotion pieces near-invisible
on WALNUT/FOREST. Container was already PANEL (not the fossil). Fix: promo
tiles pinned to fixed bone EDEAE2 so pieces of either color pop on every
theme; pixel-proof rendered across CLASSIC/WALNUT/FOREST/BLUE. Dead pal param
removed from PromotionDialog.
Promotion dialog audit (user: "theme mismatch"): container was already PANEL —
the green in the screenshot was FOREST's own board colors: tiles were
board-relative (pal.dark/pal.light), making black promotion pieces mud on
moss/walnut. Tiles now fixed bone (EDEAE2); rendered proof across all four
themes shows the worst case (black knight) crisp everywhere. testTags
untouched.
Promotion dialog "theme mismatch" (owner report, FOREST game): container was
already PANEL — the green was the ACTIVE FOREST palette itself, faithfully
mirrored by board-relative tiles, plus scrim bleed. Real defect: black
promotion pieces on dark board-relative tiles went muddy (worst on WALNUT/
FOREST — proven in an 8-render comparison). Fix: tiles pinned to fixed bone
EDEAE2 so promotion pieces pop identically on every theme; testTags untouched.
Recreation-test timeout at a9e43d1 was NOT the tile change: same disease as
deleteAll (unpinned coordinate taps vs persisted seat). Cure promoted to a
suite rule and applied family-wide: startVsAi helper + 5 direct sites now
self-pin (deliberate-Black tests untouched).
