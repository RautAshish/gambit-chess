package com.chessapp.domain

import com.chessapp.domain.engine.GameEngine
import com.chessapp.domain.engine.Material
import com.chessapp.domain.model.Color
import com.chessapp.domain.model.Move
import com.chessapp.domain.model.PieceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MaterialTest {
    @Test fun startingPositionIsEven() {
        val ge = GameEngine()
        assertTrue(Material.capturedOf(ge.board, Color.WHITE).isEmpty())
        assertEquals(0, Material.balance(ge.board, Color.WHITE))
    }

    @Test fun capturingPawnShiftsBalance() {
        val ge = GameEngine()
        listOf("e2e4", "d7d5", "e4d5").forEach { ge.makeMove(Move.fromUci(it)) }
        val capturedBlack = Material.capturedOf(ge.board, Color.BLACK)
        assertEquals(1, capturedBlack.size)
        assertEquals(PieceType.PAWN, capturedBlack[0].type)
        assertEquals(1, Material.balance(ge.board, Color.WHITE))
        assertEquals(-1, Material.balance(ge.board, Color.BLACK))
    }
}
