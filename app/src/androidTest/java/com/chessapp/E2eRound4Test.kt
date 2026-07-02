package com.chessapp

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** QA Round 4: adversarial input — spam, double-taps, dead-state interaction,
 *  activity recreation, navigation thrash. The app must shrug all of it off. */
class E2eRound4Test {

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

    /** Button mashing during the AI's longest think must leave a coherent state. */
    @Test
    fun inputStorm_duringExpertThink() {
        waitForText("Play as")
        rule.onNodeWithText("Expert").performClick()
        rule.onNodeWithText("Play vs Computer").performClick()
        waitForText("White to move")
        tap(4, 1); tap(4, 3)
        waitForText("Thinking\u2026", timeoutMs = 60_000)
        repeat(3) { rule.onNodeWithText("Undo").performClick() }
        repeat(3) { rule.onNodeWithText("Redo").performClick() }
        repeat(4) { tap(3, 3); tap(5, 4) }
        rule.onNodeWithText("New game").performClick()
        waitGone("Thinking\u2026")
        Thread.sleep(4_000)               // let any zombie coroutine try to land
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithText("1.", substring = true).fetchSemanticsNodes().isEmpty())
        rule.onNodeWithText("White to move").assertIsDisplayed()
        rule.onNodeWithText("\u2039 Home").performClick()
        waitForText("Play as")
        rule.onNodeWithText("Medium").performClick()      // restore fast difficulty
    }

    /** Double-tapping a piece selects then deselects; no phantom move. */
    @Test
    fun doubleTap_deselects_thenNormalMoveWorks() {
        waitForText("Pass & Play")
        rule.onNodeWithText("Pass & Play").performClick()
        waitForText("White to move")
        tap(4, 1); tap(4, 1)              // select e2, tap again -> deselect
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithText("1.", substring = true).fetchSemanticsNodes().isEmpty())
        tap(4, 1); tap(4, 3)
        waitForText("1. e4", substring = true)
    }

    /** Random rapid tap storm must never crash or wedge the board. */
    @Test
    fun tapStorm_neverWedges() {
        waitForText("Pass & Play")
        rule.onNodeWithText("Pass & Play").performClick()
        waitForText("White to move")
        val squares = listOf(0 to 0, 7 to 7, 4 to 1, 4 to 3, 3 to 6, 3 to 4, 2 to 0, 5 to 7, 4 to 4, 1 to 0)
        repeat(2) { for ((f, r) in squares) tap(f, r) }
        rule.waitForIdle()
        // Whatever was or wasn't played, the game must still be live and responsive.
        assertTrue(
            rule.onAllNodesWithText("to move", substring = true).fetchSemanticsNodes().isNotEmpty()
        )
        rule.onNodeWithTag("board").assertIsDisplayed()
        rule.onNodeWithText("New game").performClick()
        waitForText("White to move")
    }

    /** A decided game ignores board taps entirely. */
    @Test
    fun gameOver_boardTapsIgnored() {
        waitForText("Play vs Computer")
        rule.onNodeWithText("Play vs Computer").performClick()
        waitForText("White to move")
        rule.onNodeWithText("Resign").performClick()
        waitForText("Resign?")
        clickInDialog("Resign")
        waitForText("View board")
        rule.onNodeWithText("View board").performClick()
        waitForText("by resignation", substring = true)
        repeat(4) { tap(4, 1); tap(4, 3) }
        rule.waitForIdle()
        assertTrue(rule.onAllNodesWithText("1.", substring = true).fetchSemanticsNodes().isEmpty())
        waitForText("by resignation", substring = true)   // result untouched
    }

    /** Activity recreation (rotation/low-memory model): game is recoverable. */
    @Test
    fun activityRecreation_gameRecoverableFromSaves() {
        waitForText("Play vs Computer")
        rule.onNodeWithText("Play vs Computer").performClick()
        waitForText("White to move")
        tap(4, 1); tap(4, 3)
        waitForText("1. e4", timeoutMs = 60_000, substring = true)
        waitGone("Thinking\u2026", timeoutMs = 60_000)
        rule.activityRule.scenario.recreate()
        // Current design: nav state is in-memory, so recreation lands on Home —
        // the autosaved game must be one tap away.
        waitForText("EMERSION")
        rule.onNodeWithText("Saved Games").performClick()
        rule.waitUntil(15_000) {
            rule.onAllNodesWithTag("savedRow").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithTag("savedRow").onFirst().performClick()
        waitForText("e4", timeoutMs = 30_000, substring = true)
    }

    /** Fast navigation thrash leaves every screen intact. */
    @Test
    fun navigationThrash_screensStayIntact() {
        repeat(3) {
            waitForText("Settings")
            rule.onNodeWithText("Settings").performClick()
            waitForText("Sound effects")
            rule.onNodeWithText("\u2039 Home").performClick()
        }
        repeat(3) {
            waitForText("Saved Games")
            rule.onNodeWithText("Saved Games").performClick()
            waitForText("Saved Games")
            rule.onNodeWithText("\u2039 Home").performClick()
        }
        repeat(2) {
            waitForText("Play vs Computer")
            rule.onNodeWithText("Play vs Computer").performClick()
            waitForText("White to move")
            rule.onNodeWithText("\u2039 Home").performClick()
        }
        waitForText("EMERSION")
        rule.onNodeWithText("Play as").assertIsDisplayed()
    }
}
