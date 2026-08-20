package com.example.instacleanlink

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PrefsManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Clear all prefs before each test
        PrefsManager.clearPreferredApp(context)
    }

    // --- Preferred App ---

    @Test
    fun `getPreferredApp returns null when no app set`() {
        assertNull(PrefsManager.getPreferredApp(context))
    }

    @Test
    fun `getPreferredAppLabel returns null when no app set`() {
        assertNull(PrefsManager.getPreferredAppLabel(context))
    }

    @Test
    fun `setPreferredApp saves package and label`() {
        PrefsManager.setPreferredApp(context, "com.instagram.android", "Instagram")

        assertEquals("com.instagram.android", PrefsManager.getPreferredApp(context))
        assertEquals("Instagram", PrefsManager.getPreferredAppLabel(context))
    }

    @Test
    fun `setPreferredApp overwrites previous preference`() {
        PrefsManager.setPreferredApp(context, "com.instagram.android", "Instagram")
        PrefsManager.setPreferredApp(context, "com.instagram.lite", "Instagram Lite")

        assertEquals("com.instagram.lite", PrefsManager.getPreferredApp(context))
        assertEquals("Instagram Lite", PrefsManager.getPreferredAppLabel(context))
    }

    @Test
    fun `clearPreferredApp removes both package and label`() {
        PrefsManager.setPreferredApp(context, "com.instagram.android", "Instagram")
        PrefsManager.clearPreferredApp(context)

        assertNull(PrefsManager.getPreferredApp(context))
        assertNull(PrefsManager.getPreferredAppLabel(context))
    }

    // --- Retry tracking ---

    @Test
    fun `isRetry returns false when no failure recorded`() {
        assertFalse(PrefsManager.isRetry(context, "https://instagram.com/p/ABC?igsh=x"))
    }

    @Test
    fun `isRetry returns true after markFailed within expiry window`() {
        val url = "https://instagram.com/p/ABC?igsh=x"
        PrefsManager.markFailed(context, url)
        assertTrue(PrefsManager.isRetry(context, url))
    }

    @Test
    fun `isRetry returns false for different URL`() {
        PrefsManager.markFailed(context, "https://instagram.com/p/ABC?igsh=x")
        assertFalse(PrefsManager.isRetry(context, "https://instagram.com/p/DEF?igsh=y"))
    }

    @Test
    fun `clearFailed removes the failed record`() {
        val url = "https://instagram.com/p/ABC?igsh=x"
        PrefsManager.markFailed(context, url)
        assertTrue(PrefsManager.isRetry(context, url))

        PrefsManager.clearFailed(context, url)
        assertFalse(PrefsManager.isRetry(context, url))
    }

    @Test
    fun `markFailed can track multiple URLs`() {
        val url1 = "https://instagram.com/p/A?igsh=1"
        val url2 = "https://instagram.com/p/B?igsh=2"

        PrefsManager.markFailed(context, url1)
        PrefsManager.markFailed(context, url2)

        assertTrue(PrefsManager.isRetry(context, url1))
        assertTrue(PrefsManager.isRetry(context, url2))
    }

    @Test
    fun `clearFailed only clears specified URL`() {
        val url1 = "https://instagram.com/p/A?igsh=1"
        val url2 = "https://instagram.com/p/B?igsh=2"

        PrefsManager.markFailed(context, url1)
        PrefsManager.markFailed(context, url2)

        PrefsManager.clearFailed(context, url1)

        assertFalse(PrefsManager.isRetry(context, url1))
        assertTrue(PrefsManager.isRetry(context, url2))
    }
}
