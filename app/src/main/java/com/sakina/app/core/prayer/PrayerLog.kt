package com.sakina.app.core.prayer

import java.time.LocalDate

/**
 * The prayers the user marked as prayed, per civil day: a bit per prayer (Fajr to Isha). Optional, kept on the
 * device only, about a year back.
 */
object PrayerLog {
    const val KEEP_DAYS = 400L
    const val FULL = 0b11111

    private fun bit(k: PrayerKey): Int = 1 shl PrayerKey.prayers.indexOf(k).also { require(it >= 0) { "not a prayer: $k" } }

    fun mask(log: Map<String, Int>, date: LocalDate): Int = log[date.toString()] ?: 0

    fun isDone(log: Map<String, Int>, date: LocalDate, k: PrayerKey): Boolean = k in PrayerKey.prayers && mask(log, date) and bit(k) != 0

    fun count(log: Map<String, Int>, date: LocalDate): Int = Integer.bitCount(mask(log, date) and FULL)

    /** Marks or unmarks one prayer, dropping days older than [KEEP_DAYS] before [today]. */
    fun set(log: Map<String, Int>, date: LocalDate, k: PrayerKey, done: Boolean, today: LocalDate): Map<String, Int> {
        val m = if (done) mask(log, date) or bit(k) else mask(log, date) and bit(k).inv()
        val oldest = today.minusDays(KEEP_DAYS)
        val kept = log.filterKeys { runCatching { !LocalDate.parse(it).isBefore(oldest) }.getOrDefault(false) }
        return if (m == 0) kept - date.toString() else kept + (date.toString() to m)
    }

    fun toggle(log: Map<String, Int>, date: LocalDate, k: PrayerKey, today: LocalDate) = set(log, date, k, !isDone(log, date, k), today)

    /** Days in a row with all five prayers, ending today (or yesterday while today is still open). */
    fun streak(log: Map<String, Int>, today: LocalDate): Int {
        var d = if (count(log, today) == 5) today else today.minusDays(1)
        var n = 0
        while (count(log, d) == 5) { n++; d = d.minusDays(1) }
        return n
    }

    /** Prayers marked out of all prayers from [from] to [to] (inclusive), in percent. */
    fun percent(log: Map<String, Int>, from: LocalDate, to: LocalDate): Int {
        var done = 0
        var all = 0
        var d = from
        while (!d.isAfter(to)) { done += count(log, d); all += 5; d = d.plusDays(1) }
        return if (all == 0) 0 else done * 100 / all
    }
}
