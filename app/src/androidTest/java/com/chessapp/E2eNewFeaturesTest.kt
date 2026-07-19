package com.chessapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** On-device tests for this round's fixes: play-as-Black, saved-game deletion,
 *  and the New-Game-during-AI-thinking race (generation guard) plus its
 *  slide-window sibling (orphaned-animation inheritance through reset). */
class E2eNewFeaturesTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String, timeoutMs: Long = 30_000, substring: Boolean = false) {
        rule.waitUntil(timeoutMs) {
            rule.onAllNodesWithText(text, substring = substring)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitGone(text: String, timeoutMs: Long = 30_000, substring: Boolean = false) {
        rule.waitUntil(timeoutMs) {
            rule.onAllNodesWithText(text, substring = substring)
                .fetchSemanticsNodes().isEmpty()
        }
    }

    private fun tapSquare(file: Int, rank: Int) {
        rule.onNodeWithTag("board").performTouchInput {
            click(Offset(width * (file + 0.5f) / 8f, height * ((7 - rank) + 0.5f) / 8f))
        }
    }

    private fun clickInDialog(text: String) {
        rule.onNode(hasText(text) and hasAnyAncestor(isDialog())).performClick()
    }

    @Test
    fun playAsBlack_aiMovesFirst_thenHumanReplies() {
        waitForText("Play as")
        rule.onNodeWithText("2").performClick()       // pin fast AI
        rule.onNodeWithText("Black").performClick()
        rule.onNodeWithText("Play vs Computer").performClick()
        // The AI (White) must open the game on its own.
        waitForText("Black to move", timeoutMs = 60_000)
        waitForText("1.", timeoutMs = 60_000, substring = true)
        // The human answers as Black with 1... e5, which is legal after any
        // possible white first move (e5 and e6 are always empty, no check exists).
        tapSquare(4, 6); tapSquare(4, 4)
        waitForText("e5", timeoutMs = 60_000, substring = true)
        rule.onNodeWithText("\u2039 Home").performClick()
        waitForText("Play as")
        rule.onNodeWithText("White").performClick()      // restore persisted colour
    }

    @Test
    fun stockfishToggleNowVisible_withHonestAvailability() {
        // Policy reversed by the feature round: the engine section is back because
        // Stockfish actually ships now. On x86_64 emulators (placeholder .so) the
        // row must say so instead of offering a switch that does nothing.
        waitForText("Settings")
        rule.onNodeWithText("Settings").performClick()
        waitForText("Sound effects")
        waitForText("Use Stockfish", substring = true)
    }

    @Test
    fun deleteRemovesSavedGame() {
        // Create at least one save: play a quick move vs AI.
        waitForText("Play vs Computer")
        // Suite rule: coordinate-tapping tests self-pin seat+difficulty —
        // settings persist across tests, and a stray Black flips the board
        // under the taps (the deleteAll lesson, applied family-wide).
        rule.onNodeWithText("White").performClick()
        rule.onNodeWithText("2").performClick()
        rule.onNodeWithText("Play vs Computer").performClick()
        waitForText("White to move")
        tapSquare(4, 1); tapSquare(4, 3)
        waitForText("1. e4", timeoutMs = 60_000, substring = true)
        rule.onNodeWithText("\u2039 Home").performClick()
        waitForText("EMERSION")
        rule.onNodeWithText("Saved Games").performClick()
        rule.waitUntil(15_000) {
            rule.onAllNodesWithTag("savedRow").fetchSemanticsNodes().isNotEmpty()
        }
        val before = rule.onAllNodesWithTag("savedRow").fetchSemanticsNodes().size
        rule.onAllNodesWithText("Delete").onFirst().performClick()
        rule.waitUntil(15_000) {
            rule.onAllNodesWithTag("savedRow").fetchSemanticsNodes().size == before - 1 ||
                rule.onAllNodesWithText("No saved games yet.", substring = true)
                    .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun newGameDuringAiThinking_noStrayMoveLands() {
        waitForText("Play as")
        // EXPERT is the slowest thinker: widest window for the race.
        rule.onNodeWithText("10").performClick()
        rule.onNodeWithText("White").performClick()   // seat pin; Expert is the test's point
        rule.onNodeWithText("Play vs Computer").performClick()
        waitForText("White to move")
        tapSquare(4, 1); tapSquare(4, 3)                 // 1. e4
        waitForText("Thinking\u2026", timeoutMs = 60_000)
        rule.onNodeWithText("New game").performClick()    // reset mid-think
        waitForText("Start a new game?")                  // live game -> confirm gate
        clickInDialog("New game")
        waitGone("Thinking\u2026")
        // Give a cancelled/stale coroutine ample time to (incorrectly) land a move.
        Thread.sleep(5_000)
        rule.waitForIdle()
        // Fresh game must be untouched: no moves, White to move.
        assertTrue(rule.onAllNodesWithText("1.", substring = true)
            .fetchSemanticsNodes().isEmpty())
        rule.onNodeWithText("White to move").assertIsDisplayed()
        // Restore a fast difficulty: selection persists across tests by design (#20).
        rule.onNodeWithText("\u2039 Home").performClick()
        waitForText("Play as")
        rule.onNodeWithText("5").performClick()
    }

    @Test
    fun newGameDuringAiSlide_noGhostAnimationSurvivesReset() {
        // Field report: a reset landing during the AI's 220ms reply slide left a
        // ghost piece parked on the fresh board (anim inherited through reset) and
        // froze the tap guard permanently. With the confirm gate, the honest
        // reproduction is: open the dialog during the think, hesitate, confirm as
        // the reply slides underneath. Level 2 thinks near-instantly, so the 550ms
        // pacing makes the slide window predictable: ~[770, 990]ms after the
        // destination tap (own slide 220 + pacing 550 + AI slide 220). Three
        // staggered confirms land inside it; a miss degrades to the guarded
        // think-race, so the race can be missed but never spuriously failed.
        waitForText("Play as")
        rule.onNodeWithText("2").performClick()
        rule.onNodeWithText("White").performClick()   // seat pin (suite rule)
        rule.onNodeWithText("Play vs Computer").performClick()
        waitForText("White to move")
        for (ms in longArrayOf(780, 840, 900)) {
            tapSquare(4, 1); tapSquare(4, 3)          // 1. e4 (board fresh each pass)
            Thread.sleep(350)                          // inside the think/pacing runway
            rule.onNodeWithText("New game").performClick()
            waitForText("Start a new game?")           // dialog over the live board
            Thread.sleep(ms - 350)
            clickInDialog("New game")                  // the confirm IS the race
            // A leaked slide is PERMANENT state — give it ample time to show.
            Thread.sleep(1_500)
            rule.waitForIdle()
            assertTrue("orphaned slide survived reset (attempt ${ms}ms)",
                rule.onAllNodesWithContentDescription("Chess board, piece moving")
                    .fetchSemanticsNodes().isEmpty())
            rule.onNodeWithText("White to move").assertIsDisplayed()
            assertTrue(rule.onAllNodesWithText("1.", substring = true)
                .fetchSemanticsNodes().isEmpty())
        }
        // The decisive user-level probe: a leaked animation rejects every tap and
        // re-survives each further New game via the old defaults — so after all
        // three races the board must still accept a move.
        tapSquare(4, 1); tapSquare(4, 3)
        waitForText("1. e4", timeoutMs = 60_000, substring = true)
    }
}
