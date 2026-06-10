package com.chessapp.engine

import com.chessapp.domain.model.Board
import com.chessapp.domain.model.Move

/**
 * A no-op engine used for pass-and-play (two humans on one device). It never
 * produces a move, so the ViewModel simply waits for the other human to tap.
 * Acts as a null object so the rest of the code doesn't need null checks.
 */
object NoEngine : ChessEnginePort {
    override suspend fun bestMove(board: Board): Move? = null
    override fun setSkill(level: Int) {}
    override fun close() {}
}
