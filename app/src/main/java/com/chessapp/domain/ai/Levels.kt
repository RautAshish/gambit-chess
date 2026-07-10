package com.chessapp.domain.ai

/** The 10-rung difficulty ladder. Both engines consume UCI-style skill 0..20,
 *  so each rung maps onto that shared scale; names stay chess-flavoured and
 *  make no ELO claims we can't calibrate. */
object Levels {
    const val MIN = 1; const val MAX = 10; const val DEFAULT = 5
    private val NAMES = listOf(
        "Beginner","Casual","Novice","Improver","Club",
        "Strong","Advanced","Expert","Master","Maximum")
    fun name(level: Int) = NAMES[level.coerceIn(MIN, MAX) - 1]
    fun label(level: Int) = "Level ${level.coerceIn(MIN, MAX)}"
    fun skill(level: Int) =
        intArrayOf(0,2,4,7,9,11,13,15,17,20)[level.coerceIn(MIN, MAX) - 1]
    /** Parses persisted labels: "Level 6" plus the legacy 4-tier names. */
    fun fromLabel(s: String?): Int = when {
        s == null -> DEFAULT
        s.startsWith("Level ") ->
            s.removePrefix("Level ").trim().toIntOrNull()?.coerceIn(MIN, MAX) ?: DEFAULT
        else -> when (s) { "EASY"->2; "MEDIUM"->5; "HARD"->7; "EXPERT"->10; else->DEFAULT }
    }
}
