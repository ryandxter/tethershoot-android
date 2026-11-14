package com.rvmeur.transferin

import android.content.Context
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Logger terpusat untuk TransferIn Foundation (pola ProbeLog).
 * Format baris: [+elapsedMs][threadName] message
 * Output: Memory buffer snapshot + File di getExternalFilesDir/foundation-log-<stamp>.txt + UI callback.
 */
object FoundationLog {
    private val startTimeMs: Long = System.currentTimeMillis()
    private val lock = Any()
    private val buffer = StringBuilder(65536)
    private var writer: BufferedWriter? = null

    @Volatile
    var currentLogFile: File? = null
        private set

    @Volatile
    var uiCallback: ((String) -> Unit)? = null

    fun init(context: Context) {
        synchronized(lock) {
            if (writer != null) return
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val file = File(dir, "foundation-log-$stamp.txt")
            currentLogFile = file
            try {
                writer = BufferedWriter(OutputStreamWriter(FileOutputStream(file, true), Charsets.UTF_8))
            } catch (_: Exception) {
                writer = null
            }
        }
        log("=== TransferIn Foundation Log Dimulai (file: ${currentLogFile?.name}) ===")
    }

    fun log(message: String) {
        val elapsed = System.currentTimeMillis() - startTimeMs
        val threadName = Thread.currentThread().name
        val formatted = String.format(Locale.US, "[+%d][%s] %s", elapsed, threadName, message)
        synchronized(lock) {
            buffer.append(formatted).append('\n')
            try {
                writer?.write(formatted)
                writer?.newLine()
                writer?.flush()
            } catch (_: Exception) {
            }
        }
        uiCallback?.invoke(formatted)
    }

    fun error(message: String, t: Throwable? = null) {
        val suffix = if (t != null) " | ${t.javaClass.simpleName}: ${t.message}" else ""
        log("ERROR: $message$suffix")
    }

    fun snapshot(): String {
        synchronized(lock) {
            return buffer.toString()
        }
    }

    fun clear(context: Context) {
        synchronized(lock) {
            buffer.setLength(0)
            try {
                writer?.flush()
                writer?.close()
            } catch (_: Exception) {
            }
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val file = File(dir, "foundation-log-$stamp.txt")
            currentLogFile = file
            try {
                writer = BufferedWriter(OutputStreamWriter(FileOutputStream(file, true), Charsets.UTF_8))
            } catch (_: Exception) {
                writer = null
            }
        }
        log("=== LOG DIRESET (file: ${currentLogFile?.name}) ===")
    }

    fun close() {
        synchronized(lock) {
            try {
                writer?.flush()
                writer?.close()
            } catch (_: Exception) {
            }
            writer = null
        }
    }
}
