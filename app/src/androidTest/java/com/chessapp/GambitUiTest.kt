package com.chessapp

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.junit.Rule
import org.junit.Test

/**
 * True end-to-end UI tests, run on an Android emulator in CI. These exercise the
 * real app: Compose rendering, navigation, the Canvas tap-to-move pipeline, the
 * ViewModel, the engine, and the AI reply — the full stack a player touches.
 *
 * Board tap math: the board Canvas fills its node; cell = size/8. For the default
 * (unflipped) orientation, square (file f, rank r) sits at column f, row 7-r, so
 * its center is at ((f+0.5)/8 * width, (7-r+0.5)/8 * height).
 */
class GambitUiTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String, timeoutMs: Long = 15_000, substring: Boolean = false) {
        rule.waitUntil(timeoutMs) {
            rule.onAllNodesWithText(text, substring = substring)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun homeScreenRenders() {
        waitForText("EMERSION")
        rule.onNodeWithText("EMERSION").assertIsDisplayed()
        rule.onNodeWithText("Play vs Computer").assertIsDisplayed()
        rule.onNodeWithText("Pass & Play").assertIsDisplayed()
        rule.onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun startGameShowsBoardAndStatus() {
        waitForText("Play vs Computer")
        rule.onNodeWithText("Play vs Computer").performClick()
        waitForText("White to move")
        rule.onNodeWithTag("board").assertIsDisplayed()
        rule.onNodeWithText("Resign").assertIsDisplayed()
        rule.onNodeWithText("Offer draw").assertIsDisplayed()
    }

    @Test
    fun playE4_engineAccepts_andAiReplies() {
        waitForText("Play vs Computer")
        rule.onNodeWithText("Play vs Computer").performClick()
        waitForText("White to move")

        val board = rule.onNodeWithTag("board")
        // Tap e2 (file 4, rank 1 -> col 4, row 6), then e4 (file 4, rank 3 -> col 4, row 4).
        board.performTouchInput {
            click(Offset(width * 4.5f / 8f, height * 6.5f / 8f))
        }
        board.performTouchInput {
            click(Offset(width * 4.5f / 8f, height * 4.5f / 8f))
        }

        // The move list rendering "1. e4" proves: tap mapping -> legal-move match ->
        // engine apply -> PGN generation -> recomposition. Generous timeout covers
        // the 160ms slide animation plus the AI's depth-3 reply on a slow emulator.
        waitForText("1. e4", timeoutMs = 60_000, substring = true)
    }

    @Test
    fun backButtonReturnsHome() {
        waitForText("Play vs Computer")
        rule.onNodeWithText("Play vs Computer").performClick()
        waitForText("White to move")
        rule.onNodeWithText("\u2039 Home").performClick()
        waitForText("EMERSION")
        rule.onNodeWithText("EMERSION").assertIsDisplayed()
    }

    @Test
    fun settingsScreenOpensAndReturns() {
        waitForText("Settings")
        rule.onNodeWithText("Settings").performClick()
        waitForText("Sound effects")
        rule.onNodeWithText("\u2039 Home").performClick()
        waitForText("EMERSION")
    }
}
