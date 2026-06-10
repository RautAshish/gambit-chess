package com.chessapp.domain

import com.chessapp.domain.model.Move
import com.chessapp.domain.model.PieceType
import com.chessapp.domain.model.Square
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Input hardening for UCI/algebraic parsing. These parse paths sit on the online
 * security boundary (the validator replays client-supplied move strings), so
 * malformed input must be rejected rather than silently truncated or allowed to
 * produce out-of-range squares.
 */
class UciParsingTest {

    private fun rejects(s: String): Boolean =
        runCatching { Move.fromUci(s) }.isFailure

    @Test fun acceptsWellFormed() {
        assertEquals(PieceType.QUEEN, Move.fromUci("e7e8q").promotion)
        assertEquals(PieceType.KNIGHT, Move.fromUci("a2a1n").promotion)
        assertEquals("e2e4", Move.fromUci("e2e4").toUci())
    }

    @Test fun rejectsWrongLength() {
        assertTrue(rejects(""))
        assertTrue(rejects("e2"))
        assertTrue(rejects("e2e"))
        assertTrue(rejects("e2e4e5"))   // 6 chars — previously truncated to e2e4
        assertTrue(rejects("e2e4XY"))
    }

    @Test fun rejectsOutOfRangeSquares() {
        assertTrue(rejects("e2e9"))     // rank 9 doesn't exist
        assertTrue(rejects("i2e4"))     // file i doesn't exist
        assertTrue(rejects("zzzz"))
        assertTrue(rejects("99ee"))
    }

    @Test fun rejectsBadPromotionPiece() {
        assertTrue(rejects("e7e8k"))    // can't promote to king
        assertTrue(rejects("e7e8x"))
    }

    @Test fun squareParseRangeChecked() {
        assertTrue(runCatching { Square.parse("e9") }.isFailure)
        assertTrue(runCatching { Square.parse("i1") }.isFailure)
        assertEquals(Square.parse("a1").index, 0)
        assertEquals(Square.parse("h8").index, 63)
    }
}
