package com.sakina.app.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import com.sakina.app.core.format.Lang
import com.sakina.app.core.widgets.ContentMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import java.io.InputStream
import java.io.OutputStream

/** A widget either follows the app's place (including travel) or keeps its own, which app changes never touch. */
enum class WidgetPlaceMode { APP, FIXED }

enum class WidgetLang { APP, AR, EN }

enum class WidgetTheme { APP, SYSTEM, LIGHT, DARK }

enum class WidgetClock { APP, H12, H24 }

/** A plain colour, or the painted sky of the time of day at the widget's place. */
enum class WidgetBackground { COLOR, SKY }

/**
 * One widget's own settings. Colours are ARGB; null means "from the visual style". Stored per widget id on the
 * device only, and never part of a backup (the spec keeps widget copies out of backups).
 */
@Serializable
data class WidgetConfig(
    val kind: String = "",
    val placeMode: WidgetPlaceMode = WidgetPlaceMode.APP,
    /** A copy of the chosen place, so it stays even if the place is removed from the app's list. */
    val place: SavedLocation? = null,
    val lang: WidgetLang = WidgetLang.APP,
    /** null: the app's visual style. */
    val style: VisualStyle? = null,
    val theme: WidgetTheme = WidgetTheme.APP,
    /** Android 12+: colours from the wallpaper. null: as the app. */
    val dynamicColor: Boolean? = null,
    val clock: WidgetClock = WidgetClock.APP,
    /** Text size in percent of the type's default; the widget still shrinks text that would not fit. */
    val textScale: Int = 100,
    val background: WidgetBackground = WidgetBackground.COLOR,
    val textColor: Long? = null,
    val backgroundColor: Long? = null,
    val accentColor: Long? = null,
    /** Background opacity, 0–100. */
    val opacity: Int = DEFAULT_OPACITY,
    /** Corner radius in dp: one of [CORNERS]. */
    val corner: Int = 16,
    /** Border width step: 0 none, 1 thin, 2 thick. */
    val border: Int = 0,
    /** Glass panel: keeps the panel opaque enough for the text to stay readable on any wallpaper. */
    val contrastGuard: Boolean = true,
    val contentMode: ContentMode = ContentMode.MIXED,
    /** Steps taken with the content widget's "next" button; added to the day's position in the list. */
    val contentShift: Int = 0,
    val updatedAt: Long = 0,
) {
    companion object {
        const val DEFAULT_OPACITY = 92
        const val GLASS_OPACITY = 45
        val CORNERS = listOf(0, 8, 16, 24)
        val SCALES = 80..140

        fun defaultsFor(kind: String) = WidgetConfig(kind = kind, opacity = if (kind == "glass") GLASS_OPACITY else DEFAULT_OPACITY)
    }
}

@Serializable
data class WidgetsState(val configs: Map<String, WidgetConfig> = emptyMap())

private object WidgetsSerializer : Serializer<WidgetsState> {
    override val defaultValue = WidgetsState()
    override suspend fun readFrom(input: InputStream): WidgetsState = try {
        SettingsJson.decodeFromString(WidgetsState.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Unreadable widgets", e)
    } catch (e: IllegalArgumentException) {
        throw CorruptionException("Unreadable widgets", e)
    }

    override suspend fun writeTo(t: WidgetsState, output: OutputStream) {
        output.write(SettingsJson.encodeToString(WidgetsState.serializer(), t).encodeToByteArray())
    }
}

private val Context.widgetStore: DataStore<WidgetsState> by dataStore(
    fileName = "widgets.json",
    serializer = WidgetsSerializer,
    corruptionHandler = ReplaceFileCorruptionHandler { WidgetsState() },
)

class WidgetRepository(context: Context) {
    private val store = context.applicationContext.widgetStore
    val state: Flow<WidgetsState> = store.data

    suspend fun all(): Map<String, WidgetConfig> = store.data.first().configs

    suspend fun get(id: Int, kind: String): WidgetConfig = all()[id.toString()] ?: WidgetConfig.defaultsFor(kind)

    suspend fun put(id: Int, config: WidgetConfig) {
        store.updateData { it.copy(configs = it.configs + (id.toString() to config.copy(updatedAt = System.currentTimeMillis()))) }
    }

    suspend fun update(id: Int, kind: String, transform: (WidgetConfig) -> WidgetConfig) {
        store.updateData { st ->
            val c = st.configs[id.toString()] ?: WidgetConfig.defaultsFor(kind)
            st.copy(configs = st.configs + (id.toString() to transform(c)))
        }
    }

    /** Drops the settings of widgets that no longer exist. */
    suspend fun retain(ids: Set<Int>) {
        store.updateData { st -> st.copy(configs = st.configs.filterKeys { k -> k.toIntOrNull() in ids }) }
    }

    suspend fun remove(ids: IntArray) {
        val keys = ids.map { it.toString() }.toSet()
        store.updateData { st -> st.copy(configs = st.configs - keys) }
    }
}

/** The language and digits a widget uses. */
fun WidgetConfig.language(s: AppSettings): Pair<Lang, Boolean> = when (lang) {
    WidgetLang.APP -> s.language to s.arabicDigits
    WidgetLang.AR -> Lang.AR to (if (s.language == Lang.AR) s.arabicDigits else true)
    WidgetLang.EN -> Lang.EN to false
}

fun WidgetConfig.clock24(s: AppSettings): Boolean = when (clock) {
    WidgetClock.APP -> s.clock24
    WidgetClock.H12 -> false
    WidgetClock.H24 -> true
}

/** The place the widget shows: its own, or the app's current one (the travel destination while travelling). */
fun WidgetConfig.resolvePlace(s: AppSettings): SavedLocation? =
    (if (placeMode == WidgetPlaceMode.FIXED) place else s.activeLocation)?.takeIf { it.isUsable }
