package com.chessapp

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.junit.Rule
import org.junit.Test

/**
 * QA Round 3: on-device coverage for review fixes that previously lacked a direct
 * test, plus regressions for the two bugs found in the post-review audit:
 *  - Bug A: a settings write mid-think wiped live state (thinking/promotion).
 *  - Bug B: Undo/Redo stayed enabled after the game was decided.
 */
class E2eRound3Test {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String, timeoutMs: Long = 40_000, substring: Boolean = false) {
        rule.waitUntil(timeoutMs) {
            rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitGone(text: String, timeoutMs: Long = 40_000, substring: Boolean = false) {
        rule.waitUntil(timeoutMs) {
            rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun tap(file: Int, rank: Int) {
        rule.onNodeWithTag("board").performTouchInput {
            click(Offset(width * (file + 0.5f) / 8f, height * ((7 - rank) + 0.5f) / 8f))
        }
    }

    private fun clickInDialog(text: String) {
        rule.onNode(hasText(text) and hasAnyAncestor(isDialog())).performClick()
    }

    /** #9: the colour choice must survive leaving and re-entering Home. */
    @Test
    fun playAsBlack_survivesNavigation() {
        waitForText("Play as")
        rule.onNodeWithText("2").performClick()       // pin fast AI
        rule.onNodeWithText("Black").performClick()
        rule.onNodeWithText("Play vs Computer").performClick()
        waitForText("Black to move", timeoutMs = 60_000)
        rule.onNodeWithText("\u2039 Home").performClick()
        waitForText("Play as")
        // Re-enter WITHOUT touching the colour buttons: the AI must again move first.
        rule.onNodeWithText("Play vs Computer").performClick()
        waitForText("Black to move", timeoutMs = 60_000)
        waitForText("1.", timeoutMs = 60_000, substring = true)
        // Restore White: the colour choice persists across games by design now.
        rule.onNodeWithText("\u2039 Home").performClick()
        waitForText("Play as")
        rule.onNodeWithText("White").performClick()
    }

    /** #11: in-game Mute flips the persisted setting (visible in Settings). */
    @Test
    fun muteButton_togglesAndPersistsToSettings() {
        waitForText("Play vs Computer")
        rule.onNodeWithText("Play vs Computer").performClick()
        waitForText("White to move")
        rule.onNodeWithText("Mute").performClick()
        waitForText("Unmute")
        rule.onNodeWithText("\u2039 Home").performClick()
        waitForText("Settings")
        rule.onNodeWithText("Settings").performClick()
        waitForText("Sound effects")
        // Toggle order: legal-moves, flip, SOUND, haptics.
        rule.onAllNodes(isToggleable())[2].assertIsOff()
        rule.onAllNodes(isToggleable())[2].performClick()    // restore for other tests
        rule.waitUntil(10_000) {
            rule.onAllNodes(isToggleable()).fetchSemanticsNodes()[2]
                .config[androidx.compose.ui.semantics.SemanticsProperties.ToggleableState] ==
                androidx.compose.ui.state.ToggleableState.On
        }
    }

    /** #3: switching board theme produces a playable, rendering board. */
    @Test
    fun boardTheme_walnutGameRendersAndPlays() {
        waitForText("Settings")
        rule.onNodeWithText("Settings").performClick()
        waitForText("Walnut")
        rule.onNodeWithText("Walnut").performClick()
        rule.onNodeWithText("\u2039 Home").performClick()
        waitForText("Play vs Computer")
        rule.onNodeWithText("Play vs Computer").performClick()
        waitForText("White to move")
        rule.onNodeWithTag("board").assertIsDisplayed()
        tap(4, 1); tap(4, 3)
        waitForText("1. e4", timeoutMs = 60_000, substring = true)
        // Restore Classic so visual baseline is stable for other tests.
        rule.onNodeWithText("\u2039 Home").performClick()
        waitForText("Settings")
        rule.onNodeWithText("Settings").performClick()
        waitForText("Classic")
        rule.onNodeWithText("Classic").performClick()
    }

    /** #21: Delete all (with confirm) empties the list. */
    @Test
    fun deleteAll_clearsSavedGames() {
        // Ensure at least one save exists. Pin seat + difficulty first:
        // settings persist ACROSS tests, so an earlier test leaving Black
        // selected flips the board and the coordinate taps below hit air
        // (root cause of this test's intermittent 60s timeout).
        waitForText("Play vs Computer")
        rule.onNodeWithText("White").performClick()
        rule.onNodeWithText("2").performClick()
        rule.onNodeWithText("Play vs Computer").performClick()
        waitForText("White to move")
        tap(4, 1); tap(4, 3)
        waitForText("1. e4", timeoutMs = 60_000, substring = true)
        rule.onNodeWithText("\u2039 Home").performClick()
        waitForText("Saved Games")
        rule.onNodeWithText("Saved Games").performClick()
        rule.waitUntil(15_000) {
            rule.onAllNodesWithTag("savedRow").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Delete all").performClick()      // header button (dialog not open yet)
        waitForText("Delete all saved games?")
        clickInDialog("Delete all")
        waitForText("No saved games yet.", substring = true)
    }

    /** Bug A regression: Mute pressed DURING the AI's think must not wipe state. */
    @Test
    fun muteDuringAiThink_doesNotCorruptState() {
        waitForText("Play as")
        rule.onNodeWithText("10").performClick()
        rule.onNodeWithText("Play vs Computer").performClick()
        waitForText("White to move")
        tap(4, 1); tap(4, 3)
        waitForText("Thinking\u2026", timeoutMs = 60_000)
        rule.onNodeWithText("Mute").performClick()            // settings write mid-think
        // Pre-fix this snapshot reset thinking=false; the indicator must survive.
        rule.onNodeWithText("Thinking\u2026").assertIsDisplayed()
        waitGone("Thinking\u2026", timeoutMs = 90_000)
        waitForText("White to move", timeoutMs = 90_000)
        // Board still fully functional afterwards.
        tap(3, 1); tap(3, 3)
        waitForText("d4", timeoutMs = 60_000, substring = true)
        rule.onNodeWithText("Unmute").performClick()          // restore sound
        // Restore a fast difficulty for subsequent tests.
        rule.onNodeWithText("\u2039 Home").performClick()
        waitForText("Play as")
        rule.onNodeWithText("5").performClick()
    }

    /** Bug B regression: a decided game locks Undo/Redo. */
    @Test
    fun undoRedoDisabled_afterGameOver() {
        waitForText("Play vs Computer")
        // The last unpinned coordinate-tapper: the cold-start bug used to hide
        // persisted state from every test; once Home became honest (8f7d609),
        // the Black seat persisted by an earlier test flipped this board.
        rule.onNodeWithText("White").performClick()
        rule.onNodeWithText("2").performClick()
        rule.onNodeWithText("Play vs Computer").performClick()
        waitForText("White to move")
        tap(4, 1); tap(4, 3)
        waitForText("1. e4", timeoutMs = 60_000, substring = true)
        waitGone("Thinking\u2026", timeoutMs = 60_000)
        rule.onNodeWithText("Resign").performClick()
        waitForText("Resign?")
        clickInDialog("Resign")
        waitForText("View board")
        rule.onNodeWithText("View board").performClick()
        waitForText("by resignation", substring = true)
        rule.onNodeWithText("Undo").assertIsNotEnabled()
        rule.onNodeWithText("Redo").assertIsNotEnabled()
    }
}
