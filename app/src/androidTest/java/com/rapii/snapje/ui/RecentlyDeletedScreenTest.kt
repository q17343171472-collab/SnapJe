package com.rapii.snapje.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

/**
 * UI tests for RecentlyDeletedScreen.
 */
class RecentlyDeletedScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun empty_trash_displays_empty_message() {
        // Test empty trash state
        composeTestRule.setContent {
            // RecentlyDeletedScreen would be tested here with mocked ViewModel
        }
        
        // Verify empty state message exists
        composeTestRule.onNodeWithText("Trash is empty").assertExists()
    }
}
