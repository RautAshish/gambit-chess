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

class OnlineViewModel(
    app: Application,
    private val prefs: SettingsRepository = SettingsRepository(app)
) : AndroidViewModel(app) {

    private var repo: RestOnlineRepository? = null
    private var myUid: String = ""
    private var game: OnlineGame? = null
    private var selected: Square? = null
    private var pollJob: Job? = null

    private val _state = MutableStateFlow(OnlineUiState())
    val state: StateFlow<OnlineUiState> = _state

    init {
        viewModelScope.launch {
            val s = prefs.settings.first()
            val configured = s.onlineProjectId.isNotBlank() && s.onlineApiKey.isNotBlank()
            if (configured) repo = RestOnlineRepository(s.onlineProjectId, s.onlineApiKey, prefs)
            _state.value = _state.value.copy(configured = configured)
        }
    }

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
            fail("Game state failed validation — opponent client is not trustworthy"); return
        }
        game = g
        push()
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
            else -> "Draw \u00B7 " + g.status.lowercase().replace('_', ' ')
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
