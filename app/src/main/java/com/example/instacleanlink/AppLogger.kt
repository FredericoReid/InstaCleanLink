package com.example.instacleanlink

import android.content.Context
import android.os.Build
import android.os.Environment
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Sistema de logging persistente que grava logs detalhados em arquivo.
 * - Arquivo: instacleanlink_debug.log em Downloads/LOGS INSTACLEAN LINK/
 * - Fallback: pasta interna do app se não tiver permissão
 * - Limite de tamanho: 1GB (limpa metade dos logs mais antigos quando excede)
 * - Thread-safe
 */
object AppLogger {

    private const val LOG_FILE_NAME = "instacleanlink_debug.log"
    private const val LOG_FOLDER_NAME = "LOGS INSTACLEAN LINK"
    private const val MAX_SIZE_BYTES = 1L * 1024 * 1024 * 1024 // 1 GB
    private const val TRIM_TARGET_RATIO = 0.5 // Manter 50% quando limpar

    @Volatile
    private var logFile: File? = null

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    /**
     * Inicializa o logger. Tenta salvar em Downloads/LOGS INSTACLEAN LINK/.
     * Se não tiver permissão, usa a pasta interna do app como fallback.
     */
    fun init(context: Context) {
        if (logFile == null) {
            synchronized(this) {
                if (logFile == null) {
                    logFile = resolveLogFile(context)
                    // Log inicial informando onde os logs estão sendo salvos
                    val file = logFile
                    if (file != null) {
                        android.util.Log.i("AppLogger", "Logs serão salvos em: ${file.absolutePath}")
                    }
                }
            }
        }
    }

    private fun resolveLogFile(context: Context): File {
        // Tenta usar Downloads/LOGS INSTACLEAN LINK/
        if (hasStoragePermission()) {
            try {
                val downloadsDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                )
                val logDir = File(downloadsDir, LOG_FOLDER_NAME)
                if (!logDir.exists()) {
                    logDir.mkdirs()
                }
                if (logDir.exists() && logDir.canWrite()) {
                    return File(logDir, LOG_FILE_NAME)
                }
            } catch (e: Exception) {
                android.util.Log.w("AppLogger", "Não foi possível criar pasta em Downloads: ${e.message}")
            }
        }

        // Fallback: pasta interna do app
        android.util.Log.w("AppLogger", "Usando pasta interna como fallback para logs")
        return File(context.filesDir, LOG_FILE_NAME)
    }

    /**
     * Verifica se tem permissão de acesso ao armazenamento externo.
     */
    private fun hasStoragePermission(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                // Android 10 e abaixo — WRITE_EXTERNAL_STORAGE é suficiente
                true
            }
        } catch (e: Exception) {
            // Pode falhar em ambientes de teste (Robolectric)
            false
        }
    }

    /**
     * Reinicializa o logger (útil após o usuário conceder permissão de armazenamento).
     * Força a reavaliação do caminho do arquivo de log.
     */
    fun reinit(context: Context) {
        synchronized(this) {
            logFile = null
        }
        init(context)
    }

    fun d(tag: String, message: String) = log("DEBUG", tag, message)
    fun i(tag: String, message: String) = log("INFO", tag, message)
    fun w(tag: String, message: String) = log("WARN", tag, message)
    fun e(tag: String, message: String) = log("ERROR", tag, message)

    fun e(tag: String, message: String, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        log("ERROR", tag, "$message\n  Exception: ${throwable.javaClass.name}: ${throwable.message}\n  Stack trace:\n$sw")
    }

    /**
     * Log detalhado com informações do dispositivo e contexto.
     */
    fun logEvent(tag: String, event: String, details: Map<String, String?> = emptyMap()) {
        val sb = StringBuilder()
        sb.appendLine("=== EVENT: $event ===")
        details.forEach { (key, value) ->
            sb.appendLine("  $key: $value")
        }
        log("EVENT", tag, sb.toString())
    }

    /**
     * Log de informações do dispositivo (útil no início de cada sessão).
     */
    fun logDeviceInfo(tag: String) {
        logEvent(tag, "DEVICE_INFO", mapOf(
            "manufacturer" to Build.MANUFACTURER,
            "model" to Build.MODEL,
            "android_version" to Build.VERSION.RELEASE,
            "sdk_int" to Build.VERSION.SDK_INT.toString(),
            "brand" to Build.BRAND,
            "product" to Build.PRODUCT
        ))
    }

    private fun log(level: String, tag: String, message: String) {
        val file = logFile ?: return
        val timestamp = dateFormat.format(Date())
        val threadName = Thread.currentThread().name
        val entry = "[$timestamp] [$level] [$tag] [thread:$threadName] $message\n"

        // Também loga no logcat
        when (level) {
            "DEBUG" -> android.util.Log.d(tag, message)
            "INFO" -> android.util.Log.i(tag, message)
            "WARN" -> android.util.Log.w(tag, message)
            "ERROR" -> android.util.Log.e(tag, message)
            "EVENT" -> android.util.Log.i(tag, message)
        }

        synchronized(this) {
            try {
                // Verifica tamanho antes de escrever
                if (file.exists() && file.length() > MAX_SIZE_BYTES) {
                    trimLogFile(file)
                }
                file.appendText(entry)
            } catch (e: Exception) {
                android.util.Log.e("AppLogger", "Falha ao escrever no log: ${e.message}")
            }
        }
    }

    /**
     * Remove a primeira metade do arquivo de log para reduzir o tamanho.
     * Mantém os logs mais recentes.
     */
    private fun trimLogFile(file: File) {
        try {
            val lines = file.readLines()
            val targetLines = (lines.size * TRIM_TARGET_RATIO).toInt()
            val linesToKeep = lines.takeLast(targetLines)

            file.writeText("")
            file.appendText("[LOG TRIMMED at ${dateFormat.format(Date())}] " +
                    "Removed ${lines.size - targetLines} old entries, keeping $targetLines entries.\n")
            linesToKeep.forEach { line ->
                file.appendText(line + "\n")
            }
        } catch (e: Exception) {
            // Se falhar ao limpar, tenta apagar e recomeçar
            try {
                file.writeText("[LOG RESET at ${dateFormat.format(Date())}] Previous log was too large and could not be trimmed.\n")
            } catch (_: Exception) { }
        }
    }

    /**
     * Retorna o caminho do arquivo de log para o usuário poder acessar.
     */
    fun getLogFilePath(): String? = logFile?.absolutePath

    /**
     * Retorna o tamanho atual do arquivo de log em bytes.
     */
    fun getLogFileSize(): Long = logFile?.let { if (it.exists()) it.length() else 0L } ?: 0L

    /**
     * Limpa todo o arquivo de log.
     */
    fun clearLog() {
        val file = logFile ?: return
        synchronized(this) {
            try {
                file.writeText("[LOG CLEARED at ${dateFormat.format(Date())}]\n")
            } catch (_: Exception) { }
        }
    }
}
