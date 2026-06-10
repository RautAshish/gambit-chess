package com.chessapp.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.chessapp.domain.ai.ChessAI
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** User preferences, persisted with DataStore. */
data class Settings(
    val difficulty: ChessAI.Difficulty = ChessAI.Difficulty.MEDIUM,
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val showLegalMoves: Boolean = true,
    val boardFlipped: Boolean = false,
    val darkBoard: Boolean = false,
    val useStockfish: Boolean = false,
    val clockMinutes: Int = 10,
    val clockIncrementSeconds: Int = 5,
    val boardTheme: String = "CLASSIC"   // CLASSIC | WALNUT | FOREST
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val DIFFICULTY = stringPreferencesKey("difficulty")
        val SOUND = booleanPreferencesKey("sound")
        val HAPTICS = booleanPreferencesKey("haptics")
        val SHOW_MOVES = booleanPreferencesKey("show_moves")
        val FLIPPED = booleanPreferencesKey("flipped")
        val DARK_BOARD = booleanPreferencesKey("dark_board")
        val STOCKFISH = booleanPreferencesKey("stockfish")
        val CLOCK_MIN = intPreferencesKey("clock_min")
        val CLOCK_INC = intPreferencesKey("clock_inc")
            val BOARD_THEME = stringPreferencesKey("board_theme")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            difficulty = p[Keys.DIFFICULTY]?.let { runCatching { ChessAI.Difficulty.valueOf(it) }.getOrNull() }
                ?: ChessAI.Difficulty.MEDIUM,
            soundEnabled = p[Keys.SOUND] ?: true,
            hapticsEnabled = p[Keys.HAPTICS] ?: true,
            showLegalMoves = p[Keys.SHOW_MOVES] ?: true,
            boardFlipped = p[Keys.FLIPPED] ?: false,
            darkBoard = p[Keys.DARK_BOARD] ?: false,
            useStockfish = p[Keys.STOCKFISH] ?: false,
            clockMinutes = p[Keys.CLOCK_MIN] ?: 10,
            clockIncrementSeconds = p[Keys.CLOCK_INC] ?: 5,
            boardTheme = p[Keys.BOARD_THEME] ?: "CLASSIC"
        )
    }

    suspend fun update(transform: (MutablePreferences) -> Unit) {
        context.dataStore.edit(transform)
    }

    suspend fun setDifficulty(d: ChessAI.Difficulty) =
        update { it[Keys.DIFFICULTY] = d.name }
    suspend fun setSound(on: Boolean) = update { it[Keys.SOUND] = on }
    suspend fun setHaptics(on: Boolean) = update { it[Keys.HAPTICS] = on }
    suspend fun setShowLegalMoves(on: Boolean) = update { it[Keys.SHOW_MOVES] = on }
    suspend fun setFlipped(on: Boolean) = update { it[Keys.FLIPPED] = on }
    suspend fun setDarkBoard(on: Boolean) = update { it[Keys.DARK_BOARD] = on }
    suspend fun setUseStockfish(on: Boolean) = update { it[Keys.STOCKFISH] = on }
    suspend fun setBoardTheme(v: String) = context.dataStore.edit { it[Keys.BOARD_THEME] = v }

    suspend fun setClock(minutes: Int, incSeconds: Int) = update {
        it[Keys.CLOCK_MIN] = minutes; it[Keys.CLOCK_INC] = incSeconds
    }
}
