package com.sakina.app.core.recitation

/** Where one ayah lies in a recording, in milliseconds. */
data class AyahTiming(val ayah: Int, val startMs: Long, val endMs: Long)

/** A checked timing map: ayahs 1..N in order, each with its span in the recording. */
class TimingMap(val sura: Int, val preambleEndMs: Long?, val ayahs: List<AyahTiming>) {
    val lastEndMs: Long get() = ayahs.last().endMs

    /** The span from the start of ayah [from] to the end of ayah [to]. */
    fun span(from: Int, to: Int): LongRange {
        require(from in 1..ayahs.size && to in from..ayahs.size)
        return ayahs[from - 1].startMs..ayahs[to - 1].endMs
    }

    /** The ayah playing at [positionMs], or null before the first ayah. */
    fun ayahAt(positionMs: Long): Int? = ayahs.lastOrNull { it.startMs <= positionMs }?.ayah
}

enum class TimingProblem {
    /** Not the expected JSON list of entries. */
    NOT_A_LIST,
    /** An entry without numeric ayah, start and end. */
    BAD_ENTRY,
    /** More than one introduction entry (ayah 0), or one that is not first. */
    PREAMBLE,
    /** Ayah numbers are not exactly 1..N for this surah. */
    NUMBERING,
    /** Start times do not increase. */
    ORDER,
    END_BEFORE_START,
    /** One ayah's span runs into the next one. */
    OVERLAP,
    /** The map goes past the end of the recording. */
    PAST_AUDIO_END,
    /** The map ends long before the recording does. */
    SHORT_OF_AUDIO,
    /** The timing source no longer points at the same recording set. */
    WRONG_SOURCE,
}

sealed interface TimingCheck {
    data class Valid(val map: TimingMap) : TimingCheck
    data class Invalid(val problem: TimingProblem, val detail: String) : TimingCheck
}

/**
 * Reads MP3Quran ayat_timing JSON and checks that it is structurally sound. Ayah repeat is only
 * offered when this succeeds (and the map also fits the recording's duration): boundaries are
 * never guessed.
 */
object TimingParser {
    /** Spans may touch; a few milliseconds of overlap are rounding. */
    private const val OVERLAP_TOLERANCE_MS = 50L
    /** The last ayah may end slightly after the measured duration of the file. */
    const val END_TOLERANCE_MS = 1_500L
    /** Recordings may end with silence or a closing; more than this means the map does not fit. */
    const val TAIL_LIMIT_MS = 60_000L

    private val objectRe = Regex("""\{[^{}]*\}""")
    private fun field(obj: String, name: String): Long? =
        Regex(""""$name"\s*:\s*"?(-?\d+)"?""").find(obj)?.groupValues?.get(1)?.toLongOrNull()

    fun parse(json: String, sura: Int, ayahCount: Int): TimingCheck {
        val text = json.trim()
        if (!text.startsWith("[") || !text.endsWith("]")) return TimingCheck.Invalid(TimingProblem.NOT_A_LIST, text.take(60))
        val entries = objectRe.findAll(text).map { it.value }.toList()
        if (entries.isEmpty()) return TimingCheck.Invalid(TimingProblem.NOT_A_LIST, "empty")
        val parsed = mutableListOf<AyahTiming>()
        for (e in entries) {
            val a = field(e, "ayah")
            val s = field(e, "start_time")
            val en = field(e, "end_time")
            if (a == null || s == null || en == null || s < 0) return TimingCheck.Invalid(TimingProblem.BAD_ENTRY, e.take(80))
            parsed += AyahTiming(a.toInt(), s, en)
        }
        val preamble = parsed.filter { it.ayah == 0 }
        if (preamble.size > 1 || (preamble.isNotEmpty() && parsed.first().ayah != 0)) {
            return TimingCheck.Invalid(TimingProblem.PREAMBLE, "${preamble.size} entries for ayah 0")
        }
        val ayahs = parsed.filter { it.ayah != 0 }
        if (ayahs.map { it.ayah } != (1..ayahCount).toList()) {
            return TimingCheck.Invalid(TimingProblem.NUMBERING, "${ayahs.size} ayahs (${ayahs.firstOrNull()?.ayah}..${ayahs.lastOrNull()?.ayah}), expected 1..$ayahCount")
        }
        for (i in ayahs.indices) {
            val t = ayahs[i]
            if (t.endMs < t.startMs) return TimingCheck.Invalid(TimingProblem.END_BEFORE_START, "ayah ${t.ayah}")
            if (i > 0) {
                val prev = ayahs[i - 1]
                if (t.startMs <= prev.startMs) return TimingCheck.Invalid(TimingProblem.ORDER, "ayah ${t.ayah}")
                if (t.startMs < prev.endMs - OVERLAP_TOLERANCE_MS) return TimingCheck.Invalid(TimingProblem.OVERLAP, "ayahs ${prev.ayah}-${t.ayah}")
            }
        }
        val pre = preamble.firstOrNull()
        if (pre != null && pre.endMs > ayahs.first().startMs + OVERLAP_TOLERANCE_MS) {
            return TimingCheck.Invalid(TimingProblem.PREAMBLE, "introduction runs into ayah 1")
        }
        return TimingCheck.Valid(TimingMap(sura, pre?.endMs, ayahs))
    }

    /** Checks a structurally valid map against the recording's real duration. */
    fun fits(map: TimingMap, durationMs: Long): TimingCheck = when {
        map.lastEndMs > durationMs + END_TOLERANCE_MS ->
            TimingCheck.Invalid(TimingProblem.PAST_AUDIO_END, "ends at ${map.lastEndMs} ms, recording is $durationMs ms")
        durationMs - map.lastEndMs > TAIL_LIMIT_MS ->
            TimingCheck.Invalid(TimingProblem.SHORT_OF_AUDIO, "ends at ${map.lastEndMs} ms, recording is $durationMs ms")
        else -> TimingCheck.Valid(map)
    }

    /**
     * Finds the folder_url of [read] in the ayat_timing/reads list, to confirm the timing still
     * belongs to the recording set the app plays.
     */
    fun folderOf(readsJson: String, read: Int): String? =
        objectRe.findAll(readsJson).map { it.value }
            .firstOrNull { field(it, "id") == read.toLong() }
            ?.let { Regex(""""folder_url"\s*:\s*"([^"]*)"""").find(it)?.groupValues?.get(1)?.replace("\\/", "/") }
}
