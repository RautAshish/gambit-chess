package com.chessapp.ui.online

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chessapp.data.online.OnlineGame
import com.chessapp.data.online.RestOnlineRepository
import com.chessapp.data.online.RestOnlineRepository.Outcome
import com.chessapp.data.prefs.SettingsRepository
import com.chessapp.domain.engine.GameEngine
import com.chessapp.domain.engine.MoveGenerator
import com.chessapp.domain.engine.Notation
import com.chessapp.domain.model.Color
import com.chessapp.domain.model.Move
import com.chessapp.domain.model.PieceType
import com.chessapp.domain.model.Square
import com.chessapp.ui.board.BoardUiState
import com.chessapp.ui.sound.SoundManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class OnlineUiState(
    val configured: Boolean = false,
    val phase: Phase = Phase.LOBBY,
    val code: String = "",
    val myColor: Color? = null,
    val board: BoardUiState = BoardUiState(),
    val moveList: String = "",
    val statusLine: String = "",
    val gameOver: Boolean = false,
    val resultLine: String = "",
    val error: String = "",
    val busy: Boolean = false
) { enum class Phase { LOBBY, WAITING, PLAYING } }

class OnlineViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs: SettingsRepository = SettingsRepository(app)


    private val sound = SoundManager(app)
    private var soundOn = true
    private var hapticsOn = true
    private var inForeground = true
    private var cfg = "" to ""
    private var repo: RestOnlineRepository? = null
    private var myUid: String = ""
    private var game: OnlineGame? = null
    private var selected: Square? = null
    private var pollJob: Job? = null

    private val _state = MutableStateFlow(OnlineUiState())
    val state: StateFlow<OnlineUiState> = _state

    init {
        // Live settings: saving Firebase config takes effect on returning to the
        // lobby — no process restart. The repo only swaps while in the LOBBY so
        // an active game never loses its transport mid-match.
        viewModelScope.launch {
            prefs.settings.collect { s ->
                soundOn = s.soundEnabled
                hapticsOn = s.hapticsEnabled
                val newCfg = s.onlineProjectId to s.onlineApiKey
                val configured = newCfg.first.isNotBlank() && newCfg.second.isNotBlank()
                if (newCfg != cfg && _state.value.phase == OnlineUiState.Phase.LOBBY) {
                    cfg = newCfg
                    repo = if (configured)
                        RestOnlineRepository(newCfg.first, newCfg.second, prefs) else null
                }
                _state.value = _state.value.copy(configured = configured)
            }
        }
    }

    fun onAppResumed() { inForeground = true }
    fun onAppPaused() { inForeground = false }

    fun createGame() = launchBusy {
        val r = repo ?: return@launchBusy
        val uidOut = r.myUid()
        when (uidOut) {
            is Outcome.Err -> { fail(uidOut.message); return@launchBusy }
            is Outcome.Ok -> myUid = uidOut.value
        }
        when (val out = r.createGame()) {
            is Outcome.Ok -> { adopt(out.value.game); startPolling() }
            is Outcome.Err -> fail(out.message)
        }
    }

    fun joinGame(codeRaw: String) = launchBusy {
        val r = repo ?: return@launchBusy
        val code = codeRaw.trim().uppercase()
        if (code.length != 6) { fail("Codes are 6 characters"); return@launchBusy }
        val uidOut = r.myUid()
        when (uidOut) {
            is Outcome.Err -> { fail(uidOut.message); return@launchBusy }
            is Outcome.Ok -> myUid = uidOut.value
        }
        when (val out = r.joinGame(code)) {
            is Outcome.Ok -> { adopt(out.value.game); startPolling() }
            is Outcome.Err -> fail(out.message)
        }
    }

    fun onSquareTapped(sq: Square) {
        val g = game ?: return
        if (_state.value.gameOver || _state.value.busy) return
        val engine = engineFor(g)
        if (g.colorOf(myUid) != engine.board.sideToMove) return   // not my turn
        val sel = selected
        if (sel != null && sq != sel) {
            val legal = MoveGenerator.legalMoves(engine.board)
                .filter { it.from == sel && it.to == sq }
            if (legal.isNotEmpty()) {
                val move = legal.firstOrNull { it.promotion == PieceType.QUEEN } ?: legal.first()
                selected = null
                submit(move)
                return
            }
        }
        val piece = engine.board.pieceAt(sq)
        selected = if (piece != null && piece.color == engine.board.sideToMove &&
            piece.color == g.colorOf(myUid)) sq else null
        push()
    }

    private fun submit(move: Move) = launchBusy {
        val r = repo ?: return@launchBusy
        val code = game?.id ?: return@launchBusy
        when (val out = r.submitMove(code, move.toUci())) {
            is Outcome.Ok -> adopt(out.value.game)
            is Outcome.Err -> fail(out.message)
        }
    }

    fun resign() = launchBusy {
        val r = repo ?: return@launchBusy
        val code = game?.id ?: return@launchBusy
        when (val out = r.resign(code)) {
            is Outcome.Ok -> adopt(out.value.game)
            is Outcome.Err -> fail(out.message)
        }
    }

    fun backToLobby() {
        pollJob?.cancel(); game = null; selected = null
        _state.value = _state.value.copy(
            phase = OnlineUiState.Phase.LOBBY, code = "", error = "",
            gameOver = false, resultLine = "", moveList = ""
        )
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                delay(2_500)
                val r = repo ?: break
                val code = game?.id ?: break
                if (_state.value.gameOver) break
                when (val out = r.refresh(code)) {
                    is Outcome.Ok ->
                        if (out.value.game.updatedAt != game?.updatedAt ||
                            out.value.game.blackUid != game?.blackUid) adopt(out.value.game)
                    is Outcome.Err -> { /* transient network: keep polling */ }
                }
            }
        }
    }

    private fun adopt(g: OnlineGame) {
        val r = repo
        if (r != null && !r.historyIsValid(g)) {
            fail("This game’s data is invalid and can’t be displayed safely."); return
        }
        val prev = game
        game = g
        maybeCue(prev, g)
        push()
    }

    /** Same felt feedback as the local game: thud per new ply (yours, or the
     *  opponent's arriving via poll), check ping, end chime — foreground only. */
    private fun maybeCue(prev: OnlineGame?, g: OnlineGame) {
        if (!inForeground || prev == null) return
        val live = listOf("ONGOING", "CHECK")
        if (g.status !in live && prev.status in live) {
            sound.play(SoundManager.Cue.GAME_END, soundOn, hapticsOn); return
        }
        if (g.moves.size > prev.moves.size) {
            val cue = when {
                g.status == "CHECK" -> SoundManager.Cue.CHECK
                captureOnLastPly(g) -> SoundManager.Cue.CAPTURE
                else -> SoundManager.Cue.MOVE
            }
            sound.play(cue, soundOn, hapticsOn)
        }
    }

    private fun captureOnLastPly(g: OnlineGame): Boolean {
        if (g.moves.isEmpty()) return false
        val e = GameEngine()
        for (u in g.moves.dropLast(1)) e.makeMove(Move.fromUci(u))
        return e.board.pieceAt(Move.fromUci(g.moves.last()).to) != null
    }

    private fun engineFor(g: OnlineGame): GameEngine {
        val e = GameEngine()
        for (u in g.moves) e.makeMove(Move.fromUci(u))
        return e
    }

    private fun push() {
        val g = game ?: return
        val engine = engineFor(g)
        val my = g.colorOf(myUid)
        val waiting = g.blackUid.isEmpty()
        val over = g.status !in listOf("ONGOING", "CHECK")
        val turn = engine.board.sideToMove
        val statusLine = when {
            waiting -> "Share the code — waiting for an opponent\u2026"
            over -> ""
            turn == my -> "Your move"
            else -> "Opponent's move\u2026"
        }
        val resultLine = if (!over) "" else when (g.status) {
            "CHECKMATE" -> (if (g.winnerUid == myUid) "You win" else "You lose") + " \u00B7 checkmate"
            "RESIGNED" -> (if (g.winnerUid == myUid) "You win" else "You lose") + " \u00B7 resignation"
            else -> "Draw \u00B7 " + when (g.status) {
                "STALEMATE" -> "stalemate"
                "DRAW_FIFTY_MOVE" -> "fifty-move rule"
                "DRAW_REPETITION" -> "threefold repetition"
                "DRAW_INSUFFICIENT_MATERIAL" -> "insufficient material"
                else -> g.status.lowercase().replace('_', ' ')
            }
        }
        val targets = selected?.let { s ->
            MoveGenerator.legalMoves(engine.board).filter { it.from == s }.map { it.to }.toSet()
        } ?: emptySet()
        _state.value = _state.value.copy(
            phase = if (waiting) OnlineUiState.Phase.WAITING else OnlineUiState.Phase.PLAYING,
            code = g.id, myColor = my,
            board = BoardUiState(
                board = engine.board, selected = selected, legalTargets = targets,
                lastMove = engine.moveHistory().lastOrNull()
            ),
            moveList = Notation.toPgnMoveText(engine.annotatedHistory()),
            statusLine = statusLine, gameOver = over, resultLine = resultLine,
            error = "", busy = false
        )
    }

    private fun fail(msg: String) {
        _state.value = _state.value.copy(error = msg, busy = false)
    }

    private fun launchBusy(block: suspend () -> Unit) {
        _state.value = _state.value.copy(busy = true, error = "")
        viewModelScope.launch { block(); _state.value = _state.value.copy(busy = false) }
    }

    override fun onCleared() { pollJob?.cancel() }
}
