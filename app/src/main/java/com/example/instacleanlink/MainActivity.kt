package com.example.instacleanlink

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.instacleanlink.ui.theme.InstaCleanLinkTheme

class MainActivity : ComponentActivity() {

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Reinitialize the logger after the user returns from the storage permission screen.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            AppLogger.reinit(this)
            AppLogger.i(
                "MainActivity",
                "MANAGE_EXTERNAL_STORAGE granted — logs saved at: ${AppLogger.getLogFilePath()}"
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize logging and request storage permission when needed.
        try {
            AppLogger.init(this)
            requestStoragePermissionIfNeeded()
        } catch (e: Exception) {
            // Ignore failures in test environments.
            android.util.Log.w("MainActivity", "Failed to initialize logger/permissions: ${e.message}")
        }

        if (intent?.action == Intent.ACTION_SEND && "text/plain" == intent.type) {
            handleSharedText(intent)
        } else {
            setContent {
                InstaCleanLinkTheme {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        MainScreen(modifier = Modifier.padding(innerPadding))
                    }
                }
            }
        }
    }

    private fun requestStoragePermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    manageStorageLauncher.launch(intent)
                } catch (e: Exception) {
                    // Fallback: open the generic permission settings screen.
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    manageStorageLauncher.launch(intent)
                }
            }
        }
        // Android 10 and below: WRITE_EXTERNAL_STORAGE is already declared in the manifest
        // and is granted automatically on API levels below 29.
    }

    private fun handleSharedText(intent: Intent) {
        intent.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedText ->
            val cleanedText = UrlCleaner.cleanInstagramUrlsInText(sharedText)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, cleanedText)
                setPackage("com.whatsapp")
            }

            try {
                startActivity(shareIntent)
            } catch (e: Exception) {
                val chooserIntent = Intent.createChooser(
                    shareIntent,
                    AppLanguageManager.getString(this, R.string.share_with)
                )
                startActivity(chooserIntent)
            }
        }
        finish()
    }
}

fun findInstagramApps(context: android.content.Context): List<ResolveInfo> {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/test"))
    val allApps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.packageManager.queryIntentActivities(
            intent,
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
        )
    } else {
        @Suppress("DEPRECATION")
        context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
    }
    return allApps.filter { it.activityInfo.packageName != context.packageName }
}

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var preferredAppLabel by remember {
        mutableStateOf(PrefsManager.getPreferredAppLabel(context))
    }
    var installedApps by remember { mutableStateOf<List<ResolveInfo>>(emptyList()) }
    var showAppPicker by remember { mutableStateOf(false) }
    var appLanguage by remember { mutableStateOf(AppLanguageManager.getAppLanguage(context)) }

    fun text(@StringRes resId: Int, vararg formatArgs: Any): String {
        return AppLanguageManager.getString(context, appLanguage, resId, *formatArgs)
    }

    fun quantityText(@PluralsRes resId: Int, quantity: Int, vararg formatArgs: Any): String {
        return AppLanguageManager.getQuantityString(context, appLanguage, resId, quantity, *formatArgs)
    }

    LaunchedEffect(Unit) {
        installedApps = findInstagramApps(context)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = text(R.string.how_it_works_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = text(R.string.how_it_works_description)
        )

        HorizontalDivider()

        Text(
            text = text(R.string.language_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val currentLanguageLabel = text(
                    if (appLanguage == AppLanguage.ENGLISH) {
                        R.string.language_english
                    } else {
                        R.string.language_portuguese
                    }
                )

                Text(
                    text = text(R.string.current_language, currentLanguageLabel),
                    style = MaterialTheme.typography.bodyMedium
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            AppLanguageManager.setAppLanguage(context, AppLanguage.ENGLISH)
                            appLanguage = AppLanguage.ENGLISH
                        },
                        enabled = appLanguage != AppLanguage.ENGLISH
                    ) {
                        Text(text(R.string.language_english))
                    }
                    OutlinedButton(
                        onClick = {
                            AppLanguageManager.setAppLanguage(context, AppLanguage.PORTUGUESE)
                            appLanguage = AppLanguage.PORTUGUESE
                        },
                        enabled = appLanguage != AppLanguage.PORTUGUESE
                    ) {
                        Text(text(R.string.language_portuguese))
                    }
                }
            }
        }

        HorizontalDivider()

        Text(
            text = text(R.string.preferred_instagram_app_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (preferredAppLabel != null) {
                            text(R.string.opening_always_with)
                        } else {
                            text(R.string.no_app_set)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (preferredAppLabel != null) {
                        Text(
                            text = preferredAppLabel!!,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        Text(
                            text = text(R.string.ask_every_time),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showAppPicker = true }) {
                Text(text(R.string.change))
            }
            if (preferredAppLabel != null) {
                OutlinedButton(onClick = {
                    PrefsManager.clearPreferredApp(context)
                    preferredAppLabel = null
                }) {
                    Text(text(R.string.reset))
                }
            }
        }

        Text(
            text = quantityText(
                R.plurals.instagram_apps_found,
                installedApps.size,
                installedApps.size
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    if (showAppPicker) {
        AppPickerDialog(
            apps = installedApps,
            appLanguage = appLanguage,
            onAppSelected = { packageName, label ->
                PrefsManager.setPreferredApp(context, packageName, label)
                preferredAppLabel = label
                showAppPicker = false
            },
            onDismiss = { showAppPicker = false }
        )
    }
}

@Composable
fun AppPickerDialog(
    apps: List<ResolveInfo>,
    appLanguage: AppLanguage,
    onAppSelected: (packageName: String, label: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    fun text(@StringRes resId: Int, vararg formatArgs: Any): String {
        return AppLanguageManager.getString(context, appLanguage, resId, *formatArgs)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text(R.string.choose_instagram_app)) },
        text = {
            LazyColumn {
                items(apps) { app ->
                    val pm = context.packageManager
                    val label = app.loadLabel(pm).toString()
                    val packageName = app.activityInfo.packageName

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAppSelected(packageName, label) }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column {
                            Text(label, fontWeight = FontWeight.Medium)
                            Text(
                                packageName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text(R.string.cancel)) }
        }
    )
}
