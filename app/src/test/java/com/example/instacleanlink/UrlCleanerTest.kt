package com.example.instacleanlink

import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UrlCleanerTest {

    private fun assertTrackingParamsRemoved(text: String) {
        assertFalse(text.contains("igsh="))
        assertFalse(text.contains("igshid="))
        assertFalse(text.contains("igsi="))
        assertFalse(text.contains("stkn="))
        assertFalse(text.contains("dlrf="))
        assertFalse(text.contains("psln="))
        assertFalse(text.contains("cplk="))
    }

    // --- isInstagramLink ---

    @Test
    fun `isInstagramLink returns true for www instagram com`() {
        val uri = Uri.parse("https://www.instagram.com/p/ABC123")
        assertTrue(UrlCleaner.isInstagramLink(uri))
    }

    @Test
    fun `isInstagramLink returns true for instagram com`() {
        val uri = Uri.parse("https://instagram.com/p/ABC123")
        assertTrue(UrlCleaner.isInstagramLink(uri))
    }

    @Test
    fun `isInstagramLink returns true for m instagram com`() {
        val uri = Uri.parse("https://m.instagram.com/p/ABC123")
        assertTrue(UrlCleaner.isInstagramLink(uri))
    }

    @Test
    fun `isInstagramLink returns true for ig me`() {
        val uri = Uri.parse("https://ig.me/abc123")
        assertTrue(UrlCleaner.isInstagramLink(uri))
    }

    @Test
    fun `isInstagramLink returns false for google com`() {
        val uri = Uri.parse("https://www.google.com/search")
        assertFalse(UrlCleaner.isInstagramLink(uri))
    }

    @Test
    fun `isInstagramLink returns false for whatsapp com`() {
        val uri = Uri.parse("https://wa.me/5511999999999")
        assertFalse(UrlCleaner.isInstagramLink(uri))
    }

    @Test
    fun `isInstagramLink returns false for uri without host`() {
        val uri = Uri.parse("content://some/local/path")
        assertFalse(UrlCleaner.isInstagramLink(uri))
    }

    // --- hasTrackingParam ---

    @Test
    fun `hasTrackingParam returns true when igsh is present`() {
        val uri = Uri.parse("https://www.instagram.com/p/ABC123?igsh=abc123")
        assertTrue(UrlCleaner.hasTrackingParam(uri))
    }

    @Test
    fun `hasTrackingParam returns true when igshid is present`() {
        val uri = Uri.parse("https://www.instagram.com/p/ABC123?igshid=abc123")
        assertTrue(UrlCleaner.hasTrackingParam(uri))
    }

    @Test
    fun `hasTrackingParam returns true when igsi is present`() {
        val uri = Uri.parse("https://www.instagram.com/p/ABC123?igsi=abc123")
        assertTrue(UrlCleaner.hasTrackingParam(uri))
    }

    @Test
    fun `hasTrackingParam returns true when stkn is present`() {
        val uri = Uri.parse("https://www.instagram.com/p/ABC123?stkn=abc123")
        assertTrue(UrlCleaner.hasTrackingParam(uri))
    }

    @Test
    fun `hasTrackingParam returns false when igsh is absent`() {
        val uri = Uri.parse("https://www.instagram.com/p/ABC123")
        assertFalse(UrlCleaner.hasTrackingParam(uri))
    }

    @Test
    fun `hasTrackingParam returns false for empty query`() {
        val uri = Uri.parse("https://www.instagram.com/p/ABC123?")
        assertFalse(UrlCleaner.hasTrackingParam(uri))
    }

    @Test
    fun `hasTrackingParam returns true when igsh with other params`() {
        val uri = Uri.parse("https://www.instagram.com/p/ABC123?utm_source=share&igsh=abc&other=1")
        assertTrue(UrlCleaner.hasTrackingParam(uri))
    }

    @Test
    fun `findTrackingParams returns all known instagram trackers`() {
        val uri = Uri.parse("https://www.instagram.com/p/ABC123?igsh=one&igsi=two&igshid=three&stkn=four&utm_source=share")
        assertEquals(setOf("igsh", "igsi", "igshid", "stkn"), UrlCleaner.findTrackingParams(uri))
    }

    // --- cleanUri ---

    @Test
    fun `cleanUri removes igsh parameter`() {
        val uri = Uri.parse("https://www.instagram.com/p/ABC123?igsh=trackingValue")
        val cleaned = UrlCleaner.cleanUri(uri)
        assertNull(cleaned.getQueryParameter("igsh"))
        assertEquals("https://www.instagram.com/p/ABC123", cleaned.toString())
    }

    @Test
    fun `cleanUri preserves other query parameters`() {
        val uri = Uri.parse("https://www.instagram.com/p/ABC123?utm_source=share&igsh=abc&utm_medium=copy")
        val cleaned = UrlCleaner.cleanUri(uri)
        assertNull(cleaned.getQueryParameter("igsh"))
        assertEquals("share", cleaned.getQueryParameter("utm_source"))
        assertEquals("copy", cleaned.getQueryParameter("utm_medium"))
    }

    @Test
    fun `cleanUri removes all known trackers together`() {
        val uri = Uri.parse("https://www.instagram.com/p/ABC123?igsh=abc&igshid=def&igsi=ghi&stkn=jkl&utm_source=share")
        val cleaned = UrlCleaner.cleanUri(uri)
        assertNull(cleaned.getQueryParameter("igsh"))
        assertNull(cleaned.getQueryParameter("igshid"))
        assertNull(cleaned.getQueryParameter("igsi"))
        assertNull(cleaned.getQueryParameter("stkn"))
        assertEquals("share", cleaned.getQueryParameter("utm_source"))
    }

    @Test
    fun `cleanUri preserves path`() {
        val uri = Uri.parse("https://www.instagram.com/reel/ABC123/?igsh=xyz")
        val cleaned = UrlCleaner.cleanUri(uri)
        assertTrue(cleaned.path!!.contains("reel/ABC123"))
        assertNull(cleaned.getQueryParameter("igsh"))
    }

    @Test
    fun `cleanUri preserves scheme and host`() {
        val uri = Uri.parse("https://www.instagram.com/p/ABC123?igsh=abc")
        val cleaned = UrlCleaner.cleanUri(uri)
        assertEquals("https", cleaned.scheme)
        assertEquals("www.instagram.com", cleaned.host)
    }

    @Test
    fun `cleanUri with only igsh results in no query params`() {
        val uri = Uri.parse("https://www.instagram.com/p/XYZ?igsh=onlyParam")
        val cleaned = UrlCleaner.cleanUri(uri)
        assertTrue(cleaned.queryParameterNames.isEmpty())
    }

    // --- cleanInstagramUrlsInText ---

    @Test
    fun `cleanInstagramUrlsInText cleans igsh from text`() {
        val text = "Olha esse post https://www.instagram.com/p/ABC?igsh=track123 muito legal!"
        val cleaned = UrlCleaner.cleanInstagramUrlsInText(text)
        assertEquals("Olha esse post https://www.instagram.com/p/ABC muito legal!", cleaned)
    }

    @Test
    fun `cleanInstagramUrlsInText removes all known instagram trackers from text`() {
        val text = "Olha https://www.instagram.com/p/ABC/?igsh=one&igsi=two&igshid=three&stkn=four&utm_source=share agora"
        val cleaned = UrlCleaner.cleanInstagramUrlsInText(text)
        assertTrackingParamsRemoved(cleaned)
        assertTrue(cleaned.contains("utm_source=share"))
    }

    @Test
    fun `cleanInstagramUrlsInText preserves text without instagram links`() {
        val text = "Esse é um texto normal sem links"
        val cleaned = UrlCleaner.cleanInstagramUrlsInText(text)
        assertEquals(text, cleaned)
    }

    @Test
    fun `cleanInstagramUrlsInText preserves instagram links without igsh`() {
        val text = "Veja https://www.instagram.com/p/ABC123 aqui"
        val cleaned = UrlCleaner.cleanInstagramUrlsInText(text)
        assertEquals(text, cleaned)
    }

    @Test
    fun `cleanInstagramUrlsInText handles multiple links`() {
        val text = "Post1 https://www.instagram.com/p/A?igsh=x Post2 https://www.instagram.com/p/B?igsh=y fim"
        val cleaned = UrlCleaner.cleanInstagramUrlsInText(text)
        assertTrackingParamsRemoved(cleaned)
        assertTrue(cleaned.contains("https://www.instagram.com/p/A"))
        assertTrue(cleaned.contains("https://www.instagram.com/p/B"))
    }

    // --- cleanInstagramUrlsInText with domain variants ---

    @Test
    fun `cleanInstagramUrlsInText cleans instagram com without www`() {
        val text = "Veja https://instagram.com/p/ABC?igsh=track aqui"
        val cleaned = UrlCleaner.cleanInstagramUrlsInText(text)
        assertTrackingParamsRemoved(cleaned)
        assertTrue(cleaned.contains("https://instagram.com/p/ABC"))
    }

    @Test
    fun `cleanInstagramUrlsInText cleans m instagram com`() {
        val text = "Link https://m.instagram.com/reel/XYZ/?igsh=abc123 legal"
        val cleaned = UrlCleaner.cleanInstagramUrlsInText(text)
        assertTrackingParamsRemoved(cleaned)
        assertTrue(cleaned.contains("https://m.instagram.com/reel/XYZ/"))
    }

    @Test
    fun `cleanInstagramUrlsInText cleans ig me`() {
        val text = "Olha https://ig.me/abc123?igsh=track aqui"
        val cleaned = UrlCleaner.cleanInstagramUrlsInText(text)
        assertTrackingParamsRemoved(cleaned)
        assertTrue(cleaned.contains("https://ig.me/abc123"))
    }

    @Test
    fun `cleanInstagramUrlsInText preserves img_index and removes igsh`() {
        val text = "Post https://www.instagram.com/p/DW7gmcKDPnG/?img_index=2&igsh=MWZ3YnIxNWV5MGh4MA== fim"
        val cleaned = UrlCleaner.cleanInstagramUrlsInText(text)
        assertTrackingParamsRemoved(cleaned)
        assertTrue(cleaned.contains("img_index=2"))
        assertTrue(cleaned.contains("/p/DW7gmcKDPnG/"))
    }

    @Test
    fun `cleanInstagramUrlsInText with real reel link`() {
        val text = "Olha esse reel https://www.instagram.com/reel/DV7Q_95keh4/?igsh=djJtNmxhZDlqYmls muito bom!"
        val cleaned = UrlCleaner.cleanInstagramUrlsInText(text)
        assertTrackingParamsRemoved(cleaned)
        assertTrue(cleaned.contains("/reel/DV7Q_95keh4/"))
        assertTrue(cleaned.contains("muito bom!"))
    }

    @Test
    fun `cleanInstagramUrlsInText www instagram com still works`() {
        val text = "Post https://www.instagram.com/p/ABC?igsh=xyz aqui"
        val cleaned = UrlCleaner.cleanInstagramUrlsInText(text)
        assertTrackingParamsRemoved(cleaned)
        assertTrue(cleaned.contains("https://www.instagram.com/p/ABC"))
    }

    // --- New rotating trackers (dlrf, psln, cplk, rpxt, srtk, obrf, xtok, mdxt, exln, vrfl) & heuristic detection ---

    @Test
    fun `cleanUri removes dlrf parameter from reel link`() {
        val uri = Uri.parse("https://www.instagram.com/reel/DeE9LWlOqV-/?dlrf=bDhpeDFlZ3Jxem5i")
        assertTrue(UrlCleaner.hasTrackingParam(uri))
        val cleaned = UrlCleaner.cleanUri(uri)
        assertNull(cleaned.getQueryParameter("dlrf"))
        assertEquals("https://www.instagram.com/reel/DeE9LWlOqV-/", cleaned.toString())
    }

    @Test
    fun `cleanUri removes psln parameter from reel link`() {
        val uri = Uri.parse("https://www.instagram.com/reel/DeczrQ1PPmJ/?psln=YjkwcThnd2dxZG82")
        assertTrue(UrlCleaner.hasTrackingParam(uri))
        val cleaned = UrlCleaner.cleanUri(uri)
        assertNull(cleaned.getQueryParameter("psln"))
        assertEquals("https://www.instagram.com/reel/DeczrQ1PPmJ/", cleaned.toString())
    }

    @Test
    fun `cleanUri removes cplk parameter from reel link`() {
        val uri = Uri.parse("https://www.instagram.com/reel/DeX9deetwap/?cplk=MWtyZDNxY3R6ZGRwbA==")
        assertTrue(UrlCleaner.hasTrackingParam(uri))
        val cleaned = UrlCleaner.cleanUri(uri)
        assertNull(cleaned.getQueryParameter("cplk"))
        assertEquals("https://www.instagram.com/reel/DeX9deetwap/", cleaned.toString())
    }

    @Test
    fun `cleanUri removes all 2026 rotating 4-letter trackers`() {
        val rotatingParams = listOf("dlrf", "psln", "cplk", "rpxt", "srtk", "obrf", "xtok", "mdxt", "exln", "vrfl")
        for (param in rotatingParams) {
            val uri = Uri.parse("https://www.instagram.com/reel/DeE9LWlOqV-/?$param=bDhpeDFlZ3Jxem5i")
            assertTrue("Should detect $param as tracker", UrlCleaner.hasTrackingParam(uri))
            val cleaned = UrlCleaner.cleanUri(uri)
            assertNull("$param should be removed", cleaned.getQueryParameter(param))
            assertEquals("https://www.instagram.com/reel/DeE9LWlOqV-/", cleaned.toString())
        }
    }

    @Test
    fun `cleanUri removes unknown future rotating 4-letter tracker via heuristic on any route`() {
        // Brand-new 4-letter parameter name not in static list, carrying a Base64 Instagram share token
        val uri = Uri.parse("https://www.instagram.com/username/?zqwx=bDhpeDFlZ3Jxem5i")
        assertTrue(UrlCleaner.hasTrackingParam(uri))
        val cleaned = UrlCleaner.cleanUri(uri)
        assertNull(cleaned.getQueryParameter("zqwx"))
        assertEquals("https://www.instagram.com/username/", cleaned.toString())
    }

    @Test
    fun `cleanUri removes unknown tracker on content path while preserving img_index and hl`() {
        val uri = Uri.parse("https://www.instagram.com/p/DW7gmcKDPnG/?img_index=3&hl=pt&brand_new_tracker=some_val")
        assertTrue(UrlCleaner.hasTrackingParam(uri))
        val cleaned = UrlCleaner.cleanUri(uri)
        assertNull(cleaned.getQueryParameter("brand_new_tracker"))
        assertEquals("3", cleaned.getQueryParameter("img_index"))
        assertEquals("pt", cleaned.getQueryParameter("hl"))
    }

    @Test
    fun `cleanInstagramUrlsInText cleans dlrf psln and cplk links in shared message`() {
        val text = """
            Veja esses reels:
            https://www.instagram.com/reel/DeE9LWlOqV-/?dlrf=bDhpeDFlZ3Jxem5i
            https://www.instagram.com/reel/DeczrQ1PPmJ/?psln=YjkwcThnd2dxZG82
            https://www.instagram.com/reel/DeX9deetwap/?cplk=MWtyZDNxY3R6ZGRwbA==
        """.trimIndent()
        val cleaned = UrlCleaner.cleanInstagramUrlsInText(text)
        assertTrackingParamsRemoved(cleaned)
        assertTrue(cleaned.contains("https://www.instagram.com/reel/DeE9LWlOqV-/"))
        assertTrue(cleaned.contains("https://www.instagram.com/reel/DeczrQ1PPmJ/"))
        assertTrue(cleaned.contains("https://www.instagram.com/reel/DeX9deetwap/"))
    }
}
