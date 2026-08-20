package com.example.instacleanlink

import android.net.Uri

/**
 * Utility object for cleaning Instagram URLs by removing tracking parameters.
 */
object UrlCleaner {

    private val TRACKING_PARAMS = setOf("igsh", "igshid", "igsi")

    /**
     * Checks if a URI is an Instagram link.
     */
    fun isInstagramLink(uri: Uri): Boolean {
        val host = uri.host ?: return false
        return host.contains("instagram.com") || host == "ig.me"
    }

    /**
     * Returns the known Instagram tracking parameters found in a URI.
     */
    fun findTrackingParams(uri: Uri): Set<String> {
        return uri.queryParameterNames.filterTo(linkedSetOf()) { it in TRACKING_PARAMS }
    }

    /**
     * Checks if a URI contains any known Instagram tracking parameter.
     */
    fun hasTrackingParam(uri: Uri): Boolean {
        return findTrackingParams(uri).isNotEmpty()
    }

    /**
     * Removes known Instagram tracking parameters from a URI.
     * Returns the cleaned URI.
     * Throws IllegalStateException if any tracking parameter could not be removed.
     */
    fun cleanUri(uri: Uri): Uri {
        val builder = uri.buildUpon().clearQuery()
        uri.queryParameterNames.forEach { key ->
            if (key !in TRACKING_PARAMS) {
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
     * Cleans Instagram URLs in a text string by removing known tracking parameters.
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
