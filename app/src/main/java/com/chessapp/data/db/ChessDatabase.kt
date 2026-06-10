package com.chessapp.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * A saved game. We persist the final FEN (for instant resume) plus the full PGN
 * movetext and the UCI move list (for replay/analysis). Storing FEN means resume
 * survives process death without replaying the whole game.
 */
@Entity(tableName = "saved_games")
data class SavedGame(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val fen: String,
    val pgn: String,
    @ColumnInfo(name = "uci_moves") val uciMoves: String,  // space-separated
    val result: String,                                    // "1-0","0-1","1/2-1/2","*"
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "vs_ai") val vsAi: Boolean = true,
    val difficulty: String? = null
)

@Entity(tableName = "puzzle_progress")
data class PuzzleProgress(
    @PrimaryKey val puzzleId: String,
    val solved: Boolean,
    val attempts: Int,
    @ColumnInfo(name = "solved_at") val solvedAt: Long?
)

@Dao
interface GameDao {
    @Query("SELECT * FROM saved_games ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<SavedGame>>

    @Query("SELECT * FROM saved_games WHERE id = :id")
    suspend fun byId(id: Long): SavedGame?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(game: SavedGame): Long

    @Delete
    suspend fun delete(game: SavedGame)

    @Query("DELETE FROM saved_games WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM saved_games")
    suspend fun deleteAll()
}

@Dao
interface PuzzleDao {
    @Query("SELECT * FROM puzzle_progress")
    fun observeAll(): Flow<List<PuzzleProgress>>

    @Query("SELECT * FROM puzzle_progress WHERE puzzleId = :id")
    suspend fun byId(id: String): PuzzleProgress?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(progress: PuzzleProgress)
}

@Database(
    entities = [SavedGame::class, PuzzleProgress::class],
    version = 1,
    exportSchema = false
)
abstract class ChessDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao
    abstract fun puzzleDao(): PuzzleDao

    companion object {
        @Volatile private var instance: ChessDatabase? = null

        fun get(context: android.content.Context): ChessDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    ChessDatabase::class.java,
                    "chess.db"
                ).build().also { instance = it }
            }
    }
}
