package com.chessapp.domain

import com.chessapp.data.online.MoveOutcome
import com.chessapp.data.online.OnlineGame
import com.chessapp.data.online.OnlineGameValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlineValidatorTest {
    private val w = "uidW"; private val b = "uidB"

    private fun step(g: OnlineGame, uid: String, uci: String): OnlineGame =
        (OnlineGameValidator.applyMove(g, uid, uci) as MoveOutcome.Applied).game

    @Test fun rejectsOutOfTurnAndIllegal() {
        var g = OnlineGame(id = "g", whiteUid = w, blackUid = b)
        g = step(g, w, "e2e4")
        assertTrue(OnlineGameValidator.applyMove(g, w, "d2d4") is MoveOutcome.NotYourTurn)
        assertTrue(OnlineGameValidator.applyMove(g, "stranger", "e7e5") is MoveOutcome.NotYourTurn)
        assertTrue(OnlineGameValidator.applyMove(g, b, "e2e4") is MoveOutcome.IllegalMove)
    }

    @Test fun detectsCheckmateAndWinner() {
        var g = OnlineGame(id = "g", whiteUid = w, blackUid = b)
        listOf(w to "e2e4", b to "e7e5", w to "f1c4", b to "b8c6", w to "d1h5", b to "g8f6")
            .forEach { (uid, uci) -> g = step(g, uid, uci) }
        val fin = OnlineGameValidator.applyMove(g, w, "h5f7") as MoveOutcome.Applied
        assertEquals("CHECKMATE", fin.game.status)
        assertEquals(w, fin.game.winnerUid)
        assertTrue(OnlineGameValidator.applyMove(fin.game, b, "e8e7") is MoveOutcome.GameOver)
    }
}
