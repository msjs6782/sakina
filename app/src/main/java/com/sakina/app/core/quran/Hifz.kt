package com.sakina.app.core.quran

import kotlinx.serialization.Serializable

/**
 * A passage being memorised: ayahs [from]..[to] of [sura]. Each successful review moves it one step further
 * apart ([level]); a review that needs more work brings it back a step. Stored on the device only.
 */
@Serializable
data class HifzItem(
    val sura: Int,
    val from: Int,
    val to: Int,
    val addedEpochDay: Long,
    val dueEpochDay: Long,
    val level: Int = 0,
    val reviews: Int = 0,
) {
    val id: String get() = "$sura:$from-$to"
}

/** How much of the text a memorisation session shows: all of it, the first word of each ayah, or nothing. */
enum class HifzMask { NONE, FIRST_WORD, ALL }

object Hifz {
    /** Days until the next review after each successful one (spaced repetition, kept simple). */
    val INTERVALS = intArrayOf(1, 2, 4, 7, 15, 30, 60)
    val MAX_LEVEL: Int get() = INTERVALS.size - 1

    /** Adds a passage (or restarts it if the same passage is already there), due tomorrow. */
    fun add(items: List<HifzItem>, sura: Int, from: Int, to: Int, today: Long): List<HifzItem> {
        require(from in 1..to)
        val item = HifzItem(sura, from, to, addedEpochDay = today, dueEpochDay = today + INTERVALS[0])
        return items.filterNot { it.id == item.id } + item
    }

    fun remove(items: List<HifzItem>, id: String) = items.filterNot { it.id == id }

    /**
     * Records a review: remembered moves the passage a step further apart; not remembered moves it a step back
     * and brings it again tomorrow.
     */
    fun review(item: HifzItem, remembered: Boolean, today: Long): HifzItem {
        val level = if (remembered) (item.level + 1).coerceAtMost(MAX_LEVEL) else (item.level - 1).coerceAtLeast(0)
        val days = if (remembered) INTERVALS[level] else 1
        return item.copy(level = level, dueEpochDay = today + days, reviews = item.reviews + 1)
    }

    /** Passages due today or overdue, the most overdue first. */
    fun due(items: List<HifzItem>, today: Long): List<HifzItem> = items.filter { it.dueEpochDay <= today }.sortedBy { it.dueEpochDay }

    /** Ayahs covered by all passages (for "ayahs memorised"), counting overlaps once. */
    fun ayahCount(items: List<HifzItem>): Int = items.flatMap { i -> (i.from..i.to).map { i.sura to it } }.toSet().size

    /** Pause marks and other signs written between words in the Uthmani text; never counted as words. */
    private fun isMarkOnly(token: String) = token.all { it in 'ۖ'..'ۭ' || it == '۞' || it == '۩' }

    /**
     * The words of an ayah as character ranges, for masking. Tokens that are only pause marks are left out
     * (they stay visible between hidden words).
     */
    fun words(text: String): List<IntRange> {
        val out = mutableListOf<IntRange>()
        var i = 0
        while (i < text.length) {
            while (i < text.length && text[i] == ' ') i++
            val start = i
            while (i < text.length && text[i] != ' ') i++
            if (i > start && !isMarkOnly(text.substring(start, i))) out += start until i
        }
        return out
    }

    /** The word ranges hidden by [mask] in an ayah (the first word stays for FIRST_WORD). */
    fun hidden(text: String, mask: HifzMask): List<IntRange> = when (mask) {
        HifzMask.NONE -> emptyList()
        HifzMask.FIRST_WORD -> words(text).drop(1)
        HifzMask.ALL -> words(text)
    }
}
