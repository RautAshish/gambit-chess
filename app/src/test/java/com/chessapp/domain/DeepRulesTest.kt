package com.chessapp.domain

import com.chessapp.domain.engine.MoveGenerator
import com.chessapp.domain.model.Board
import com.chessapp.domain.model.PieceType
import com.chessapp.domain.model.Square
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Deepest rule-edge suite: CPW positions 5/6 and the traps that pass basic Perft. */
class DeepRulesTest {

    private fun perft(b: Board, d: Int): Long {
        if (d == 0) return 1
        val m = MoveGenerator.legalMoves(b)
        if (d == 1) return m.size.toLong()
        var n = 0L
        for (x in m) n += perft(b.apply(x), d - 1)
        return n
    }

    @Test fun cpwPosition5() {
        val b = Board.fromFen("rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8")
        assertEquals(44L, perft(b, 1))
        assertEquals(1486L, perft(b, 2))
        assertEquals(62379L, perft(b, 3))
        assertEquals(2103487L, perft(b, 4))
    }

    @Test fun cpwPosition6() {
        val b = Board.fromFen("r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10")
        assertEquals(46L, perft(b, 1))
        assertEquals(2079L, perft(b, 2))
        assertEquals(89890L, perft(b, 3))
    }

    @Test fun enPassantPinIsIllegal() {
        // Capturing ep would expose black's king to Qh4 along the rank.
        val pinned = Board.fromFen("8/8/8/8/k2Pp2Q/8/8/4K3 b - d3 0 1")
        assertTrue(MoveGenerator.legalMoves(pinned).none { it.isEnPassant })
        // Without the queen the ep capture is legal.
        val free = Board.fromFen("8/8/8/8/k2Pp3/8/8/4K3 b - d3 0 1")
        assertTrue(MoveGenerator.legalMoves(free).any { it.isEnPassant })
    }

    @Test fun castlingEdges() {
        // In check: no castling at all.
        val inCheck = Board.fromFen("4k3/8/8/8/8/8/4r3/R3K2R w KQ - 0 1")
        assertTrue(MoveGenerator.legalMoves(inCheck).none { it.isCastle })
        // f1 attacked: kingside blocked, queenside still legal.
        val thru = Board.fromFen("4k3/8/8/8/8/8/5r2/R3K2R w KQ - 0 1")
        val castles = MoveGenerator.legalMoves(thru).filter { it.isCastle }
        assertEquals(1, castles.size)
        assertEquals(Square.parse("c1"), castles[0].to)
        // Rook captured at home square removes that right.
        val cap = Board.fromFen("r3k2r/8/8/8/8/8/6n1/R3K2R b KQkq - 0 1")
        val after = cap.apply(com.chessapp.domain.model.Move(Square.parse("g2"), Square.parse("h1")))
        val cr = after.toFen().split(" ")[2]
        assertTrue(!cr.contains("K") && cr.contains("Q"))
    }

    @Test fun doubleCheckOnlyKingMoves() {
        val b = Board.fromFen("4k3/8/8/8/8/2b5/4r3/4K3 w - - 0 1")
        assertTrue(MoveGenerator.legalMoves(b).all { b.pieceAt(it.from)?.type == PieceType.KING })
    }

    @Test fun classicStalemate() {
        val b = Board.fromFen("k7/P7/K7/8/8/8/8/8 b - - 0 1")
        assertTrue(MoveGenerator.legalMoves(b).isEmpty())
    }
}
