package com.chessapp.domain

import com.chessapp.data.puzzle.Puzzle
import com.chessapp.data.puzzle.PuzzleBank
import com.chessapp.data.puzzle.PuzzleResult
import com.chessapp.data.puzzle.PuzzleSession
import com.chessapp.domain.engine.GameEngine
import com.chessapp.domain.engine.MoveGenerator
import com.chessapp.domain.model.Board
import com.chessapp.domain.model.GameStatus
import com.chessapp.domain.model.Move
import org.junit.Assert
import org.junit.Test

/** Re-proves the ENTIRE puzzle bank on every build: ids unique, lines legal,
 *  themes truthful (mateIn1 mates in one; mateIn2 first move forces mate
 *  against every reply), and PuzzleSession solves each via its own line. */
class PuzzleBankTest {

    private fun mates(b: Board) = MoveGenerator.legalMoves(b).filter {
        val e = GameEngine(b); e.makeMove(it); e.status() == GameStatus.CHECKMATE
    }

    @Test
    fun idsAreUnique() {
        val ids = PuzzleBank.builtIn.map { it.id }
        Assert.assertEquals(ids.size, ids.toSet().size)
        Assert.assertTrue("bank should be substantial", ids.size >= 100)
    }

    @Test
    fun everyLineIsLegalAndEndsInCheckmate() {
        for (p in PuzzleBank.builtIn) {
            val e = GameEngine(Board.fromFen(p.fen))
            for (u in p.solution) {
                Assert.assertTrue("${p.id}: illegal $u", e.makeMove(Move.fromUci(u)))
            }
            Assert.assertEquals("${p.id}: line must end in mate",
                GameStatus.CHECKMATE, e.status())
        }
    }

    @Test
    fun themesAreTruthful() {
        for (p in PuzzleBank.builtIn) {
            val b0 = Board.fromFen(p.fen)
            if ("mateIn1" in p.themes) {
                Assert.assertEquals("${p.id}: mateIn1 line length", 1, p.solution.size)
            }
            if ("mateIn2" in p.themes) {
                Assert.assertEquals("${p.id}: mateIn2 line length", 3, p.solution.size)
                Assert.assertTrue("${p.id}: must not be mate-in-1", mates(b0).isEmpty())
                val e1 = GameEngine(b0)
                Assert.assertTrue(e1.makeMove(Move.fromUci(p.solution[0])))
                for (r in MoveGenerator.legalMoves(e1.board)) {
                    val e2 = GameEngine(e1.board); e2.makeMove(r)
                    Assert.assertTrue("${p.id}: reply ${r.toUci()} escapes mate",
                        mates(e2.board).isNotEmpty())
                }
            }
        }
    }

    @Test
    fun puzzleSessionSolvesEachBankPuzzleViaItsOwnLine() {
        for (p in PuzzleBank.builtIn) {
            val s = PuzzleSession(p)
            var i = 0
            while (!s.isSolved) {
                val r = s.submit(Move.fromUci(p.solution[i]))
                Assert.assertTrue("${p.id}: step $i rejected", r is PuzzleResult.Correct)
                i += 2
            }
        }
    }
}
