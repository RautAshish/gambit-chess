package com.chessapp.domain.engine

import com.chessapp.domain.model.Color
import com.chessapp.domain.model.GameStatus

/**
 * The final outcome of a game. This is distinct from [GameStatus], which only
 * describes the board position (checkmate, stalemate, draws by rule). Resignation,
 * draw-agreement, and loss on time are PLAYER/CLOCK events the board can't express,
 * so the game layer owns a [GameResult] that combines both sources.
 */
sealed interface GameResult {
    data object Ongoing : GameResult
    data class Win(val winner: Color, val reason: WinReason) : GameResult
    data class Draw(val reason: DrawReason) : GameResult
}

enum class WinReason { CHECKMATE, RESIGNATION, TIMEOUT }

enum class DrawReason {
    STALEMATE, FIFTY_MOVE, REPETITION, INSUFFICIENT_MATERIAL, AGREEMENT
}

/**
 * Derives a [GameResult] from the board status plus optional player/clock events.
 * The board is consulted first for natural endings; resignation/agreement/timeout
 * are layered on top.
 */
object ResultEvaluator {

    fun evaluate(
        status: GameStatus,
        sideToMove: Color,
        resignedBy: Color? = null,
        drawAgreed: Boolean = false,
        flaggedSide: Color? = null
    ): GameResult {
        // Explicit player/clock events take precedence — they end the game even if
        // the position itself is still playable.
        if (resignedBy != null) {
            return GameResult.Win(resignedBy.opposite(), WinReason.RESIGNATION)
        }
        if (flaggedSide != null) {
            // Loss on time. (A stricter rule set would check whether the opponent
            // has mating material; we treat a flag as a loss for simplicity.)
            return GameResult.Win(flaggedSide.opposite(), WinReason.TIMEOUT)
        }
        if (drawAgreed) {
            return GameResult.Draw(DrawReason.AGREEMENT)
        }

        return when (status) {
            GameStatus.CHECKMATE ->
                // The side to move is checkmated, so the other side won.
                GameResult.Win(sideToMove.opposite(), WinReason.CHECKMATE)
            GameStatus.STALEMATE -> GameResult.Draw(DrawReason.STALEMATE)
            GameStatus.DRAW_FIFTY_MOVE -> GameResult.Draw(DrawReason.FIFTY_MOVE)
            GameStatus.DRAW_REPETITION -> GameResult.Draw(DrawReason.REPETITION)
            GameStatus.DRAW_INSUFFICIENT_MATERIAL ->
                GameResult.Draw(DrawReason.INSUFFICIENT_MATERIAL)
            else -> GameResult.Ongoing
        }
    }

    /** Human-readable headline + detail for a result, for the game-over dialog. */
    fun describe(result: GameResult): Pair<String, String> = when (result) {
        is GameResult.Ongoing -> "" to ""
        is GameResult.Win -> {
            val who = result.winner.name.lowercase().replaceFirstChar { it.uppercase() }
            val headline = "$who wins"
            val detail = when (result.reason) {
                WinReason.CHECKMATE -> "by checkmate"
                WinReason.RESIGNATION -> "by resignation"
                WinReason.TIMEOUT -> "on time"
            }
            headline to detail
        }
        is GameResult.Draw -> {
            val detail = when (result.reason) {
                DrawReason.STALEMATE -> "by stalemate"
                DrawReason.FIFTY_MOVE -> "by the fifty-move rule"
                DrawReason.REPETITION -> "by threefold repetition"
                DrawReason.INSUFFICIENT_MATERIAL -> "by insufficient material"
                DrawReason.AGREEMENT -> "by agreement"
            }
            "Draw" to detail
        }
    }

    /** PGN-style result token. */
    fun resultToken(result: GameResult): String = when (result) {
        is GameResult.Ongoing -> "*"
        is GameResult.Win -> if (result.winner == Color.WHITE) "1-0" else "0-1"
        is GameResult.Draw -> "1/2-1/2"
    }
}
