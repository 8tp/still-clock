package dev.chuds.stillclock

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {

    @get:Rule(order = 0)
    val notificationPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeRendersBottomTabs() {
        composeRule.onNodeWithText("clock").assertIsDisplayed()
        composeRule.onNodeWithText("alarms").assertIsDisplayed()
        composeRule.onNodeWithText("timer").assertIsDisplayed()
        composeRule.onNodeWithText("stopwatch").assertIsDisplayed()
    }
}
