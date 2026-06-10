# First Build Checklist — Android Studio

This project's domain/logic layers are verified on the JVM, but the Compose UI has
never been compiled (no Android SDK in the authoring environment). Use this list to
get from zip to running app.

## 1. Open & sync
- [ ] Open the project root in Android Studio (Giraffe or newer).
- [ ] Let Gradle sync. Confirm: minSdk 24, compileSdk 35, Kotlin 2.0.x, Compose BOM.
- [ ] If sync fails on plugin versions, accept Studio's suggested AGP/Kotlin upgrade.

## 2. Run the unit tests first (fastest signal)
- [ ] `./gradlew :app:testDebugUnitTest`
- [ ] Expect 24 tests passing: PerftTest, NotationTest, ClockTest, ResultTest,
      MaterialTest, OnlineValidatorTest, PuzzleTest.
- [ ] These exercise the verified core; if they pass, the engine survived the build.

## 3. Compile the app (expect to fix Compose nits here)
- [ ] `./gradlew :app:assembleDebug`
- [ ] Likely first-build fixes (static review couldn't catch these):
  - [ ] `BackHandler` import — uses `androidx.activity.compose.BackHandler`
        (requires `androidx.activity:activity-compose`, already in deps).
  - [ ] `LocalLifecycleOwner` — now in `androidx.lifecycle.compose`
        (`lifecycle-runtime-compose` dep present); if unresolved, swap to
        `androidx.compose.ui.platform.LocalLifecycleOwner` for older Compose.
  - [ ] `collectAsState` import in AppNav (`androidx.compose.runtime.collectAsState`).
  - [ ] PieceRenderer uses `android.graphics.Path`/`Matrix` + `asAndroidPath()` /
        `asComposePath()` — confirm these resolve against your Compose UI version.
  - [ ] Any `Color as UiColor` alias clashes in board files.
- [ ] Resolve unresolved-reference errors one file at a time; the logic is sound.

## 4. Smoke-test on an emulator (the real end-to-end pass I could not do)
Run through these flows and watch for the issues static analysis can't see:
- [ ] Home → Play vs Computer (each difficulty) → make moves; AI replies.
- [ ] Piece move animation: confirm the piece slides and does NOT leave a ghost on
      the origin square (this was just fixed — verify it visually).
- [ ] Capture, check, checkmate, stalemate each play the right sound + show dialog.
- [ ] Promotion: push a pawn to the last rank → picker appears → choose each piece.
- [ ] Clock counts down; background the app ~30s; return → time deducted correctly;
      it does NOT keep counting while you were away beyond the pause point.
- [ ] Resign → confirm dialog → game ends "by resignation", saved as a loss.
- [ ] Offer draw vs AI: once when losing (should accept) and once when winning
      (should show "declined").
- [ ] Undo/redo across an AI reply behaves sanely.
- [ ] Back button (both the on-screen "‹ Home" and the system gesture) returns to
      Home, not exits the app.
- [ ] Pass & Play: both colors are tappable and alternate correctly.
- [ ] Saved Games → resume a game → it restores position; a pass-and-play game
      resumes WITHOUT an AI move; a HARD game resumes at HARD.
- [ ] Settings: toggle Show legal moves, Sound, Haptics, Flip board → start a new
      game → confirm each takes effect.

## 5. Known gaps to decide on before release (see AUDIT.md "STILL OPEN")
- [ ] useStockfish / darkBoard toggles are inert — either implement or hide them.
- [ ] "Play as Black" vs AI isn't exposed on Home (the engine supports it).
- [ ] Clock does not survive full process death (only in-session backgrounding).
- [ ] Settings changes don't apply to a game already in progress.
- [ ] New Game during AI "thinking" doesn't cancel the in-flight AI coroutine.
- [ ] No delete affordance for saved games.
- [ ] Online and Puzzles screens are not built (entry points are no-ops).

## 6. When you hit a specific compile error
Send me the file + error text and I'll give a precise fix. The static audit found
and fixed 10 logic bugs; the remaining risk is purely Compose-API surface that only
a real compile surfaces.
