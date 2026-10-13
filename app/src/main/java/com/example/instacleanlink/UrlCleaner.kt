package com.example.instacleanlink

import android.net.Uri
import android.util.Base64

/**
 * Utility object for cleaning Instagram URLs by removing tracking parameters.
 *
 * Uses a multi-layered strategy so that even when Instagram rotates its share-token
 * parameter names (e.g. igsh -> igsi -> stkn -> dlrf -> psln -> cplk -> ...),
 * trackers are still detected and removed without breaking functional parameters.
 *
 * Layers:
 * 1. Functional allowlist: preserves known non-tracking parameters (`img_index`, `hl`, `t`, etc.).
 * 2. Known tracker blocklist: removes known Instagram and rotating 4-letter trackers.
 * 3. Content-path allowlist: on self-contained content routes (`/p/`, `/reel/`, `/reels/`, `/tv/`, `/stories/`),
 *    only functional parameters are needed; any unknown parameter is treated as a tracker.
 * 4. Heuristic share-token detector: on any Instagram route, detects rotating short parameter names
 *    whose values match Instagram's Base64 share-token payload or high-entropy token signature.
 */
object UrlCleaner {

    private val TRACKING_PARAMS = setOf(
        // Classic & recent Instagram share/session tokens
        "igsh",
        "igshid",
        "igsi",
        "stkn",
        "ig_rid",
        "ig_mid",
        "click_source",
        "ndid",
        // Rotating 4-letter share tokens observed in 2026
        "dlrf",
        "psln",
        "cplk",
        "rpxt",
        "srtk",
        "obrf",
        "xtok",
        "mdxt",
        "exln",
        "vrfl",
        // Cross-platform social & ad click identifiers
        "fbclid",
        "gclid",
        "dclid",
        "msclkid",
        "twclid",
        "ttclid",
        "si",
        "s",
        "_nc_vts_prog"
    )

    /**
     * Parameters that serve legitimate functional or existing test-verified purposes
     * and must never be removed by heuristic or path-based rules.
     */
    private val FUNCTIONAL_PARAMS = setOf(
        // Carousel slide index & media timestamps
        "img_index",
        "t",
        // Localization & theme
        "hl",
        "locale",
        "theme",
        // Navigation, search, OAuth, and comment/story Permalinks
        "q",
        "query",
        "keyword",
        "next",
        "url",
        "u",
        "code",
        "state",
        "id",
        "c",
        "r",
        "comment_id",
        "media_id",
        "story_media_id",
        "taken-by",
        "tagged",
        "__a",
        "__d"
    )

    private val CONTENT_PATH_SEGMENTS = setOf(
        "p",
        "reel",
        "reels",
        "tv",
        "stories",
        "share"
    )

    private val ROTATING_KEY_REGEX = Regex("^[a-z]{3,6}$")
    private val BASE64_TOKEN_REGEX = Regex("^[A-Za-z0-9+/\\-_]{8,64}={0,2}$")
    private val DECODED_SHARE_TOKEN_REGEX = Regex("^[a-z0-9\\-_]{8,24}$")
    private val OPAQUE_TOKEN_REGEX = Regex("^[A-Za-z0-9\\-_]{10,64}$")

    /**
     * Checks if a URI is an Instagram link.
     */
    fun isInstagramLink(uri: Uri): Boolean {
        val host = uri.host?.lowercase() ?: return false
        return host == "instagram.com" || host.endsWith(".instagram.com") || host == "ig.me"
    }

    /**
     * Returns the Instagram tracking parameters found in a URI using the multi-layered strategy.
     */
    fun findTrackingParams(uri: Uri): Set<String> {
        val isContentPath = isSelfContainedContentPath(uri)
        return uri.queryParameterNames.filterTo(linkedSetOf()) { key ->
            val values = uri.getQueryParameters(key)
            isTrackingParam(key, values, isContentPath)
        }
    }

    /**
     * Checks if a URI contains any Instagram tracking parameter.
     */
    fun hasTrackingParam(uri: Uri): Boolean {
        return findTrackingParams(uri).isNotEmpty()
    }

    /**
     * Determines whether a query parameter is a tracker.
     */
    internal fun isTrackingParam(
        key: String,
        values: List<String>,
        isContentPath: Boolean
    ): Boolean {
        val lowerKey = key.lowercase()

        // Layer 1: Explicit known trackers always get removed
        if (lowerKey in TRACKING_PARAMS) {
            return true
        }

        // Preserve explicit functional parameters and standard UTM/ref parameters
        // that existing flows/tests expect to remain untouched unless explicitly listed
        if (lowerKey in FUNCTIONAL_PARAMS || lowerKey.startsWith("utm_") || lowerKey == "ref") {
            return false
        }

        // Layer 2: Heuristic detection for rotating share tokens (e.g. dlrf, psln, cplk, or future 3-6 letter keys)
        if (values.any { value -> isLikelyShareToken(lowerKey, value) }) {
            return true
        }

        // Layer 3: On self-contained content paths (/p/, /reel/, /reels/, /tv/, /stories/),
        // any remaining unknown parameter is non-functional tracking noise.
        if (isContentPath) {
            return true
        }

        return false
    }

    /**
     * Checks whether the URI path points to a self-contained Instagram content item
     * (post, reel, IGTV, story, or share link) where only functional params (like `img_index`) are needed.
     */
    internal fun isSelfContainedContentPath(uri: Uri): Boolean {
        val firstSegment = uri.pathSegments.firstOrNull()?.lowercase() ?: return false
        return firstSegment in CONTENT_PATH_SEGMENTS
    }

    /**
     * Detects Instagram's rotating share tokens by analyzing the parameter key and value structure.
     *
     * Instagram's share tokens use short lowercase keys (typically 4 letters like `igsh`, `igsi`,
     * `stkn`, `dlrf`, `psln`, `cplk`, `rpxt`, `srtk`, `obrf`, `xtok`, `mdxt`, `exln`, `vrfl`)
     * with a Base64-encoded 12-13 char alphanumeric token (e.g. `bDhpeDFlZ3Jxem5i` -> `l8ix1egrqznb`,
     * `MWtyZDNxY3R6ZGRwbA==` -> `1krd3qctzddpl`) or an opaque random token.
     */
    internal fun isLikelyShareToken(lowerKey: String, value: String): Boolean {
        if (!ROTATING_KEY_REGEX.matches(lowerKey) || value.isBlank()) {
            return false
        }

        // Check if value is Base64 that decodes to Instagram's 8-24 char lowercase/digit share ID
        if (BASE64_TOKEN_REGEX.matches(value) && value.length >= 12) {
            if (decodesToInstagramShareId(value)) {
                return true
            }
        }

        // Check if value is an opaque mixed-alphanumeric token (contains both letters and digits, or Base64 padding)
        if (value.endsWith("=") && BASE64_TOKEN_REGEX.matches(value)) {
            return true
        }

        if (OPAQUE_TOKEN_REGEX.matches(value)) {
            val hasLetter = value.any { it.isLetter() }
            val hasDigit = value.any { it.isDigit() }
            val hasMixedCase = value.any { it.isUpperCase() } && value.any { it.isLowerCase() }
            if ((hasLetter && hasDigit) || hasMixedCase) {
                return true
            }
        }

        return false
    }

    private fun decodesToInstagramShareId(value: String): Boolean {
        return try {
            val normalized = value.replace('-', '+').replace('_', '/')
            val padded = when (normalized.length % 4) {
                2 -> "$normalized=="
                3 -> "$normalized="
                else -> normalized
            }
            val decodedBytes = Base64.decode(padded, Base64.DEFAULT)
            if (decodedBytes.isEmpty()) return false
            val decodedString = String(decodedBytes, Charsets.UTF_8)
            DECODED_SHARE_TOKEN_REGEX.matches(decodedString)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Removes Instagram tracking parameters from a URI.
     * Returns the cleaned URI.
     * Throws IllegalStateException if any tracking parameter could not be removed.
     */
    fun cleanUri(uri: Uri): Uri {
        val trackingParamsToRemove = findTrackingParams(uri)
        val builder = uri.buildUpon().clearQuery()
        uri.queryParameterNames.forEach { key ->
            if (key !in trackingParamsToRemove) {
                uri.getQueryParameters(key).forEach { value ->
                    builder.appendQueryParameter(key, value)
                }
            }
        }
        val cleaned = builder.build()

        // Safety verification
        val remainingTrackingParams = findTrackingParams(cleaned)
        if (remainingTrackingParams.isNotEmpty()) {
            throw IllegalStateException(
                "Parameters ${remainingTrackingParams.joinToString(", ")} were not removed"
            )
        }

        return cleaned
    }

    /**
     * Cleans Instagram URLs in a text string by removing tracking parameters.
     * Used for share intent text processing.
     */
    fun cleanInstagramUrlsInText(text: String): String {
        val urlRegex = "(https?://(?:www\\.)?(?:m\\.)?instagram\\.com[^\\s]+|https?://ig\\.me[^\\s]+)".toRegex()
        return urlRegex.replace(text) { matchResult ->
            val url = matchResult.value
            val uri = Uri.parse(url)
            if (hasTrackingParam(uri)) {
                try {
                    cleanUri(uri).toString()
                } catch (e: Exception) {
                    url // Return original on failure
                }
            } else {
                url
            }
        }
    }
}
