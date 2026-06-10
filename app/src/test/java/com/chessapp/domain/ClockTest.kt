package com.chessapp.domain

import com.chessapp.domain.engine.ChessClock
import com.chessapp.domain.model.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The clock is timestamp-based so it stays correct across app backgrounding and
 * process death. A controllable time source ([now]) lets these tests simulate
 * arbitrary wall-clock jumps deterministically.
 */
class ClockTest {

    @Test fun basicCountdownAndIncrement() {
        var now = 0L
        val clk = ChessClock(60_000, 2_000) { now }
        clk.start(Color.WHITE)
        now = 5_000
        assertEquals(55_000L, clk.remainingMillis(Color.WHITE))
        assertEquals(60_000L, clk.remainingMillis(Color.BLACK))
        clk.press(Color.WHITE)                       // 55s + 2s increment = 57s, black to move
        assertEquals(57_000L, clk.remainingMillis(Color.WHITE))
        assertEquals(Color.BLACK, clk.activeColor)
    }

    @Test fun selfHealsAcrossBackgrounding() {
        var now = 0L
        val clk = ChessClock(60_000, 0) { now }
        clk.start(Color.WHITE)
        // No tick() calls at all — simulate the app being suspended — then a big jump.
        now = 35_000
        assertEquals(25_000L, clk.remainingMillis(Color.WHITE))
    }

    @Test fun pauseFreezesAndResumeContinues() {
        var now = 0L
        val clk = ChessClock(60_000, 0) { now }
        clk.start(Color.WHITE)
        now = 10_000
        clk.pause()
        now = 100_000                                 // 90s pass while paused
        assertEquals(50_000L, clk.remainingMillis(Color.WHITE))
        clk.resume()
        now = 105_000                                 // 5s after resume
        assertEquals(45_000L, clk.remainingMillis(Color.WHITE))
    }

    @Test fun survivesProcessDeathViaSnapshot() {
        var now = 0L
        val clk = ChessClock(60_000, 0) { now }
        clk.start(Color.WHITE)
        now = 12_000
        val snap = clk.snapshot()
        val restored = ChessClock(60_000, 0) { now }
        restored.restore(snap)
        now = 15_000
        assertEquals(45_000L, restored.remainingMillis(Color.WHITE))
        assertEquals(Color.WHITE, restored.activeColor)
    }

    @Test fun onlyActiveSideFlags() {
        var now = 0L
        val clk = ChessClock(3_000, 0) { now }
        clk.start(Color.WHITE)
        now = 1_000
        assertNull(clk.flagged)
        now = 4_000
        assertEquals(Color.WHITE, clk.flagged)
        assertEquals(0L, clk.remainingMillis(Color.WHITE))
    }

    @Test fun formatting() {
        assertEquals("0:57", ChessClock.format(57_000))
        assertEquals("1:05", ChessClock.format(65_000))
        assertEquals("0:01", ChessClock.format(400))
        assertEquals("0:00", ChessClock.format(0))
    }
}
