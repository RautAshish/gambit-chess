package com.chessapp.domain.engine

import com.chessapp.domain.model.Color

/**
 * A two-sided chess clock with Fischer increment.
 *
 * Design: the clock is TIMESTAMP-BASED, not tick-accumulation based. When a side is
 * on the move, we store its "banked" remaining time plus the wall-clock instant the
 * turn started; the live remaining time is `banked - (now - turnStart)`. This makes
 * the clock self-correcting across app backgrounding and process death: however long
 * the app was suspended, the elapsed wall time is deducted the moment we read the
 * clock again. A tick loop is still used, but only to refresh the UI — it is not the
 * source of truth, so a missed tick can never give a player free time.
 *
 * [nowProvider] is injectable so tests can control time deterministically.
 */
class ChessClock(
    initialMillis: Long,
    private val incrementMillis: Long = 0L,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {
    // "Banked" time: what each side had at the moment their current turn began (or
    // ended, for the inactive side).
    private val banked = longArrayOf(initialMillis, initialMillis) // [white, black]

    var running: Boolean = false
        private set
    var activeColor: Color = Color.WHITE
        private set

    // Wall-clock instant the active side's turn began. Null when paused/stopped.
    private var turnStart: Long? = null

    /** Live remaining time for [color], accounting for elapsed time if it's running. */
    fun remainingMillis(color: Color): Long {
        val base = banked[color.ordinal]
        if (running && color == activeColor) {
            val start = turnStart ?: return base
            val elapsed = (nowProvider() - start).coerceAtLeast(0L)
            return (base - elapsed).coerceAtLeast(0L)
        }
        return base
    }

    /**
     * The side that has run out of time, or null. Only the side currently on the
     * move can flag (you can't lose on time during the opponent's turn), which
     * matches real chess and keeps the rule unambiguous.
     */
    val flagged: Color?
        get() {
            if (!started) return null
            return if (remainingMillis(activeColor) <= 0L) activeColor else null
        }

    private var started = false

    fun start(active: Color = Color.WHITE) {
        if (flagged != null) return
        activeColor = active
        running = true
        started = true
        turnStart = nowProvider()
    }

    /** Pause: bank the elapsed time so it isn't lost, and stop counting. */
    fun pause() {
        if (!running) return
        bankActive()
        running = false
        turnStart = null
    }

    /** Resume after a pause, restarting the active side's turn clock. */
    fun resume() {
        if (running || flagged != null) return
        running = true
        turnStart = nowProvider()
    }

    /** Fold the active side's elapsed time into its banked total. */
    private fun bankActive() {
        val start = turnStart ?: return
        val elapsed = (nowProvider() - start).coerceAtLeast(0L)
        val i = activeColor.ordinal
        banked[i] = (banked[i] - elapsed).coerceAtLeast(0L)
    }

    /**
     * A no-op for the timestamp model except that it lets a UI loop poke the clock;
     * kept for API compatibility. Returns the current flagged side, if any.
     */
    fun tick(@Suppress("UNUSED_PARAMETER") deltaMillis: Long = 0L): Color? = flagged

    /** Called when [mover] completes a move: bank their time, add increment, switch.
     *  Ignores a press whose color isn't the side actually on the move — a press can
     *  only come from the player whose clock is running. */
    fun press(mover: Color) {
        if (flagged != null) return
        if (mover != activeColor) return
        // Bank the mover's elapsed time (they were the active side).
        if (running) bankActive()
        banked[mover.ordinal] += incrementMillis
        activeColor = mover.opposite()
        turnStart = if (running) nowProvider() else null
    }

    fun reset(initialMillis: Long) {
        banked[0] = initialMillis
        banked[1] = initialMillis
        running = false
        started = false
        activeColor = Color.WHITE
        turnStart = null
    }

    /**
     * Snapshot for persistence. We bank the active side's elapsed time first so the
     * saved state is accurate as of "now". Restore with [restore].
     */
    fun snapshot(): ClockState {
        if (running) bankActive()
        // Re-anchor turnStart so we don't double-count after banking.
        if (running) turnStart = nowProvider()
        return ClockState(
            whiteMillis = banked[0],
            blackMillis = banked[1],
            activeColor = activeColor,
            running = running,
            started = started
        )
    }

    fun restore(state: ClockState) {
        banked[0] = state.whiteMillis
        banked[1] = state.blackMillis
        activeColor = state.activeColor
        started = state.started
        running = state.running
        turnStart = if (state.running) nowProvider() else null
    }

    companion object {
        fun format(millis: Long): String {
            val totalSec = (millis + 999) / 1000   // round up so 0.4s shows as 1, not 0
            val m = totalSec / 60
            val s = totalSec % 60
            return "%d:%02d".format(m, s)
        }
    }
}

/** Serializable clock state. */
data class ClockState(
    val whiteMillis: Long,
    val blackMillis: Long,
    val activeColor: Color,
    val running: Boolean,
    val started: Boolean
)
