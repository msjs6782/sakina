package com.sakina.app.core.calendar

import java.time.LocalDate

/** A day the user fasted: voluntarily, or making up a day of Ramadan. */
enum class FastMark(val code: Int) {
    VOLUNTARY(1),
    QADA(2);

    companion object {
        fun of(code: Int?): FastMark? = entries.firstOrNull { it.code == code }
    }
}

/**
 * The days the user fasted (civil date → [FastMark.code]) and the Ramadan days they owe. What is still owed is the
 * days owed minus the make-up fasts marked since the count was set, so marking and unmarking a fast always add up.
 * On the device only.
 */
object FastLog {
    const val KEEP_DAYS = 800L

    fun mark(log: Map<String, Int>, d: LocalDate): FastMark? = FastMark.of(log[d.toString()])

    /** Whether a fast can be marked on [d]: today or before, and not a day on which fasting is not allowed. */
    fun canMark(d: LocalDate, today: LocalDate, hijriOffset: Int): Boolean =
        !d.isAfter(today) && FastingDays.on(d, hijriOffset).none { it == FastingKind.FORBIDDEN_EID || it == FastingKind.FORBIDDEN_TASHREEQ }

    /** Sets (or clears, with null) the mark of [d], dropping marks older than [KEEP_DAYS] before [today]. */
    fun set(log: Map<String, Int>, d: LocalDate, mark: FastMark?, today: LocalDate): Map<String, Int> {
        val oldest = today.minusDays(KEEP_DAYS)
        val kept = log.filterKeys { runCatching { !LocalDate.parse(it).isBefore(oldest) }.getOrDefault(false) }
        return if (mark == null) kept - d.toString() else kept + (d.toString() to mark.code)
    }

    fun count(log: Map<String, Int>, from: LocalDate, to: LocalDate, mark: FastMark? = null): Int =
        log.count { (k, v) ->
            val d = runCatching { LocalDate.parse(k) }.getOrNull()
            d != null && !d.isBefore(from) && !d.isAfter(to) && (mark == null || v == mark.code)
        }

    /**
     * The day after the latest Ramadan that has ended by [today] (1 Shawwal): the days owed are counted from there, so
     * a make-up fast marked late (yesterday's, say) still counts.
     */
    fun seasonStart(today: LocalDate, hijriOffset: Int): LocalDate? {
        val h = UmmAlQura.displayed(today, hijriOffset) ?: return null
        val year = if (h.month >= 10) h.year else h.year - 1
        return DateConvert.hijriToGregorian(HijriDate(year, 10, 1), hijriOffset)
    }

    /** Ramadan days still owed: [owed] (counted from [since]) less the make-up fasts marked on or after [since]. */
    fun qadaRemaining(log: Map<String, Int>, owed: Int, since: LocalDate?): Int {
        if (owed <= 0) return 0
        val made = log.count { (k, v) ->
            v == FastMark.QADA.code && runCatching { LocalDate.parse(k) }.getOrNull()?.let { since == null || !it.isBefore(since) } == true
        }
        return maxOf(0, owed - made)
    }
}
