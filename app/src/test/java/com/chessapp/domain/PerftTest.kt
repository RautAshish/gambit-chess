package com.chessapp.domain

import com.chessapp.domain.engine.MoveGenerator
import com.chessapp.domain.model.Board
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Perft (performance test) validates the move generator by counting leaf nodes
 * to a fixed depth. These reference counts are the chess-programming standard;
 * matching them proves castling, en passant, promotion, and check handling are
 * all correct. Deeper plies are excluded here to keep unit tests fast — run them
 * as an instrumented/long test if you change MoveGenerator.
 */
class PerftTest {

    private fun perft(board: Board, depth: Int): Long {
        if (depth == 0) return 1L
        val moves = MoveGenerator.legalMoves(board)
        if (depth == 1) return moves.size.toLong()
        var n = 0L
        for (m in moves) n += perft(board.apply(m), depth - 1)
        return n
    }

    @Test fun startingPosition() {
        val b = Board.initial()
        assertEquals(20L, perft(b, 1))
        assertEquals(400L, perft(b, 2))
        assertEquals(8902L, perft(b, 3))
        assertEquals(197281L, perft(b, 4))
    }

    @Test fun kiwipete() {
        val b = Board.fromFen(
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"
        )
        assertEquals(48L, perft(b, 1))
        assertEquals(2039L, perft(b, 2))
        assertEquals(97862L, perft(b, 3))
    }

    @Test fun enPassantAndPromotion() {
        val b = Board.fromFen("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1")
        assertEquals(14L, perft(b, 1))
        assertEquals(191L, perft(b, 2))
        assertEquals(2812L, perft(b, 3))
        assertEquals(43238L, perft(b, 4))
    }

    @Test fun fenRoundTrip() {
        val fen = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"
        assertEquals(fen, Board.fromFen(fen).toFen())
    }
}
