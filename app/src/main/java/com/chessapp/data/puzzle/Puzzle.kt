package com.chessapp.data.puzzle

import com.chessapp.domain.engine.GameEngine
import com.chessapp.domain.model.Board
import com.chessapp.domain.model.Move

/**
 * A tactics puzzle: a starting FEN and the expected solution as a line of UCI moves.
 * Odd-indexed moves are the opponent's forced replies (auto-played); even-indexed
 * (0,2,...) are the moves the solver must find.
 */
data class Puzzle(
    val id: String,
    val fen: String,
    val solution: List<String>,   // UCI moves, full line including opponent replies
    val rating: Int = 1500,
    val themes: List<String> = emptyList()
)

sealed interface PuzzleResult {
    /** Correct move; [reply] is the opponent's auto-response (null if puzzle solved). */
    data class Correct(val reply: Move?, val solved: Boolean) : PuzzleResult
    data object Wrong : PuzzleResult
}

/**
 * Drives a single puzzle attempt. The solver makes moves; the session checks them
 * against the solution and auto-plays the opponent's forced replies.
 */
class PuzzleSession(val puzzle: Puzzle) {
    private val engine = GameEngine(Board.fromFen(puzzle.fen))
    private var index = 0   // pointer into solution

    val board: Board get() = engine.board
    val isSolved: Boolean get() = index >= puzzle.solution.size

    /** The most recent move on the board (for last-move highlighting). */
    fun lastMovePlayed(): Move? = engine.moveHistory().lastOrNull()

    /** Submit the solver's move. Advances and auto-plays the reply if correct. */
    fun submit(move: Move): PuzzleResult {
        if (isSolved) return PuzzleResult.Wrong
        val expected = Move.fromUci(puzzle.solution[index])
        val matches = move.from == expected.from && move.to == expected.to &&
            (expected.promotion == null || move.promotion == expected.promotion)
        if (!matches) return PuzzleResult.Wrong

        engine.makeMove(expected)
        index++

        if (isSolved) return PuzzleResult.Correct(reply = null, solved = true)

        // Auto-play opponent's forced reply.
        val replyMove = Move.fromUci(puzzle.solution[index])
        engine.makeMove(replyMove)
        index++
        return PuzzleResult.Correct(reply = replyMove, solved = isSolved)
    }

    /** The next correct move, for a hint. */
    fun hint(): Move? =
        if (isSolved) null else Move.fromUci(puzzle.solution[index])
}

/** A small built-in puzzle set. In production, load from a bundled JSON asset or server. */
object PuzzleBank {
    /** First entries are hand-curated (stable ids used by UI tests); the rest are
     *  MINED from engine self-play and machine-verified: every mateIn1 line mates,
     *  every mateIn2 first move forces mate against ALL replies (see PuzzleBankTest,
     *  which re-proves this in CI on every build). */
    val builtIn: List<Puzzle> = listOf(
        Puzzle("mate-in-1-a", "6k1/5ppp/8/8/8/8/8/4R1K1 w - - 0 1",
            listOf("e1e8"), 800, listOf("backRank", "mateIn1")),
        Puzzle("mate-in-1-b", "6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1",
            listOf("d1d8"), 900, listOf("backRank", "mateIn1")),
        Puzzle("m1-001", "3r4/1r5k/4bp2/pp1p3p/PpPp1P1P/1P1KP3/4QPR1/1R6 w - a6 0 38", listOf("e2h5"), 900, listOf("mateIn1")),
        Puzzle("m1-002", "1n3k1r/r3p1b1/p1p2p2/P6p/1PB1pPP1/1P5p/R4K1P/2BQ3R w - - 1 25", listOf("d1d8"), 900, listOf("mateIn1")),
        Puzzle("m1-003", "r3k1nQ/1bqppp2/n1p3pp/p1PP4/P4P1P/pP6/3BP1P1/4KBNR w Kq - 1 14", listOf("h8g8"), 900, listOf("mateIn1")),
        Puzzle("m1-004", "rn4r1/p3kp1p/1Pp2b2/R4P1P/7p/1PPP4/4Bp2/1N3KN1 b - - 1 21", listOf("f2g1q"), 900, listOf("mateIn1")),
        Puzzle("m1-005", "r3q1k1/5p2/2pp3p/8/3p1P1p/Pp1bP2P/3N1KPR/6N1 b - - 0 26", listOf("e8e3"), 900, listOf("mateIn1")),
        Puzzle("m1-006", "8/7k/2p1pQ2/1pP5/pP4p1/2B1pp1p/2NP1P1P/2KR4 w - - 6 36", listOf("f6g7"), 900, listOf("mateIn1")),
        Puzzle("m1-007", "5k2/4p3/4P3/1p1p2p1/1P4P1/2QP2Kp/8/1R6 w - - 1 39", listOf("c3h8"), 900, listOf("mateIn1")),
        Puzzle("m1-008", "2Q5/4k1r1/1p1p4/3P1p2/R4Ppp/3P3P/PB3KN1/5R2 w - - 1 43", listOf("a4a7"), 900, listOf("mateIn1")),
        Puzzle("m1-009", "rq3rk1/p3pp1p/2p4b/p1Pp4/P6p/2KP2PP/1PR1P3/1NB2B1R b - - 1 17", listOf("b8b4"), 900, listOf("mateIn1")),
        Puzzle("m1-010", "r2qkbnr/p1ppp2p/bp3p2/6p1/1nP4P/4PP1R/PP1PN1P1/RNBQKB2 b Qkq - 4 7", listOf("b4d3"), 900, listOf("mateIn1")),
        Puzzle("m1-011", "8/8/1p4k1/P5p1/6P1/5pK1/2Pq4/8 b - - 3 41", listOf("d2g2"), 900, listOf("mateIn1")),
        Puzzle("m1-012", "rnbqkbnr/p1pp1ppp/1p6/4p3/6P1/1P3P2/P1PPP2P/RNBQKBNR b KQkq g3 0 3", listOf("d8h4"), 900, listOf("mateIn1")),
        Puzzle("m1-013", "8/3k4/2pp2p1/4p2p/ppK5/PP3P2/3P4/1q6 b - - 1 38", listOf("b1b3"), 900, listOf("mateIn1")),
        Puzzle("m1-014", "1n2kbnr/7p/1p3p2/p1ppp1P1/8/1PP5/P1QPP2P/R1B1K1Nq b Qk - 1 13", listOf("h1g1"), 900, listOf("mateIn1")),
        Puzzle("m1-015", "1rb1k1nr/p1ppq1bp/8/2n1N1pP/P4p2/1PP3P1/3PPPB1/q1B1KR2 b k - 1 17", listOf("a1c1"), 900, listOf("mateIn1")),
        Puzzle("m1-016", "rnbqkbnr/1ppp1ppp/p3p3/5P2/2P3P1/1P6/P2PP2P/RNBQKBNR b KQk - 0 5", listOf("d8h4"), 900, listOf("mateIn1")),
        Puzzle("m1-017", "8/6Q1/1k3p1p/pP6/4P1pP/2N2P1R/4K3/1R6 w - - 0 38", listOf("c3a4"), 900, listOf("mateIn1")),
        Puzzle("m1-018", "5k2/5q1p/8/2P1p3/4p2P/2p1K3/2P5/8 b - - 0 40", listOf("f7f3"), 900, listOf("mateIn1")),
        Puzzle("m1-019", "8/5p1p/Pppk3p/P6P/3Pp1PK/P7/8/6q1 b - - 4 38", listOf("g1h2"), 900, listOf("mateIn1")),
        Puzzle("m1-020", "4k3/2p5/2P5/pP3Ppp/6N1/4P1p1/5p2/7K b - - 1 45", listOf("f2f1q"), 900, listOf("mateIn1")),
        Puzzle("m1-021", "7k/p1ppr2p/P7/2p1Ppq1/6PP/P4p2/2P2P1R/4K3 b - - 0 27", listOf("g5c1"), 900, listOf("mateIn1")),
        Puzzle("m1-022", "8/1r5p/p2pkP2/p1P3P1/P2p4/3Ppp2/8/4K3 b - - 2 39", listOf("b7b1"), 900, listOf("mateIn1")),
        Puzzle("m1-023", "1n1Q2nR/1pp1pk2/r4pp1/p2p4/1PP5/P2P4/1B3PP1/RN3KN1 w - - 1 21", listOf("d8g8"), 900, listOf("mateIn1")),
        Puzzle("m1-024", "1n1q1bnr/r1p1pkp1/3p4/1p2p2p/p4P1P/N2B3P/PPPP1K2/R1B3QR w - - 2 11", listOf("g1g6"), 900, listOf("mateIn1")),
        Puzzle("m1-025", "1r1k1b2/ppn1pppr/3q3n/7p/1PP1P2P/2p2P2/P2NK1PR/R4B2 b - - 1 17", listOf("d6d2"), 900, listOf("mateIn1")),
        Puzzle("m1-026", "1r2k3/2p1Pb2/p2P2r1/2P1Pp1p/1p5P/5B2/1P4P1/6RK w - - 1 33", listOf("f3c6"), 900, listOf("mateIn1")),
        Puzzle("m1-027", "4kb1r/2ppppp1/Qp3n1p/8/p3P2P/P4P2/1PPP2P1/1RB1KBNR w Kk - 0 13", listOf("a6a8"), 900, listOf("mateIn1")),
        Puzzle("m1-028", "1rb5/3k1p1r/4pP2/p1K2PP1/Pp1pP3/1P6/1b5P/5q2 b - - 0 33", listOf("f1c1"), 900, listOf("mateIn1")),
        Puzzle("m1-029", "1r1k2br/1pp4p/2P2p1P/4p3/2P1p3/P7/1p1KPP2/7q b - - 0 21", listOf("h1c1"), 900, listOf("mateIn1")),
        Puzzle("m1-030", "1k6/6R1/4QP1p/p6P/P7/KPpP4/8/8 w - - 4 44", listOf("e6e8"), 900, listOf("mateIn1")),
        Puzzle("m1-031", "5b2/4p3/2P2kPp/4p3/p6P/pP1p1q2/P7/R3K3 b - - 3 37", listOf("f3e2"), 900, listOf("mateIn1")),
        Puzzle("m1-032", "r1bqkbnr/ppppp1p1/2n2p1p/8/6Q1/4P1P1/PPPP1P1P/RNB1KBNR w KQkq - 0 4", listOf("g4g6"), 900, listOf("mateIn1")),
        Puzzle("m1-033", "r1bqkbnr/p1pp1p1p/1pn1p1p1/8/6P1/P4P2/RPPPP2P/1NBQKBNR b Kkq - 0 5", listOf("d8h4"), 900, listOf("mateIn1")),
        Puzzle("m1-034", "rnbqk1nr/1pppbppp/p7/8/1P1p1PP1/B7/P1PPP2P/RN1QKB1R b KQkq g3 0 6", listOf("e7h4"), 900, listOf("mateIn1")),
        Puzzle("m1-035", "r1b3Q1/2nk4/7b/1p2n1p1/pp6/P1PpPP2/3K4/R1B2q2 b - - 1 38", listOf("f1e2"), 900, listOf("mateIn1")),
        Puzzle("m1-036", "8/6r1/p1k5/P1P5/3Q1pP1/5P1P/8/1R5K w - - 1 45", listOf("d4d6"), 900, listOf("mateIn1")),
        Puzzle("m1-037", "6k1/p3p3/2p4P/2PP4/K2P2Pp/8/5r2/1q6 b - - 3 45", listOf("f2a2"), 900, listOf("mateIn1")),
        Puzzle("m1-038", "r1b1k1r1/p3p2p/Pq1p2pn/2p3P1/7P/1P2pP2/P2PP2b/2BQKB2 b q - 0 17", listOf("h2g3"), 900, listOf("mateIn1")),
        Puzzle("m1-039", "6r1/p2k1p2/2p2P2/3P4/pP2p2P/8/7K/4q3 b - - 0 37", listOf("e1h4"), 900, listOf("mateIn1")),
        Puzzle("m1-040", "2r4r/p1p1k1p1/1P2b2n/p3P2p/2pKP1PP/8/5P2/4q3 b - - 0 34", listOf("c7c5"), 900, listOf("mateIn1")),
        Puzzle("m1-041", "3rkr2/2Q2p2/3P1Ppp/pp6/3p4/1P1P2P1/P4P1R/RK3B2 w - - 1 27", listOf("c7e7"), 900, listOf("mateIn1")),
        Puzzle("m1-042", "r1bk2nr/1ppp1p1p/p3p3/2Q3p1/1P5P/P4P2/2P1P1PR/1qB1KBN1 w - - 0 11", listOf("c5f8"), 900, listOf("mateIn1")),
        Puzzle("m1-043", "3r2k1/2p5/8/P5p1/1p4p1/5pP1/5P2/5K2 b - - 3 40", listOf("d8d1"), 900, listOf("mateIn1")),
        Puzzle("m1-044", "rnbqkbnr/p1ppp1p1/p6p/8/6Q1/N1P1p3/PP1P1PPP/R1B1K1NR w KQkq - 0 6", listOf("g4g6"), 900, listOf("mateIn1")),
        Puzzle("m1-045", "r2k4/p7/1P1p1P2/7p/b1p2n1P/2PP1P2/1P2r3/1NK4R b - - 1 29", listOf("f4d3"), 900, listOf("mateIn1")),
        Puzzle("m1-046", "5Q2/7k/2p1p3/7B/1P5p/2P3PK/1P6/R7 w - - 0 36", listOf("a1a7"), 900, listOf("mateIn1")),
        Puzzle("m1-047", "rnbqkbnr/2pp3p/8/pp1Nppp1/5P1P/4P3/PPPP2P1/R1BQKBNR w KQkq g6 0 6", listOf("d1h5"), 900, listOf("mateIn1")),
        Puzzle("m1-048", "6rr/2p3bk/3p3p/p6P/PpP1PP2/3B1K2/2R5/8 w - - 3 31", listOf("e4e5"), 900, listOf("mateIn1")),
        Puzzle("m1-049", "6k1/2p1P2p/2p4P/P4p2/6n1/7p/2PPp3/4K3 w - - 1 41", listOf("e7e8q"), 900, listOf("mateIn1")),
        Puzzle("m1-050", "4k3/2P2p2/1P1ppPpr/r6p/7P/3P1K2/8/8 w - - 1 44", listOf("c7c8q"), 900, listOf("mateIn1")),
        Puzzle("m1-051", "2Q5/6Rp/3k3P/1p5P/pP1pP3/P4K2/P7/2B5 w - - 3 45", listOf("c1f4"), 900, listOf("mateIn1")),
        Puzzle("m1-052", "6Q1/3k4/p7/P1Q2Pp1/4P1K1/N1B5/3P4/8 w - - 0 37", listOf("g8c8"), 900, listOf("mateIn1")),
        Puzzle("m1-053", "rnb1kb1r/ppp1pp1p/6pn/3p4/3P1qP1/1P5B/P1P1PP1P/RNQ1K1NR b KQkq - 4 7", listOf("f4c1"), 900, listOf("mateIn1")),
        Puzzle("m1-054", "8/2p1Q1p1/2kP3p/1p1p4/3P1P1P/2p5/2P1N3/4K2b w - - 1 31", listOf("e7c7"), 900, listOf("mateIn1")),
        Puzzle("m1-055", "3Q2Q1/2p5/7k/1P4pp/1P2ppP1/N3P3/5PN1/5K2 w - - 1 38", listOf("d8f8"), 900, listOf("mateIn1")),
        Puzzle("m1-056", "2b3r1/5r1p/pP6/2k4P/4P3/3pK3/3P4/8 b - - 4 43", listOf("g8g3"), 900, listOf("mateIn1")),
        Puzzle("m1-057", "5k2/8/4PP1p/p6P/3p3P/3P2K1/2R5/R7 w - - 1 44", listOf("c2c8"), 900, listOf("mateIn1")),
        Puzzle("m1-058", "1rb4k/1p1p1p2/3p4/5P1p/P6P/2P3p1/3p2P1/5KR1 b - - 1 35", listOf("d2d1q"), 900, listOf("mateIn1")),
        Puzzle("m1-059", "rnbqkbnr/p1pp1ppp/1p2p3/8/5PP1/7P/PPPPP3/RNBQKBNR b KQkq f3 0 3", listOf("d8h4"), 900, listOf("mateIn1")),
        Puzzle("m1-060", "r2q4/1pp2p1p/p6P/1k1P4/7p/P1Q4P/RP1KP2P/5B1R w - - 1 23", listOf("c3b4"), 900, listOf("mateIn1")),
        Puzzle("m1-061", "7k/3R4/8/2Q4p/p1p1p3/1pP2P1P/P7/3K4 w - - 2 41", listOf("c5c8"), 900, listOf("mateIn1")),
        Puzzle("m1-062", "rn1q1bnr/1pp2kp1/p2p4/5p2/4Pp2/P2N1P2/1PPP1K1P/RNBB3q b - - 1 14", listOf("h8h2"), 900, listOf("mateIn1")),
        Puzzle("m1-063", "rnbqkbnr/pp1pp3/5p2/2p3p1/3P1P1p/3Q2PN/PPP1P2P/RNB1KB1R w KQkq - 0 6", listOf("d3g6"), 900, listOf("mateIn1")),
        Puzzle("m1-064", "Q2b2nr/2k5/1p2p2p/1Q5P/p1pp1pP1/P1PP1P1B/4P1K1/R5NR w - - 1 27", listOf("b5c6"), 900, listOf("mateIn1")),
        Puzzle("m1-065", "1R6/8/p1P2p1p/2P1k2P/2ppP1P1/5PK1/4NQ2/8 w - - 1 43", listOf("b8e8"), 900, listOf("mateIn1")),
        Puzzle("m1-066", "rn2kb1r/p3ppp1/6Pp/1pp5/P1p3Q1/3P4/1P1P1KPP/RNB2B1R w kq - 0 15", listOf("g4c8"), 900, listOf("mateIn1")),
        Puzzle("m1-067", "2rq1br1/p1p1k1pp/3p4/p3PQ2/3P1Nn1/P1P5/1P4PP/RNB2RK1 w - - 1 15", listOf("f5e6"), 900, listOf("mateIn1")),
        Puzzle("m1-068", "rnbqkbnr/pppp1ppp/4p3/8/5PP1/8/PPPPP2P/RNBQKBNR b KQkq g3 0 2", listOf("d8h4"), 900, listOf("mateIn1")),
        Puzzle("m1-069", "2k5/8/1Q1B4/7p/R6P/1PP1ppP1/2P1PP2/5KR1 w - - 0 36", listOf("b6c7"), 900, listOf("mateIn1")),
        Puzzle("m1-070", "8/1np5/3p4/p2P4/P1Pb1kp1/2pK4/8/q7 b - - 7 44", listOf("a1d1"), 900, listOf("mateIn1")),
        Puzzle("m1-071", "1QQ5/5k2/p4p2/3P1P2/8/Pp1p3P/1B1P1p2/5R1K w - - 0 44", listOf("b8b7"), 900, listOf("mateIn1")),
        Puzzle("m1-072", "5Q2/3k4/prp4R/1p2pP2/2P5/P3P3/1P2N3/1RK5 w - - 0 45", listOf("h6h7"), 900, listOf("mateIn1")),
        Puzzle("m1-073", "5br1/1p2k3/p1qp2pr/5P1p/1P1PPp1P/1P1R1N2/P3Q3/K4B2 b - - 1 30", listOf("c6c1"), 900, listOf("mateIn1")),
        Puzzle("m1-074", "1nb2k2/rp1p2pp/1qp5/4P3/1P2P3/p4Pp1/P1PN2PP/R4KNR b - - 0 16", listOf("b6f2"), 900, listOf("mateIn1")),
        Puzzle("m1-075", "rnb2r2/p1k2pp1/Bp2pn2/7p/P2P3P/4Pp2/1P1N1PPR/q1B1K3 b - - 1 19", listOf("a1c1"), 900, listOf("mateIn1")),
        Puzzle("m1-076", "6k1/4Q1pp/8/5P2/3p4/2pp3B/P1p2P1K/2R5 w - - 0 36", listOf("e7e8"), 900, listOf("mateIn1")),
        Puzzle("m1-077", "4Q3/2p5/k1P2p1p/P5P1/P2P4/7q/2K5/8 w - - 2 43", listOf("e8a8"), 900, listOf("mateIn1")),
        Puzzle("m1-078", "6k1/3Q4/5pP1/3p1P2/1ppp1P2/2P5/P7/4R1K1 w - - 2 45", listOf("e1e8"), 900, listOf("mateIn1")),
        Puzzle("m1-079", "8/5p1p/7P/p5p1/P1b1QPk1/2p1P3/1rP3PR/3K2N1 b - - 1 37", listOf("b2b1"), 900, listOf("mateIn1")),
        Puzzle("m1-080", "3Q4/p6p/p2p3p/P6k/4B3/6PP/RP2P3/4K3 w - - 1 34", listOf("g3g4"), 900, listOf("mateIn1")),
        Puzzle("m1-081", "rnbqkbnr/ppppp2p/5p2/1B4p1/4P3/8/PPPP1PPP/RNBQK1NR w KQkq g6 0 3", listOf("d1h5"), 900, listOf("mateIn1")),
        Puzzle("m1-082", "1nbqkbnr/2ppp2p/p7/p4Pp1/1P3P2/8/P1PP2PP/RNBQK1NR w KQk g6 0 7", listOf("d1h5"), 900, listOf("mateIn1")),
        Puzzle("m1-083", "r2k4/3p1pr1/3b4/pp1PpPpp/1P2P2P/8/PB5R/3q1B1K b - - 1 31", listOf("d1f1"), 900, listOf("mateIn1")),
        Puzzle("m1-084", "1k6/p7/6p1/5pPp/1P2ppPP/p6K/P6R/4q3 b - - 3 37", listOf("e1g3"), 900, listOf("mateIn1")),
        Puzzle("m1-085", "rn2kbnr/ppp1pp1p/1q6/3p2p1/6QP/4P1P1/PPPP1P2/RNB1KBNR w KQkq - 1 6", listOf("g4c8"), 900, listOf("mateIn1")),
        Puzzle("m1-086", "5kr1/p2Ppp2/2r2P2/P1p5/2P4p/1P6/K7/8 w - - 1 38", listOf("d7d8q"), 900, listOf("mateIn1")),
        Puzzle("m1-087", "rnbqkbnr/2ppp2p/5p2/1p4p1/p2PP3/P1P5/1P3PPP/RNBQKBNR w KQkq g6 0 6", listOf("d1h5"), 900, listOf("mateIn1")),
        Puzzle("m1-088", "8/4Q3/5P1k/1p6/3p1pPp/P5p1/4K3/6R1 w - - 3 40", listOf("e7g7"), 900, listOf("mateIn1")),
        Puzzle("m1-089", "5r2/2pk3p/3P3b/1p4p1/P1r1p1PP/4K3/8/1q6 b - - 0 41", listOf("b1e1"), 900, listOf("mateIn1")),
        Puzzle("m1-090", "r1bk1b2/2pp1pp1/n2p4/p7/R1P2p1q/1P4P1/3PPP2/2Q2K2 b - - 0 16", listOf("h4h1"), 900, listOf("mateIn1")),
        Puzzle("m2-001", "5N1k/8/p1p4p/P3p2P/1PNpP3/4P3/1B5K/6R1 w - - 0 42", listOf("c4e5", "d4d3", "e5d3"), 1300, listOf("mateIn2")),
        Puzzle("m2-002", "8/5k2/p7/3p2pp/4P3/1P3pPp/3P3P/7K b - - 1 32", listOf("f3f2", "d2d3", "f2f1q"), 1300, listOf("mateIn2")),
        Puzzle("m2-003", "8/8/p5Qk/6p1/6Pp/1P1p3p/3P1p1P/7K b - - 2 38", listOf("h6g6", "b3b4", "f2f1q"), 1300, listOf("mateIn2")),
        Puzzle("m2-004", "4k3/7Q/ppp5/5p1p/1P1P4/4p3/2P3PP/R5K1 w - - 0 34", listOf("a1a6", "e3e2", "a6a8"), 1300, listOf("mateIn2")),
        Puzzle("m2-005", "1r1qkb1r/p1p1p1p1/p1Pp3p/5p2/PP6/7P/2PPQP1P/RNB1K2R w KQk f6 0 10", listOf("e2h5", "g7g6", "h5g6"), 1300, listOf("mateIn2")),
        Puzzle("m2-006", "2b1k3/2P1p3/4n3/1PP3p1/6p1/p2p2P1/K5P1/4q3 b - - 0 38", listOf("e1c3", "a2b1", "c3b2"), 1300, listOf("mateIn2")),
        Puzzle("m2-007", "2b1k3/2P1p3/8/2P3p1/6p1/p2p2P1/K5P1/3q4 b - - 0 40", listOf("c8e6", "a2a3", "d1b3"), 1300, listOf("mateIn2")),
        Puzzle("m2-008", "2rqkb2/1ppnp3/3p1p2/p5rp/1PPP4/N2B4/P2P1P1P/R1BQK2R w KQ h6 0 14", listOf("d1h5", "g5h5", "d3g6"), 1300, listOf("mateIn2")),
        Puzzle("m2-009", "3Q4/2p2p1k/8/pP1P1p2/P4R2/2P3KP/6P1/2B5 w - - 5 40", listOf("d8g5", "c7c6", "f4h4"), 1300, listOf("mateIn2")),
        Puzzle("m2-010", "rnbr4/pp1p1p2/3P3n/1P2p1p1/4Pk1p/P1N5/2BPKPPP/R1B2Q1R w - - 0 19", listOf("f2f3", "h4h3", "d2d3"), 1300, listOf("mateIn2")),
        Puzzle("m2-011", "rnb4r/pp1p1p2/3P3n/1P2p1p1/4Pk1p/P1N5/3PKPPP/R1BB1Q1R w - - 2 20", listOf("d2d3", "f4g4", "e2e1"), 1300, listOf("mateIn2")),
        Puzzle("m2-012", "r1bqkbnr/pp1pp1p1/n4p1p/2p5/2P1P2P/P7/1P1P1PP1/RNBQKBNR w KQkq - 0 5", listOf("d1h5", "g7g6", "h5g6"), 1300, listOf("mateIn2")),
        Puzzle("m2-013", "r1bqkbnr/pp1pp1p1/n6p/2p2p2/P1P1P2P/8/1P1P1PP1/RNBQKBNR w KQkq - 0 6", listOf("d1h5", "g7g6", "h5g6"), 1300, listOf("mateIn2")),
        Puzzle("m2-014", "rnbqkbnr/1pppp1p1/7p/p4p2/4P3/2PP4/PP3PPP/RNBQKBNR w KQkq a6 0 4", listOf("d1h5", "g7g6", "h5g6"), 1300, listOf("mateIn2")),
        Puzzle("m2-015", "r1qk2nQ/p1ppp1b1/bpP3p1/2P2p1p/7P/1P2B3/P3PPP1/RN2KBNR w KQ - 1 11", listOf("h8g8", "g7f8", "g8f8"), 1300, listOf("mateIn2")),
        Puzzle("m2-016", "3Q4/k1pp3r/ppP3p1/3Pp2p/8/3P1P2/P5PP/4KBR1 w - - 1 23", listOf("d8c7", "a7a8", "c7b7"), 1300, listOf("mateIn2")),
        Puzzle("m2-017", "3Q4/k1pp4/ppP3pr/3Pp2p/7P/3P1P2/P5P1/4KBR1 w - - 1 24", listOf("d8c7", "a7a8", "c7b7"), 1300, listOf("mateIn2")),
        Puzzle("m2-018", "rr6/p4kpp/P6P/P1pppP2/3P4/q4P2/3K4/4R1N1 b - - 1 33", listOf("a3a2", "d2c1", "b8b1"), 1300, listOf("mateIn2")),
        Puzzle("m2-019", "r7/p4kpp/P6P/P2pRP2/2pP4/qr3P2/2K5/6N1 b - - 0 35", listOf("a3a2", "c2c1", "b3b1"), 1300, listOf("mateIn2")),
        Puzzle("m2-020", "r7/p4k1p/P6p/P2p1P2/2pP4/qr3P2/2K1R3/6N1 b - - 1 36", listOf("a3a2", "c2c1", "b3b1"), 1300, listOf("mateIn2")),
        Puzzle("m2-021", "3Q4/2p5/1p5k/P2p2p1/6P1/5p2/1pPB4/1R3K2 w - - 1 35", listOf("d8g8", "f3f2", "d2g5"), 1300, listOf("mateIn2")),
        Puzzle("m2-022", "2R5/p3kr2/3p4/p2P1pPp/3p4/2p5/P2PK2P/2B1Q2R w - - 0 27", listOf("e2d1", "e7d7", "e1e6"), 1300, listOf("mateIn2")),
        Puzzle("m2-023", "2bqkbnr/r1pp1ppp/n2p4/pp6/8/1P3P1P/P1PPP1P1/R1BQKBNR b KQk - 0 6", listOf("d8h4", "g2g3", "h4g3"), 1300, listOf("mateIn2")),
        Puzzle("m2-024", "Q7/2p4k/2P5/p2B3p/P6P/2KPpp1P/5P2/8 w - - 2 43", listOf("a8f8", "e3e2", "d5e4"), 1300, listOf("mateIn2")),
        Puzzle("m2-025", "Q7/2p2B1k/2P5/p6p/P6P/2KP1p1P/4pP2/8 w - - 0 44", listOf("a8g8", "h7h6", "g8h8"), 1300, listOf("mateIn2")),
        Puzzle("m2-026", "Q7/2p2B1k/2P5/p6p/P1K4P/3P1p1P/5P2/4q3 w - - 0 45", listOf("a8g8", "h7h6", "g8h8"), 1300, listOf("mateIn2")),
        Puzzle("m2-027", "6nr/r2k2p1/p1b4p/5PP1/8/2p1p1RK/P3P1P1/5q2 b - - 0 38", listOf("h6g5", "h3g4", "f1f4"), 1300, listOf("mateIn2")),
        Puzzle("m2-028", "2k5/2p5/2P4p/P1P1p3/4p2p/3p1P1P/3K2P1/1q6 b - - 0 44", listOf("b1c2", "d2e1", "c2e2"), 1300, listOf("mateIn2")),
        Puzzle("m2-029", "2bk3r/2r1b3/p3p1p1/RpP1Kpp1/2p5/2P1P2P/3B2P1/5B1R b - - 3 28", listOf("c7c5", "e5d4", "c5d5"), 1300, listOf("mateIn2")),
        Puzzle("m2-030", "r1bq1b1r/2pp3p/2P2pkp/p3p3/4P2P/2P5/PP2BPP1/RN1QK1NR w KQ - 2 10", listOf("e2h5", "g6g7", "d1g4"), 1300, listOf("mateIn2")),
        Puzzle("m2-031", "r2qkb1r/p1pp1ppp/bp2p3/8/1P2P3/4P2P/PBPP2P1/RN1QK1NR b KQkq - 0 7", listOf("d8h4", "g2g3", "h4g3"), 1300, listOf("mateIn2")),
        Puzzle("m2-032", "3r4/3qkp2/1p5b/4pP1p/2p1P2P/1Pp3P1/2P1K3/8 b - - 0 34", listOf("d7d1", "e2f2", "d8d2"), 1300, listOf("mateIn2")),
        Puzzle("m2-033", "r1q1kb1r/p1p1pppp/1pnp1n2/1N6/2K1P3/2P4P/PP1P1PP1/R1BQ1BNR b kq - 0 8", listOf("c8e6", "c4d3", "e6e4"), 1300, listOf("mateIn2")),
        Puzzle("m2-034", "k7/3R4/p5pP/P1pP4/4p2P/1P2P3/7P/R3B1K1 w - - 1 34", listOf("h6h7", "c5c4", "h7h8q"), 1300, listOf("mateIn2")),
        Puzzle("m2-035", "k7/3R4/p5pP/P2P4/2p1p2P/1P2P3/R6P/4B1K1 w - - 0 35", listOf("a2f2", "c4c3", "f2f8"), 1300, listOf("mateIn2")),
        Puzzle("m2-036", "k7/4R3/p5pP/P2P4/4p2P/1p2P3/R6P/4B1K1 w - - 0 36", listOf("a2f2", "b3b2", "f2f8"), 1300, listOf("mateIn2")),
        Puzzle("m2-037", "2Q5/5k2/4R3/6pP/1Pp3p1/p5P1/P4P2/2K5 w - - 1 43", listOf("h5h6", "c4c3", "c8e8"), 1300, listOf("mateIn2")),
        Puzzle("m2-038", "8/1k6/3B2p1/p1p2pP1/P1P2P2/4P1P1/2R1PK2/4QB1R w - - 5 37", listOf("e1a5", "b7c6", "a5c7"), 1300, listOf("mateIn2")),
        Puzzle("m2-039", "8/8/3k2p1/p1p2pP1/P1P2P2/4P1P1/2R1PK2/Q4B1R w - - 0 39", listOf("h1h7", "d6c6", "a1f6"), 1300, listOf("mateIn2")),
        Puzzle("m2-040", "1n4n1/1r4k1/3P4/p3p3/6Pp/P2Pp3/7P/7K b - - 0 36", listOf("h4h3", "h1g1", "b7b1"), 1300, listOf("mateIn2")),
        Puzzle("m2-041", "r1bqkbnr/ppppp1p1/n6p/5p2/1P2P3/N7/P1PP1PPP/R1BQKBNR w KQkq - 1 4", listOf("d1h5", "g7g6", "h5g6"), 1300, listOf("mateIn2")),
        Puzzle("m2-042", "r1bqkbnr/p1ppp1p1/p6p/5p2/1P2P3/N7/P1PP1PPP/R1BQK1NR w KQkq - 0 5", listOf("d1h5", "g7g6", "h5g6"), 1300, listOf("mateIn2")),
        Puzzle("m2-043", "1r2kb2/q1p3pr/4p3/3pPP1p/pP4PP/N1K2P2/P1P5/R6R b - - 1 25", listOf("a7e3", "c3b2", "b8b4"), 1300, listOf("mateIn2")),
        Puzzle("m2-044", "5b2/1p2k1r1/r3Pp1n/2p5/Pp1PPP1P/8/1P6/K7 b - - 2 29", listOf("b4b3", "a1b1", "g7g1"), 1300, listOf("mateIn2")),
        Puzzle("m2-045", "4k3/5Pr1/p2P3p/2P1K1p1/br4P1/7P/P7/6q1 b - - 0 38", listOf("e8f7", "a2a3", "g1c5"), 1300, listOf("mateIn2")),
    )
}
