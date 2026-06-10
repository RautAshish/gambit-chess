package com.chessapp.data.db

import com.chessapp.domain.engine.GameEngine
import com.chessapp.domain.model.Move
import kotlinx.coroutines.flow.Flow

/**
 * Saves and restores games. We persist the FEN (fast resume), the PGN (display),
 * and the UCI move list (faithful replay with full history for undo). Restoring
 * replays the moves so threefold-repetition and undo work exactly as in the live game.
 */
class GameRepository(private val dao: GameDao) {

    fun observeSavedGames(): Flow<List<SavedGame>> = dao.observeAll()

    /** Insert or update. Returns the row id (use it as the resume handle). */
    suspend fun save(
        engine: GameEngine,
        title: String,
        vsAi: Boolean,
        difficulty: String?,
        existingId: Long = 0,
        resultToken: String? = null
    ): Long {
        // Prefer the caller's authoritative result (it knows about resignation,
        // draw agreement, and loss on time); fall back to deriving from the board.
        val result = resultToken ?: when (engine.status()) {
            com.chessapp.domain.model.GameStatus.CHECKMATE ->
                if (engine.board.sideToMove == com.chessapp.domain.model.Color.WHITE) "0-1" else "1-0"
            com.chessapp.domain.model.GameStatus.STALEMATE,
            com.chessapp.domain.model.GameStatus.DRAW_FIFTY_MOVE,
            com.chessapp.domain.model.GameStatus.DRAW_REPETITION,
            com.chessapp.domain.model.GameStatus.DRAW_INSUFFICIENT_MATERIAL -> "1/2-1/2"
            else -> "*"
        }
        return dao.upsert(
            SavedGame(
                id = existingId,
                title = title,
                fen = engine.board.toFen(),
                pgn = engine.pgnMoveText(),
                uciMoves = engine.moveHistory().joinToString(" ") { it.toUci() },
                result = result,
                vsAi = vsAi,
                difficulty = difficulty,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun load(id: Long): GameEngine? {
        val saved = dao.byId(id) ?: return null
        val engine = GameEngine()
        if (saved.uciMoves.isNotBlank()) {
            for (uci in saved.uciMoves.trim().split(" ")) {
                engine.makeMove(Move.fromUci(uci))
            }
        }
        return engine
    }

    suspend fun delete(id: Long) = dao.deleteById(id)
}
