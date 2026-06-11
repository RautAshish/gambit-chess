package com.chessapp.ui.puzzle

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chessapp.data.prefs.SettingsRepository
import com.chessapp.data.puzzle.Puzzle
import com.chessapp.data.puzzle.PuzzleBank
import com.chessapp.data.puzzle.PuzzleResult
import com.chessapp.data.puzzle.PuzzleSession
import com.chessapp.domain.engine.MoveGenerator
import com.chessapp.domain.model.Color
import com.chessapp.domain.model.Move
import com.chessapp.domain.model.PieceType
import com.chessapp.domain.model.Square
import com.chessapp.ui.board.BoardUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class PuzzleUiState(
    val board: BoardUiState = BoardUiState(),
    val index: Int = 0,
    val total: Int = PuzzleBank.builtIn.size,
    val solvedCount: Int = 0,
    val sideToMove: Color = Color.WHITE,
    val message: String = "",
    val solved: Boolean = false,
    val wrong: Boolean = false,
    val themes: List<String> = emptyList()
)

class PuzzleViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs: SettingsRepository = SettingsRepository(app)


    private val bank = PuzzleBank.builtIn
    private var session = PuzzleSession(bank[0])
    private var index = 0
    private var solvedIds: Set<String> = emptySet()
    private var selected: Square? = null

    private val _state = MutableStateFlow(PuzzleUiState())
    val state: StateFlow<PuzzleUiState> = _state

    init {
        viewModelScope.launch {
            prefs.settings.collect { s ->
                val firstLoad = solvedIds.isEmpty() && s.solvedPuzzles.isNotEmpty()
                solvedIds = s.solvedPuzzles
                if (firstLoad) {
                    // resume at the first unsolved puzzle
                    index = bank.indexOfFirst { it.id !in solvedIds }.coerceAtLeast(0)
                    session = PuzzleSession(bank[index])
                }
                push()
            }
        }
        push()
    }

    private fun puzzle(): Puzzle = bank[index]

    fun onSquareTapped(sq: Square) {
        if (session.isSolved) return
        val board = session.board
        val sel = selected
        if (sel != null && sq != sel) {
            val legal = MoveGenerator.legalMoves(board)
                .filter { it.from == sel && it.to == sq }
            if (legal.isNotEmpty()) {
                // Auto-queen: puzzle lines in the bank only ever promote to queens.
                val move = legal.firstOrNull { it.promotion == PieceType.QUEEN }
                    ?: legal.first()
                selected = null
                when (val r = session.submit(move)) {
                    is PuzzleResult.Correct -> {
                        if (r.solved && puzzle().id !in solvedIds) {
                            viewModelScope.launch { prefs.markPuzzleSolved(puzzle().id) }
                        }
                        push(message = if (r.solved) "Solved!" else "Correct — keep going",
                            wrong = false)
                    }
                    PuzzleResult.Wrong -> push(message = "Not the best move — try again", wrong = true)
                }
                return
            }
        }
        val piece = board.pieceAt(sq)
        selected = if (piece != null && piece.color == board.sideToMove) sq else null
        push(wrong = _state.value.wrong)
    }

    fun hint() {
        val h = session.hint() ?: return
        selected = h.from
        push(message = "Hint: the piece to move is highlighted")
    }

    fun retry() {
        session = PuzzleSession(puzzle()); selected = null
        push(message = "")
    }

    fun next() {
        // first unsolved after the current one, wrapping; falls back to plain next
        val order = (index + 1 until bank.size) + (0..index)
        index = order.firstOrNull { bank[it].id !in solvedIds } ?: (index + 1) % bank.size
        session = PuzzleSession(bank[index]); selected = null
        push(message = "")
    }

    private fun push(message: String? = null, wrong: Boolean = false) {
        val b = session.board
        val targets = selected?.let { s ->
            MoveGenerator.legalMoves(b).filter { it.from == s }.map { it.to }.toSet()
        } ?: emptySet()
        val last = session.lastMovePlayed()
        _state.value = PuzzleUiState(
            board = BoardUiState(
                board = b, selected = selected, legalTargets = targets, lastMove = last
            ),
            index = index, total = bank.size,
            solvedCount = solvedIds.count { id -> bank.any { it.id == id } },
            sideToMove = b.sideToMove,
            message = message ?: _state.value.message,
            solved = session.isSolved,
            wrong = wrong,
            themes = puzzle().themes
        )
    }
}
