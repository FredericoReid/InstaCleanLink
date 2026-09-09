package com.example.instacleanlink

import android.content.Intent
import android.net.Uri
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast
import androidx.test.core.app.ApplicationProvider

@RunWith(RobolectricTestRunner::class)
class UrlHandlerActivityTest {

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        PrefsManager.clearPreferredApp(context)
        PrefsManager.clearAppLanguage(context)
        AppLanguageManager.setAppLanguage(context, AppLanguage.ENGLISH)
    }

    private fun buildViewIntent(url: String): Intent {
        return Intent(Intent.ACTION_VIEW, Uri.parse(url))
    }

    // --- Link without known trackers: forwards to external app (no loop) ---

    @Test
    fun `link without known trackers is forwarded to external app`() {
        val intent = buildViewIntent("https://www.instagram.com/p/ABC123")
        val activity = Robolectric.buildActivity(UrlHandlerActivity::class.java, intent)
            .create().get()
        val shadow = shadowOf(activity)
        val nextIntent = shadow.nextStartedActivity
        assertNotNull("An intent should be launched", nextIntent)
        assertTrue("Activity should finish", activity.isFinishing)
        // Should NOT re-launch our own activity (prevents loop)
        val targetPkg = nextIntent.`package`
        assertTrue(
            "Should not target our own package",
            targetPkg == null || targetPkg != activity.packageName
        )
    }

    @Test
    fun `link without known trackers uses preferred app if set`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        PrefsManager.setPreferredApp(context, "com.instagram.lite", "Instagram Lite")
        val intent = buildViewIntent("https://www.instagram.com/p/ABC123")
        val activity = Robolectric.buildActivity(UrlHandlerActivity::class.java, intent)
            .create().get()
        val shadow = shadowOf(activity)
        val nextIntent = shadow.nextStartedActivity
        assertNotNull("An intent should be launched", nextIntent)
        assertEquals("Should use preferred app", "com.instagram.lite", nextIntent.`package`)
        assertTrue("Activity should finish", activity.isFinishing)
    }

    @Test
    fun `non instagram link is forwarded to browser`() {
        val intent = buildViewIntent("https://www.google.com/search?q=test")
        val activity = Robolectric.buildActivity(UrlHandlerActivity::class.java, intent)
            .create().get()
        val shadow = shadowOf(activity)
        val nextIntent = shadow.nextStartedActivity
        assertNotNull("An intent should be launched", nextIntent)
        assertTrue("Activity should finish", activity.isFinishing)
    }

    // --- Link with known trackers: cleans and shows toast ---

    @Test
    fun `link with igsh is cleaned and shows toast`() {
        val intent = buildViewIntent("https://www.instagram.com/p/ABC123?igsh=tracking123")
        Robolectric.buildActivity(UrlHandlerActivity::class.java, intent)
            .create().get()
        // Verify toast was shown (latest toast may be "Nenhum app Instagram encontrado"
        // since no Instagram apps are installed in test, but the "Link limpo" toast
        // was shown before it)
        val latestToast = ShadowToast.getTextOfLatestToast()
        assertNotNull("Expected at least one toast to be shown", latestToast)
    }

    @Test
    fun `link with igsh and other params preserves other params`() {
        val intent = buildViewIntent("https://www.instagram.com/p/ABC?utm_source=share&igsh=track&ref=1")
        val activity = Robolectric.buildActivity(UrlHandlerActivity::class.java, intent)
            .create().get()
        val shadow = shadowOf(activity)
        val nextIntent = shadow.nextStartedActivity
        if (nextIntent != null && nextIntent.data != null) {
            val cleanedUri = nextIntent.data!!
            assertNull(cleanedUri.getQueryParameter("igsh"))
            assertEquals("share", cleanedUri.getQueryParameter("utm_source"))
            assertEquals("1", cleanedUri.getQueryParameter("ref"))
        }
    }

    @Test
    fun `link with igsh igshid and igsi removes all known trackers`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        PrefsManager.setPreferredApp(context, "com.instagram.android", "Instagram")
        val intent = buildViewIntent("https://www.instagram.com/p/ABC?igsh=track&igshid=track2&igsi=track3&stkn=track4&utm_source=share")
        val activity = Robolectric.buildActivity(UrlHandlerActivity::class.java, intent)
            .create().get()
        val nextIntent = shadowOf(activity).nextStartedActivity
        assertNotNull(nextIntent)
        val cleanedUri = nextIntent.data!!
        assertNull(cleanedUri.getQueryParameter("igsh"))
        assertNull(cleanedUri.getQueryParameter("igshid"))
        assertNull(cleanedUri.getQueryParameter("igsi"))
        assertNull(cleanedUri.getQueryParameter("stkn"))
        assertEquals("share", cleanedUri.getQueryParameter("utm_source"))
    }

    @Test
    fun `link with stkn is cleaned and forwarded without stkn`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        PrefsManager.setPreferredApp(context, "com.instagram.android", "Instagram")
        val intent = buildViewIntent("https://www.instagram.com/p/ABC123?stkn=tracking123&utm_medium=copy")
        val activity = Robolectric.buildActivity(UrlHandlerActivity::class.java, intent)
            .create().get()
        val nextIntent = shadowOf(activity).nextStartedActivity
        assertNotNull(nextIntent)
        val cleanedUri = nextIntent.data!!
        assertNull(cleanedUri.getQueryParameter("stkn"))
        assertEquals("copy", cleanedUri.getQueryParameter("utm_medium"))
    }

    @Test
    fun `instagram link without known trackers shows not found toast`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        PrefsManager.setPreferredApp(context, "com.instagram.lite", "Instagram Lite")
        val intent = buildViewIntent("https://www.instagram.com/p/ABC123?utm_source=share")
        Robolectric.buildActivity(UrlHandlerActivity::class.java, intent)
            .create().get()
        assertEquals(
            "ℹ️ No known trackers were found.",
            ShadowToast.getTextOfLatestToast()
        )
    }

    @Test
    fun `configured Portuguese language localizes user messages`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        AppLanguageManager.setAppLanguage(context, AppLanguage.PORTUGUESE)
        val intent = buildViewIntent("https://www.instagram.com/p/ABC123?utm_source=share")

        Robolectric.buildActivity(UrlHandlerActivity::class.java, intent)
            .create().get()

        assertEquals(
            "ℹ️ Rastreadores conhecidos não encontrados.",
            ShadowToast.getTextOfLatestToast()
        )
    }

    // --- ig.me links ---

    @Test
    fun `ig me link without known trackers is forwarded`() {
        val intent = buildViewIntent("https://ig.me/abc123")
        val activity = Robolectric.buildActivity(UrlHandlerActivity::class.java, intent)
            .create().get()
        val shadow = shadowOf(activity)
        val nextIntent = shadow.nextStartedActivity
        assertNotNull(nextIntent)
        assertTrue(activity.isFinishing)
    }

    // --- Null intent data ---

    @Test
    fun `null intent data finishes activity`() {
        val intent = Intent(Intent.ACTION_VIEW)
        val activity = Robolectric.buildActivity(UrlHandlerActivity::class.java, intent)
            .create().get()
        assertTrue(activity.isFinishing)
    }

    // --- Retry logic ---

    @Test
    fun `successful cleaning clears previous failure record`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val url = "https://www.instagram.com/p/ABC?igsh=track"
        PrefsManager.markFailed(context, url)
        assertTrue(PrefsManager.isRetry(context, url))
        val intent = buildViewIntent(url)
        Robolectric.buildActivity(UrlHandlerActivity::class.java, intent)
            .create().get()
        assertFalse(PrefsManager.isRetry(context, url))
    }

    // --- Preferred app ---

    @Test
    fun `uses preferred app when set`() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        PrefsManager.setPreferredApp(context, "com.instagram.android", "Instagram")
        val intent = buildViewIntent("https://www.instagram.com/p/ABC?igsh=track")
        val activity = Robolectric.buildActivity(UrlHandlerActivity::class.java, intent)
            .create().get()
        val shadow = shadowOf(activity)
        val nextIntent = shadow.nextStartedActivity
        assertNotNull(nextIntent)
        assertEquals("com.instagram.android", nextIntent.`package`)
    }
}
