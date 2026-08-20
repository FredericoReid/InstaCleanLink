package com.example.instacleanlink

import android.content.Context

object PrefsManager {
    private const val PREFS_NAME = "instaclean_prefs"
    private const val KEY_PREFERRED_APP = "preferred_instagram_app"
    private const val KEY_PREFERRED_APP_LABEL = "preferred_instagram_app_label"
    private const val KEY_APP_LANGUAGE = "app_language"
    private const val FAILED_PREFS_NAME = "failed_urls"
    private const val RETRY_EXPIRY_MS = 60_000L // 1 minute

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun failedPrefs(context: Context) =
        context.getSharedPreferences(FAILED_PREFS_NAME, Context.MODE_PRIVATE)

    // --- Preferred app ---

    fun getPreferredApp(context: Context): String? =
        prefs(context).getString(KEY_PREFERRED_APP, null)

    fun getPreferredAppLabel(context: Context): String? =
        prefs(context).getString(KEY_PREFERRED_APP_LABEL, null)

    fun setPreferredApp(context: Context, packageName: String, label: String) {
        prefs(context).edit()
            .putString(KEY_PREFERRED_APP, packageName)
            .putString(KEY_PREFERRED_APP_LABEL, label)
            .apply()
    }

    fun clearPreferredApp(context: Context) {
        prefs(context).edit()
            .remove(KEY_PREFERRED_APP)
            .remove(KEY_PREFERRED_APP_LABEL)
            .apply()
    }

    // --- App language ---

    fun getAppLanguageCode(context: Context): String? =
        prefs(context).getString(KEY_APP_LANGUAGE, null)

    fun setAppLanguageCode(context: Context, languageCode: String) {
        prefs(context).edit()
            .putString(KEY_APP_LANGUAGE, languageCode)
            .apply()
    }

    fun clearAppLanguage(context: Context) {
        prefs(context).edit()
            .remove(KEY_APP_LANGUAGE)
            .apply()
    }

    // --- Retry tracking ---

    fun isRetry(context: Context, url: String): Boolean {
        val lastFail = failedPrefs(context).getLong(url, 0L)
        return lastFail > 0 && (System.currentTimeMillis() - lastFail) < RETRY_EXPIRY_MS
    }

    fun markFailed(context: Context, url: String) {
        failedPrefs(context).edit()
            .putLong(url, System.currentTimeMillis())
            .apply()
    }

    fun clearFailed(context: Context, url: String) {
        failedPrefs(context).edit().remove(url).apply()
    }
}
