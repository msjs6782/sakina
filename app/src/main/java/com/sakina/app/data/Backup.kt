package com.sakina.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

/**
 * A backup file of everything the user set or recorded: settings (places, alerts, look, calendar and personal
 * occasions), the Quran state (bookmarks, reading position, khatma, memorisation) and the tools (adhkar progress,
 * tasbih, prayer log). Downloads (Mushaf pages, recordings) and widget settings are not included.
 */
object Backup {
    const val FORMAT = "sakina-backup"
    const val VERSION = 1
    /** Larger files are refused before parsing. */
    const val MAX_BYTES = 8 * 1024 * 1024

    @Serializable
    data class Contents(
        val format: String = FORMAT,
        val version: Int = VERSION,
        val exportedAt: String = "",
        val settings: AppSettings = AppSettings(),
        val quran: QuranState = QuranState(),
        val tools: ToolsState = ToolsState(),
    )

    private val json = Json(SettingsJson) { prettyPrint = true }

    fun encode(settings: AppSettings, quran: QuranState, tools: ToolsState, now: Instant): String =
        json.encodeToString(Contents.serializer(), Contents(exportedAt = now.toString(), settings = settings, quran = quran, tools = tools))

    sealed interface Read {
        data class Ok(val contents: Contents) : Read
        data class Bad(val reason: Reason) : Read
    }

    enum class Reason { TOO_LARGE, NOT_JSON, NOT_SAKINA, NEWER_VERSION }

    fun decode(text: String): Read {
        if (text.length > MAX_BYTES) return Read.Bad(Reason.TOO_LARGE)
        val c = try {
            SettingsJson.decodeFromString(Contents.serializer(), text)
        } catch (e: Exception) {
            return Read.Bad(Reason.NOT_JSON)
        }
        if (c.format != FORMAT) return Read.Bad(Reason.NOT_SAKINA)
        if (c.version > VERSION) return Read.Bad(Reason.NEWER_VERSION)
        return Read.Ok(c)
    }
}
