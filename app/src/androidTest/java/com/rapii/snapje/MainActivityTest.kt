package com.rapii.snapje

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI tests for MainActivity and navigation.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {

 @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun app_launches_successfully() {
        // Verify app launches without crashing
        composeTestRule.waitForIdle()
    }

    @Test
    fun home_screen_displays_app_name() {
        // Verify app name is displayed
        composeTestRule.onNodeWithText("PhotoX").assertExists()
    }
}
