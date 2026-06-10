package com.chessapp.data.puzzle

import com.chessapp.domain.engine.GameEngine
import com.chessapp.domain.model.Board
import com.chessapp.domain.model.Move

/**
 * A tactics puzzle: a starting FEN and the expected solution as a line of UCI moves.
 * Odd-indexed moves are the opponent's forced replies (auto-played); even-indexed
 * (0,2,...) are the moves the solver must find.
 */
data class Puzzle(
    val id: String,
    val fen: String,
    val solution: List<String>,   // UCI moves, full line including opponent replies
    val rating: Int = 1500,
    val themes: List<String> = emptyList()
)

sealed interface PuzzleResult {
    /** Correct move; [reply] is the opponent's auto-response (null if puzzle solved). */
    data class Correct(val reply: Move?, val solved: Boolean) : PuzzleResult
    data object Wrong : PuzzleResult
}

/**
 * Drives a single puzzle attempt. The solver makes moves; the session checks them
 * against the solution and auto-plays the opponent's forced replies.
 */
class PuzzleSession(val puzzle: Puzzle) {
    private val engine = GameEngine(Board.fromFen(puzzle.fen))
    private var index = 0   // pointer into solution

    val board: Board get() = engine.board
    val isSolved: Boolean get() = index >= puzzle.solution.size

    /** Submit the solver's move. Advances and auto-plays the reply if correct. */
    fun submit(move: Move): PuzzleResult {
        if (isSolved) return PuzzleResult.Wrong
        val expected = Move.fromUci(puzzle.solution[index])
        val matches = move.from == expected.from && move.to == expected.to &&
            (expected.promotion == null || move.promotion == expected.promotion)
        if (!matches) return PuzzleResult.Wrong

        engine.makeMove(expected)
        index++

        if (isSolved) return PuzzleResult.Correct(reply = null, solved = true)

        // Auto-play opponent's forced reply.
        val replyMove = Move.fromUci(puzzle.solution[index])
        engine.makeMove(replyMove)
        index++
        return PuzzleResult.Correct(reply = replyMove, solved = isSolved)
    }

    /** The next correct move, for a hint. */
    fun hint(): Move? =
        if (isSolved) null else Move.fromUci(puzzle.solution[index])
}

/** A small built-in puzzle set. In production, load from a bundled JSON asset or server. */
object PuzzleBank {
    val builtIn = listOf(
        Puzzle(
            id = "mate-in-1-a",
            // White to move, back-rank mate: Re8#
            fen = "6k1/5ppp/8/8/8/8/8/4R1K1 w - - 0 1",
            solution = listOf("e1e8"),
            rating = 800,
            themes = listOf("backRank", "mateIn1")
        ),
        Puzzle(
            id = "mate-in-2-fork",
            // White: 1.Qd8+ Kxd8? no — use a clean mate-in-1 queen check setup
            fen = "6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1",
            solution = listOf("d1d8"),
            rating = 900,
            themes = listOf("backRank")
        )
    )
}
