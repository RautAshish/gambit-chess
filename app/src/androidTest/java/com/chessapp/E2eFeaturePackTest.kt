package com.chessapp

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.junit.Rule
import org.junit.Test

/** On-device coverage for the feature round: Puzzles, Online (unconfigured path),
 *  and the Stockfish engine section incl. the placeholder-ABI fallback. */
class E2eFeaturePackTest {

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

    /** Solves the first curated puzzle (back-rank Re8#) through real taps. */
    @Test
    fun puzzles_solveFirstMateInOne() {
        waitForText("Puzzles")
        rule.onNodeWithText("Puzzles").performClick()
        waitForText("mate in 1", substring = true)
        tap(4, 0); tap(4, 7)                       // Re1 -> e8#
        waitForText("Solved!")
        rule.onNodeWithText("Next puzzle").assertIsDisplayed()
    }

    /** A wrong move prompts retry; Retry resets cleanly. Order-independent:
     *  Kg1-f1 is legal-but-wrong on BOTH curated mate-in-1 puzzles, whichever
     *  one the progress-resume logic lands on. */
    @Test
    fun puzzles_wrongMoveThenRetryResets() {
        waitForText("Puzzles")
        rule.onNodeWithText("Puzzles").performClick()
        waitForText("mate in 1", substring = true)
        tap(6, 0); tap(5, 0)                       // Kg1-f1: legal, never the mate
        waitForText("try again", substring = true)
        rule.onNodeWithText("Retry").performClick()
        waitForText("find mate in 1", substring = true)   // message cleared, prompt back
        rule.onNodeWithText("Skip").assertIsDisplayed()   // board controls live again
    }

    /** Without Firebase config, Online explains setup and links to Settings. */
    @Test
    fun online_unconfiguredShowsSetupPath() {
        waitForText("Play Online")
        rule.onNodeWithText("Play Online").performClick()
        waitForText("One-time setup needed")
        rule.onNodeWithText("Open Settings").performClick()
        waitForText("Sound effects")
    }

    /** Engine section is present; on x86_64 emulators the placeholder .so must be
     *  treated as absent (size guard), so the fallback wording shows. */
    @Test
    fun engineSection_showsFallbackOnEmulator() {
        waitForText("Settings")
        rule.onNodeWithText("Settings").performClick()
        waitForText("Use Stockfish", substring = true)
        waitForText("not bundled on this device", substring = true)
        waitForText("built-in engine is used", substring = true)
    }
}
