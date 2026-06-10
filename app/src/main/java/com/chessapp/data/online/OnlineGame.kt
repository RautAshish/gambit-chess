package com.chessapp.data.online

import com.chessapp.domain.engine.GameEngine
import com.chessapp.domain.model.Board
import com.chessapp.domain.model.Color
import com.chessapp.domain.model.GameStatus
import com.chessapp.domain.model.Move

/**
 * Online game state stored as a single Firestore document. The move list is the
 * source of truth; FEN is denormalized for quick reads. Clients NEVER write a new
 * position directly — they append a UCI move, and validation re-derives the board.
 */
data class OnlineGame(
    val id: String = "",
    val whiteUid: String = "",
    val blackUid: String = "",
    val moves: List<String> = emptyList(),     // UCI moves in order
    val fen: String = Board.START_FEN,
    val status: String = "ONGOING",
    val winnerUid: String? = null,
    val whiteTimeMillis: Long = 0,
    val blackTimeMillis: Long = 0,
    val updatedAt: Long = 0
) {
    fun colorOf(uid: String): Color? = when (uid) {
        whiteUid -> Color.WHITE
        blackUid -> Color.BLACK
        else -> null
    }
}

sealed interface MoveOutcome {
    data class Applied(val game: OnlineGame) : MoveOutcome
    data object NotYourTurn : MoveOutcome
    data object IllegalMove : MoveOutcome
    data object GameOver : MoveOutcome
}

/**
 * Validates and applies a move to an [OnlineGame]. This logic must run where you
 * trust it: a Cloud Function, or a Firestore transaction guarded by security rules.
 * Running it only on the client would let a tampered client post illegal moves.
 *
 * Pure and framework-free, so it is shared verbatim between client (optimistic UI)
 * and server (authority), and is unit-tested below.
 */
object OnlineGameValidator {

    fun applyMove(game: OnlineGame, byUid: String, uci: String): MoveOutcome {
        if (game.status != "ONGOING" && game.status != "CHECK") return MoveOutcome.GameOver

        // Rebuild authoritative board from the move list.
        val engine = GameEngine()
        for (m in game.moves) {
            val parsed = runCatching { Move.fromUci(m) }.getOrNull()
                ?: return MoveOutcome.IllegalMove
            if (!engine.makeMove(parsed)) return MoveOutcome.IllegalMove
        }

        val mover = game.colorOf(byUid) ?: return MoveOutcome.NotYourTurn
        if (engine.board.sideToMove != mover) return MoveOutcome.NotYourTurn

        val move = runCatching { Move.fromUci(uci) }.getOrNull() ?: return MoveOutcome.IllegalMove
        if (!engine.makeMove(move)) return MoveOutcome.IllegalMove

        val status = engine.status()
        val winner = when (status) {
            GameStatus.CHECKMATE -> byUid     // the player who just moved delivered mate
            else -> null
        }

        return MoveOutcome.Applied(
            game.copy(
                moves = game.moves + uci,
                fen = engine.board.toFen(),
                status = status.name,
                winnerUid = winner,
                updatedAt = System.currentTimeMillis()
            )
        )
    }
}
