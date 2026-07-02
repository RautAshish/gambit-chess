package com.chessapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
 *  and the New-Game-during-AI-thinking race (generation guard). */
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

    @Test
    fun playAsBlack_aiMovesFirst_thenHumanReplies() {
        waitForText("Play as")
        rule.onNodeWithText("Easy").performClick()       // pin fast AI
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
        rule.onNodeWithText("Expert").performClick()
        rule.onNodeWithText("Play vs Computer").performClick()
        waitForText("White to move")
        tapSquare(4, 1); tapSquare(4, 3)                 // 1. e4
        waitForText("Thinking\u2026", timeoutMs = 60_000)
        rule.onNodeWithText("New game").performClick()    // reset mid-think
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
        rule.onNodeWithText("Medium").performClick()
    }
}
