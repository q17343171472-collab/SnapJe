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
    fun auth_screen_displays_app_name() {
        // 应用改为保险库模式：启动后先展示生物识别验证页（App 名为 "SnapJe!"）
        // 注意：真机/模拟器需支持指纹或面部识别，否则停留在"设备不支持"提示页。
        composeTestRule.onNodeWithText("SnapJe!").assertExists()
    }
}
