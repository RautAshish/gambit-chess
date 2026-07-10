package com.chessapp.engine.stockfish

import com.chessapp.domain.engine.MoveGenerator
import com.chessapp.domain.model.Board
import com.chessapp.domain.model.Move
import com.chessapp.engine.ChessEnginePort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * Talks to a Stockfish binary over the UCI protocol via stdin/stdout.
 *
 * Setup on Android:
 *  1. Bundle a Stockfish executable compiled for each ABI under
 *     src/main/jniLibs/<abi>/libstockfish.so  (or extract from assets to filesDir
 *     and chmod +x at runtime).
 *  2. Pass its absolute path as [enginePath].
 *
 * Stockfish is GPLv3 — distributing it obliges you to honor the GPL (provide source,
 * etc.). Keep NativeEngine as the default and offer this as an opt-in "strong engine".
 *
 * The class is deliberately framework-free so it can be unit-tested against any
 * UCI-speaking process; on a desktop with stockfish installed the same code works.
 */
class StockfishEngine(enginePath: String) : ChessEnginePort {

    private val process: Process =
        ProcessBuilder(enginePath).redirectErrorStream(true).start()
    private val writer = OutputStreamWriter(process.outputStream)
    private val reader = BufferedReader(InputStreamReader(process.inputStream))

    private var skillLevel = 10
    private var moveTimeMillis = 1000L
    private var blunderPct = 0

    init {
        send("uci")
        waitFor("uciok")
        send("isready")
        waitFor("readyok")
        applySkill()
    }

    private fun send(cmd: String) {
        writer.write(cmd); writer.write("\n"); writer.flush()
    }

    /** Read lines until one starts with [token]; returns that line. */
    private fun waitFor(token: String): String {
        while (true) {
            val line = reader.readLine() ?: error("engine closed while waiting for $token")
            if (line.startsWith(token) || line.contains(token)) return line
        }
    }

    private fun applySkill() {
        // Skill Level 0..20 is the ONLY strength knob we use. UCI_LimitStrength
        // is deliberately OFF: its Elo floor is ~1320-1350 — a club player — which
        // made "Level 1" crush beginners (field-reported). Raw low Skill plus a
        // starved clock is genuinely weaker than any UCI_Elo Stockfish accepts.
        send("setoption name Skill Level value $skillLevel")
        send("setoption name UCI_LimitStrength value false")
    }

    override fun setSkill(level: Int) {
        skillLevel = level.coerceIn(0, 20)
        // Think-budget curve: the bottom rungs get MILLISECONDS — at 30ms even
        // Skill 0 wobbles like a human — scaling to real thinking at the top.
        moveTimeMillis = when {
            skillLevel <= 1 -> 30L;  skillLevel <= 3 -> 50L
            skillLevel <= 5 -> 80L;  skillLevel <= 8 -> 150L
            skillLevel <= 10 -> 250L; skillLevel <= 12 -> 400L
            skillLevel <= 14 -> 600L; skillLevel <= 16 -> 900L
            skillLevel <= 18 -> 1300L; else -> 2000L
        }
        // Human-style errors on the low rungs (slightly gentler than the built-in
        // engine's, since low Skill already randomizes among candidate moves).
        blunderPct = when {
            skillLevel <= 1 -> 35; skillLevel <= 3 -> 22
            skillLevel <= 5 -> 12; skillLevel <= 8 -> 6; else -> 0
        }
        applySkill()
    }

    override suspend fun bestMove(board: Board): Move? = withContext(Dispatchers.IO) {
        if (blunderPct > 0 && kotlin.random.Random.nextInt(100) < blunderPct) {
            // A real beginner sometimes just hangs a piece; so does Level 1 now.
            MoveGenerator.legalMoves(board).randomOrNull()?.let { return@withContext it }
        }
        send("position fen ${board.toFen()}")
        send("go movetime $moveTimeMillis")
        val line = waitFor("bestmove")          // e.g. "bestmove e2e4 ponder e7e5"
        val token = line.split(" ").getOrNull(1) ?: return@withContext null
        if (token == "(none)") null else runCatching { Move.fromUci(token) }.getOrNull()
    }

    override fun close() {
        runCatching {
            send("quit")
            // NOTE: stick to API-24-safe Process calls. destroyForcibly() and the
            // timed waitFor(long, TimeUnit) overload require Android API 26 and
            // would crash on Android 7.x (minSdk 24). destroy() exists since API 1
            // and SIGKILLs the engine, which is acceptable after "quit".
            process.destroy()
            // Reap off-thread so the child never lingers as a zombie; waitFor()
            // without a timeout is the only API-24-safe overload, so it must not
            // run on the caller's (possibly main) thread.
            Thread { runCatching { process.waitFor() } }.also { it.isDaemon = true }.start()
        }
        runCatching { writer.close() }
        runCatching { reader.close() }
    }
}
