package com.chessapp.domain

import com.chessapp.domain.engine.DrawReason
import com.chessapp.domain.engine.GameResult
import com.chessapp.domain.engine.ResultEvaluator
import com.chessapp.domain.engine.WinReason
import com.chessapp.domain.model.Color
import com.chessapp.domain.model.GameStatus
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The game result combines the board status (checkmate, stalemate, rule draws) with
 * player/clock events (resignation, draw agreement, loss on time) that the board
 * alone can't express. Player/clock events take precedence over the position.
 */
class ResultTest {

    @Test fun checkmateAwardsTheOtherSide() {
        // Black is to move and mated, so White won.
        assertEquals(
            GameResult.Win(Color.WHITE, WinReason.CHECKMATE),
            ResultEvaluator.evaluate(GameStatus.CHECKMATE, Color.BLACK)
        )
    }

    @Test fun resignationAwardsOpponent() {
        assertEquals(
            GameResult.Win(Color.BLACK, WinReason.RESIGNATION),
            ResultEvaluator.evaluate(GameStatus.ONGOING, Color.WHITE, resignedBy = Color.WHITE)
        )
    }

    @Test fun timeoutAwardsOpponent() {
        assertEquals(
            GameResult.Win(Color.WHITE, WinReason.TIMEOUT),
            ResultEvaluator.evaluate(GameStatus.ONGOING, Color.BLACK, flaggedSide = Color.BLACK)
        )
    }

    @Test fun drawAgreementAndRuleDraws() {
        assertEquals(
            GameResult.Draw(DrawReason.AGREEMENT),
            ResultEvaluator.evaluate(GameStatus.ONGOING, Color.WHITE, drawAgreed = true)
        )
        assertEquals(
            GameResult.Draw(DrawReason.STALEMATE),
            ResultEvaluator.evaluate(GameStatus.STALEMATE, Color.WHITE)
        )
        assertEquals(
            GameResult.Draw(DrawReason.REPETITION),
            ResultEvaluator.evaluate(GameStatus.DRAW_REPETITION, Color.WHITE)
        )
    }

    @Test fun playerEventsTakePrecedenceOverPosition() {
        // Even if the board shows mate, an explicit resignation is what's recorded.
        assertEquals(
            GameResult.Win(Color.BLACK, WinReason.RESIGNATION),
            ResultEvaluator.evaluate(GameStatus.CHECKMATE, Color.WHITE, resignedBy = Color.WHITE)
        )
    }

    @Test fun describeAndTokens() {
        val (headline, detail) = ResultEvaluator.describe(
            GameResult.Win(Color.WHITE, WinReason.RESIGNATION)
        )
        assertEquals("White wins", headline)
        assertEquals("by resignation", detail)

        assertEquals("1-0", ResultEvaluator.resultToken(GameResult.Win(Color.WHITE, WinReason.CHECKMATE)))
        assertEquals("0-1", ResultEvaluator.resultToken(GameResult.Win(Color.BLACK, WinReason.TIMEOUT)))
        assertEquals("1/2-1/2", ResultEvaluator.resultToken(GameResult.Draw(DrawReason.AGREEMENT)))
        assertEquals("*", ResultEvaluator.resultToken(GameResult.Ongoing))
    }
}
