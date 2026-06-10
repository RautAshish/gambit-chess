package com.chessapp.domain.model

enum class PieceType(val char: Char) {
    PAWN('p'), KNIGHT('n'), BISHOP('b'), ROOK('r'), QUEEN('q'), KING('k')
}

enum class Color {
    WHITE, BLACK;
    fun opposite(): Color = if (this == WHITE) BLACK else WHITE
}

data class Piece(val type: PieceType, val color: Color) {
    /** FEN char: uppercase for white, lowercase for black. */
    fun fenChar(): Char =
        if (color == Color.WHITE) type.char.uppercaseChar() else type.char

    companion object {
        fun fromFenChar(c: Char): Piece {
            val color = if (c.isUpperCase()) Color.WHITE else Color.BLACK
            val type = PieceType.entries.first { it.char == c.lowercaseChar() }
            return Piece(type, color)
        }
    }
}

/**
 * A board square indexed 0..63 (a1 = 0, b1 = 1, ... h8 = 63).
 * file = column 0..7 (a..h), rank = row 0..7 (1..8).
 */
@JvmInline
value class Square(val index: Int) {
    val file: Int get() = index and 7
    val rank: Int get() = index shr 3

    fun isValid(): Boolean = index in 0..63

    override fun toString(): String =
        if (isValid()) "${'a' + file}${rank + 1}" else "invalid($index)"

    companion object {
        fun of(file: Int, rank: Int): Square = Square(rank * 8 + file)

        /** Parse algebraic like "e4". */
        fun parse(s: String): Square {
            require(s.length == 2) { "bad square: $s" }
            val file = s[0] - 'a'
            val rank = s[1] - '1'
            require(file in 0..7 && rank in 0..7) { "square out of range: $s" }
            return of(file, rank)
        }
    }
}

data class Move(
    val from: Square,
    val to: Square,
    val promotion: PieceType? = null,
    val isCastle: Boolean = false,
    val isEnPassant: Boolean = false,
    val isDoublePush: Boolean = false
) {
    /** Long algebraic notation, e.g. "e2e4" or "e7e8q". */
    fun toUci(): String =
        "$from$to" + (promotion?.let { it.char.toString() } ?: "")

    companion object {
        fun fromUci(s: String): Move {
            require(s.length == 4 || s.length == 5) { "bad UCI move: $s" }
            val from = Square.parse(s.substring(0, 2))
            val to = Square.parse(s.substring(2, 4))
            val promo = if (s.length == 5) {
                val c = s[4].lowercaseChar()
                // Only Q/R/B/N are legal promotion targets.
                when (c) {
                    'q' -> PieceType.QUEEN
                    'r' -> PieceType.ROOK
                    'b' -> PieceType.BISHOP
                    'n' -> PieceType.KNIGHT
                    else -> throw IllegalArgumentException("bad promotion in UCI: $s")
                }
            } else null
            return Move(from, to, promo)
        }
    }
}

/** Castling rights, tracked per side. */
data class CastlingRights(
    val whiteKingSide: Boolean = true,
    val whiteQueenSide: Boolean = true,
    val blackKingSide: Boolean = true,
    val blackQueenSide: Boolean = true
)

enum class GameStatus {
    ONGOING, CHECK, CHECKMATE, STALEMATE,
    DRAW_FIFTY_MOVE, DRAW_REPETITION, DRAW_INSUFFICIENT_MATERIAL
}
