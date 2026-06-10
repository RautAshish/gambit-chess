package com.chessapp.domain

import com.chessapp.domain.engine.ChessClock
import com.chessapp.domain.engine.GameEngine
import com.chessapp.domain.engine.MoveGenerator
import com.chessapp.domain.model.Board
import com.chessapp.domain.model.Color
import com.chessapp.domain.model.GameStatus
import com.chessapp.domain.model.PieceType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Random

/** Property fuzzing: global invariants over thousands of random positions. */
class PropertyInvariantsTest {

    @Test fun boardInvariantsOverRandomGames() {
        val rng = Random(2024)
        var kingV = 0; var fenV = 0; var epV = 0; var pawnV = 0; var matV = 0; var subV = 0
        var positions = 0
        repeat(100) {
            val ge = GameEngine()
            var prevCount = 32
            for (ply in 0 until 60) {
                val b = ge.board
                positions++
                val wk = b.allPieces().count { it.second.type == PieceType.KING && it.second.color == Color.WHITE }
                val bk = b.allPieces().count { it.second.type == PieceType.KING && it.second.color == Color.BLACK }
                if (wk != 1 || bk != 1) kingV++
                if (Board.fromFen(b.toFen()).toFen() != b.toFen()) fenV++
                b.enPassant?.let { ep ->
                    if (ep.rank != 2 && ep.rank != 5) epV++
                    if (b.pieceAt(ep) != null) epV++
                }
                if (b.allPieces().any { it.second.type == PieceType.PAWN && (it.first.rank == 0 || it.first.rank == 7) }) pawnV++
                val count = b.allPieces().size
                if (count > prevCount || prevCount - count > 1) matV++
                prevCount = count
                val legal = MoveGenerator.legalMoves(b).map { it.toUci() }.toSet()
                val pseudo = MoveGenerator.pseudoLegalMoves(b).map { it.toUci() }.toSet()
                if (!pseudo.containsAll(legal)) subV++
                val st = ge.status()
                if (legal.isEmpty() || st == GameStatus.CHECKMATE || st == GameStatus.STALEMATE || st.name.startsWith("DRAW")) break
                val moves = MoveGenerator.legalMoves(b)
                ge.makeMove(moves[rng.nextInt(moves.size)])
            }
        }
        assertEquals("one king per side ($positions positions)", 0, kingV)
        assertEquals("FEN idempotent", 0, fenV)
        assertEquals("ep target valid", 0, epV)
        assertEquals("no pawn on rank 1/8", 0, pawnV)
        assertEquals("material monotone", 0, matV)
        assertEquals("legal subset of pseudo", 0, subV)
    }

    @Test fun clockFuzz() {
        var bad = 0
        for (seed in 0 until 100) {
            val r = Random(seed.toLong())
            var t = 0L
            val c = ChessClock(5_000 + r.nextInt(60_000).toLong(), r.nextInt(3_000).toLong()) { t }
            c.start(Color.WHITE)
            var active = Color.WHITE
            for (op in 0 until 40) {
                when (r.nextInt(5)) {
                    0 -> t += r.nextInt(4000).toLong()
                    1 -> if (c.flagged == null) { c.press(active); active = active.opposite() }
                    2 -> c.pause()
                    3 -> c.resume()
                    4 -> {
                        val snap = c.snapshot()
                        val c2 = ChessClock(1, 0) { t }
                        c2.restore(snap)
                        if (c2.remainingMillis(Color.WHITE) != c.remainingMillis(Color.WHITE) ||
                            c2.remainingMillis(Color.BLACK) != c.remainingMillis(Color.BLACK)) bad++
                    }
                }
                if (c.remainingMillis(Color.WHITE) < 0 || c.remainingMillis(Color.BLACK) < 0) bad++
                if (c.flagged != null) { t += 10_000; if (c.flagged == null) bad++ }
            }
        }
        assertEquals(0, bad)
    }
}
