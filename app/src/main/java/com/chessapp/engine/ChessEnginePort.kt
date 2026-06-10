package com.chessapp.engine

import com.chessapp.domain.model.Board
import com.chessapp.domain.model.Move

/**
 * A chess engine that can pick a move for the side to move on a given board.
 * Implementations: [com.chessapp.engine.NativeEngine] (built-in Kotlin Minimax)
 * and [com.chessapp.engine.stockfish.StockfishEngine] (UCI over a native binary).
 * Both are suspend so callers stay off the main thread.
 */
interface ChessEnginePort {
    suspend fun bestMove(board: Board): Move?
    fun setSkill(level: Int)   // 0..20, mapped to depth or Stockfish skill level
    fun close()
}
