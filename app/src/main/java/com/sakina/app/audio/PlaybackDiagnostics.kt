package com.sakina.app.audio

import android.content.Context
import android.os.Build
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Optional playback diagnostics, off by default. When on, the player writes short event lines
 * (state changes, errors, audio focus, timing checks) to a local file. Nothing leaves the device
 * unless the user previews the report and chooses to share it.
 */
object PlaybackDiagnostics {
    private const val PREFS = "playback_diagnostics"
    private const val MAX_LINES = 400
    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault())

    private fun file(c: Context) = File(c.filesDir, "diagnostics/playback.log")

    fun isEnabled(c: Context): Boolean = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("enabled", false)

    fun setEnabled(c: Context, on: Boolean) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("enabled", on).apply()
        if (on) log(c, "diagnostics on") else clear(c)
    }

    @Synchronized
    fun log(c: Context, line: String) {
        if (!isEnabled(c)) return
        val f = file(c)
        f.parentFile?.mkdirs()
        val lines = if (f.isFile) f.readLines() else emptyList()
        val next = (lines + (fmt.format(Instant.now()) + "  " + line.replace('\n', ' '))).takeLast(MAX_LINES)
        f.writeText(next.joinToString("\n") + "\n")
    }

    /** The report as the user will see it before sharing. */
    fun report(c: Context): String {
        val header = buildString {
            appendLine("Sakina playback report")
            appendLine("App ${runCatching { c.packageManager.getPackageInfo(c.packageName, 0).versionName }.getOrNull()} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("—")
        }
        val f = file(c)
        return header + (if (f.isFile) f.readText() else "")
    }

    @Synchronized
    fun clear(c: Context) {
        file(c).delete()
    }
}
