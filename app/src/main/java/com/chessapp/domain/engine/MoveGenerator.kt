package com.chessapp.domain.engine

import com.chessapp.domain.model.*

/**
 * Generates legal moves. Strategy: generate pseudo-legal moves, then filter out
 * any that leave the mover's own king in check by actually making the move.
 * Simple and correct; optimize with pinned-piece detection later if needed.
 */
object MoveGenerator {

    private val KNIGHT_OFFSETS = listOf(
        -17, -15, -10, -6, 6, 10, 15, 17
    )
    private val KING_OFFSETS = listOf(-9, -8, -7, -1, 1, 7, 8, 9)

    // Sliding directions as (fileDelta, rankDelta).
    private val ROOK_DIRS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
    private val BISHOP_DIRS = listOf(1 to 1, 1 to -1, -1 to 1, -1 to -1)

    fun legalMoves(board: Board): List<Move> {
        val side = board.sideToMove
        return pseudoLegalMoves(board).filter { move ->
            val after = board.apply(move)
            val king = after.kingSquare(side) ?: return@filter false
            !isSquareAttacked(after, king, side.opposite())
        }
    }

    fun pseudoLegalMoves(board: Board): List<Move> {
        val moves = ArrayList<Move>(48)
        val side = board.sideToMove
        for ((sq, piece) in board.allPieces()) {
            if (piece.color != side) continue
            when (piece.type) {
                PieceType.PAWN -> pawnMoves(board, sq, piece, moves)
                PieceType.KNIGHT -> stepMoves(board, sq, piece, KNIGHT_OFFSETS, moves, maxFileJump = 2)
                PieceType.KING -> {
                    stepMoves(board, sq, piece, KING_OFFSETS, moves, maxFileJump = 1)
                    castleMoves(board, sq, piece, moves)
                }
                PieceType.ROOK -> slideMoves(board, sq, piece, ROOK_DIRS, moves)
                PieceType.BISHOP -> slideMoves(board, sq, piece, BISHOP_DIRS, moves)
                PieceType.QUEEN -> slideMoves(board, sq, piece, ROOK_DIRS + BISHOP_DIRS, moves)
            }
        }
        return moves
    }

    private fun stepMoves(
        board: Board, from: Square, piece: Piece,
        offsets: List<Int>, out: MutableList<Move>, maxFileJump: Int
    ) {
        for (off in offsets) {
            val targetIdx = from.index + off
            if (targetIdx !in 0..63) continue
            val to = Square(targetIdx)
            // Reject wraparound: file distance must stay within range.
            if (kotlin.math.abs(to.file - from.file) > maxFileJump) continue
            val occupant = board.pieceAt(to)
            if (occupant == null || occupant.color != piece.color) out.add(Move(from, to))
        }
    }

    private fun slideMoves(
        board: Board, from: Square, piece: Piece,
        dirs: List<Pair<Int, Int>>, out: MutableList<Move>
    ) {
        for ((df, dr) in dirs) {
            var f = from.file + df
            var r = from.rank + dr
            while (f in 0..7 && r in 0..7) {
                val to = Square.of(f, r)
                val occ = board.pieceAt(to)
                if (occ == null) out.add(Move(from, to))
                else {
                    if (occ.color != piece.color) out.add(Move(from, to))
                    break
                }
                f += df; r += dr
            }
        }
    }

    private fun pawnMoves(board: Board, from: Square, piece: Piece, out: MutableList<Move>) {
        val dir = if (piece.color == Color.WHITE) 1 else -1
        val startRank = if (piece.color == Color.WHITE) 1 else 6
        val promoRank = if (piece.color == Color.WHITE) 7 else 0

        // Single push.
        val oneRank = from.rank + dir
        if (oneRank in 0..7) {
            val one = Square.of(from.file, oneRank)
            if (board.pieceAt(one) == null) {
                addPawnMove(from, one, oneRank == promoRank, false, out)
                // Double push.
                if (from.rank == startRank) {
                    val two = Square.of(from.file, from.rank + 2 * dir)
                    if (board.pieceAt(two) == null)
                        out.add(Move(from, two, isDoublePush = true))
                }
            }
        }
        // Captures (including en passant).
        for (df in listOf(-1, 1)) {
            val cf = from.file + df
            val cr = from.rank + dir
            if (cf !in 0..7 || cr !in 0..7) continue
            val to = Square.of(cf, cr)
            val occ = board.pieceAt(to)
            if (occ != null && occ.color != piece.color) {
                addPawnMove(from, to, cr == promoRank, false, out)
            } else if (to == board.enPassant) {
                out.add(Move(from, to, isEnPassant = true))
            }
        }
    }

    private fun addPawnMove(
        from: Square, to: Square, promotion: Boolean, ep: Boolean, out: MutableList<Move>
    ) {
        if (promotion) {
            for (t in listOf(PieceType.QUEEN, PieceType.ROOK, PieceType.BISHOP, PieceType.KNIGHT))
                out.add(Move(from, to, promotion = t))
        } else {
            out.add(Move(from, to, isEnPassant = ep))
        }
    }

    private fun castleMoves(board: Board, from: Square, king: Piece, out: MutableList<Move>) {
        val rank = if (king.color == Color.WHITE) 0 else 7
        if (from != Square.of(4, rank)) return
        val enemy = king.color.opposite()
        // Can't castle out of check.
        if (isSquareAttacked(board, from, enemy)) return

        val c = board.castling
        val kingSide = if (king.color == Color.WHITE) c.whiteKingSide else c.blackKingSide
        val queenSide = if (king.color == Color.WHITE) c.whiteQueenSide else c.blackQueenSide

        if (kingSide &&
            board.pieceAt(Square.of(5, rank)) == null &&
            board.pieceAt(Square.of(6, rank)) == null &&
            !isSquareAttacked(board, Square.of(5, rank), enemy) &&
            !isSquareAttacked(board, Square.of(6, rank), enemy)
        ) out.add(Move(from, Square.of(6, rank), isCastle = true))

        if (queenSide &&
            board.pieceAt(Square.of(3, rank)) == null &&
            board.pieceAt(Square.of(2, rank)) == null &&
            board.pieceAt(Square.of(1, rank)) == null &&
            !isSquareAttacked(board, Square.of(3, rank), enemy) &&
            !isSquareAttacked(board, Square.of(2, rank), enemy)
        ) out.add(Move(from, Square.of(2, rank), isCastle = true))
    }

    /** Is [target] attacked by any piece of [byColor]? */
    fun isSquareAttacked(board: Board, target: Square, byColor: Color): Boolean {
        // Pawns.
        val pawnDir = if (byColor == Color.WHITE) 1 else -1
        for (df in listOf(-1, 1)) {
            val f = target.file + df
            val r = target.rank - pawnDir
            if (f in 0..7 && r in 0..7) {
                val p = board.pieceAt(Square.of(f, r))
                if (p != null && p.color == byColor && p.type == PieceType.PAWN) return true
            }
        }
        // Knights.
        for (off in KNIGHT_OFFSETS) {
            val idx = target.index + off
            if (idx !in 0..63) continue
            val to = Square(idx)
            if (kotlin.math.abs(to.file - target.file) > 2) continue
            val p = board.pieceAt(to)
            if (p != null && p.color == byColor && p.type == PieceType.KNIGHT) return true
        }
        // King (adjacency).
        for (off in KING_OFFSETS) {
            val idx = target.index + off
            if (idx !in 0..63) continue
            val to = Square(idx)
            if (kotlin.math.abs(to.file - target.file) > 1) continue
            val p = board.pieceAt(to)
            if (p != null && p.color == byColor && p.type == PieceType.KING) return true
        }
        // Sliding: rook/queen orthogonally, bishop/queen diagonally.
        if (slideAttack(board, target, ROOK_DIRS, byColor, PieceType.ROOK)) return true
        if (slideAttack(board, target, BISHOP_DIRS, byColor, PieceType.BISHOP)) return true
        return false
    }

    private fun slideAttack(
        board: Board, target: Square, dirs: List<Pair<Int, Int>>,
        byColor: Color, straight: PieceType
    ): Boolean {
        for ((df, dr) in dirs) {
            var f = target.file + df
            var r = target.rank + dr
            while (f in 0..7 && r in 0..7) {
                val p = board.pieceAt(Square.of(f, r))
                if (p != null) {
                    if (p.color == byColor &&
                        (p.type == straight || p.type == PieceType.QUEEN)
                    ) return true
                    break
                }
                f += df; r += dr
            }
        }
        return false
    }
}
