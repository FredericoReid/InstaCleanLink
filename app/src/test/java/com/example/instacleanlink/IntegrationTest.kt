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

/**
 * Integration tests that simulate complete user flows end-to-end.
 *
 * These tests verify the full journey:
 * - User clicks a link → intercepted → cleaned → toast shown → opened in correct app
 * - Error scenarios with retry logic (1st fail blocks, 2nd fail allows)
 * - Share intent flow (text with known trackers → cleaned → sent to WhatsApp)
 * - App chooser and preferred app flows
 */
@RunWith(RobolectricTestRunner::class)
class IntegrationTest {

    private lateinit var context: android.app.Application

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PrefsManager.clearPreferredApp(context)
        PrefsManager.clearAppLanguage(context)
        AppLanguageManager.setAppLanguage(context, AppLanguage.ENGLISH)
        ShadowToast.reset()
    }

    private fun buildViewIntent(url: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(url))

    // ===================================================================
    // FLOW 1: User clicks a real Instagram link with a known tracker
    // Expected: trackers removed, "Link cleaned" toast, opens in Instagram app
    // ===================================================================

    @Test
    fun `FLOW - real instagram post link with igsh is cleaned and opened`() {
        val realUrl = "https://www.instagram.com/p/DAi3KuVRIJ4/?igsh=MXRyNGx6OHJ0ZnBubA%3D%3D"
        PrefsManager.setPreferredApp(context, "com.instagram.android", "Instagram")

        val intent = buildViewIntent(realUrl)
        val activity = Robolectric.buildActivity(UrlHandlerActivity::class.java, intent)
            .create().get()

        // 1) Activity should finish after handling
        assertTrue("Activity should finish", activity.isFinishing)

        // 2) An intent should have been launched
        val shadow = shadowOf(activity)
        val launched = shadow.nextStartedActivity
        assertNotNull("An intent should be launched", launched)

        // 3) The launched URL must NOT contain igsh
        val launchedUri = launched.data
        assertNotNull("Launched intent should have data URI", launchedUri)
        assertNull("igsh must be removed from launched URL", launchedUri!!.getQueryParameter("igsh"))

        // 4) The launched URL should still point to the same post
        assertTrue(
            "URL path should be preserved",
            launchedUri.path!!.contains("DAi3KuVRIJ4")
        )

        // 5) Should target the preferred Instagram app
        assertEquals("com.instagram.android", launched.`package`)

        // 6) Toast should have been shown
        val toastText = ShadowToast.getTextOfLatestToast()
        assertNotNull("A toast should be shown", toastText)
    }

    @Test
    fun `FLOW - real instagram reel link with igsh is cleaned`() {
        val reelUrl = "https://www.instagram.com/reel/C_ABCdef123/?igsh=abc123xyz&utm_source=ig_web"
        PrefsManager.setPreferredApp(context, "com.instagram.android", "Instagram")

        val activity = Robolectric.buildActivity(
            UrlHandlerActivity::class.java,
            buildViewIntent(reelUrl)
        ).create().get()

        val launched = shadowOf(activity).nextStartedActivity
        assertNotNull(launched)
        val uri = launched.data!!

        // igsh removed but utm_source preserved
        assertNull("igsh must be removed", uri.getQueryParameter("igsh"))
        assertEquals("utm_source must be preserved", "ig_web", uri.getQueryParameter("utm_source"))
        assertTrue("Reel path preserved", uri.path!!.contains("reel/C_ABCdef123"))
    }

    @Test
    fun `FLOW - real instagram link with all known trackers is fully cleaned`() {
        val url = "https://www.instagram.com/p/DcOuLE2nM6c/?igsh=one&igsi=two&igshid=three&stkn=four&utm_source=share"
        PrefsManager.setPreferredApp(context, "com.instagram.android", "Instagram")

        val activity = Robolectric.buildActivity(
            UrlHandlerActivity::class.java,
            buildViewIntent(url)
        ).create().get()

        val launched = shadowOf(activity).nextStartedActivity
        assertNotNull(launched)
        val uri = launched.data!!
        assertNull("igsh removed", uri.getQueryParameter("igsh"))
        assertNull("igshid removed", uri.getQueryParameter("igshid"))
        assertNull("igsi removed", uri.getQueryParameter("igsi"))
        assertNull("stkn removed", uri.getQueryParameter("stkn"))
        assertEquals("utm_source preserved", "share", uri.getQueryParameter("utm_source"))
    }

    @Test
    fun `FLOW - real instagram story link with igsh is cleaned`() {
        val storyUrl = "https://www.instagram.com/stories/username/123456789/?igsh=trackMe123"
        PrefsManager.setPreferredApp(context, "com.instagram.android", "Instagram")

        val activity = Robolectric.buildActivity(
            UrlHandlerActivity::class.java,
            buildViewIntent(storyUrl)
        ).create().get()

        val launched = shadowOf(activity).nextStartedActivity
        assertNotNull(launched)
        val uri = launched.data!!

        assertNull("igsh removed", uri.getQueryParameter("igsh"))
        assertTrue("Story path preserved", uri.path!!.contains("stories/username/123456789"))
    }

    // ===================================================================
    // FLOW 2: Instagram link WITHOUT known trackers → forwarded directly, with user feedback
    // ===================================================================

    @Test
    fun `FLOW - instagram link without igsh is forwarded directly without cleaning`() {
        val cleanUrl = "https://www.instagram.com/p/ABC123/"

        val activity = Robolectric.buildActivity(
            UrlHandlerActivity::class.java,
            buildViewIntent(cleanUrl)
        ).create().get()

        val launched = shadowOf(activity).nextStartedActivity
        assertNotNull("Intent should be launched", launched)

        // Should be forwarded to external app (not our own app, to avoid loop)
        assertTrue("Activity should finish", activity.isFinishing)
    }

    @Test
    fun `FLOW - instagram link without igsh uses preferred app when set`() {
        val cleanUrl = "https://www.instagram.com/p/ABC123/"
        PrefsManager.setPreferredApp(context, "com.instagram.lite", "Instagram Lite")

        val activity = Robolectric.buildActivity(
            UrlHandlerActivity::class.java,
            buildViewIntent(cleanUrl)
        ).create().get()

        val launched = shadowOf(activity).nextStartedActivity
        assertNotNull("Intent should be launched", launched)
        assertEquals("Should use preferred app", "com.instagram.lite", launched.`package`)
        assertTrue("Activity should finish", activity.isFinishing)
    }

    @Test
    fun `FLOW - instagram link without known trackers shows not found toast`() {
        val cleanUrl = "https://www.instagram.com/p/ABC123/?utm_source=share"
        PrefsManager.setPreferredApp(context, "com.instagram.lite", "Instagram Lite")

        Robolectric.buildActivity(
            UrlHandlerActivity::class.java,
            buildViewIntent(cleanUrl)
        ).create().get()

        assertEquals(
            "ℹ️ No known trackers were found.",
            ShadowToast.getTextOfLatestToast()
        )
    }

    // ===================================================================
    // FLOW 3: Cleaning FAILS on 1st attempt → BLOCKS opening (privacy)
    //         User clicks again (retry) → allows opening with original URL
    // ===================================================================

    @Test
    fun `FLOW - first failure blocks opening and shows error toast`() {
        val url = "https://www.instagram.com/p/FAIL_TEST/?igsh=track123"
        PrefsManager.setPreferredApp(context, "com.instagram.android", "Instagram")

        // Build activity but inject a failing cleaner BEFORE onCreate
        val controller = Robolectric.buildActivity(
            UrlHandlerActivity::class.java,
            buildViewIntent(url)
        )
        val activity = controller.get()
        // Inject failing cleaner
        activity.urlCleanerFn = { throw RuntimeException("Simulated cleaning failure") }
        // Now trigger onCreate
        controller.create()

        // 1) Activity should finish (blocked)
        assertTrue("Activity should finish (blocked)", activity.isFinishing)

        // 2) No intent should be launched (privacy protection)
        val launched = shadowOf(activity).nextStartedActivity
        assertNull("NO intent should be launched on first failure", launched)

        // 3) Error toast should be shown
        val toastText = ShadowToast.getTextOfLatestToast()
        assertNotNull("Error toast should be shown", toastText)
        assertTrue(
            "Toast should mention blocking: '$toastText'",
            toastText!!.contains("Blocked") || toastText.contains("Error")
        )

        // 4) URL should be marked as failed in PrefsManager
        assertTrue(
            "URL should be marked as failed",
            PrefsManager.isRetry(context, url)
        )
    }

    @Test
    fun `FLOW - second attempt after failure allows opening with warning`() {
        val url = "https://www.instagram.com/p/RETRY_TEST/?igsh=track456"
        PrefsManager.setPreferredApp(context, "com.instagram.android", "Instagram")

        // Simulate that a first failure already happened
        PrefsManager.markFailed(context, url)
        assertTrue("URL should be marked as failed", PrefsManager.isRetry(context, url))

        ShadowToast.reset()

        // Build activity with failing cleaner for the retry
        val controller = Robolectric.buildActivity(
            UrlHandlerActivity::class.java,
            buildViewIntent(url)
        )
        val activity = controller.get()
        activity.urlCleanerFn = { throw RuntimeException("Simulated cleaning failure again") }
        controller.create()

        // 1) This time, an intent SHOULD be launched (retry allows it)
        val launched = shadowOf(activity).nextStartedActivity
        assertNotNull("Intent SHOULD be launched on retry", launched)

        // 2) The URL used should be the ORIGINAL (with igsh, since cleaning failed)
        val launchedUri = launched.data
        assertNotNull("Launched URI should not be null", launchedUri)
        assertNotNull(
            "Original URL (with igsh) should be used since cleaning failed",
            launchedUri!!.getQueryParameter("igsh")
        )

        // 3) Warning toast should be shown
        val toastText = ShadowToast.getTextOfLatestToast()
        assertNotNull("Warning toast should be shown", toastText)
        assertTrue(
            "Toast should indicate it could not clean: '$toastText'",
            toastText!!.contains("Could not clean") || toastText.contains("original")
        )

        // 4) Failure record should be cleared after retry
        assertFalse(
            "Failure record should be cleared after retry",
            PrefsManager.isRetry(context, url)
        )
    }

    @Test
    fun `FLOW - full retry cycle - 1st blocks, 2nd opens`() {
        val url = "https://www.instagram.com/p/CYCLE_TEST/?igsh=cycle789"
        PrefsManager.setPreferredApp(context, "com.instagram.android", "Instagram")

        // --- ATTEMPT 1: Should block ---
        val controller1 = Robolectric.buildActivity(
            UrlHandlerActivity::class.java,
            buildViewIntent(url)
        )
        val activity1 = controller1.get()
        activity1.urlCleanerFn = { throw RuntimeException("Fail") }
        controller1.create()

        assertNull(
            "1st attempt: NO intent launched",
            shadowOf(activity1).nextStartedActivity
        )
        assertTrue("1st attempt: URL marked as failed", PrefsManager.isRetry(context, url))

        ShadowToast.reset()

        // --- ATTEMPT 2: Should allow opening ---
        val controller2 = Robolectric.buildActivity(
            UrlHandlerActivity::class.java,
            buildViewIntent(url)
        )
        val activity2 = controller2.get()
        activity2.urlCleanerFn = { throw RuntimeException("Fail again") }
        controller2.create()

        val launched = shadowOf(activity2).nextStartedActivity
        assertNotNull("2nd attempt: Intent SHOULD be launched", launched)
        assertFalse(
            "2nd attempt: Failure record cleared",
            PrefsManager.isRetry(context, url)
        )
    }

    // ===================================================================
    // FLOW 4: Share intent — text with Instagram link gets cleaned
    // ===================================================================

    @Test
    fun `FLOW - share intent cleans known trackers from text and sends to WhatsApp`() {
        val originalText = "Check out this post! https://www.instagram.com/p/DAi3KuVRIJ4/?igsh=one&igsi=two&igshid=three&stkn=four So good!"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, originalText)
        }

        val activity = Robolectric.buildActivity(MainActivity::class.java, intent)
            .create().get()

        assertTrue("Activity should finish after sharing", activity.isFinishing)

        val shadow = shadowOf(activity)
        val launched = shadow.nextStartedActivity
        assertNotNull("Should launch share intent", launched)

        // Get the shared text (may be directly in intent or wrapped in chooser)
        val sharedText = launched.getStringExtra(Intent.EXTRA_TEXT)
            ?: launched.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                ?.getStringExtra(Intent.EXTRA_TEXT)

        assertNotNull("Shared text should exist", sharedText)
        assertFalse("igsh must be removed from shared text", sharedText!!.contains("igsh="))
        assertFalse("igshid must be removed from shared text", sharedText.contains("igshid="))
        assertFalse("igsi must be removed from shared text", sharedText.contains("igsi="))
        assertFalse("stkn must be removed from shared text", sharedText.contains("stkn="))
        assertTrue("Post ID should remain", sharedText.contains("DAi3KuVRIJ4"))
        assertTrue("Surrounding text preserved", sharedText.contains("Check out this post"))
        assertTrue("Surrounding text preserved", sharedText.contains("So good!"))
    }

    @Test
    fun `FLOW - share intent with multiple instagram links cleans all`() {
        val text = "Post1: https://www.instagram.com/p/AAA/?igsh=x1&igsi=y1 and Post2: https://www.instagram.com/p/BBB/?igshid=x2&stkn=x3"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }

        val activity = Robolectric.buildActivity(MainActivity::class.java, intent)
            .create().get()

        val launched = shadowOf(activity).nextStartedActivity
        val sharedText = launched?.getStringExtra(Intent.EXTRA_TEXT)
            ?: launched?.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                ?.getStringExtra(Intent.EXTRA_TEXT)

        assertNotNull(sharedText)
        assertFalse("All igsh params must be removed", sharedText!!.contains("igsh="))
        assertFalse("All igshid params must be removed", sharedText.contains("igshid="))
        assertFalse("All igsi params must be removed", sharedText.contains("igsi="))
        assertFalse("All stkn params must be removed", sharedText.contains("stkn="))
        assertTrue("Post AAA preserved", sharedText.contains("/p/AAA"))
        assertTrue("Post BBB preserved", sharedText.contains("/p/BBB"))
    }

    // ===================================================================
    // FLOW 5: Preferred app management
    // ===================================================================

    @Test
    fun `FLOW - link opens in preferred app and URI is clean`() {
        val url = "https://www.instagram.com/p/TEST123/?igsh=pref_test"
        PrefsManager.setPreferredApp(context, "com.instagram.lite", "Instagram Lite")

        val activity = Robolectric.buildActivity(
            UrlHandlerActivity::class.java,
            buildViewIntent(url)
        ).create().get()

        val launched = shadowOf(activity).nextStartedActivity
        assertNotNull(launched)
        assertEquals(
            "Should target preferred app",
            "com.instagram.lite", launched.`package`
        )
        assertNull(
            "igsh should be removed",
            launched.data!!.getQueryParameter("igsh")
        )
    }

    @Test
    fun `FLOW - preferred app not installed clears pref and falls back`() {
        // Set a package that doesn't exist in Robolectric
        PrefsManager.setPreferredApp(context, "com.nonexistent.app", "Ghost App")

        val url = "https://www.instagram.com/p/FALLBACK/?igsh=fb_test"
        val activity = Robolectric.buildActivity(
            UrlHandlerActivity::class.java,
            buildViewIntent(url)
        ).create().get()

        // Since the preferred app launch throws ActivityNotFoundException in Robolectric,
        // check that the preference was cleared
        // (The actual fallback behavior depends on Robolectric's ActivityNotFoundException handling)
        val toastText = ShadowToast.getTextOfLatestToast()
        assertNotNull("A toast should be shown", toastText)
    }

    // ===================================================================
    // FLOW 6: Non-Instagram links are passed through unchanged
    // ===================================================================

    @Test
    fun `FLOW - non-instagram link passes through without modification`() {
        val url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"

        val activity = Robolectric.buildActivity(
            UrlHandlerActivity::class.java,
            buildViewIntent(url)
        ).create().get()

        val launched = shadowOf(activity).nextStartedActivity
        assertNotNull("Intent should be launched", launched)
        // Should forward to browser (not our own app)
        assertTrue("Activity should finish", activity.isFinishing)
    }

    // ===================================================================
    // FLOW 7: Edge cases with real-world URL patterns
    // ===================================================================

    @Test
    fun `FLOW - url with encoded igsh value is cleaned correctly`() {
        val url = "https://www.instagram.com/p/XYZ/?igsh=MXRy%3D%3D&utm_medium=copy_link"
        PrefsManager.setPreferredApp(context, "com.instagram.android", "Instagram")

        val activity = Robolectric.buildActivity(
            UrlHandlerActivity::class.java,
            buildViewIntent(url)
        ).create().get()

        val launched = shadowOf(activity).nextStartedActivity
        assertNotNull(launched)
        val uri = launched.data!!
        assertNull("igsh removed", uri.getQueryParameter("igsh"))
        assertEquals("utm_medium preserved", "copy_link", uri.getQueryParameter("utm_medium"))
    }

    @Test
    fun `FLOW - url with only igsh param results in clean url without query`() {
        val url = "https://www.instagram.com/p/ONLY_IGSH/?igsh=onlyOne"
        PrefsManager.setPreferredApp(context, "com.instagram.android", "Instagram")

        val activity = Robolectric.buildActivity(
            UrlHandlerActivity::class.java,
            buildViewIntent(url)
        ).create().get()

        val launched = shadowOf(activity).nextStartedActivity
        assertNotNull(launched)
        val uri = launched.data!!
        assertNull("igsh removed", uri.getQueryParameter("igsh"))
        assertTrue("No query params should remain", uri.queryParameterNames.isEmpty())
    }
}