package com.example.instacleanlink

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
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
        // Quando o usuário volta da tela de permissão, reinicializa o logger
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            AppLogger.reinit(this)
            AppLogger.i("MainActivity", "Permissão MANAGE_EXTERNAL_STORAGE concedida — logs salvos em: ${AppLogger.getLogFilePath()}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicializa o logger e pede permissão de armazenamento
        try {
            AppLogger.init(this)
            requestStoragePermissionIfNeeded()
        } catch (e: Exception) {
            // Silencia erros em ambientes de teste
            android.util.Log.w("MainActivity", "Erro ao inicializar logger/permissões: ${e.message}")
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
                    // Fallback: abre a tela genérica de permissão
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    manageStorageLauncher.launch(intent)
                }
            }
        }
        // Android 10 e abaixo: WRITE_EXTERNAL_STORAGE já está no manifest
        // e é concedido automaticamente em API < 29
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
                val chooserIntent = Intent.createChooser(shareIntent, "Compartilhar com")
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
        // --- Seção: Como funciona ---
        Text(
            text = "Como funciona",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Este app limpa links do Instagram automaticamente, " +
                    "removendo rastreadores de privacidade (igsh, igshid e igsi).\n\n" +
                    "• Ao clicar em links: limpa antes de abrir\n" +
                    "• Ao compartilhar: limpa antes de enviar\n\n" +
                    "Se não houver rastreadores conhecidos, o app avisa antes de abrir o link.\n\n" +
                    "Se houver erro na limpeza, o link é bloqueado na primeira tentativa " +
                    "para proteger sua privacidade. Toque novamente para abrir mesmo assim."
        )

        HorizontalDivider()

        // --- Seção: App preferido ---
        Text(
            text = "App Instagram preferido",
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
                        text = if (preferredAppLabel != null) "Abrindo sempre com:"
                        else "Nenhum app definido",
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
                            text = "Será perguntado a cada vez",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        // Botões de ação
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showAppPicker = true }) {
                Text("Alterar")
            }
            if (preferredAppLabel != null) {
                OutlinedButton(onClick = {
                    PrefsManager.clearPreferredApp(context)
                    preferredAppLabel = null
                }) {
                    Text("Resetar")
                }
            }
        }

        // Info sobre apps encontrados
        Text(
            text = "${installedApps.size} app(s) Instagram encontrado(s)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // Dialog para escolher app
    if (showAppPicker) {
        AppPickerDialog(
            apps = installedApps,
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
    onAppSelected: (packageName: String, label: String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Escolha o app Instagram") },
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
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
