package com.chessapp.ui.nav

import android.app.Application
import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.first
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.compose.ui.platform.LocalContext
import com.chessapp.data.db.ChessDatabase
import com.chessapp.data.db.GameRepository
import com.chessapp.data.prefs.SettingsRepository
import com.chessapp.domain.ai.Levels
import com.chessapp.domain.model.Color
import com.chessapp.engine.NativeEngine
import com.chessapp.ui.board.ChessScreen
import com.chessapp.ui.board.ChessViewModel
import com.chessapp.ui.home.HomeScreen
import com.chessapp.ui.home.SavedGamesScreen
import com.chessapp.ui.settings.SettingsScreen

/** Lightweight screen graph. Kept dependency-free (no navigation-compose) so the
 *  whole flow is obvious in one file and easy to extend. */
sealed interface Screen {
    data object Home : Screen
    data class Game(
        val level: Int,
        val playerColor: Color,
        val resumeId: Long? = null,
        val passAndPlay: Boolean = false,
        val serial: Int = 0
    ) : Screen
    data object Settings : Screen
    data object SavedGames : Screen
    data object Puzzles : Screen
    data object Online : Screen
}

@Composable
fun AppNav(app: Application, initialDest: String? = null) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var gameSerial by remember { mutableStateOf(0) }
    val settingsRepo = remember { SettingsRepository(app) }
    val gameRepo = remember { GameRepository(ChessDatabase.get(app).gameDao()) }
    val owner = LocalViewModelStoreOwner.current!!
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val settings by settingsRepo.settings.collectAsState(initial = com.chessapp.data.prefs.Settings())

    // System back returns to Home from any sub-screen instead of exiting the app.
    // App-shortcut destinations ("New game" / "Puzzles" from launcher long-press).
    androidx.compose.runtime.LaunchedEffect(initialDest) {
        when (initialDest) {
            "puzzles" -> screen = Screen.Puzzles
            "new_game" -> {
                val s = settingsRepo.settings.first()
                screen = Screen.Game(
                    level = s.aiLevel,
                    playerColor = resolvePlayAs(s.playAs),
                    serial = -1
                )
            }
        }
    }

    androidx.activity.compose.BackHandler(enabled = screen != Screen.Home) {
        screen = Screen.Home
    }

    when (val s = screen) {
        Screen.Home -> HomeScreen(
            selectedLevel = settings.aiLevel,
            onSelectLevel = { n -> scope.launch { settingsRepo.setAiLevel(n) } },
            selectedPlayAs = settings.playAs,
            onSelectPlayAs = { v -> scope.launch { settingsRepo.setPlayAs(v) } },
            levelStats = settings.levelStats,
            onPlayAi = { level, playAs ->
                gameSerial++
                screen = Screen.Game(level, resolvePlayAs(playAs), serial = gameSerial)
            },
            onPlayLocal = {
                gameSerial++
                screen = Screen.Game(settings.aiLevel, Color.WHITE, passAndPlay = true, serial = gameSerial)
            },
            onPuzzles = { screen = Screen.Puzzles },
            puzzlesSolved = settings.solvedPuzzles.size,
            onSavedGames = { screen = Screen.SavedGames },
            onSettings = { screen = Screen.Settings },
            onPlayOnline = { screen = Screen.Online }
        )

        is Screen.Game -> {
            val vm = remember(s.serial) {
                buildGameViewModel(app, owner, s, settingsRepo, gameRepo, settings.useStockfish)
            }
            if (s.resumeId != null) {
                androidx.compose.runtime.LaunchedEffect(s.resumeId) { vm.resume(s.resumeId) }
            }
            ChessScreen(vm, flipped = settings.boardFlipped && s.playerColor == Color.BLACK,
                onBack = { screen = Screen.Home }, boardTheme = settings.boardTheme)
        }

        Screen.Settings -> SettingsScreen(settingsRepo) { screen = Screen.Home }

        Screen.Puzzles -> {
            val pvm: com.chessapp.ui.puzzle.PuzzleViewModel = viewModel()
            com.chessapp.ui.puzzle.PuzzleScreen(pvm, settings.boardTheme) { screen = Screen.Home }
        }

        Screen.Online -> {
            val ovm: com.chessapp.ui.online.OnlineViewModel = viewModel()
            com.chessapp.ui.online.OnlineScreen(
                ovm, settings.boardTheme,
                onBack = { screen = Screen.Home },
                onOpenSettings = { screen = Screen.Settings }
            )
        }

        Screen.SavedGames -> SavedGamesScreen(
            repo = gameRepo,
            onResume = { saved ->
                gameSerial++
                screen = Screen.Game(
                    level = Levels.fromLabel(saved.difficulty),
                    playerColor = Color.WHITE,
                    resumeId = saved.id,
                    passAndPlay = !saved.vsAi,
                    serial = gameSerial
                )
            },
            onBack = { screen = Screen.Home }
        )
    }
}

private fun buildGameViewModel(
    app: Application,
    owner: ViewModelStoreOwner,
    game: Screen.Game,
    settingsRepo: SettingsRepository,
    gameRepo: GameRepository,
    useStockfish: Boolean
): ChessViewModel {
    val stockfishPath = com.chessapp.engine.stockfish.StockfishInstaller.path(app)
    val factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            return ChessViewModel(
                app = app,
                playerColor = game.playerColor,
                opponent = when {
                    game.passAndPlay -> com.chessapp.engine.NoEngine
                    useStockfish && stockfishPath != null ->
                        com.chessapp.engine.stockfish.StockfishEngine(stockfishPath)
                            .apply { setSkill(Levels.skill(game.level)) }
                    else -> NativeEngine().apply { setSkill(Levels.skill(game.level)) }
                },
                settingsRepo = settingsRepo,
                gameRepo = gameRepo,
                difficultyLabel = Levels.label(game.level),
                aiLevel = game.level,
                vsAi = !game.passAndPlay
            ) as T
        }
    }
    // Unique key per game instance so resuming/new games get a fresh VM.
    val key = "game-${game.serial}"
    return ViewModelProvider(owner, factory)[key, ChessViewModel::class.java]
}

/** RANDOM resolves at launch time so the game itself always has a concrete seat. */
private fun resolvePlayAs(v: String): Color = when (v) {
    "BLACK" -> Color.BLACK
    "RANDOM" -> if (kotlin.random.Random.nextBoolean()) Color.WHITE else Color.BLACK
    else -> Color.WHITE
}
