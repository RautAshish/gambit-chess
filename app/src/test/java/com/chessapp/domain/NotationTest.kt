package com.chessapp.domain

import com.chessapp.domain.engine.GameEngine
import com.chessapp.domain.model.GameStatus
import com.chessapp.domain.model.Move
import org.junit.Assert.assertEquals
import org.junit.Test

class NotationTest {
    @Test fun scholarsMatePgn() {
        val ge = GameEngine()
        listOf("e2e4","e7e5","f1c4","b8c6","d1h5","g8f6","h5f7")
            .forEach { assertEquals(true, ge.makeMove(Move.fromUci(it))) }
        assertEquals("1. e4 e5 2. Bc4 Nc6 3. Qh5 Nf6 4. Qxf7#", ge.pgnMoveText())
        assertEquals(GameStatus.CHECKMATE, ge.status())
    }

    @Test fun kingsideCastleSan() {
        val ge = GameEngine()
        listOf("e2e4","e7e5","g1f3","b8c6","f1c4","f8c5","e1g1")
            .forEach { assertEquals(true, ge.makeMove(Move.fromUci(it))) }
        assertEquals("O-O", ge.pgnMoveText().split(" ").last())
    }
}
