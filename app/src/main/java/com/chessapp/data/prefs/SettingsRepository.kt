package com.chessapp.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.chessapp.domain.ai.ChessAI
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
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
    val boardTheme: String = "CLASSIC",  // CLASSIC | WALNUT | FOREST
    val playAsBlack: Boolean = false,
    // Online play (free-tier Firestore REST; see SERVER_SETUP.md)
    val onlineProjectId: String = "",
    val onlineApiKey: String = "",
    // Puzzle progress
    val solvedPuzzles: Set<String> = emptySet()
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
        val PLAY_AS_BLACK = booleanPreferencesKey("play_as_black")
        val ONLINE_PROJECT = stringPreferencesKey("online_project_id")
        val ONLINE_APIKEY = stringPreferencesKey("online_api_key")
        val SOLVED_PUZZLES = stringSetPreferencesKey("solved_puzzles")
        // anonymous-auth cache
        val ON_UID = stringPreferencesKey("online_uid")
        val ON_IDTOKEN = stringPreferencesKey("online_id_token")
        val ON_REFRESH = stringPreferencesKey("online_refresh_token")
        val ON_EXPIRY = longPreferencesKey("online_token_expiry")
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
            // Professional positioning: the strongest engine is the default on
            // devices that carry it (arm64); others silently use the built-in.
            useStockfish = p[Keys.STOCKFISH] ?: true,
            clockMinutes = p[Keys.CLOCK_MIN] ?: 10,
            clockIncrementSeconds = p[Keys.CLOCK_INC] ?: 5,
            boardTheme = p[Keys.BOARD_THEME] ?: "CLASSIC",
            playAsBlack = p[Keys.PLAY_AS_BLACK] ?: false,
            onlineProjectId = p[Keys.ONLINE_PROJECT] ?: "",
            onlineApiKey = p[Keys.ONLINE_APIKEY] ?: "",
            solvedPuzzles = p[Keys.SOLVED_PUZZLES] ?: emptySet()
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
    suspend fun setOnlineConfig(projectId: String, apiKey: String) =
        context.dataStore.edit {
            it[Keys.ONLINE_PROJECT] = projectId.trim()
            it[Keys.ONLINE_APIKEY] = apiKey.trim()
        }

    suspend fun markPuzzleSolved(id: String) = context.dataStore.edit {
        it[Keys.SOLVED_PUZZLES] = (it[Keys.SOLVED_PUZZLES] ?: emptySet()) + id
    }

    data class OnlineAuth(val uid: String, val idToken: String, val refresh: String, val expiry: Long)
    suspend fun readAuth(): OnlineAuth? {
        val p = context.dataStore.data.first()
        val uid = p[Keys.ON_UID] ?: return null
        return OnlineAuth(uid, p[Keys.ON_IDTOKEN] ?: return null,
            p[Keys.ON_REFRESH] ?: return null, p[Keys.ON_EXPIRY] ?: 0L)
    }
    suspend fun writeAuth(a: OnlineAuth) = context.dataStore.edit {
        it[Keys.ON_UID] = a.uid; it[Keys.ON_IDTOKEN] = a.idToken
        it[Keys.ON_REFRESH] = a.refresh; it[Keys.ON_EXPIRY] = a.expiry
    }

    suspend fun setPlayAsBlack(v: Boolean) = context.dataStore.edit { it[Keys.PLAY_AS_BLACK] = v }

    suspend fun setBoardTheme(v: String) = context.dataStore.edit { it[Keys.BOARD_THEME] = v }

    suspend fun setClock(minutes: Int, incSeconds: Int) = update {
        it[Keys.CLOCK_MIN] = minutes; it[Keys.CLOCK_INC] = incSeconds
    }
}
