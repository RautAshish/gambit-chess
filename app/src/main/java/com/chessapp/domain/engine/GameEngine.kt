package com.chessapp.domain.engine

import com.chessapp.domain.model.*

/**
 * High-level game facade: tracks position history (for repetition),
 * validates moves, and reports game status.
 */
class GameEngine(start: Board = Board.initial()) {

    private val history = ArrayList<Board>().apply { add(start) }
    private val moves = ArrayList<Move>()          // moves actually played
    private val redoStack = ArrayList<Pair<Board, Move>>()

    val board: Board get() = history.last()

    /** Moves played so far, in order. */
    fun moveHistory(): List<Move> = moves.toList()

    /** (boardBeforeMove, move) pairs — what Notation.toPgnMoveText expects. */
    fun annotatedHistory(): List<Pair<Board, Move>> =
        moves.mapIndexed { i, m -> history[i] to m }

    fun pgnMoveText(): String = Notation.toPgnMoveText(annotatedHistory())

    fun legalMoves(): List<Move> = MoveGenerator.legalMoves(board)

    fun legalMovesFrom(from: Square): List<Move> =
        legalMoves().filter { it.from == from }

    /** Applies [move] if legal; returns true on success. */
    fun makeMove(move: Move): Boolean {
        val legal = legalMoves().firstOrNull {
            it.from == move.from && it.to == move.to &&
                it.promotion == move.promotion
        } ?: return false
        history.add(board.apply(legal))
        moves.add(legal)
        redoStack.clear()
        return true
    }

    fun undo(): Boolean {
        if (history.size <= 1) return false
        val undone = moves.removeAt(moves.lastIndex)
        history.removeAt(history.lastIndex)
        redoStack.add(history.last() to undone)
        return true
    }

    fun redo(): Boolean {
        val (_, move) = redoStack.removeLastOrNull() ?: return false
        history.add(board.apply(move))
        moves.add(move)
        return true
    }

    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun inCheck(color: Color = board.sideToMove): Boolean {
        val king = board.kingSquare(color) ?: return false
        return MoveGenerator.isSquareAttacked(board, king, color.opposite())
    }

    fun status(): GameStatus {
        val moves = legalMoves()
        val check = inCheck()
        if (moves.isEmpty()) return if (check) GameStatus.CHECKMATE else GameStatus.STALEMATE
        if (board.halfmoveClock >= 100) return GameStatus.DRAW_FIFTY_MOVE
        if (isThreefoldRepetition()) return GameStatus.DRAW_REPETITION
        if (isInsufficientMaterial()) return GameStatus.DRAW_INSUFFICIENT_MATERIAL
        return if (check) GameStatus.CHECK else GameStatus.ONGOING
    }

    private fun isThreefoldRepetition(): Boolean {
        // Compare the position portion of the FEN (piece placement, side, castling, ep).
        val key = positionKey(board)
        return history.count { positionKey(it) == key } >= 3
    }

    private fun positionKey(b: Board): String =
        b.toFen().split(" ").take(4).joinToString(" ")

    private fun isInsufficientMaterial(): Boolean {
        val pieces = board.allPieces().map { it.second }
        val nonKings = pieces.filter { it.type != PieceType.KING }
        return when {
            nonKings.isEmpty() -> true                                   // K vs K
            nonKings.size == 1 &&
                (nonKings[0].type == PieceType.BISHOP ||
                 nonKings[0].type == PieceType.KNIGHT) -> true           // K+minor vs K
            else -> false
        }
    }
}
