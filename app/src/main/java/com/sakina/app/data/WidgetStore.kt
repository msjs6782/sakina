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

/** The widget's typeface: the app's (IBM Plex Sans Arabic) or Noto Kufi Arabic. */
enum class WidgetFont { APP, KUFI }

/** How the next prayer stands out in a table: a filled pill behind it, or its text colour only. */
enum class WidgetHighlight { FILL, TEXT }

/**
 * A plain colour, the painted sky of the time of day at the widget's place, a 3D scene that follows the time the same
 * way, or a painted texture: a geometric pattern, old paper, a gold lattice on night blue, white or navy marble with
 * gold veins, a gold lattice on emerald, or carved ivory (docs/ART.md). Behind the glass panel, COLOR is the wallpaper.
 */
enum class WidgetBackground { COLOR, SKY, GEOMETRIC, PAPER, NIGHT, SCENE, MARBLE, MARBLE_NAVY, EMERALD, IVORY }

/** How the day-name and dhikr widgets draw their large word: gold in relief, raised in the text colour, or flat. */
enum class WidgetWordLook { GOLD, RAISED, FLAT }

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
    /**
     * Each part's size in percent, on top of [textScale]: the large text (the time, the day, the countdown), the
     * prayer table, and the small lines (the place, the date, the time left). Parts keep these proportions when the
     * widget has to shrink its text to fit.
     */
    val heroScale: Int = 100,
    val tableScale: Int = 100,
    val smallScale: Int = 100,
    /** Heavier weights for all of the widget's text. */
    val bold: Boolean = false,
    /** The smallest text the widget accepts before it leaves out less important lines: 0 as fits, 1 clear, 2 large. */
    val clarity: Int = 0,
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
    val font: WidgetFont = WidgetFont.APP,
    val highlight: WidgetHighlight = WidgetHighlight.FILL,
    /** What the widget shows, where its type has it: the place (with the refresh button), the refresh button, the date. */
    val showPlace: Boolean = true,
    val showRefresh: Boolean = true,
    val showDate: Boolean = true,
    /** The time left until the next prayer, and the time it was last updated. */
    val showRemaining: Boolean = true,
    val showStamp: Boolean = true,
    /** Sunrise in the prayer tables; null: the type's default (the day-name widget leaves it out). */
    val showSunrise: Boolean? = null,
    /** The raised look: light from above, a soft shade below and a gold hairline edge. */
    val depth: Boolean = true,
    /** A gold line along the edge that grows from the last prayer to the next. */
    val progress: Boolean = true,
    /** The day-name and dhikr widgets' large word. */
    val wordLook: WidgetWordLook = WidgetWordLook.GOLD,
    /** Glass panel: a bevelled rim, a sheen across the glass and frosted chips under the prayers. */
    val glass3d: Boolean = true,
    val contentMode: ContentMode = ContentMode.MIXED,
    /** Steps taken with the content widget's "next" button; added to the day's position in the list. */
    val contentShift: Int = 0,
    val updatedAt: Long = 0,
) {
    companion object {
        const val DEFAULT_OPACITY = 92
        const val GLASS_OPACITY = 45
        val CORNERS = listOf(0, 8, 16, 24)
        val SCALES = 70..170
        val PART_SCALES = 60..200
        /** The smallest size per unit of the type's size each [clarity] step accepts (in dp per sp). */
        val CLARITY = floatArrayOf(0.72f, 0.92f, 1.12f)

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

/**
 * Applies settings written as "bg=SCENE,bold=1,hero=140,table=130,small=120,clarity=1,word=RAISED,glass3d=0,
 * font=KUFI,scale=120,theme=DARK" (for demos and checks); unknown keys and values are ignored.
 */
fun WidgetConfig.withOptions(spec: String): WidgetConfig = spec.split(',').map { it.trim() }.filter { '=' in it }.fold(this) { c, kv ->
    val (k, v) = kv.split('=', limit = 2).let { it[0].trim() to it[1].trim() }
    fun on() = v == "1" || v.equals("true", true)
    fun num(range: IntRange) = v.toIntOrNull()?.coerceIn(range)
    runCatching {
        when (k) {
            "bg" -> c.copy(background = WidgetBackground.valueOf(v.uppercase()))
            "bold" -> c.copy(bold = on())
            "hero" -> num(WidgetConfig.PART_SCALES)?.let { c.copy(heroScale = it) } ?: c
            "table" -> num(WidgetConfig.PART_SCALES)?.let { c.copy(tableScale = it) } ?: c
            "small" -> num(WidgetConfig.PART_SCALES)?.let { c.copy(smallScale = it) } ?: c
            "scale" -> num(WidgetConfig.SCALES)?.let { c.copy(textScale = it) } ?: c
            "clarity" -> num(0 until WidgetConfig.CLARITY.size)?.let { c.copy(clarity = it) } ?: c
            "word" -> c.copy(wordLook = WidgetWordLook.valueOf(v.uppercase()))
            "glass3d" -> c.copy(glass3d = on())
            "depth" -> c.copy(depth = on())
            "font" -> c.copy(font = WidgetFont.valueOf(v.uppercase()))
            "theme" -> c.copy(theme = WidgetTheme.valueOf(v.uppercase()))
            "opacity" -> num(0..100)?.let { c.copy(opacity = it) } ?: c
            else -> c
        }
    }.getOrDefault(c)
}
