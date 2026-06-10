package com.chessapp

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
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
import org.junit.Rule
import org.junit.Test

/**
 * Full end-to-end scenario suite, run on a real emulator. Every interactive flow
 * is exercised through actual clicks: board taps, dialogs, resign/draw, promotion,
 * undo/redo, pass-and-play, resume, navigation. Buttons that also appear inside
 * dialogs ("Resign", "New game") are disambiguated with an isDialog ancestor match.
 */
class E2eScenariosTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    // ---------- helpers ----------

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

    /** Tap the centre of (file, rank), 0-indexed, default unflipped orientation. */
    private fun tapSquare(file: Int, rank: Int) {
        rule.onNodeWithTag("board").performTouchInput {
            click(Offset(width * (file + 0.5f) / 8f, height * ((7 - rank) + 0.5f) / 8f))
        }
    }

    private fun move(fromFile: Int, fromRank: Int, toFile: Int, toRank: Int, expectSan: String) {
        tapSquare(fromFile, fromRank)
        tapSquare(toFile, toRank)
        waitForText(expectSan, substring = true)
    }

    private fun clickInDialog(text: String) {
        rule.onNode(hasText(text) and hasAnyAncestor(isDialog())).performClick()
    }

    private fun startVsAi() {
        waitForText("Play vs Computer")
        rule.onNodeWithText("Play vs Computer").performClick()
        waitForText("White to move")
    }

    private fun startPassAndPlay() {
        waitForText("Pass & Play")
        rule.onNodeWithText("Pass & Play").performClick()
        waitForText("White to move")
    }

    /** Wait until the AI's reply has landed and it is the human's turn again. */
    private fun waitAiReplied() {
        waitGone("Thinking\u2026", timeoutMs = 60_000)
        waitForText("White to move", timeoutMs = 60_000)
    }

    // ---------- scenarios ----------

    @Test
    fun illegalTargetClearsSelection_thenLegalMoveWorks() {
        startVsAi()
        // e2 selected, e5 is not a legal pawn target: selection must clear, no move.
        tapSquare(4, 1); tapSquare(4, 4)
        org.junit.Assert.assertTrue(
            rule.onAllNodesWithText("1.", substring = true).fetchSemanticsNodes().isEmpty()
        )
        // Pipeline must have recovered: a normal move still works.
        move(4, 1, 4, 3, "1. e4")
    }

    @Test
    fun undoRollsBackBothPlies_redoReapplies() {
        startVsAi()
        move(4, 1, 4, 3, "1. e4")
        waitAiReplied()
        rule.onNodeWithText("Undo").performClick()
        waitGone("1.", substring = true)               // move list back to empty
        rule.onNodeWithText("Undo").assertIsNotEnabled()
        rule.onNodeWithText("Redo").performClick()
        waitForText("e4", substring = true)            // redo re-applies the move
    }

    @Test
    fun passAndPlay_bothColorsMoveAlternately() {
        startPassAndPlay()
        move(4, 1, 4, 3, "e4")                          // white
        waitForText("Black to move")
        move(3, 6, 3, 4, "d5")                          // black moves too
        waitForText("White to move")
    }

    @Test
    fun resignShowsConfirm_thenGameOver_thenNewGameResets() {
        startVsAi()
        rule.onNodeWithText("Resign").performClick()
        waitForText("Resign?")
        rule.onNodeWithText("This ends the game as a loss.").assertIsDisplayed()
        clickInDialog("Resign")
        waitForText("by resignation")
        rule.onNodeWithText("Black wins").assertIsDisplayed()
        clickInDialog("New game")
        waitGone("by resignation")
        waitForText("White to move")
    }

    @Test
    fun resignCancelKeepsPlaying() {
        startVsAi()
        rule.onNodeWithText("Resign").performClick()
        waitForText("Resign?")
        rule.onNodeWithText("Cancel").performClick()
        waitGone("Resign?")
        move(4, 1, 4, 3, "1. e4")                       // game still live
    }

    @Test
    fun drawOfferVsAi_acceptedWhenBalanced() {
        startVsAi()
        rule.onNodeWithText("Offer draw").performClick()
        // Material is level at the start, so the AI's policy accepts.
        waitForText("by agreement")
        rule.onNodeWithText("Draw").assertIsDisplayed()
    }

    @Test
    fun drawOfferPassAndPlay_declineThenAccept() {
        startPassAndPlay()
        rule.onNodeWithText("Offer draw").performClick()
        waitForText("Draw offered")
        rule.onNodeWithText("Decline").performClick()
        waitGone("Draw offered")
        waitForText("White to move")                    // game continues
        rule.onNodeWithText("Offer draw").performClick()
        waitForText("Draw offered")
        rule.onNodeWithText("Accept").performClick()
        waitForText("by agreement")
    }

    @Test
    fun promotionDialog_underpromoteToRook() {
        startPassAndPlay()
        move(0, 1, 0, 3, "a4")                          // 1. a4
        move(1, 6, 1, 4, "b5")                          // 1... b5
        move(0, 3, 1, 4, "axb5")                        // 2. axb5
        move(0, 6, 0, 5, "a6")                          // 2... a6
        move(1, 4, 0, 5, "bxa6")                        // 3. bxa6
        move(1, 7, 2, 5, "Nc6")                         // 3... Nc6
        move(0, 5, 0, 6, "a7")                          // 4. a7
        move(2, 5, 1, 7, "Nb8")                         // 4... Nb8
        tapSquare(0, 6); tapSquare(1, 7)                // 5. axb8 -> defer for choice
        waitForText("Promote to")
        rule.onNodeWithTag("promote-ROOK").performClick()
        waitForText("=R", substring = true)             // SAN axb8=R confirms rook chosen
    }

    @Test
    fun checkmate_scholarsMate_endsGameWithDialog() {
        startPassAndPlay()
        move(4, 1, 4, 3, "e4")
        move(4, 6, 4, 4, "e5")
        move(5, 0, 2, 3, "Bc4")
        move(1, 7, 2, 5, "Nc6")
        move(3, 0, 7, 4, "Qh5")
        move(6, 7, 5, 5, "Nf6")
        tapSquare(7, 4); tapSquare(5, 6)                // Qxf7#
        waitForText("by checkmate")
        rule.onNodeWithText("White wins").assertIsDisplayed()
        clickInDialog("New game")
        waitForText("White to move")
    }

    @Test
    fun savedGameResume_restoresMoves() {
        startVsAi()
        move(4, 1, 4, 3, "1. e4")
        waitAiReplied()                                  // ensure autosave has 2 plies
        rule.onNodeWithText("\u2039 Home").performClick()
        waitForText("GAMBIT")
        rule.onNodeWithText("Saved Games").performClick()
        rule.waitUntil(15_000) {
            rule.onAllNodesWithTag("savedRow").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onAllNodesWithTag("savedRow").onFirst().performClick()
        waitForText("e4", timeoutMs = 30_000, substring = true)   // history replayed
        rule.onNodeWithTag("board").assertIsDisplayed()
    }

    @Test
    fun allDifficultiesStartAGame() {
        for (d in listOf("Easy", "Medium", "Hard", "Expert")) {
            waitForText("GAMBIT")
            rule.onNodeWithText(d).performClick()
            waitForText("White to move")
            rule.onNodeWithText("\u2039 Home").performClick()
        }
        waitForText("GAMBIT")
    }

    @Test
    fun settingsShowsAllControls() {
        waitForText("Settings")
        rule.onNodeWithText("Settings").performClick()
        waitForText("Sound effects")
        for (label in listOf(
            "Show legal moves", "Flip board for black", "Sound effects", "Haptics", "Clock"
        )) {
            rule.onAllNodesWithText(label, substring = true).onFirst().assertExists()
        }
        rule.onNodeWithText("Back").performClick()
        waitForText("GAMBIT")
    }
}
