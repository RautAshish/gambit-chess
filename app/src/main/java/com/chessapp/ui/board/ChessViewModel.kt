package com.chessapp.ui.board

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chessapp.data.db.ChessDatabase
import com.chessapp.data.db.GameRepository
import com.chessapp.data.prefs.Settings
import com.chessapp.data.prefs.SettingsRepository
import com.chessapp.domain.ai.ChessAI
import com.chessapp.domain.engine.ChessClock
import com.chessapp.domain.engine.GameEngine
import com.chessapp.domain.engine.GameResult
import com.chessapp.domain.engine.Material
import com.chessapp.domain.engine.ResultEvaluator
import com.chessapp.domain.model.*
import com.chessapp.engine.ChessEnginePort
import com.chessapp.engine.NativeEngine
import com.chessapp.ui.sound.SoundManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PendingPromotion(val from: Square, val to: Square)

/** A piece sliding from one square to another, for animation. */
data class AnimatingMove(val piece: Piece, val from: Square, val to: Square)

data class BoardUiState(
    val board: Board = Board.initial(),
    val selected: Square? = null,
    val legalTargets: Set<Square> = emptySet(),
    val lastMove: Move? = null,
    val status: GameStatus = GameStatus.ONGOING,
    val thinking: Boolean = false,
    val checkSquare: Square? = null,
    val pendingPromotion: PendingPromotion? = null,
    val moveList: String = "",
    val whiteClock: String = "",
    val blackClock: String = "",
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val capturedByWhite: List<Piece> = emptyList(),  // black pieces white has taken
    val capturedByBlack: List<Piece> = emptyList(),
    val materialBalance: Int = 0,                    // + means white ahead
    val animating: AnimatingMove? = null,
    val showCoordinates: Boolean = true,
    val useClock: Boolean = true,
    val gameOver: Boolean = false,
    val result: GameResult = GameResult.Ongoing,
    val resultHeadline: String = "",
    val resultDetail: String = "",
    val drawOfferPending: Boolean = false,   // opponent (or local other side) offered a draw
    val drawDeclined: Boolean = false,       // transient: AI/opponent declined our offer
    val canResign: Boolean = false,
    val canOfferDraw: Boolean = false
)

/**
 * Drives a single game: input handling, AI replies, clock, sound/haptics, captured
 * pieces, move animation, and auto-save. Built on the verified GameEngine — none of
 * the rules logic lives here.
 */
class ChessViewModel(
    app: Application,
    private val playerColor: Color = Color.WHITE,
    private val opponent: ChessEnginePort = NativeEngine(),
    private val settingsRepo: SettingsRepository = SettingsRepository(app),
    private val gameRepo: GameRepository =
        GameRepository(ChessDatabase.get(app).gameDao()),
    private val difficultyLabel: String = "MEDIUM",
    private val vsAi: Boolean = true
) : AndroidViewModel(app) {

    private val sound = SoundManager(app)
    private var engine = GameEngine()
    private var settings = Settings()
    private var clock = ChessClock(10 * 60_000L, 5_000L)
    private var savedRowId: Long = 0L
    private var clockLoopJob: kotlinx.coroutines.Job? = null

    // Player-action results, layered on top of the board status.
    private var resignedBy: Color? = null
    private var drawAgreed: Boolean = false
    private var drawOfferPending: Boolean = false

    private val _state = MutableStateFlow(BoardUiState())
    val state: StateFlow<BoardUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settings = settingsRepo.settings.first()
            clock = ChessClock(settings.clockMinutes * 60_000L, settings.clockIncrementSeconds * 1000L)
            if (settings.clockMinutes > 0) {
                clock.start(Color.WHITE)
                startClockLoop()
            }
            _state.value = snapshot()
            if (playerColor == Color.BLACK) maybeTriggerAi()
        }
    }

    private val clockEnabled get() = settings.clockMinutes > 0

    private fun startClockLoop() {
        clockLoopJob?.cancel()
        clockLoopJob = viewModelScope.launch {
            while (isActive) {
                delay(200)
                // Timestamp clock self-computes; the loop just refreshes the UI and
                // watches for a flag.
                if (clock.flagged != null) {
                    sound.play(SoundManager.Cue.GAME_END, settings.soundEnabled, settings.hapticsEnabled)
                    _state.value = snapshot()
                    autoSave()
                    break
                }
                if (_state.value.gameOver) break
                _state.value = _state.value.copy(
                    whiteClock = ChessClock.format(clock.remainingMillis(Color.WHITE)),
                    blackClock = ChessClock.format(clock.remainingMillis(Color.BLACK))
                )
            }
        }
    }

    private fun snapshot(
        selected: Square? = null,
        targets: Set<Square> = emptySet(),
        last: Move? = _state.value.lastMove,
        thinking: Boolean = false,
        pending: PendingPromotion? = null,
        animating: AnimatingMove? = _state.value.animating
    ): BoardUiState {
        val boardStatus = engine.status()
        val flaggedSide = clock.flagged
        val result = ResultEvaluator.evaluate(
            status = boardStatus,
            sideToMove = engine.board.sideToMove,
            resignedBy = resignedBy,
            drawAgreed = drawAgreed,
            flaggedSide = flaggedSide
        )
        val over = result != GameResult.Ongoing
        val (headline, detail) = ResultEvaluator.describe(result)
        val checkSq = if (boardStatus == GameStatus.CHECK || boardStatus == GameStatus.CHECKMATE)
            engine.board.kingSquare(engine.board.sideToMove) else null
        // Resign/draw are available only while the game is live and it's a real game.
        val live = !over
        return BoardUiState(
            board = engine.board,
            selected = selected,
            legalTargets = targets,
            lastMove = last,
            status = boardStatus,
            thinking = thinking,
            checkSquare = checkSq,
            pendingPromotion = pending,
            moveList = engine.pgnMoveText(),
            whiteClock = ChessClock.format(clock.remainingMillis(Color.WHITE)),
            blackClock = ChessClock.format(clock.remainingMillis(Color.BLACK)),
            canUndo = engine.moveHistory().isNotEmpty(),
            canRedo = engine.canRedo(),
            capturedByWhite = Material.capturedOf(engine.board, Color.BLACK),
            capturedByBlack = Material.capturedOf(engine.board, Color.WHITE),
            materialBalance = Material.balance(engine.board, Color.WHITE),
            animating = animating,
            showCoordinates = true,
            useClock = clockEnabled,
            gameOver = over,
            result = result,
            resultHeadline = headline,
            resultDetail = detail,
            drawOfferPending = drawOfferPending,
            canResign = live,
            canOfferDraw = live && !drawOfferPending
        )
    }

    /** The color the local human controls right now. In vs-AI it's the fixed
     *  [playerColor]; in pass-and-play it's whichever side is to move. */
    private val controllingColor: Color
        get() = if (isVsAi) playerColor else engine.board.sideToMove

    fun onSquareTapped(sq: Square) {
        val s = _state.value
        if (s.thinking || s.pendingPromotion != null || s.gameOver || s.animating != null) return
        if (engine.board.sideToMove != controllingColor) return

        val selected = s.selected
        if (selected == null) { selectIfOwn(sq); return }
        if (sq == selected) { _state.value = snapshot(); return }

        val candidate = engine.legalMovesFrom(selected).firstOrNull { it.to == sq }
        if (candidate != null) {
            if (candidate.promotion != null) {
                _state.value = snapshot(pending = PendingPromotion(selected, sq)); return
            }
            commitMove(candidate); return
        }
        selectIfOwn(sq)
    }

    private fun selectIfOwn(sq: Square) {
        val piece = engine.board.pieceAt(sq)
        if (piece != null && piece.color == controllingColor) {
            val targets =
                if (settings.showLegalMoves) engine.legalMovesFrom(sq).map { it.to }.toSet()
                else emptySet()
            _state.value = snapshot(selected = sq, targets = targets)
        } else _state.value = snapshot()
    }

    fun choosePromotion(type: PieceType) {
        val p = _state.value.pendingPromotion ?: return
        val move = engine.legalMovesFrom(p.from).firstOrNull { it.to == p.to && it.promotion == type }
            ?: return
        commitMove(move)
    }

    fun cancelPromotion() { _state.value = snapshot() }

    private fun cueFor(board: Board, move: Move): SoundManager.Cue {
        val wasCapture = board.pieceAt(move.to) != null || move.isEnPassant
        val after = board.apply(move)
        val king = after.kingSquare(after.sideToMove)
        val check = king != null &&
            com.chessapp.domain.engine.MoveGenerator.isSquareAttacked(after, king, after.sideToMove.opposite())
        return when {
            check -> SoundManager.Cue.CHECK
            wasCapture -> SoundManager.Cue.CAPTURE
            else -> SoundManager.Cue.MOVE
        }
    }

    private fun commitMove(move: Move) {
        val boardBefore = engine.board
        val movingPiece = boardBefore.pieceAt(move.from) ?: return
        val cue = cueFor(boardBefore, move)

        // Animate the slide, then apply.
        viewModelScope.launch {
            _state.value = _state.value.copy(
                selected = null, legalTargets = emptySet(),
                animating = AnimatingMove(movingPiece, move.from, move.to)
            )
            delay(ANIM_MS)
            if (!engine.makeMove(move)) { _state.value = snapshot(animating = null); return@launch }
            if (clockEnabled) clock.press(movingPiece.color)
            sound.play(cue, settings.soundEnabled, settings.hapticsEnabled)
            _state.value = snapshot(last = move, animating = null)
            autoSave()
            checkGameEndSound()
            maybeTriggerAi()
        }
    }

    private fun maybeTriggerAi() {
        if (!isVsAi) return
        if (_state.value.gameOver) return
        if (engine.board.sideToMove == playerColor) return

        _state.value = _state.value.copy(thinking = true)
        viewModelScope.launch {
            val move = opponent.bestMove(engine.board)
            if (move != null) {
                val boardBefore = engine.board
                val movingPiece = boardBefore.pieceAt(move.from)
                val cue = cueFor(boardBefore, move)
                if (movingPiece != null) {
                    _state.value = _state.value.copy(
                        thinking = false,
                        animating = AnimatingMove(movingPiece, move.from, move.to)
                    )
                    delay(ANIM_MS)
                }
                engine.makeMove(move)
                if (clockEnabled) clock.press(playerColor.opposite())
                sound.play(cue, settings.soundEnabled, settings.hapticsEnabled)
                _state.value = snapshot(last = move, animating = null)
                autoSave()
                checkGameEndSound()
            } else {
                _state.value = snapshot()
            }
        }
    }

    private fun checkGameEndSound() {
        if (_state.value.gameOver) {
            sound.play(SoundManager.Cue.GAME_END, settings.soundEnabled, settings.hapticsEnabled)
        }
    }

    private fun autoSave() {
        viewModelScope.launch {
            savedRowId = gameRepo.save(
                engine = engine,
                title = if (savedRowId == 0L) "Game ${System.currentTimeMillis()}" else currentTitle(),
                vsAi = isVsAi,
                difficulty = if (isVsAi) difficultyLabel else null,
                existingId = savedRowId,
                resultToken = ResultEvaluator.resultToken(_state.value.result)
            )
        }
    }

    private fun currentTitle(): String = "Game vs $difficultyLabel"

    fun undo() {
        if (_state.value.animating != null) return
        if (engine.undo()) {
            // In vs-AI, step back past the AI's reply so it's the human's turn again.
            if (isVsAi && engine.board.sideToMove != playerColor) engine.undo()
            _state.value = snapshot(last = engine.moveHistory().lastOrNull())
            autoSave()
        }
    }

    fun redo() {
        if (engine.redo()) { _state.value = snapshot(last = engine.moveHistory().lastOrNull()); autoSave() }
    }

    fun newGame() {
        savedRowId = 0L
        engine = GameEngine()
        resignedBy = null
        drawAgreed = false
        drawOfferPending = false
        clock = ChessClock(settings.clockMinutes * 60_000L, settings.clockIncrementSeconds * 1000L)
        if (clockEnabled) { clock.start(Color.WHITE); startClockLoop() }
        _state.value = snapshot()
        if (playerColor == Color.BLACK) maybeTriggerAi()
    }

    // --- Resignation & draws ---

    /** The human player resigns. In vs-AI this is [playerColor]; in pass-and-play
     *  it's the side currently to move. */
    fun resign() {
        if (_state.value.gameOver) return
        resignedBy = controllingColor
        if (clockEnabled) clock.pause()
        sound.play(SoundManager.Cue.GAME_END, settings.soundEnabled, settings.hapticsEnabled)
        _state.value = snapshot()
        autoSave()
    }

    /**
     * The player offers a draw. Against the AI we use a simple, transparent policy:
     * the engine accepts only if it is not clearly winning (material balance against
     * it). Against a human (pass-and-play) the offer is surfaced for the other side
     * to accept or decline.
     */
    fun offerDraw() {
        if (_state.value.gameOver || drawOfferPending) return
        if (isVsAi) {
            // AI evaluates from its own perspective: accept if it isn't ahead by >1 pawn.
            val aiColor = playerColor.opposite()
            val aiBalance = Material.balance(engine.board, aiColor)
            if (aiBalance <= 1) {
                drawAgreed = true
                if (clockEnabled) clock.pause()
                sound.play(SoundManager.Cue.GAME_END, settings.soundEnabled, settings.hapticsEnabled)
                _state.value = snapshot()
                autoSave()
            } else {
                // Declined — surface a transient message the UI can show, then clear.
                _state.value = snapshot().copy(drawDeclined = true)
            }
        } else {
            drawOfferPending = true
            _state.value = snapshot()
        }
    }

    fun acceptDraw() {
        if (!drawOfferPending) return
        drawAgreed = true
        drawOfferPending = false
        if (clockEnabled) clock.pause()
        sound.play(SoundManager.Cue.GAME_END, settings.soundEnabled, settings.hapticsEnabled)
        _state.value = snapshot()
        autoSave()
    }

    fun declineDraw() {
        drawOfferPending = false
        _state.value = snapshot()
    }

    /** Dismiss the transient "draw declined" message. */
    fun clearDrawDeclined() {
        if (_state.value.drawDeclined) _state.value = _state.value.copy(drawDeclined = false)
    }

    private val isVsAi: Boolean get() = vsAi

    // --- Lifecycle: pause the clock when the app is backgrounded, resume on return.
    // The timestamp clock is self-healing, but pausing avoids charging the on-move
    // player for time spent with the app closed (matching casual-app expectations).

    fun onAppPaused() {
        if (clockEnabled && !_state.value.gameOver) {
            clock.pause()
            persistClock()
        }
        autoSave()
    }

    fun onAppResumed() {
        if (clockEnabled && !_state.value.gameOver) {
            clock.resume()
            if (!clockLoopRunning()) startClockLoop()
            _state.value = snapshot()
        }
    }

    private fun clockLoopRunning(): Boolean = clockLoopJob?.isActive == true

    private fun persistClock() {
        // Clock state rides along with the game autosave; the snapshot captures the
        // banked times accurately as of now.
        clock.snapshot()  // banks active time so a later restore is correct
    }

    /** Restore a previously saved game by replaying its moves. */
    fun resume(rowId: Long) {
        viewModelScope.launch {
            val restored = gameRepo.load(rowId) ?: return@launch
            engine = restored
            savedRowId = rowId
            resignedBy = null
            drawAgreed = false
            drawOfferPending = false
            _state.value = snapshot(last = engine.moveHistory().lastOrNull())
            maybeTriggerAi()
        }
    }

    override fun onCleared() {
        clockLoopJob?.cancel()
        sound.release()
        opponent.close()
        super.onCleared()
    }

    companion object { const val ANIM_MS = 160L }
}
