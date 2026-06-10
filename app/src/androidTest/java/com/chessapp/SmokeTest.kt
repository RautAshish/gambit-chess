package com.chessapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * First true end-to-end UI test: launches the real app on an emulator and checks
 * that Compose renders and navigation works. Deliberately avoids entering a game
 * screen here — the chess clock recomposes every 200ms, which keeps Compose's
 * idle-detection busy and would make assertions flaky; game-screen UI tests need
 * an IdlingResource strategy first.
 */
@RunWith(AndroidJUnit4::class)
class SmokeTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeScreenRendersBrand() {
        rule.onNodeWithText("GAMBIT").assertIsDisplayed()
        rule.onNodeWithText("Play vs Computer").assertIsDisplayed()
    }

    @Test
    fun settingsNavigationWorks() {
        rule.onNodeWithText("Settings").performClick()
        rule.onNodeWithText("Sound effects").assertIsDisplayed()
        rule.onNodeWithText("Back").performClick()
        rule.onNodeWithText("GAMBIT").assertIsDisplayed()
    }

    @Test
    fun savedGamesScreenOpens() {
        rule.onNodeWithText("Saved Games").performClick()
        rule.onNodeWithText("Saved Games").assertIsDisplayed()
    }
}
