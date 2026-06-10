package com.chessapp.engine

import com.chessapp.domain.ai.ChessAI
import com.chessapp.domain.model.Board
import com.chessapp.domain.model.Move
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Adapts the built-in Minimax [ChessAI] to [ChessEnginePort]. Fully offline. */
class NativeEngine(
    private var difficulty: ChessAI.Difficulty = ChessAI.Difficulty.MEDIUM
) : ChessEnginePort {

    override suspend fun bestMove(board: Board): Move? =
        withContext(Dispatchers.Default) {
            ChessAI(difficulty).bestMove(board)
        }

    /** Map UCI-style skill 0..20 onto the four difficulty tiers. */
    override fun setSkill(level: Int) {
        difficulty = when {
            level <= 4 -> ChessAI.Difficulty.EASY
            level <= 9 -> ChessAI.Difficulty.MEDIUM
            level <= 14 -> ChessAI.Difficulty.HARD
            else -> ChessAI.Difficulty.EXPERT
        }
    }

    override fun close() { /* nothing to release */ }
}
