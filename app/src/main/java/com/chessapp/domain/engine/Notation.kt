package com.chessapp.domain.engine

import com.chessapp.domain.model.*

/**
 * Converts moves to Standard Algebraic Notation (SAN, e.g. "Nf3", "exd5", "O-O", "e8=Q+")
 * and assembles full PGN move text. SAN requires the board *before* the move so it can
 * compute disambiguation and check/mate suffixes.
 */
object Notation {

    /** SAN for [move] played on [board]. */
    fun toSan(board: Board, move: Move): String {
        val piece = board.pieceAt(move.from) ?: return move.toUci()

        // Castling.
        if (move.isCastle) {
            val san = if (move.to.file == 6) "O-O" else "O-O-O"
            return san + checkSuffix(board, move)
        }

        val sb = StringBuilder()
        val isCapture = board.pieceAt(move.to) != null || move.isEnPassant

        if (piece.type == PieceType.PAWN) {
            if (isCapture) sb.append(('a' + move.from.file)).append('x')
            sb.append(move.to)
            if (move.promotion != null) sb.append('=').append(move.promotion.char.uppercaseChar())
        } else {
            sb.append(piece.type.char.uppercaseChar())
            sb.append(disambiguation(board, move, piece))
            if (isCapture) sb.append('x')
            sb.append(move.to)
        }
        return sb.toString() + checkSuffix(board, move)
    }

    /** If another same-type piece can also reach the target, add file/rank to disambiguate. */
    private fun disambiguation(board: Board, move: Move, piece: Piece): String {
        val rivals = MoveGenerator.legalMoves(board).filter {
            it.to == move.to && it.from != move.from &&
                board.pieceAt(it.from)?.type == piece.type
        }
        if (rivals.isEmpty()) return ""
        val sameFile = rivals.any { it.from.file == move.from.file }
        val sameRank = rivals.any { it.from.rank == move.from.rank }
        return when {
            !sameFile -> ('a' + move.from.file).toString()
            !sameRank -> (move.from.rank + 1).toString()
            else -> "${'a' + move.from.file}${move.from.rank + 1}"
        }
    }

    private fun checkSuffix(board: Board, move: Move): String {
        val after = board.apply(move)
        val enemyKing = after.kingSquare(after.sideToMove) ?: return ""
        val inCheck = MoveGenerator.isSquareAttacked(after, enemyKing, after.sideToMove.opposite())
        if (!inCheck) return ""
        val hasReply = MoveGenerator.legalMoves(after).isNotEmpty()
        return if (hasReply) "+" else "#"
    }

    /**
     * Builds PGN movetext from a list of (boardBeforeMove, move) pairs.
     * Returns e.g. "1. e4 e5 2. Nf3 Nc6 ...".
     */
    fun toPgnMoveText(history: List<Pair<Board, Move>>): String {
        val sb = StringBuilder()
        for ((i, pair) in history.withIndex()) {
            val (board, move) = pair
            if (board.sideToMove == Color.WHITE) {
                sb.append(board.fullmoveNumber).append(". ")
            } else if (i == 0) {
                sb.append(board.fullmoveNumber).append("... ")
            }
            sb.append(toSan(board, move)).append(' ')
        }
        return sb.toString().trim()
    }
}
