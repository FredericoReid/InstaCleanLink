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
import androidx.activity.ComponentActivity

class UrlHandlerActivity : ComponentActivity() {

    companion object {
        private const val TAG = "UrlHandler"
    }

    /** Visible for testing — allows injecting a custom cleaner to simulate failures */
    internal var urlCleanerFn: (Uri) -> Uri = { uri -> UrlCleaner.cleanUri(uri) }

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
            android.util.Log.w(TAG, "Erro ao inicializar logger: ${e.message}")
        }

        intent?.data?.let { uri ->
            handleUrl(uri)
        } ?: run {
            AppLogger.w(TAG, "Intent data é null — finalizando activity")
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
            AppLogger.i(TAG, "Link não é do Instagram — encaminhando sem limpar")
            forwardToExternalApp(uri)
            return
        }

        if (!hasTracking) {
            AppLogger.i(TAG, "Link sem rastreadores conhecidos — encaminhando sem limpar")
            Toast.makeText(this, "ℹ️ Rastreadores conhecidos não encontrados.", Toast.LENGTH_SHORT).show()
            forwardToExternalApp(uri)
            return
        }

        // Tenta limpar
        try {
            val cleanedUri = urlCleanerFn(uri)
            AppLogger.i(TAG, "Link limpo com sucesso: original=$uri → limpo=$cleanedUri")

            PrefsManager.clearFailed(this, uri.toString())
            Toast.makeText(this, "✅ Link limpo!", Toast.LENGTH_SHORT).show()
            openInInstagramApp(cleanedUri)

        } catch (e: Exception) {
            AppLogger.e(TAG, "Falha ao limpar link: $uri", e)
            handleCleaningFailure(uri, e)
        }
    }

    /**
     * Encaminha um link para um app externo, EXCLUINDO nosso próprio app
     * para evitar loop infinito no chooser.
     */
    private fun forwardToExternalApp(uri: Uri) {
        val preferredApp = PrefsManager.getPreferredApp(this)

        // Se é um link do Instagram e tem app preferido, tenta abrir direto
        if (UrlCleaner.isInstagramLink(uri) && preferredApp != null) {
            AppLogger.d(TAG, "forwardToExternalApp: tentando app preferido=$preferredApp para link Instagram sem rastreadores conhecidos")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage(preferredApp)
            }
            try {
                startActivity(intent)
                AppLogger.i(TAG, "forwardToExternalApp: aberto com sucesso no app preferido=$preferredApp")
                finish()
                return
            } catch (e: Exception) {
                AppLogger.e(TAG, "forwardToExternalApp: app preferido falhou=$preferredApp", e)
                PrefsManager.clearPreferredApp(this)
            }
        }

        // Fallback: abre no browser excluindo nosso app
        openInBrowser(uri)
    }

    private fun handleCleaningFailure(originalUri: Uri, error: Exception) {
        val urlKey = originalUri.toString()

        if (PrefsManager.isRetry(this, urlKey)) {
            AppLogger.w(TAG, "handleCleaningFailure: 2ª tentativa — abrindo link original: $originalUri")
            Toast.makeText(
                this,
                "⚠️ Não foi possível limpar. Abrindo link original...",
                Toast.LENGTH_LONG
            ).show()
            PrefsManager.clearFailed(this, urlKey)
            openInInstagramApp(originalUri)
        } else {
            AppLogger.w(TAG, "handleCleaningFailure: 1ª tentativa — bloqueando por segurança: $originalUri")
            PrefsManager.markFailed(this, urlKey)
            Toast.makeText(
                this,
                "🚫 Erro ao limpar link. Bloqueado por segurança.\nToque novamente para abrir mesmo assim.",
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
                AppLogger.i(TAG, "openInInstagramApp: aberto com sucesso no app preferido=$preferredApp")
                finish()
                return
            } catch (e: Exception) {
                AppLogger.e(TAG, "openInInstagramApp: app preferido não encontrado=$preferredApp", e)
                PrefsManager.clearPreferredApp(this)
                Toast.makeText(
                    this,
                    "App preferido não encontrado. Escolha outro.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        showAppChooser(uri)
    }

    private fun showAppChooser(uri: Uri) {
        val instagramApps = findInstagramApps()
        AppLogger.d(TAG, "showAppChooser: encontrados ${instagramApps.size} apps Instagram: " +
                instagramApps.joinToString { it.activityInfo.packageName })

        when {
            instagramApps.isEmpty() -> {
                AppLogger.w(TAG, "showAppChooser: nenhum app Instagram encontrado — abrindo no browser")
                Toast.makeText(
                    this,
                    "Nenhum app Instagram encontrado. Abrindo no navegador.",
                    Toast.LENGTH_SHORT
                ).show()
                openInBrowser(uri)
            }
            instagramApps.size == 1 -> {
                val pkg = instagramApps[0].activityInfo.packageName
                AppLogger.i(TAG, "showAppChooser: único app encontrado=$pkg — abrindo direto")
                startActivity(Intent(Intent.ACTION_VIEW, uri).apply { setPackage(pkg) })
                finish()
            }
            else -> {
                showAppChooserDialog(uri, instagramApps)
            }
        }
    }

    /**
     * Abre no browser, excluindo nosso próprio app para evitar loop infinito.
     * Usa Intent.makeMainSelectorActivity para abrir especificamente no browser.
     */
    private fun openInBrowser(uri: Uri) {
        AppLogger.d(TAG, "openInBrowser: uri=$uri")
        try {
            // Abordagem 1: Usar seletor de browser para evitar que nosso app intercepte
            val browserIntent = Intent.makeMainSelectorActivity(
                Intent.ACTION_MAIN,
                Intent.CATEGORY_APP_BROWSER
            ).apply {
                data = uri
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(browserIntent)
            AppLogger.i(TAG, "openInBrowser: aberto com sucesso via CATEGORY_APP_BROWSER")
        } catch (e: Exception) {
            AppLogger.e(TAG, "openInBrowser: fallback CATEGORY_APP_BROWSER falhou", e)
            try {
                // Abordagem 2: Chooser excluindo nosso próprio componente
                val viewIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                }
                val chooser = Intent.createChooser(viewIntent, "Abrir com").apply {
                    val ownComponent = ComponentName(this@UrlHandlerActivity, UrlHandlerActivity::class.java)
                    putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ownComponent))
                }
                startActivity(chooser)
                AppLogger.i(TAG, "openInBrowser: aberto via chooser com EXTRA_EXCLUDE_COMPONENTS")
            } catch (e2: Exception) {
                AppLogger.e(TAG, "openInBrowser: todas as abordagens falharam", e2)
                Toast.makeText(this, "Não foi possível abrir o link.", Toast.LENGTH_LONG).show()
            }
        }
        finish()
    }

    private fun showAppChooserDialog(uri: Uri, apps: List<ResolveInfo>) {
        val pm = packageManager
        val appNames = apps.map { it.loadLabel(pm).toString() }.toTypedArray()
        val appPackages = apps.map { it.activityInfo.packageName }

        AppLogger.d(TAG, "showAppChooserDialog: mostrando dialog com ${apps.size} opções")

        AlertDialog.Builder(this)
            .setTitle("Abrir com qual Instagram?")
            .setItems(appNames) { _, which ->
                val chosenPackage = appPackages[which]
                val chosenLabel = appNames[which]
                AppLogger.i(TAG, "showAppChooserDialog: usuário escolheu $chosenLabel ($chosenPackage)")

                AlertDialog.Builder(this)
                    .setTitle("Usar \"$chosenLabel\" sempre?")
                    .setPositiveButton("Sempre") { _, _ ->
                        PrefsManager.setPreferredApp(this, chosenPackage, chosenLabel)
                        AppLogger.i(TAG, "showAppChooserDialog: salvo como preferido=$chosenPackage")
                        startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                            setPackage(chosenPackage)
                        })
                        finish()
                    }
                    .setNegativeButton("Só desta vez") { _, _ ->
                        startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                            setPackage(chosenPackage)
                        })
                        finish()
                    }
                    .setOnCancelListener { finish() }
                    .show()
            }
            .setNegativeButton("Cancelar") { _, _ -> finish() }
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
        AppLogger.d(TAG, "findInstagramApps: total=${allApps.size}, sem nosso app=${filtered.size}, " +
                "todos: ${allApps.joinToString { "${it.activityInfo.packageName}/${it.activityInfo.name}" }}")
        return filtered
    }
}