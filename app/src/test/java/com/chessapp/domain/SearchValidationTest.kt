package com.chessapp.domain

import com.chessapp.domain.ai.ChessAI
import com.chessapp.domain.ai.Evaluator
import com.chessapp.domain.engine.MoveGenerator
import com.chessapp.domain.model.Board
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Proves the alpha-beta search returns the same value as an unpruned minimax —
 * the strongest correctness guarantee for pruning. Also locks in mate preference.
 */
class SearchValidationTest {

    private val MATE = 1_000_000

    private fun plainNegamax(b: Board, depth: Int, persp: Int): Int {
        val moves = MoveGenerator.legalMoves(b)
        if (moves.isEmpty()) {
            val k = b.kingSquare(b.sideToMove)
            val chk = k != null && MoveGenerator.isSquareAttacked(b, k, b.sideToMove.opposite())
            return if (chk) -(MATE + depth) else 0
        }
        if (depth == 0) return persp * Evaluator.evaluate(b)
        var best = -MATE * 2
        for (m in moves) {
            val s = -plainNegamax(b.apply(m), depth - 1, -persp)
            if (s > best) best = s
        }
        return best
    }

    private fun plainBestScore(b: Board, depth: Int): Int {
        val persp = if (b.sideToMove == com.chessapp.domain.model.Color.WHITE) 1 else -1
        var bs = Int.MIN_VALUE
        for (m in MoveGenerator.legalMoves(b)) {
            val s = -plainNegamax(b.apply(m), depth - 1, -persp)
            if (s > bs) bs = s
        }
        return bs
    }

    @Test fun alphaBetaEqualsPlainMinimax() {
        val fens = listOf(
            Board.START_FEN,
            "r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4",
            "6k1/5ppp/8/8/8/8/8/4R1K1 w - - 0 1"
        )
        for (fen in fens) {
            val b = Board.fromFen(fen)
            val plain = plainBestScore(b, 3)
            val ai = ChessAI(ChessAI.Difficulty.MEDIUM)   // depth 3
            val move = ai.bestMove(b)
            assertNotNull(move)
            val persp = if (b.sideToMove == com.chessapp.domain.model.Color.WHITE) 1 else -1
            val aiScore = -plainNegamax(b.apply(move!!), 2, -persp)
            assertEquals("score parity for $fen", plain, aiScore)
        }
    }

    @Test fun prefersImmediateMate() {
        val b = Board.fromFen("6k1/5ppp/8/8/8/8/8/4R1K1 w - - 0 1")
        assertEquals("e1e8", ChessAI(ChessAI.Difficulty.HARD).bestMove(b)?.toUci())
    }
}
