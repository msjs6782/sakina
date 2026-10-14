package com.sakina.app.core.adhkar

import com.sakina.app.core.location.SearchText
import kotlinx.serialization.Serializable

/** The bundled adhkar library (assets/adhkar/adhkar.json, built by tools/adhkar/build.py). */
@Serializable
data class AdhkarLibrary(
    val version: Int = 1,
    val categories: List<AdhkarCategory> = emptyList(),
    val practices: List<Practice> = emptyList(),
    val items: List<Dhikr> = emptyList(),
) {
    private val byId: Map<String, Dhikr> by lazy { items.associateBy { it.id } }

    fun item(id: String): Dhikr? = byId[id]
    fun practice(id: String): Practice? = practices.firstOrNull { it.id == id }
    fun category(id: String): AdhkarCategory? = categories.firstOrNull { it.id == id }
    fun inCategory(id: String): List<Dhikr> = items.filter { it.cat == id }
    fun itemsOf(p: Practice): List<Dhikr> = p.items.mapNotNull { byId[it] }

    private val index: List<Pair<Dhikr, String>> by lazy {
        items.map { d ->
            val cat = category(d.cat)
            val words = buildList {
                add(d.titleAr); add(d.titleEn)
                cat?.let { add(it.titleAr); add(it.titleEn) }
                d.parts.forEach { p -> p.text?.let(::add); p.morning?.let(::add); p.evening?.let(::add); p.labelAr?.let(::add); p.labelEn?.let(::add) }
            }
            d to SearchText.normalize(words.joinToString(" | "))
        }
    }

    /**
     * Items whose Arabic text, Arabic or English title, or category name contains every word of [query]
     * (diacritics, letter forms and case ignored). Title matches come first.
     */
    fun search(query: String): List<Dhikr> {
        val words = SearchText.normalize(query).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        val hits = index.filter { (_, text) -> words.all { text.contains(it) } }.map { it.first }
        val inTitle = hits.filter { d -> words.all { w -> SearchText.normalize(d.titleAr + " " + d.titleEn).contains(w) } }
        return inTitle + (hits - inTitle.toSet())
    }
}

@Serializable
data class AdhkarCategory(val id: String, val titleAr: String, val titleEn: String)

/** A time-bound set of adhkar ("ورد") with its own counters; [reminder] names the matching civil reminder. */
@Serializable
data class Practice(
    val id: String,
    val titleAr: String,
    val titleEn: String,
    val reminder: String? = null,
    val ctxAr: String = "",
    val ctxEn: String = "",
    val items: List<String> = emptyList(),
) {
    /** After-prayer adhkar have one session per prayer; the others one per day. */
    val sessions: List<String> get() = if (id == AFTER_PRAYER) PRAYER_SESSIONS else listOf(MAIN_SESSION)

    companion object {
        const val MORNING = "morning"
        const val EVENING = "evening"
        const val SLEEP = "sleep"
        const val WAKING = "waking"
        const val AFTER_PRAYER = "after_prayer"
        const val MAIN_SESSION = "main"
        val PRAYER_SESSIONS = listOf("fajr", "dhuhr", "asr", "maghrib", "isha")
    }
}

@Serializable
data class QuranSpan(val sura: Int, val from: Int, val to: Int)

/** One part of a dhikr: its text (or a Quran passage) and how many times it is said. */
@Serializable
data class DhikrPart(
    val text: String? = null,
    /** The morning and evening wordings, when they differ. */
    val morning: String? = null,
    val evening: String? = null,
    val quran: QuranSpan? = null,
    /** Narrated repetitions; 0 when the narration gives no number. */
    val count: Int = 1,
    val labelAr: String? = null,
    val labelEn: String? = null,
) {
    /** The wording for a practice: the morning or evening form when there is one. */
    fun textFor(practice: String?): String? = when (practice) {
        Practice.MORNING -> morning ?: text
        Practice.EVENING -> evening ?: text
        else -> text
    }

    /** Taps this part takes in a counter: a part without a narrated number counts once. */
    val taps: Int get() = if (count <= 0) 1 else count
}

@Serializable
data class Grade(val ar: String, val en: String, val byAr: String, val byEn: String)

@Serializable
data class Grader(val by: String, val grade: String)

@Serializable
data class DhikrSource(
    val book: String,
    val number: String,
    val ar: String = "",
    val en: String = "",
    val grade: Grade,
    val graders: List<Grader> = emptyList(),
)

@Serializable
data class SourceLink(val titleAr: String, val titleEn: String, val url: String)

@Serializable
data class Dhikr(
    val id: String,
    val cat: String,
    val titleAr: String,
    val titleEn: String,
    /** How many times the whole set of parts is said (the protective surahs: three rounds). */
    val rounds: Int = 1,
    val parts: List<DhikrPart>,
    val ctxAr: String = "",
    val ctxEn: String = "",
    val source: DhikrSource,
    val links: List<SourceLink> = emptyList(),
    val noteAr: String? = null,
    val noteEn: String? = null,
    val countNoteAr: String? = null,
) {
    /** Total taps to complete this dhikr once in a practice. */
    val totalTaps: Int get() = rounds.coerceAtLeast(1) * parts.sumOf { it.taps }

    /** True when no part has a narrated number (a free count). */
    val unnumbered: Boolean get() = parts.all { it.count <= 0 }
}

/** Where a counter is in a dhikr after [taps] taps. */
data class DhikrPosition(
    val round: Int,
    val part: Int,
    /** Taps done in the current part. */
    val inPart: Int,
    val done: Boolean,
)

object DhikrCounter {
    fun position(d: Dhikr, taps: Int): DhikrPosition {
        val total = d.totalTaps
        if (taps >= total) return DhikrPosition(d.rounds.coerceAtLeast(1), d.parts.lastIndex, d.parts.last().taps, true)
        val perRound = d.parts.sumOf { it.taps }
        val t = taps.coerceAtLeast(0)
        val round = t / perRound + 1
        var rest = t % perRound
        for ((i, p) in d.parts.withIndex()) {
            if (rest < p.taps) return DhikrPosition(round, i, rest, false)
            rest -= p.taps
        }
        return DhikrPosition(round, d.parts.lastIndex, d.parts.last().taps, false)
    }
}

/**
 * Wird progress: taps per dhikr, kept separately for each day, practice and session, so the same dhikr in
 * the morning and before sleep (or after Fajr and after Dhuhr) never shares a counter.
 */
object Wird {
    fun key(date: String, practice: String, session: String) = "$date|$practice|$session"

    /** Key for a dhikr counted on its own page, outside any practice. */
    fun itemKey(date: String) = "$date|item|-"

    fun dateOf(key: String) = key.substringBefore('|')

    fun tap(progress: Map<String, Map<String, Int>>, key: String, d: Dhikr, free: Boolean = false): Map<String, Map<String, Int>> {
        val cur = progress[key].orEmpty()
        val now = cur[d.id] ?: 0
        val next = if (free) now + 1 else minOf(d.totalTaps, now + 1)
        if (next == now) return progress
        return progress + (key to (cur + (d.id to next)))
    }

    fun untap(progress: Map<String, Map<String, Int>>, key: String, d: Dhikr): Map<String, Map<String, Int>> {
        val cur = progress[key].orEmpty()
        val now = cur[d.id] ?: 0
        if (now <= 0) return progress
        return progress + (key to (cur + (d.id to now - 1)))
    }

    fun resetItem(progress: Map<String, Map<String, Int>>, key: String, id: String): Map<String, Map<String, Int>> {
        val cur = progress[key] ?: return progress
        if (id !in cur) return progress
        return progress + (key to (cur - id))
    }

    fun resetSession(progress: Map<String, Map<String, Int>>, key: String) = progress - key

    /** Completed and total dhikr in a session. */
    fun summary(progress: Map<String, Map<String, Int>>, key: String, items: List<Dhikr>): Pair<Int, Int> {
        val cur = progress[key].orEmpty()
        return items.count { (cur[it.id] ?: 0) >= it.totalTaps } to items.size
    }

    /** Drops days before [keepFrom] (ISO date strings compare in date order). */
    fun prune(progress: Map<String, Map<String, Int>>, keepFrom: String) = progress.filterKeys { dateOf(it) >= keepFrom }
}

/** The personal tasbih: a free count with an optional target. */
@Serializable
data class TasbihState(
    val count: Int = 0,
    /** 0 = no target. */
    val target: Int = 0,
    val label: String = "",
    val vibrate: Boolean = true,
) {
    /** Completed rounds of the target, and the count within the current round. */
    val rounds: Int get() = if (target > 0) count / target else 0
    val inRound: Int get() = if (target > 0) count % target else count

    companion object {
        const val MAX_COUNT = 1_000_000
        const val MAX_TARGET = 10_000
        val PRESET_TARGETS = listOf(33, 99, 100, 1000)
    }
}

/**
 * Tasbih changes with a single undo slot: only a change that actually alters the count, target or label
 * replaces the slot, so an accidental reset can always be taken back even after a no-op tap on reset.
 */
data class Tasbih(val state: TasbihState = TasbihState(), val undo: TasbihState? = null) {
    private fun apply(next: TasbihState): Tasbih =
        if (next.count == state.count && next.target == state.target && next.label == state.label) copy(state = next)
        else Tasbih(next, state)

    fun tap(): Tasbih = apply(state.copy(count = minOf(TasbihState.MAX_COUNT, state.count + 1)))
    fun reset(): Tasbih = apply(state.copy(count = 0))
    fun setTarget(target: Int): Tasbih = apply(state.copy(target = target.coerceIn(0, TasbihState.MAX_TARGET)))
    fun setLabel(label: String): Tasbih = apply(state.copy(label = label.take(80)))
    fun setVibrate(on: Boolean): Tasbih = copy(state = state.copy(vibrate = on))

    fun undo(): Tasbih = undo?.let { Tasbih(it.copy(vibrate = state.vibrate), null) } ?: this

    /** True when this tap just completed a round of the target. */
    fun reachedTarget(): Boolean = state.target > 0 && state.count > 0 && state.count % state.target == 0
}
