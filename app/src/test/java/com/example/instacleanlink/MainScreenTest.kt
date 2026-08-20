package com.example.instacleanlink

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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
    }

    @Test
    fun `displays how it works section`() {
        composeTestRule.setContent {
            InstaCleanLinkTheme {
                MainScreen()
            }
        }

        composeTestRule.onNodeWithText("Como funciona").assertIsDisplayed()
    }

    @Test
    fun `displays privacy info text`() {
        composeTestRule.setContent {
            InstaCleanLinkTheme {
                MainScreen()
            }
        }

        composeTestRule.onNodeWithText("Ao clicar em links", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("Ao compartilhar", substring = true).assertIsDisplayed()
    }

    @Test
    fun `displays preferred app section`() {
        composeTestRule.setContent {
            InstaCleanLinkTheme {
                MainScreen()
            }
        }

        composeTestRule.onNodeWithText("App Instagram preferido").assertIsDisplayed()
    }

    @Test
    fun `displays no app set message when no preference`() {
        composeTestRule.setContent {
            InstaCleanLinkTheme {
                MainScreen()
            }
        }

        composeTestRule.onNodeWithText("Nenhum app definido").assertIsDisplayed()
        composeTestRule.onNodeWithText("Será perguntado a cada vez").assertIsDisplayed()
    }

    @Test
    fun `displays preferred app label when set`() {
        PrefsManager.setPreferredApp(context, "com.instagram.android", "Instagram")

        composeTestRule.setContent {
            InstaCleanLinkTheme {
                MainScreen()
            }
        }

        composeTestRule.onNodeWithText("Abrindo sempre com:").assertIsDisplayed()
        composeTestRule.onNodeWithText("Instagram").assertIsDisplayed()
    }

    @Test
    fun `reset button clears preference`() {
        PrefsManager.setPreferredApp(context, "com.instagram.android", "Instagram")

        composeTestRule.setContent {
            InstaCleanLinkTheme {
                MainScreen()
            }
        }

        composeTestRule.onNodeWithText("Resetar").performScrollTo().performClick()

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Nenhum app definido").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Será perguntado a cada vez").performScrollTo().assertIsDisplayed()
        assertNull(PrefsManager.getPreferredApp(context))
    }

    @Test
    fun `alter button is always visible`() {
        composeTestRule.setContent {
            InstaCleanLinkTheme {
                MainScreen()
            }
        }

        composeTestRule.onNodeWithText("Alterar").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `reset button only visible when app is set`() {
        composeTestRule.setContent {
            InstaCleanLinkTheme {
                MainScreen()
            }
        }

        // Reset button should NOT exist when no app is set
        composeTestRule.onNodeWithText("Resetar").assertDoesNotExist()
    }

    @Test
    fun `clicking alterar shows app picker dialog`() {
        composeTestRule.setContent {
            InstaCleanLinkTheme {
                MainScreen()
            }
        }

        composeTestRule.onNodeWithText("Alterar").performScrollTo().performClick()

        composeTestRule.onNodeWithText("Escolha o app Instagram").assertIsDisplayed()
    }
}
