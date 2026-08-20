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
 * Persistent logging system that writes detailed logs to a file.
 * - File: instacleanlink_debug.log in Downloads/LOGS INSTACLEAN LINK/
 * - Fallback: the app's internal storage if permission is unavailable
 * - Max size: 1 GB (removes the oldest half of the log when exceeded)
 * - Thread-safe
 */
object AppLogger {

    private const val LOG_FILE_NAME = "instacleanlink_debug.log"
    private const val LOG_FOLDER_NAME = "LOGS INSTACLEAN LINK"
    private const val MAX_SIZE_BYTES = 1L * 1024 * 1024 * 1024 // 1 GB
    private const val TRIM_TARGET_RATIO = 0.5 // Keep 50% of the newest entries after trimming.

    @Volatile
    private var logFile: File? = null

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    /**
     * Initializes the logger. Tries to save to Downloads/LOGS INSTACLEAN LINK/.
     * Falls back to the app's internal storage when permission is unavailable.
     */
    fun init(context: Context) {
        if (logFile == null) {
            synchronized(this) {
                if (logFile == null) {
                    logFile = resolveLogFile(context)
                    // Initial log entry showing where logs will be saved.
                    val file = logFile
                    if (file != null) {
                        android.util.Log.i("AppLogger", "Logs will be saved to: ${file.absolutePath}")
                    }
                }
            }
        }
    }

    private fun resolveLogFile(context: Context): File {
        // Try using Downloads/LOGS INSTACLEAN LINK/.
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
                android.util.Log.w("AppLogger", "Could not create the Downloads log directory: ${e.message}")
            }
        }

        // Fallback: use the app's internal storage.
        android.util.Log.w("AppLogger", "Using internal app storage as the log fallback")
        return File(context.filesDir, LOG_FILE_NAME)
    }

    /**
     * Checks whether the app has permission to access external storage.
     */
    private fun hasStoragePermission(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                // Android 10 and below — WRITE_EXTERNAL_STORAGE is enough.
                true
            }
        } catch (e: Exception) {
            // This can fail in test environments such as Robolectric.
            false
        }
    }

    /**
     * Reinitializes the logger after the user grants storage permission.
     * Forces the log file path to be re-evaluated.
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
     * Detailed log event with device and context information.
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
     * Logs device information, useful at the start of each session.
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

        // Also send entries to logcat.
        when (level) {
            "DEBUG" -> android.util.Log.d(tag, message)
            "INFO" -> android.util.Log.i(tag, message)
            "WARN" -> android.util.Log.w(tag, message)
            "ERROR" -> android.util.Log.e(tag, message)
            "EVENT" -> android.util.Log.i(tag, message)
        }

        synchronized(this) {
            try {
                // Check size before writing.
                if (file.exists() && file.length() > MAX_SIZE_BYTES) {
                    trimLogFile(file)
                }
                file.appendText(entry)
            } catch (e: Exception) {
                android.util.Log.e("AppLogger", "Failed to write log entry: ${e.message}")
            }
        }
    }

    /**
     * Removes the oldest half of the log file to reduce its size.
     * Keeps the most recent entries.
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
            // If trimming fails, wipe the file and start fresh.
            try {
                file.writeText("[LOG RESET at ${dateFormat.format(Date())}] Previous log was too large and could not be trimmed.\n")
            } catch (_: Exception) { }
        }
    }

    /**
     * Returns the path of the active log file.
     */
    fun getLogFilePath(): String? = logFile?.absolutePath

    /**
     * Returns the current log file size in bytes.
     */
    fun getLogFileSize(): Long = logFile?.let { if (it.exists()) it.length() else 0L } ?: 0L

    /**
     * Clears the entire log file.
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
