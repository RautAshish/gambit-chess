package com.chessapp.engine.stockfish

import com.chessapp.domain.model.Board
import com.chessapp.domain.model.Move
import com.chessapp.engine.ChessEnginePort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.TimeUnit

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
        // Stockfish "Skill Level" is 0..20. We also cap search via UCI_LimitStrength
        // for the lowest tiers so weak settings actually feel weak.
        send("setoption name Skill Level value $skillLevel")
        if (skillLevel < 10) {
            send("setoption name UCI_LimitStrength value true")
            // Rough Elo mapping: skill 0->1350, 9->~2000.
            val elo = 1350 + skillLevel * 70
            send("setoption name UCI_Elo value $elo")
        } else {
            send("setoption name UCI_LimitStrength value false")
        }
    }

    override fun setSkill(level: Int) {
        skillLevel = level.coerceIn(0, 20)
        // More thinking time at higher levels.
        moveTimeMillis = (300L + skillLevel * 120L)
        applySkill()
    }

    override suspend fun bestMove(board: Board): Move? = withContext(Dispatchers.IO) {
        send("position fen ${board.toFen()}")
        send("go movetime $moveTimeMillis")
        val line = waitFor("bestmove")          // e.g. "bestmove e2e4 ponder e7e5"
        val token = line.split(" ").getOrNull(1) ?: return@withContext null
        if (token == "(none)") null else runCatching { Move.fromUci(token) }.getOrNull()
    }

    override fun close() {
        runCatching {
            send("quit")
            if (!process.waitFor(1, TimeUnit.SECONDS)) process.destroyForcibly()
        }
        runCatching { writer.close() }
        runCatching { reader.close() }
    }
}
