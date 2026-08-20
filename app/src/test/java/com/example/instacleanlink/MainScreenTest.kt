package com.example.instacleanlink

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.instacleanlink.ui.theme.InstaCleanLinkTheme
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MainScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PrefsManager.clearPreferredApp(context)
        PrefsManager.clearAppLanguage(context)
        AppLanguageManager.setAppLanguage(context, AppLanguage.ENGLISH)
    }

    @Test
    fun `displays how it works section`() {
        composeTestRule.setContent {
            InstaCleanLinkTheme {
                MainScreen()
            }
        }

        composeTestRule.onNodeWithText("How it works").assertIsDisplayed()
    }

    @Test
    fun `displays privacy info text`() {
        composeTestRule.setContent {
            InstaCleanLinkTheme {
                MainScreen()
            }
        }

        composeTestRule.onNodeWithText("When opening links", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("When sharing text", substring = true).assertIsDisplayed()
    }

    @Test
    fun `displays preferred app section`() {
        composeTestRule.setContent {
            InstaCleanLinkTheme {
                MainScreen()
            }
        }

        composeTestRule.onNodeWithText("Preferred Instagram app").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `displays no app set message when no preference`() {
        composeTestRule.setContent {
            InstaCleanLinkTheme {
                MainScreen()
            }
        }

        composeTestRule.onNodeWithText("No app selected").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("You will be asked every time").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `displays preferred app label when set`() {
        PrefsManager.setPreferredApp(context, "com.instagram.android", "Instagram")

        composeTestRule.setContent {
            InstaCleanLinkTheme {
                MainScreen()
            }
        }

        composeTestRule.onNodeWithText("Always opening with:").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Instagram").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `reset button clears preference`() {
        PrefsManager.setPreferredApp(context, "com.instagram.android", "Instagram")

        composeTestRule.setContent {
            InstaCleanLinkTheme {
                MainScreen()
            }
        }

        composeTestRule.onNodeWithText("Reset").performScrollTo().performClick()

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("No app selected").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("You will be asked every time").performScrollTo().assertIsDisplayed()
        assertNull(PrefsManager.getPreferredApp(context))
    }

    @Test
    fun `alter button is always visible`() {
        composeTestRule.setContent {
            InstaCleanLinkTheme {
                MainScreen()
            }
        }

        composeTestRule.onNodeWithText("Change").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `reset button only visible when app is set`() {
        composeTestRule.setContent {
            InstaCleanLinkTheme {
                MainScreen()
            }
        }

        // Reset button should NOT exist when no app is set
        composeTestRule.onAllNodesWithText("Reset").assertCountEquals(0)
    }

    @Test
    fun `clicking change shows app picker dialog`() {
        composeTestRule.setContent {
            InstaCleanLinkTheme {
                MainScreen()
            }
        }

        composeTestRule.onNodeWithText("Change").performScrollTo().performClick()

        composeTestRule.onNodeWithText("Choose an Instagram app").assertIsDisplayed()
    }

    @Test
    fun `clicking Portuguese updates the interface language`() {
        composeTestRule.setContent {
            InstaCleanLinkTheme {
                MainScreen()
            }
        }

        composeTestRule.onNodeWithText("Portuguese").performScrollTo().performClick()

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Como funciona").assertIsDisplayed()
        assertEquals("pt", PrefsManager.getAppLanguageCode(context))
    }
}
