package com.sakina.app.core.quran

/** A verse reference (1-based surah and ayah numbers). */
data class AyahRef(val sura: Int, val aya: Int) : Comparable<AyahRef> {
    override fun compareTo(other: AyahRef) = compareValuesBy(this, other, { it.sura }, { it.aya })
    override fun toString() = "$sura:$aya"
}

enum class Revelation { MECCAN, MEDINAN }

data class Surah(
    val index: Int,
    val ayahCount: Int,
    /** Global index (0-based) of the first ayah. */
    val start: Int,
    val nameAr: String,
    val nameTranslit: String,
    val nameEn: String,
    val revelation: Revelation,
    val order: Int,
)

enum class SajdaType { RECOMMENDED, OBLIGATORY }

data class Sajda(val index: Int, val ref: AyahRef, val type: SajdaType)

/** Structure of the Quran from Tanzil's metadata (Madani mushaf pages, juz and hizb quarters). */
class QuranMeta(
    val suras: List<Surah>,
    /** Start of each juz (30). */
    val juzStarts: List<AyahRef>,
    /** Start of each hizb quarter (240): hizb = (q - 1) / 4 + 1. */
    val quarterStarts: List<AyahRef>,
    /** Start of each page (604) of the Madani mushaf layout. */
    val pageStarts: List<AyahRef>,
    val sajdas: List<Sajda>,
) {
    val totalAyahs: Int get() = suras.last().start + suras.last().ayahCount
    val pageCount: Int get() = pageStarts.size

    private val juzGlobal = juzStarts.map { global(it) }
    private val quarterGlobal = quarterStarts.map { global(it) }
    private val pageGlobal = pageStarts.map { global(it) }
    private val sajdaByGlobal = sajdas.associateBy { global(it.ref) }

    fun surah(index: Int): Surah = suras[index - 1]

    fun isValid(ref: AyahRef) = ref.sura in 1..suras.size && ref.aya in 1..surah(ref.sura).ayahCount

    fun global(ref: AyahRef): Int {
        require(isValid(ref)) { "Invalid reference $ref" }
        return suras[ref.sura - 1].start + ref.aya - 1
    }

    fun ref(global: Int): AyahRef {
        require(global in 0 until totalAyahs) { "Invalid index $global" }
        var lo = 0
        var hi = suras.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (suras[mid].start <= global) lo = mid else hi = mid - 1
        }
        return AyahRef(lo + 1, global - suras[lo].start + 1)
    }

    private fun bucket(starts: List<Int>, global: Int): Int {
        var lo = 0
        var hi = starts.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (starts[mid] <= global) lo = mid else hi = mid - 1
        }
        return lo + 1
    }

    fun pageOf(global: Int): Int = bucket(pageGlobal, global)
    fun juzOf(global: Int): Int = bucket(juzGlobal, global)
    fun quarterOf(global: Int): Int = bucket(quarterGlobal, global)
    fun hizbOf(global: Int): Int = (quarterOf(global) - 1) / 4 + 1

    /** Global index range of a page. */
    fun pageRange(page: Int): IntRange {
        val start = pageGlobal[page - 1]
        val end = if (page < pageGlobal.size) pageGlobal[page] - 1 else totalAyahs - 1
        return start..end
    }

    fun juzStartGlobal(juz: Int) = juzGlobal[juz - 1]
    fun quarterStartGlobal(q: Int) = quarterGlobal[q - 1]

    fun sajdaAt(global: Int): Sajda? = sajdaByGlobal[global]

    /** True when an ayah starts a new hizb quarter (shown as ۞ in the Uthmani text). */
    fun startsQuarter(global: Int): Boolean = quarterGlobal.binarySearch(global) >= 0
}

/** The Uthmani text (verbatim from Tanzil) and the per-surah basmala as Tanzil gives it. */
class QuranText(
    val ayahs: List<String>,
    /** Basmala shown above each surah, keyed by surah; absent for surah 1 (it is ayah 1) and surah 9. */
    val basmala: Map<Int, String>,
)

/** A translation or tafsir with one entry per ayah, in global order. */
class AyahTexts(val id: String, val header: List<String>, val lines: List<String>)
