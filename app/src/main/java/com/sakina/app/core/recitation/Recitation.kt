package com.sakina.app.core.recitation

/**
 * The three reciters, each with one MP3Quran recording set (Hafs an Asim, murattal) and the
 * MP3Quran timing "read" made for that same set (its folder_url is the same server).
 */
enum class Reciter(
    val key: String,
    val nameAr: String,
    val nameEn: String,
    /** MP3Quran moshaf id. */
    val moshafId: Int,
    val server: String,
    /** MP3Quran ayat_timing read id for this recording set. */
    val timingRead: Int,
) {
    ALAFASY("alafasy", "مشاري العفاسي", "Mishary Alafasy", 123, "https://cdn.mp3quran.net/audio/mishary-alafasy/r1/", 123),
    MINSHAWI("minshawi", "محمد صديق المنشاوي", "Mohamed Siddiq al-Minshawi", 112, "https://cdn.mp3quran.net/audio/muhammad-minshawi/r1/", 112),
    HUSARY("husary", "محمود خليل الحصري", "Mahmoud Khalil al-Husary", 118, "https://cdn.mp3quran.net/audio/mahmoud-husary/r1/", 118);

    fun url(sura: Int): String = server + "%03d.mp3".format(java.util.Locale.ROOT, sura)
    fun timingUrl(sura: Int): String = "$API/ayat_timing?surah=$sura&read=$timingRead"

    companion object {
        const val API = "https://www.mp3quran.net/api/v3"
        const val READS_URL = "$API/ayat_timing/reads"
        fun byKey(key: String?): Reciter? = entries.firstOrNull { it.key == key }
    }
}

/** One recording: a surah by a reciter. Its id is used for files, media items and downloads. */
data class Recording(val reciter: Reciter, val sura: Int) {
    init {
        require(sura in 1..114)
    }

    val id: String get() = "${reciter.key}:$sura"

    companion object {
        fun parse(id: String?): Recording? {
            val f = id?.split(':') ?: return null
            val r = Reciter.byKey(f.getOrNull(0)) ?: return null
            val s = f.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..114 } ?: return null
            return Recording(r, s)
        }
    }
}

/** Sizes and durations of the recordings, as measured when the app was built (shown before a download). */
class RecitationSources(private val bytes: Map<String, Long>, private val durations: Map<String, Long>) {
    fun bytes(r: Recording): Long = bytes[r.id] ?: 0
    fun durationMs(r: Recording): Long = durations[r.id] ?: 0

    companion object {
        fun parse(text: String): RecitationSources {
            val b = HashMap<String, Long>()
            val d = HashMap<String, Long>()
            for (line in text.lineSequence()) {
                if (line.isBlank() || line.startsWith("#")) continue
                val f = line.trim().split('\t')
                val id = "${f[0]}:${f[1]}"
                b[id] = f[2].toLong()
                d[id] = f[3].toLong()
            }
            return RecitationSources(b, d)
        }
    }
}

/** Download limits: one recording at most 256 MiB, the whole library at most 1 GiB. */
object LibraryLimits {
    const val PER_RECORDING: Long = 256L * 1024 * 1024
    const val LIBRARY: Long = 1024L * 1024 * 1024

    enum class Check { OK, TOO_BIG, LIBRARY_FULL, UNKNOWN_SIZE }

    /** [size] is the recording's size (-1 when the server does not say); [used] is the library's current size. */
    fun check(size: Long, used: Long): Check = when {
        size < 0 -> Check.UNKNOWN_SIZE
        size > PER_RECORDING -> Check.TOO_BIG
        used + size > LIBRARY -> Check.LIBRARY_FULL
        else -> Check.OK
    }
}

/** Playback speeds offered. */
val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f)

/** How many times a repeated range plays; [FOREVER] keeps going until stopped. */
enum class RepeatCount(val times: Int) {
    ONCE(1), THREE(3), FIVE(5), FOREVER(-1);

    companion object {
        fun of(times: Int) = entries.firstOrNull { it.times == times } ?: ONCE
    }
}

/**
 * What happens each time playback reaches the end of the repeated range. Pure logic, used by the
 * player service: play the range [count] times in all (or forever), then pause at its end.
 */
class RepeatRun(val from: Int, val to: Int, val count: RepeatCount, val startMs: Long, val endMs: Long) {
    var played = 0
        private set

    enum class Action { AGAIN, FINISH }

    fun reachedEnd(): Action {
        played++
        return if (count == RepeatCount.FOREVER || played < count.times) Action.AGAIN else Action.FINISH
    }
}
