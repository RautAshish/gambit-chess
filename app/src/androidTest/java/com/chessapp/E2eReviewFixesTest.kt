package com.chessapp

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** On-device regressions for the product-review fixes. */
class E2eReviewFixesTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String, timeoutMs: Long = 40_000, substring: Boolean = false) {
        rule.waitUntil(timeoutMs) {
            rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun tap(file: Int, rank: Int, flipped: Boolean = false) {
        rule.onNodeWithTag("board").performTouchInput {
            val col = if (flipped) 7 - file else file
            val row = if (flipped) rank else 7 - rank
            click(Offset(width * (col + 0.5f) / 8f, height * (row + 0.5f) / 8f))
        }
    }

    /** #13: playing Black, undoing the AI's opening move must make the AI move again. */
    @Test
    fun undoAsBlack_aiRestartsPlay() {
        waitForText("Play as")
        rule.onNodeWithText("2").performClick()       // pin fast AI: suite-order independent
        rule.onNodeWithText("Black").performClick()
        rule.onNodeWithText("Play vs Computer").performClick()
        waitForText("Black to move", timeoutMs = 60_000)
        waitForText("1.", substring = true)
        rule.onNodeWithText("Undo").performClick()
        // The AI must take its turn again rather than leaving the game stuck.
        waitForText("Black to move", timeoutMs = 60_000)
        waitForText("1.", timeoutMs = 60_000, substring = true)
        // And the human can still answer.
        tap(4, 6); tap(4, 4)
        waitForText("e5", timeoutMs = 60_000, substring = true)
        rule.onNodeWithText("\u2039 Home").performClick()
        waitForText("Play as")
        rule.onNodeWithText("White").performClick()      // restore persisted colour
    }

    /** #10: with "Flip board for black" on, a Black game is oriented black-at-bottom. */
    @Test
    fun flipBoardForBlack_orientsAndAcceptsMoves() {
        waitForText("Settings")
        rule.onNodeWithText("Settings").performClick()
        waitForText("Flip board for black")
        rule.onAllNodes(isToggleable())[1].performClick()    // row order: legal, flip, sound, haptics
        rule.onNodeWithText("\u2039 Home").performClick()
        waitForText("Play as")
        rule.onNodeWithText("2").performClick()       // pin fast AI: suite-order independent
        rule.onNodeWithText("Black").performClick()
        rule.onNodeWithText("Play vs Computer").performClick()
        waitForText("Black to move", timeoutMs = 60_000)
        // Tap e7 -> e5 using FLIPPED screen coordinates; if orientation were wrong
        // this would hit the wrong squares and no "e5" would ever appear.
        tap(4, 6, flipped = true); tap(4, 4, flipped = true)
        waitForText("e5", timeoutMs = 60_000, substring = true)
        // Restore colour AND flip so other tests (unflipped math) are unaffected.
        rule.onNodeWithText("\u2039 Home").performClick()
        waitForText("Play as")
        rule.onNodeWithText("White").performClick()
        waitForText("Settings")
        rule.onNodeWithText("Settings").performClick()
        waitForText("Flip board for black")
        rule.onAllNodes(isToggleable())[1].performClick()
    }

    /** #7: the clock picker explains the selected control in plain language. */
    @Test
    fun clockDescriptionShown() {
        waitForText("Settings")
        rule.onNodeWithText("Settings").performClick()
        waitForText("CLOCK")
        assertTrue(
            rule.onAllNodesWithText("per move", substring = true).fetchSemanticsNodes().isNotEmpty() ||
            rule.onAllNodesWithText("minute", substring = true).fetchSemanticsNodes().isNotEmpty()
        )
    }

    /** #18: the game-over dialog can be dismissed to inspect the final position. */
    @Test
    fun gameOverDialog_viewBoardDismisses() {
        waitForText("Play vs Computer")
        rule.onNodeWithText("Play vs Computer").performClick()
        waitForText("White to move")
        rule.onNodeWithText("Resign").performClick()
        waitForText("Resign?")
        rule.onNode(hasText("Resign") and hasAnyAncestor(isDialog())).performClick()
        waitForText("View board")
        rule.onNodeWithText("View board").performClick()
        rule.waitUntil(15_000) {
            rule.onAllNodesWithText("View board").fetchSemanticsNodes().isEmpty()
        }
        // Status line carries the result (substring: full text is "Black wins \u00B7 by resignation")
        waitForText("by resignation", substring = true)
        // The board is in a scrollable column; scroll it into view, then assert.
        rule.onNodeWithTag("board").performScrollTo().assertIsDisplayed()
    }
}
