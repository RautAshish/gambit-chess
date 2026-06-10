package com.chessapp.domain

import com.chessapp.data.puzzle.PuzzleBank
import com.chessapp.data.puzzle.PuzzleResult
import com.chessapp.data.puzzle.PuzzleSession
import com.chessapp.domain.engine.GameEngine
import com.chessapp.domain.model.Board
import com.chessapp.domain.model.GameStatus
import com.chessapp.domain.model.Move
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PuzzleTest {
    @Test fun builtInPuzzlesAreRealMates() {
        for (p in PuzzleBank.builtIn) {
            val ge = GameEngine(Board.fromFen(p.fen))
            p.solution.forEach { ge.makeMove(Move.fromUci(it)) }
            assertEquals("puzzle ${p.id}", GameStatus.CHECKMATE, ge.status())
        }
    }

    @Test fun sessionAcceptsCorrectRejectsWrong() {
        val ok = PuzzleSession(PuzzleBank.builtIn[0]).submit(Move.fromUci("e1e8"))
        assertTrue(ok is PuzzleResult.Correct && ok.solved)
        val bad = PuzzleSession(PuzzleBank.builtIn[0]).submit(Move.fromUci("e1e2"))
        assertTrue(bad is PuzzleResult.Wrong)
    }
}
