package com.example.instacleanlink

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class MainActivityTest {

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        PrefsManager.clearAppLanguage(context)
        AppLanguageManager.setAppLanguage(context, AppLanguage.ENGLISH)
    }

    private fun assertTrackingParamsRemoved(text: String) {
        assertFalse("igsh should be removed", text.contains("igsh="))
        assertFalse("igshid should be removed", text.contains("igshid="))
        assertFalse("igsi should be removed", text.contains("igsi="))
    }

    // --- Normal launch (no share intent) ---

    @Test
    fun `normal launch does not finish activity`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java)
            .create().get()
        assertFalse(activity.isFinishing)
    }

    // --- Share intent ---

    @Test
    fun `share intent with instagram link cleans known trackers and finishes`() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Check this post https://www.instagram.com/p/ABC?igsh=track123&igsi=track456")
        }
        val activity = Robolectric.buildActivity(MainActivity::class.java, intent)
            .create().get()

        val shadow = shadowOf(activity)
        val nextIntent = shadow.nextStartedActivity
        assertNotNull(nextIntent)

        // The shared text should be cleaned
        val sharedText = nextIntent.getStringExtra(Intent.EXTRA_TEXT)
            ?: nextIntent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)?.getStringExtra(Intent.EXTRA_TEXT)

        if (sharedText != null) {
            assertTrackingParamsRemoved(sharedText)
            assertTrue("Link base should remain", sharedText.contains("https://www.instagram.com/p/ABC"))
        }

        assertTrue(activity.isFinishing)
    }

    @Test
    fun `share intent targets whatsapp package`() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Link https://www.instagram.com/p/X?igsh=y&igshid=z")
        }
        val activity = Robolectric.buildActivity(MainActivity::class.java, intent)
            .create().get()

        val shadow = shadowOf(activity)
        val nextIntent = shadow.nextStartedActivity
        assertNotNull(nextIntent)
        // The intent either targets whatsapp directly or is a chooser fallback
        val targetPkg = nextIntent.`package`
            ?: nextIntent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)?.`package`
        // Either whatsapp or chooser is acceptable
        assertTrue(targetPkg == "com.whatsapp" || nextIntent.action == Intent.ACTION_CHOOSER)
    }

    @Test
    fun `share intent with text without instagram link forwards unchanged`() {
        val original = "Just some normal text without any link"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, original)
        }
        val activity = Robolectric.buildActivity(MainActivity::class.java, intent)
            .create().get()

        val shadow = shadowOf(activity)
        val nextIntent = shadow.nextStartedActivity
        assertNotNull(nextIntent)

        val sharedText = nextIntent.getStringExtra(Intent.EXTRA_TEXT)
            ?: nextIntent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)?.getStringExtra(Intent.EXTRA_TEXT)

        if (sharedText != null) {
            assertEquals(original, sharedText)
        }

        assertTrue(activity.isFinishing)
    }

    @Test
    fun `share intent with no extra text finishes activity`() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            // No EXTRA_TEXT
        }
        val activity = Robolectric.buildActivity(MainActivity::class.java, intent)
            .create().get()

        assertTrue(activity.isFinishing)
    }

    @Test
    fun `share intent with instagram link without igsh forwards as-is`() {
        val original = "Check this: https://www.instagram.com/p/ABC123"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, original)
        }
        val activity = Robolectric.buildActivity(MainActivity::class.java, intent)
            .create().get()

        val shadow = shadowOf(activity)
        val nextIntent = shadow.nextStartedActivity
        assertNotNull(nextIntent)

        val sharedText = nextIntent.getStringExtra(Intent.EXTRA_TEXT)
            ?: nextIntent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)?.getStringExtra(Intent.EXTRA_TEXT)

        if (sharedText != null) {
            assertTrue(sharedText.contains("https://www.instagram.com/p/ABC123"))
        }

        assertTrue(activity.isFinishing)
    }
}
