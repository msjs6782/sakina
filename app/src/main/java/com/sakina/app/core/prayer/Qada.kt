package com.sakina.app.core.prayer

import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * Missed prayers to make up: how many of each the user owes (as they count them) and how many they have made up.
 * Kept on the device only; nothing is ever added by itself.
 */
@Serializable
data class QadaState(
    /** Prayer key name → prayers owed in all. */
    val owed: Map<String, Int> = emptyMap(),
    /** Prayer key name → prayers made up so far. */
    val done: Map<String, Int> = emptyMap(),
)

object Qada {
    fun owed(s: QadaState, k: PrayerKey) = s.owed[k.name] ?: 0
    fun done(s: QadaState, k: PrayerKey) = s.done[k.name] ?: 0
    fun remaining(s: QadaState, k: PrayerKey) = maxOf(0, owed(s, k) - done(s, k))
    fun totalRemaining(s: QadaState) = PrayerKey.prayers.sumOf { remaining(s, it) }
    fun totalOwed(s: QadaState) = PrayerKey.prayers.sumOf { owed(s, it) }
    fun totalDone(s: QadaState) = PrayerKey.prayers.sumOf { minOf(done(s, it), owed(s, it)) }

    /** Made up so far, in percent of all owed. */
    fun percent(s: QadaState): Int = totalOwed(s).let { if (it == 0) 0 else totalDone(s) * 100 / it }

    private fun put(m: Map<String, Int>, k: PrayerKey, v: Int) = if (v <= 0) m - k.name else m + (k.name to v)

    fun addOwed(s: QadaState, k: PrayerKey, n: Int = 1): QadaState {
        require(k in PrayerKey.prayers)
        return s.copy(owed = put(s.owed, k, (owed(s, k) + n).coerceIn(0, MAX)))
    }

    /** One fewer owed (a count entered by mistake); never below what is already made up. */
    fun removeOwed(s: QadaState, k: PrayerKey): QadaState = s.copy(owed = put(s.owed, k, maxOf(done(s, k), owed(s, k) - 1)))

    /** A whole day missed: one of each of the five. */
    fun addDay(s: QadaState, days: Int = 1): QadaState = PrayerKey.prayers.fold(s) { acc, k -> addOwed(acc, k, days) }

    fun makeUp(s: QadaState, k: PrayerKey): QadaState =
        if (remaining(s, k) == 0) s else s.copy(done = put(s.done, k, done(s, k) + 1))

    fun undoMakeUp(s: QadaState, k: PrayerKey): QadaState = s.copy(done = put(s.done, k, done(s, k) - 1))

    const val MAX = 99_999
}

/** More from the prayer log: the best run of full days and each prayer's share over a period. */
object PrayerStats {
    /** The longest run of days with all five marked, in the days kept. */
    fun bestStreak(log: Map<String, Int>): Int {
        val full = log.filterValues { Integer.bitCount(it and PrayerLog.FULL) == 5 }.keys
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.sorted()
        var best = 0
        var run = 0
        var prev: LocalDate? = null
        for (d in full) {
            run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
            best = maxOf(best, run)
            prev = d
        }
        return best
    }

    /** Each prayer marked, in percent of the days from [from] to [to] (inclusive). */
    fun perPrayer(log: Map<String, Int>, from: LocalDate, to: LocalDate): Map<PrayerKey, Int> {
        var days = 0
        val counts = IntArray(5)
        var d = from
        while (!d.isAfter(to)) {
            days++
            PrayerKey.prayers.forEachIndexed { i, k -> if (PrayerLog.isDone(log, d, k)) counts[i]++ }
            d = d.plusDays(1)
        }
        return PrayerKey.prayers.withIndex().associate { (i, k) -> k to if (days == 0) 0 else counts[i] * 100 / days }
    }

    /** Prayers marked on each day from [from] to [to], oldest first (for the month's bars). */
    fun daily(log: Map<String, Int>, from: LocalDate, to: LocalDate): List<Pair<LocalDate, Int>> =
        generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.map { it to PrayerLog.count(log, it) }.toList()
}
