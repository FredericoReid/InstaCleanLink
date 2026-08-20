package com.example.instacleanlink

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.activity.ComponentActivity

class UrlHandlerActivity : ComponentActivity() {

    companion object {
        private const val TAG = "UrlHandler"
    }

    /** Visible for testing — allows injecting a custom cleaner to simulate failures */
    internal var urlCleanerFn: (Uri) -> Uri = { uri -> UrlCleaner.cleanUri(uri) }

    private fun text(@StringRes resId: Int, vararg formatArgs: Any): String {
        return AppLanguageManager.getString(this, resId, *formatArgs)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            AppLogger.init(this)
            AppLogger.logDeviceInfo(TAG)
            AppLogger.logEvent(TAG, "ACTIVITY_CREATED", mapOf(
                "intent_action" to intent?.action,
                "intent_data" to intent?.data?.toString(),
                "intent_type" to intent?.type,
                "intent_package" to intent?.`package`,
                "intent_component" to intent?.component?.toString(),
                "intent_categories" to intent?.categories?.joinToString(","),
                "intent_flags" to intent?.flags?.toString(),
                "referrer" to referrer?.toString(),
                "calling_package" to callingPackage
            ))
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to initialize logger: ${e.message}")
        }

        intent?.data?.let { uri ->
            handleUrl(uri)
        } ?: run {
            AppLogger.w(TAG, "Intent data is null — finishing activity")
            finish()
        }
    }

    private fun handleUrl(uri: Uri) {
        AppLogger.i(TAG, "handleUrl: uri=$uri, host=${uri.host}, path=${uri.path}, query=${uri.query}")

        val isInstagram = UrlCleaner.isInstagramLink(uri)
        val trackingParams = UrlCleaner.findTrackingParams(uri)
        val hasTracking = trackingParams.isNotEmpty()

        AppLogger.d(
            TAG,
            "isInstagramLink=$isInstagram, hasTrackingParam=$hasTracking, trackingParams=${trackingParams.joinToString(",")}" 
        )

        if (!isInstagram) {
            AppLogger.i(TAG, "Link is not from Instagram — forwarding without cleaning")
            forwardToExternalApp(uri)
            return
        }

        if (!hasTracking) {
            AppLogger.i(TAG, "Link has no known trackers — forwarding without cleaning")
            Toast.makeText(this, text(R.string.known_trackers_not_found), Toast.LENGTH_SHORT).show()
            forwardToExternalApp(uri)
            return
        }

        // Try to sanitize the link.
        try {
            val cleanedUri = urlCleanerFn(uri)
            AppLogger.i(TAG, "Link cleaned successfully: original=$uri → cleaned=$cleanedUri")

            PrefsManager.clearFailed(this, uri.toString())
            Toast.makeText(this, text(R.string.link_cleaned), Toast.LENGTH_SHORT).show()
            openInInstagramApp(cleanedUri)

        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to clean link: $uri", e)
            handleCleaningFailure(uri)
        }
    }

    /**
     * Forwards a link to an external app while excluding this app itself
     * to avoid an infinite loop in the chooser.
     */
    private fun forwardToExternalApp(uri: Uri) {
        val preferredApp = PrefsManager.getPreferredApp(this)

        // If this is an Instagram link and a preferred app exists, try it first.
        if (UrlCleaner.isInstagramLink(uri) && preferredApp != null) {
            AppLogger.d(TAG, "forwardToExternalApp: trying preferred app=$preferredApp for Instagram link without known trackers")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(preferredApp)
            }
            try {
                startActivity(intent)
                AppLogger.i(TAG, "forwardToExternalApp: opened successfully in preferred app=$preferredApp")
                finish()
                return
            } catch (e: Exception) {
                AppLogger.e(TAG, "forwardToExternalApp: preferred app failed=$preferredApp", e)
                PrefsManager.clearPreferredApp(this)
            }
        }

        // Fallback: open in a browser while excluding this app.
        openInBrowser(uri)
    }

    private fun handleCleaningFailure(originalUri: Uri) {
        val urlKey = originalUri.toString()

        if (PrefsManager.isRetry(this, urlKey)) {
            AppLogger.w(TAG, "handleCleaningFailure: second attempt — opening original link: $originalUri")
            Toast.makeText(
                this,
                text(R.string.could_not_clean_opening_original),
                Toast.LENGTH_LONG
            ).show()
            PrefsManager.clearFailed(this, urlKey)
            openInInstagramApp(originalUri)
        } else {
            AppLogger.w(TAG, "handleCleaningFailure: first attempt — blocking for safety: $originalUri")
            PrefsManager.markFailed(this, urlKey)
            Toast.makeText(
                this,
                text(R.string.error_cleaning_link_blocked),
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }

    private fun openInInstagramApp(uri: Uri) {
        val preferredApp = PrefsManager.getPreferredApp(this)
        AppLogger.d(TAG, "openInInstagramApp: uri=$uri, preferredApp=$preferredApp")

        if (preferredApp != null) {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(preferredApp)
            }
            try {
                startActivity(intent)
                AppLogger.i(TAG, "openInInstagramApp: opened successfully in preferred app=$preferredApp")
                finish()
                return
            } catch (e: Exception) {
                AppLogger.e(TAG, "openInInstagramApp: preferred app not found=$preferredApp", e)
                PrefsManager.clearPreferredApp(this)
                Toast.makeText(
                    this,
                    text(R.string.preferred_app_not_found_choose_another),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        showAppChooser(uri)
    }

    private fun showAppChooser(uri: Uri) {
        val instagramApps = findInstagramApps()
        AppLogger.d(TAG, "showAppChooser: found ${instagramApps.size} Instagram apps: " +
                instagramApps.joinToString { it.activityInfo.packageName })

        when {
            instagramApps.isEmpty() -> {
                AppLogger.w(TAG, "showAppChooser: no Instagram apps found — opening in browser")
                Toast.makeText(
                    this,
                    text(R.string.no_instagram_app_found_opening_browser),
                    Toast.LENGTH_SHORT
                ).show()
                openInBrowser(uri)
            }
            instagramApps.size == 1 -> {
                val pkg = instagramApps[0].activityInfo.packageName
                AppLogger.i(TAG, "showAppChooser: single app found=$pkg — opening directly")
                startActivity(Intent(Intent.ACTION_VIEW, uri).apply { setPackage(pkg) })
                finish()
            }
            else -> {
                showAppChooserDialog(uri, instagramApps)
            }
        }
    }

    /**
     * Opens a browser while excluding this app itself to avoid an infinite loop.
     * Uses Intent.makeMainSelectorActivity to target browser apps specifically.
     */
    private fun openInBrowser(uri: Uri) {
        AppLogger.d(TAG, "openInBrowser: uri=$uri")
        try {
            // Approach 1: use the browser selector to avoid app interception.
            val browserIntent = Intent.makeMainSelectorActivity(
                Intent.ACTION_MAIN,
                Intent.CATEGORY_APP_BROWSER
            ).apply {
                data = uri
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(browserIntent)
            AppLogger.i(TAG, "openInBrowser: opened successfully via CATEGORY_APP_BROWSER")
        } catch (e: Exception) {
            AppLogger.e(TAG, "openInBrowser: CATEGORY_APP_BROWSER fallback failed", e)
            try {
                // Approach 2: chooser excluding this app component.
                val viewIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                }
                val chooser = Intent.createChooser(viewIntent, text(R.string.open_with)).apply {
                    val ownComponent = ComponentName(this@UrlHandlerActivity, UrlHandlerActivity::class.java)
                    putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ownComponent))
                }
                startActivity(chooser)
                AppLogger.i(TAG, "openInBrowser: opened via chooser with EXTRA_EXCLUDE_COMPONENTS")
            } catch (e2: Exception) {
                AppLogger.e(TAG, "openInBrowser: all approaches failed", e2)
                Toast.makeText(this, text(R.string.could_not_open_link), Toast.LENGTH_LONG).show()
            }
        }
        finish()
    }

    private fun showAppChooserDialog(uri: Uri, apps: List<ResolveInfo>) {
        val pm = packageManager
        val appNames = apps.map { it.loadLabel(pm).toString() }.toTypedArray()
        val appPackages = apps.map { it.activityInfo.packageName }

        AppLogger.d(TAG, "showAppChooserDialog: showing dialog with ${apps.size} options")

        AlertDialog.Builder(this)
            .setTitle(text(R.string.open_with_which_instagram))
            .setItems(appNames) { _, which ->
                val chosenPackage = appPackages[which]
                val chosenLabel = appNames[which]
                AppLogger.i(TAG, "showAppChooserDialog: user selected $chosenLabel ($chosenPackage)")

                AlertDialog.Builder(this)
                    .setTitle(text(R.string.always_use_app, chosenLabel))
                    .setPositiveButton(text(R.string.always)) { _, _ ->
                        PrefsManager.setPreferredApp(this, chosenPackage, chosenLabel)
                        AppLogger.i(TAG, "showAppChooserDialog: saved preferred app=$chosenPackage")
                        startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                            setPackage(chosenPackage)
                        })
                        finish()
                    }
                    .setNegativeButton(text(R.string.just_once)) { _, _ ->
                        startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                            setPackage(chosenPackage)
                        })
                        finish()
                    }
                    .setOnCancelListener { finish() }
                    .show()
            }
            .setNegativeButton(text(R.string.cancel)) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun findInstagramApps(): List<ResolveInfo> {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/test"))
        val allApps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        }
        val filtered = allApps.filter { it.activityInfo.packageName != packageName }
        AppLogger.d(TAG, "findInstagramApps: total=${allApps.size}, without this app=${filtered.size}, " +
                "all: ${allApps.joinToString { "${it.activityInfo.packageName}/${it.activityInfo.name}" }}")
        return filtered
    }
}