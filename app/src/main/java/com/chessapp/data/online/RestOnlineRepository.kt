package com.chessapp.data.online

import com.chessapp.data.prefs.SettingsRepository
import org.json.JSONObject

/**
 * Room-code multiplayer on the free Firestore tier (REST, no SDK, no Functions).
 *
 * Trust model (documented in SERVER_SETUP.md): games are private friendly matches
 * joined by a shared 6-char code. BOTH clients run [OnlineGameValidator] on every
 * incoming state, and Firestore rules restrict writes to the two participants,
 * so an illegal move can only come from a deliberately tampered opponent client —
 * in which case the honest client flags the game as corrupt rather than render it.
 * (The Cloud-Functions server-authoritative variant in OnlineRepository.kt remains
 * for a future ranked mode.)
 */
class RestOnlineRepository(
    projectId: String,
    apiKey: String,
    prefs: SettingsRepository
) {
    private val fs = FirestoreRest(projectId, apiKey, prefs)

    sealed interface Outcome<out T> {
        data class Ok<T>(val value: T) : Outcome<T>
        data class Err(val message: String) : Outcome<Nothing>
    }

    data class GameDoc(val game: OnlineGame, val updateTime: String)

    suspend fun myUid(): Outcome<String> = guard { fs.myUid() }

    /** Creates a game with a fresh code; retries on the (rare) code collision. */
    suspend fun createGame(): Outcome<GameDoc> = guard {
        val uid = fs.myUid()
        repeat(5) {
            val code = newCode()
            val g = OnlineGame(
                id = code, whiteUid = uid, blackUid = "",
                moves = emptyList(), fen = com.chessapp.domain.model.Board.START_FEN,
                status = "ONGOING", updatedAt = System.currentTimeMillis()
            )
            if (fs.createGame(code, encode(g))) {
                val doc = fs.getGame(code) ?: error("created game vanished")
                return@guard GameDoc(decode(code, doc.fields), doc.updateTime)
            }
        }
        error("could not allocate a game code")
    }

    /** Joins (or re-enters) a game by code. */
    suspend fun joinGame(code: String): Outcome<GameDoc> = guard {
        val uid = fs.myUid()
        val doc = fs.getGame(code) ?: error("No game found for code $code")
        var g = decode(code, doc.fields)
        if (g.whiteUid == uid || g.blackUid == uid) return@guard GameDoc(g, doc.updateTime)
        if (g.blackUid.isNotEmpty()) error("That game already has two players")
        val joined = g.copy(blackUid = uid, updatedAt = System.currentTimeMillis())
        val ok = fs.patchGame(code, encode(joined),
            listOf("blackUid", "updatedAt"), doc.updateTime)
        if (!ok) error("Someone joined a moment before you — ask for a new code")
        GameDoc(joined, fetch(code).updateTime)
    }

    suspend fun refresh(code: String): Outcome<GameDoc> = guard { fetch(code) }

    /**
     * Validates locally (instant feedback), then writes with an optimistic-
     * concurrency precondition. A lost race returns the fresh authoritative doc.
     */
    suspend fun submitMove(code: String, uci: String): Outcome<GameDoc> = guard {
        val uid = fs.myUid()
        var cur = fetch(code)
        repeat(2) {
            when (val out = OnlineGameValidator.applyMove(cur.game, uid, uci)) {
                is MoveOutcome.Applied -> {
                    val ok = fs.patchGame(
                        code, encode(out.game),
                        listOf("moves", "fen", "status", "winnerUid", "updatedAt"),
                        cur.updateTime
                    )
                    if (ok) return@guard fetch(code)
                    cur = fetch(code)   // lost the race; re-validate against fresh state
                }
                MoveOutcome.NotYourTurn -> error("Not your turn")
                MoveOutcome.IllegalMove -> error("Illegal move")
                MoveOutcome.GameOver -> error("The game is over")
            }
        }
        error("Connection raced twice — try again")
    }

    suspend fun resign(code: String): Outcome<GameDoc> = guard {
        val uid = fs.myUid()
        val cur = fetch(code)
        val g = cur.game
        if (g.colorOf(uid) == null) error("Not a player in this game")
        if (g.status !in listOf("ONGOING", "CHECK")) return@guard cur
        val winner = if (uid == g.whiteUid) g.blackUid else g.whiteUid
        val resigned = g.copy(
            status = "RESIGNED", winnerUid = winner.ifEmpty { null },
            updatedAt = System.currentTimeMillis()
        )
        fs.patchGame(code, encode(resigned),
            listOf("status", "winnerUid", "updatedAt"), cur.updateTime)
        fetch(code)
    }

    /** A game whose history fails validation came from a tampered client. */
    fun historyIsValid(g: OnlineGame): Boolean {
        val e = com.chessapp.domain.engine.GameEngine()
        for (u in g.moves) {
            val m = runCatching { com.chessapp.domain.model.Move.fromUci(u) }.getOrNull()
                ?: return false
            if (!e.makeMove(m)) return false
        }
        return true
    }

    // ---------- helpers ----------

    private suspend fun fetch(code: String): GameDoc {
        val doc = fs.getGame(code) ?: error("Game $code no longer exists")
        return GameDoc(decode(code, doc.fields), doc.updateTime)
    }

    private inline fun <T> guard(block: () -> T): Outcome<T> =
        try { Outcome.Ok(block()) }
        catch (e: Exception) { Outcome.Err(e.message ?: "Network error") }


    private fun encode(g: OnlineGame): JSONObject = JSONObject()
        .put("whiteUid", FirestoreRest.str(g.whiteUid))
        .put("blackUid", FirestoreRest.str(g.blackUid))
        .put("moves", FirestoreRest.arr(g.moves))
        .put("fen", FirestoreRest.str(g.fen))
        .put("status", FirestoreRest.str(g.status))
        .put("winnerUid", FirestoreRest.str(g.winnerUid ?: ""))
        .put("updatedAt", FirestoreRest.int(g.updatedAt))

    private fun decode(code: String, f: JSONObject): OnlineGame = OnlineGame(
        id = code,
        whiteUid = FirestoreRest.getStr(f, "whiteUid"),
        blackUid = FirestoreRest.getStr(f, "blackUid"),
        moves = FirestoreRest.getArr(f, "moves"),
        fen = FirestoreRest.getStr(f, "fen", com.chessapp.domain.model.Board.START_FEN),
        status = FirestoreRest.getStr(f, "status", "ONGOING"),
        winnerUid = FirestoreRest.getStr(f, "winnerUid").ifEmpty { null },
        updatedAt = FirestoreRest.getInt(f, "updatedAt")
    )

    private fun newCode(): String {
        val alphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"   // no 0/O/1/I/L
        return (1..6).map { alphabet.random() }.joinToString("")
    }
}
