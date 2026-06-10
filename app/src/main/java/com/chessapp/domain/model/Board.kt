package com.chessapp.domain.model

/**
 * Immutable board position. Applying a move returns a new Board.
 * Uses a simple 64-element mailbox representation — clear and correct.
 * Swap for bitboards later if the AI needs more speed.
 */
class Board private constructor(
    private val squares: Array<Piece?>,
    val sideToMove: Color,
    val castling: CastlingRights,
    val enPassant: Square?,      // target square behind a pawn that just double-pushed
    val halfmoveClock: Int,      // for the fifty-move rule
    val fullmoveNumber: Int
) {
    fun pieceAt(sq: Square): Piece? = squares[sq.index]

    fun kingSquare(color: Color): Square? {
        for (i in 0..63) {
            val p = squares[i]
            if (p != null && p.type == PieceType.KING && p.color == color) return Square(i)
        }
        return null
    }

    /** Returns a new board with [move] applied. Does NOT check legality. */
    fun apply(move: Move): Board {
        val next = squares.copyOf()
        val moving = next[move.from.index]!!

        // Reset / advance the fifty-move clock.
        val isCapture = next[move.to.index] != null || move.isEnPassant
        val isPawn = moving.type == PieceType.PAWN
        val newHalfmove = if (isCapture || isPawn) 0 else halfmoveClock + 1

        // Move the piece.
        next[move.from.index] = null
        next[move.to.index] =
            if (move.promotion != null) Piece(move.promotion, moving.color) else moving

        // En passant capture removes the pawn behind the target square.
        if (move.isEnPassant) {
            val capturedRank = move.from.rank
            next[Square.of(move.to.file, capturedRank).index] = null
        }

        // Castling moves the rook too.
        if (move.isCastle) {
            val rank = move.from.rank
            if (move.to.file == 6) { // king side
                next[Square.of(5, rank).index] = next[Square.of(7, rank).index]
                next[Square.of(7, rank).index] = null
            } else if (move.to.file == 2) { // queen side
                next[Square.of(3, rank).index] = next[Square.of(0, rank).index]
                next[Square.of(0, rank).index] = null
            }
        }

        val newCastling = updateCastling(moving, move)
        val newEnPassant = if (move.isDoublePush)
            Square.of(move.from.file, (move.from.rank + move.to.rank) / 2) else null

        return Board(
            squares = next,
            sideToMove = sideToMove.opposite(),
            castling = newCastling,
            enPassant = newEnPassant,
            halfmoveClock = newHalfmove,
            fullmoveNumber = if (sideToMove == Color.BLACK) fullmoveNumber + 1 else fullmoveNumber
        )
    }

    private fun updateCastling(moving: Piece, move: Move): CastlingRights {
        var c = castling
        // King moves lose both rights for that color.
        if (moving.type == PieceType.KING) {
            c = if (moving.color == Color.WHITE)
                c.copy(whiteKingSide = false, whiteQueenSide = false)
            else c.copy(blackKingSide = false, blackQueenSide = false)
        }
        // A rook moving from, or being captured on, a corner removes that right.
        fun touch(sq: Square) {
            when (sq.index) {
                Square.parse("a1").index -> c = c.copy(whiteQueenSide = false)
                Square.parse("h1").index -> c = c.copy(whiteKingSide = false)
                Square.parse("a8").index -> c = c.copy(blackQueenSide = false)
                Square.parse("h8").index -> c = c.copy(blackKingSide = false)
            }
        }
        touch(move.from)
        touch(move.to)
        return c
    }

    fun allPieces(): List<Pair<Square, Piece>> =
        (0..63).mapNotNull { i -> squares[i]?.let { Square(i) to it } }

    // ---- FEN ----

    fun toFen(): String {
        val sb = StringBuilder()
        for (rank in 7 downTo 0) {
            var empty = 0
            for (file in 0..7) {
                val p = squares[Square.of(file, rank).index]
                if (p == null) empty++
                else {
                    if (empty > 0) { sb.append(empty); empty = 0 }
                    sb.append(p.fenChar())
                }
            }
            if (empty > 0) sb.append(empty)
            if (rank > 0) sb.append('/')
        }
        sb.append(' ').append(if (sideToMove == Color.WHITE) 'w' else 'b')
        sb.append(' ')
        val cr = buildString {
            if (castling.whiteKingSide) append('K')
            if (castling.whiteQueenSide) append('Q')
            if (castling.blackKingSide) append('k')
            if (castling.blackQueenSide) append('q')
        }
        sb.append(if (cr.isEmpty()) "-" else cr)
        sb.append(' ').append(enPassant?.toString() ?: "-")
        sb.append(' ').append(halfmoveClock)
        sb.append(' ').append(fullmoveNumber)
        return sb.toString()
    }

    companion object {
        const val START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

        fun initial(): Board = fromFen(START_FEN)

        fun fromFen(fen: String): Board {
            val parts = fen.trim().split(" ")
            require(parts.size >= 4) { "bad FEN: $fen" }
            val squares = arrayOfNulls<Piece>(64)
            val rows = parts[0].split("/")
            require(rows.size == 8) { "bad FEN ranks: $fen" }
            for ((rowIdx, row) in rows.withIndex()) {
                val rank = 7 - rowIdx
                var file = 0
                for (ch in row) {
                    if (ch.isDigit()) file += ch - '0'
                    else { squares[Square.of(file, rank).index] = Piece.fromFenChar(ch); file++ }
                }
            }
            val side = if (parts[1] == "w") Color.WHITE else Color.BLACK
            val cr = parts[2]
            val castling = CastlingRights(
                whiteKingSide = cr.contains('K'),
                whiteQueenSide = cr.contains('Q'),
                blackKingSide = cr.contains('k'),
                blackQueenSide = cr.contains('q')
            )
            val ep = if (parts[3] == "-") null else Square.parse(parts[3])
            val half = parts.getOrNull(4)?.toIntOrNull() ?: 0
            val full = parts.getOrNull(5)?.toIntOrNull() ?: 1
            return Board(squares, side, castling, ep, half, full)
        }
    }
}
