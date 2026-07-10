package com.chessapp.engine

import com.chessapp.domain.ai.ChessAI
import com.chessapp.domain.engine.MoveGenerator
import com.chessapp.domain.model.Board
import com.chessapp.domain.model.Move
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Adapts the built-in Minimax [ChessAI] to [ChessEnginePort]. Fully offline. */
class NativeEngine(
    difficulty: ChessAI.Difficulty = ChessAI.Difficulty.MEDIUM
) : ChessEnginePort {

    private var searchDepth: Int = difficulty.depth
    private var blunderPct: Int = if (difficulty == ChessAI.Difficulty.EASY) 30 else 0

    override suspend fun bestMove(board: Board): Move? =
        withContext(Dispatchers.Default) {
            if (blunderPct > 0 && kotlin.random.Random.nextInt(100) < blunderPct)
            // Honest weakness on the low rungs: occasionally play ANY legal move,
            // the way a beginner hangs a piece — not merely a shallower search.
                MoveGenerator.legalMoves(board).randomOrNull()
                    ?: ChessAI(searchDepth).bestMove(board)
            else ChessAI(searchDepth).bestMove(board)
        }

    /** Finer ladder from UCI-style skill 0..20: depth climbs 1→6 while the
     *  deliberate blunder-rate falls 45%→0 — ten distinguishable rungs. */
    override fun setSkill(level: Int) {
        val l = level.coerceIn(0, 20)
        searchDepth = when { l<=3->1; l<=8->2; l<=12->3; l<=16->4; l<=18->5; else->6 }
        blunderPct  = when { l<=1->45; l<=3->28; l<=5->18; l<=8->10; l<=10->6; l<=12->3; l<=14->1; else->0 }
    }

    override fun close() { /* nothing to release */ }
}
