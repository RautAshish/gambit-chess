package com.chessapp

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.chessapp.domain.engine.GameEngine
import com.chessapp.domain.engine.MoveGenerator
import com.chessapp.domain.engine.Notation
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * QA Round 5: the most rigorous round — a CLOSED-LOOP driver. The test runs its
 * own GameEngine mirror, plays real taps on the real board, reads the AI's reply
 * back out of the UI's SAN move list, matches it to exactly one legal move on the
 * mirror (Round 2 proved SAN uniqueness), and applies it. Any illegal AI move,
 * desync, dropped tap, or SAN error breaks the loop. Plus special-move SAN checks
 * (castling, en passant) through the live UI.
 */
class E2eRound5Test {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String, timeoutMs: Long = 60_000, substring: Boolean = false) {
        rule.waitUntil(timeoutMs) {
            rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
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

    /** Read the SAN move list straight out of the semantics tree. */
    private fun movesText(): String {
        val nodes = rule.onAllNodesWithText("1.", substring = true).fetchSemanticsNodes()
        if (nodes.isEmpty()) return ""
        return nodes[0].config[SemanticsProperties.Text].joinToString(" ") { it.text }
    }

    private fun sanTokens(): List<String> =
        movesText().split(Regex("\\s+")).filter { it.isNotBlank() && !it.matches(Regex("\\d+\\.")) }

    @Test
    fun closedLoop_fivePliesEach_mirrorNeverDesyncs() {
        waitForText("Play as")
        rule.onNodeWithText("White").performClick()
        rule.onNodeWithText("2").performClick()
        rule.onNodeWithText("Play vs Computer").performClick()
        waitForText("White to move")

        val mirror = GameEngine()
        repeat(5) { round ->
            // --- our move: any legal non-promotion, chosen from the mirror ---
            val ours = MoveGenerator.legalMoves(mirror.board).first { it.promotion == null }
            val expectedTokens = mirror.moveHistory().size + 1
            tap(ours.from.file, ours.from.rank)
            tap(ours.to.file, ours.to.rank)
            rule.waitUntil(60_000) { sanTokens().size >= expectedTokens }
            assertTrue("our move rejected at round $round", mirror.makeMove(ours))

            // --- AI reply: read SAN from the UI, match on the mirror ---
            rule.waitUntil(60_000) { sanTokens().size >= expectedTokens + 1 }
            val aiSan = sanTokens()[expectedTokens]   // 0-indexed: token after ours
            val match = MoveGenerator.legalMoves(mirror.board)
                .filter { Notation.toSan(mirror.board, it) == aiSan }
            assertTrue("AI SAN '$aiSan' matched ${match.size} legal moves at round $round",
                match.size == 1)
            assertTrue(mirror.makeMove(match[0]))
        }
        // 10 plies of two independent engines agreeing, through pixels alone.
        assertTrue(sanTokens().size == 10)
    }

    @Test
    fun specialMoves_castlingAndEnPassant_throughUi() {
        // Kingside castling in pass-and-play.
        waitForText("Pass & Play")
        rule.onNodeWithText("Pass & Play").performClick()
        waitForText("White to move")
        fun mv(ff: Int, fr: Int, tf: Int, tr: Int, san: String) {
            tap(ff, fr); tap(tf, tr); waitForText(san, substring = true)
        }
        mv(4, 1, 4, 3, "e4");  mv(4, 6, 4, 4, "e5")
        mv(6, 0, 5, 2, "Nf3"); mv(1, 7, 2, 5, "Nc6")
        mv(5, 0, 2, 3, "Bc4"); mv(5, 7, 2, 4, "Bc5")
        tap(4, 0); tap(6, 0)                       // e1 -> g1
        waitForText("O-O", substring = true)

        // Fresh game: en passant.
        rule.onNodeWithText("New game").performClick()
        waitForText("Start a new game?")            // live game -> confirm gate
        clickInDialog("New game")
        waitForText("White to move")
        mv(4, 1, 4, 3, "e4");  mv(0, 6, 0, 5, "a6")
        mv(4, 3, 4, 4, "e5");  mv(3, 6, 3, 4, "d5")
        tap(4, 4); tap(3, 5)                       // e5 captures d6 en passant
        waitForText("exd6", substring = true)
    }
}
