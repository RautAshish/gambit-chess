package com.chessapp.domain.ai

import com.chessapp.domain.engine.MoveGenerator
import com.chessapp.domain.model.*

/**
 * Negamax search with alpha-beta pruning. Difficulty maps to search depth.
 * Designed to run off the main thread (call from a coroutine on Dispatchers.Default).
 */
class ChessAI(val difficulty: Difficulty = Difficulty.MEDIUM) {

    enum class Difficulty(val depth: Int) {
        EASY(2), MEDIUM(3), HARD(4), EXPERT(5)
    }

    private val mateScore = 1_000_000
    private var nodes = 0L

    /** Returns the best move for the side to move, or null if none exist. */
    fun bestMove(board: Board): Move? {
        nodes = 0
        val moves = orderMoves(board, MoveGenerator.legalMoves(board))
        if (moves.isEmpty()) return null

        val sign = if (board.sideToMove == Color.WHITE) 1 else -1
        var best: Move? = null
        var bestScore = Int.MIN_VALUE
        var alpha = -mateScore * 2
        val beta = mateScore * 2

        for (move in moves) {
            val score = -negamax(board.apply(move), difficulty.depth - 1, -beta, -alpha, -sign)
            if (score > bestScore) { bestScore = score; best = move }
            if (score > alpha) alpha = score
        }
        return best
    }

    /**
     * Negamax: always returns the score from the perspective of the side to move.
     * [perspective] is +1 when White is to move, -1 when Black, so the white-centric
     * evaluation can be flipped consistently.
     */
    private fun negamax(board: Board, depth: Int, alphaIn: Int, beta: Int, perspective: Int): Int {
        nodes++
        val moves = MoveGenerator.legalMoves(board)

        if (moves.isEmpty()) {
            val king = board.kingSquare(board.sideToMove)
            val inCheck = king != null &&
                MoveGenerator.isSquareAttacked(board, king, board.sideToMove.opposite())
            // Prefer faster mates by folding depth into the score.
            return if (inCheck) -(mateScore + depth) else 0  // checkmate : stalemate
        }
        if (depth == 0) return perspective * Evaluator.evaluate(board)

        var alpha = alphaIn
        var best = -mateScore * 2
        for (move in orderMoves(board, moves)) {
            val score = -negamax(board.apply(move), depth - 1, -beta, -alpha, -perspective)
            if (score > best) best = score
            if (best > alpha) alpha = best
            if (alpha >= beta) break   // beta cutoff
        }
        return best
    }

    /** Cheap move ordering: try captures first to improve alpha-beta cutoffs. */
    private fun orderMoves(board: Board, moves: List<Move>): List<Move> =
        moves.sortedByDescending { m ->
            val victim = board.pieceAt(m.to)
            val attacker = board.pieceAt(m.from)
            if (victim != null && attacker != null)
                // MVV-LVA: value most valuable victim, least valuable attacker.
                pieceValue(victim.type) * 10 - pieceValue(attacker.type)
            else if (m.promotion != null) 800
            else 0
        }

    private fun pieceValue(t: PieceType): Int = when (t) {
        PieceType.PAWN -> 1; PieceType.KNIGHT -> 3; PieceType.BISHOP -> 3
        PieceType.ROOK -> 5; PieceType.QUEEN -> 9; PieceType.KING -> 100
    }

    fun nodesSearched(): Long = nodes
}
