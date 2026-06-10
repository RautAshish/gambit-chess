package com.chessapp.domain

import com.chessapp.data.online.MoveOutcome
import com.chessapp.data.online.OnlineGame
import com.chessapp.data.online.OnlineGameValidator
import com.chessapp.domain.engine.GameEngine
import com.chessapp.domain.model.Color
import com.chessapp.domain.model.GameStatus
import com.chessapp.domain.model.Move
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/** save→load→continue lockstep + online validator parity over random games. */
class IntegrationLockstepTest {

    @Test fun saveLoadContinueLockstep() {
        val rng = Random(11)
        repeat(15) {
            val ge = GameEngine()
            val plies = 5 + rng.nextInt(25)
            for (i in 0 until plies) {
                val mv = ge.legalMoves(); if (mv.isEmpty()) break
                val st = ge.status()
                if (st != GameStatus.ONGOING && st != GameStatus.CHECK) break
                ge.makeMove(mv[rng.nextInt(mv.size)])
            }
            val savedFen = ge.board.toFen()
            val savedUci = ge.moveHistory().map { it.toUci() }
            val re = GameEngine()
            for (u in savedUci) assertTrue("replay $u", re.makeMove(Move.fromUci(u)))
            assertEquals(savedFen, re.board.toFen())
            assertEquals(ge.pgnMoveText(), re.pgnMoveText())
            assertEquals(ge.status(), re.status())
            for (i in 0 until 5) {
                val mv = ge.legalMoves(); if (mv.isEmpty()) break
                val st = ge.status()
                if (st != GameStatus.ONGOING && st != GameStatus.CHECK) break
                val pick = mv[rng.nextInt(mv.size)]
                ge.makeMove(pick); re.makeMove(pick)
            }
            assertEquals(ge.board.toFen(), re.board.toFen())
        }
    }

    @Test fun onlineValidatorFenLockstep() {
        val rng = Random(7)
        repeat(8) {
            val ge = GameEngine()
            var og = OnlineGame(id = "g", whiteUid = "W", blackUid = "B")
            for (i in 0 until 20) {
                val mv = ge.legalMoves(); if (mv.isEmpty()) break
                val st = ge.status()
                if (st != GameStatus.ONGOING && st != GameStatus.CHECK) break
                val pick = mv[rng.nextInt(mv.size)]
                val uid = if (ge.board.sideToMove == Color.WHITE) "W" else "B"
                ge.makeMove(pick)
                val r = OnlineGameValidator.applyMove(og, uid, pick.toUci())
                assertTrue(r is MoveOutcome.Applied)
                og = (r as MoveOutcome.Applied).game
                assertEquals(ge.board.toFen(), og.fen)
            }
        }
    }
}
