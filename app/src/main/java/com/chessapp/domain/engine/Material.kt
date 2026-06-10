package com.chessapp.domain.engine

import com.chessapp.domain.model.Board
import com.chessapp.domain.model.Color
import com.chessapp.domain.model.Piece
import com.chessapp.domain.model.PieceType

/**
 * Derives captured pieces and material balance by comparing the current board to
 * the full starting army. This avoids tracking captures incrementally (which is
 * error-prone with undo/redo) — we just diff against the known initial set.
 */
object Material {

    private val STARTING_COUNTS = mapOf(
        PieceType.PAWN to 8,
        PieceType.KNIGHT to 2,
        PieceType.BISHOP to 2,
        PieceType.ROOK to 2,
        PieceType.QUEEN to 1,
        PieceType.KING to 1
    )

    private val VALUE = mapOf(
        PieceType.PAWN to 1, PieceType.KNIGHT to 3, PieceType.BISHOP to 3,
        PieceType.ROOK to 5, PieceType.QUEEN to 9, PieceType.KING to 0
    )

    /** Pieces of [color] that have been captured (i.e. are missing from the board). */
    fun capturedOf(board: Board, color: Color): List<Piece> {
        val onBoard = board.allPieces()
            .map { it.second }
            .filter { it.color == color }
            .groupingBy { it.type }
            .eachCount()
        val captured = mutableListOf<Piece>()
        for ((type, start) in STARTING_COUNTS) {
            val remaining = onBoard[type] ?: 0
            repeat((start - remaining).coerceAtLeast(0)) { captured.add(Piece(type, color)) }
        }
        // Show most valuable first.
        return captured.sortedByDescending { VALUE.getValue(it.type) }
    }

    /**
     * Net material from [color]'s perspective in pawns. Positive means [color] is
     * ahead. Kings are excluded.
     */
    fun balance(board: Board, color: Color): Int {
        var score = 0
        for ((_, piece) in board.allPieces()) {
            val v = VALUE.getValue(piece.type)
            score += if (piece.color == color) v else -v
        }
        return score
    }
}
