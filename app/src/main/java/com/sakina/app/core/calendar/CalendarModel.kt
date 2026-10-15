package com.sakina.app.core.calendar

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit

enum class CalendarKind { HIJRI, GREGORIAN }

/** A month in either calendar. Hijri months are the displayed calendar (Umm al-Qura plus the display offset). */
data class MonthRef(val kind: CalendarKind, val year: Int, val month: Int) {
    fun plus(n: Int): MonthRef {
        val idx = year * 12 + (month - 1) + n
        return MonthRef(kind, Math.floorDiv(idx, 12), Math.floorMod(idx, 12) + 1)
    }

    /** Months from this one to [other] (positive when [other] is later). */
    fun distanceTo(other: MonthRef): Int = (other.year * 12 + other.month) - (year * 12 + month)

    /** Inside the range the calendar can compute (Umm al-Qura data covers 1300–1600 AH). */
    val isSupported: Boolean
        get() = when (kind) {
            CalendarKind.HIJRI -> year in UmmAlQura.MIN_YEAR..UmmAlQura.MAX_YEAR
            CalendarKind.GREGORIAN -> year in 1883..2173
        }

    companion object {
        fun of(kind: CalendarKind, date: LocalDate, hijriOffset: Int): MonthRef? = when (kind) {
            CalendarKind.GREGORIAN -> MonthRef(kind, date.year, date.monthValue)
            CalendarKind.HIJRI -> UmmAlQura.displayed(date, hijriOffset)?.let { MonthRef(kind, it.year, it.month) }
        }
    }
}

/** One cell of a month grid: a civil date, its displayed Hijri date, and whether it belongs to the month shown. */
data class GridDay(val date: LocalDate, val hijri: HijriDate?, val inMonth: Boolean)

object MonthGrid {
    const val CELLS = 42

    /** First and last civil dates of a month (Hijri months in the displayed calendar). */
    fun range(m: MonthRef, hijriOffset: Int): ClosedRange<LocalDate>? = when (m.kind) {
        CalendarKind.GREGORIAN -> {
            val first = LocalDate.of(m.year, m.month, 1)
            first..first.plusDays(first.lengthOfMonth() - 1L)
        }
        CalendarKind.HIJRI -> {
            val start = UmmAlQura.toGregorian(HijriDate(m.year, m.month, 1))
            val len = UmmAlQura.monthLength(m.year, m.month)
            if (start == null || len == null) null
            else start.minusDays(hijriOffset.toLong()).let { it..it.plusDays(len - 1L) }
        }
    }

    /** Six full weeks starting on [weekStart] that contain the month. */
    fun build(m: MonthRef, hijriOffset: Int, weekStart: DayOfWeek): List<GridDay>? {
        val r = range(m, hijriOffset) ?: return null
        val shift = Math.floorMod(r.start.dayOfWeek.value - weekStart.value, 7)
        val first = r.start.minusDays(shift.toLong())
        return (0 until CELLS).map { i ->
            val d = first.plusDays(i.toLong())
            GridDay(d, UmmAlQura.displayed(d, hijriOffset), d in r)
        }
    }

    /** Weekday order for the grid header and rows. */
    fun weekdays(weekStart: DayOfWeek): List<DayOfWeek> = (0 until 7).map { weekStart.plus(it.toLong()) }
}

object DateConvert {
    /** The civil date of a displayed Hijri date, or null when the day does not exist in that month. */
    fun hijriToGregorian(h: HijriDate, hijriOffset: Int): LocalDate? {
        val len = UmmAlQura.monthLength(h.year, h.month) ?: return null
        if (h.day !in 1..len) return null
        return UmmAlQura.toGregorian(h)?.minusDays(hijriOffset.toLong())
    }

    fun gregorianToHijri(d: LocalDate, hijriOffset: Int): HijriDate? = UmmAlQura.displayed(d, hijriOffset)
}

/** The span between two dates in days, weeks and calendar units. */
data class Interval(
    val days: Long,
    val weeks: Long,
    val weekDays: Long,
    val gregorian: Period,
    /** Whole Hijri years, months and days, or null outside the Umm al-Qura range. */
    val hijri: Triple<Int, Int, Int>?,
)

object Intervals {
    fun between(a: LocalDate, b: LocalDate, hijriOffset: Int): Interval {
        val (from, to) = if (a <= b) a to b else b to a
        val days = ChronoUnit.DAYS.between(from, to)
        return Interval(days, days / 7, days % 7, Period.between(from, to), hijriBetween(from, to, hijriOffset))
    }

    /** Counts whole Hijri months from [from] to [to] (same day-of-month, clamped to the month's length). */
    private fun hijriBetween(from: LocalDate, to: LocalDate, offset: Int): Triple<Int, Int, Int>? {
        val h = UmmAlQura.displayed(from, offset) ?: return null
        if (UmmAlQura.displayed(to, offset) == null) return null
        var months = 0
        var anchor = from
        while (true) {
            val m = MonthRef(CalendarKind.HIJRI, h.year, h.month).plus(months + 1)
            val len = UmmAlQura.monthLength(m.year, m.month) ?: return null
            val next = DateConvert.hijriToGregorian(HijriDate(m.year, m.month, minOf(h.day, len)), offset) ?: return null
            if (next > to) break
            months++
            anchor = next
        }
        return Triple(months / 12, months % 12, ChronoUnit.DAYS.between(anchor, to).toInt())
    }
}

/** Sunnah fasting days and the days on which fasting is not allowed, from the displayed Hijri date. */
enum class FastingKind {
    RAMADAN,
    MONDAY,
    THURSDAY,
    WHITE_DAY,
    ARAFAH,
    TASUA,
    ASHURA,
    SHAWWAL,
    FORBIDDEN_EID,
    FORBIDDEN_TASHREEQ,
}

object FastingDays {
    fun on(date: LocalDate, hijriOffset: Int): List<FastingKind> {
        val h = UmmAlQura.displayed(date, hijriOffset) ?: return emptyList()
        // Eid days and the days of Tashreeq: fasting is not allowed, whatever else they are.
        if ((h.month == 10 && h.day == 1) || (h.month == 12 && h.day == 10)) return listOf(FastingKind.FORBIDDEN_EID)
        if (h.month == 12 && h.day in 11..13) return listOf(FastingKind.FORBIDDEN_TASHREEQ)
        if (h.month == 9) return listOf(FastingKind.RAMADAN)
        val out = ArrayList<FastingKind>()
        if (h.month == 12 && h.day == 9) out += FastingKind.ARAFAH
        if (h.month == 1 && h.day == 9) out += FastingKind.TASUA
        if (h.month == 1 && h.day == 10) out += FastingKind.ASHURA
        if (h.day in 13..15) out += FastingKind.WHITE_DAY
        if (h.month == 10) out += FastingKind.SHAWWAL
        if (date.dayOfWeek == DayOfWeek.MONDAY) out += FastingKind.MONDAY
        if (date.dayOfWeek == DayOfWeek.THURSDAY) out += FastingKind.THURSDAY
        return out
    }

    fun isForbidden(kinds: List<FastingKind>) = kinds.any { it == FastingKind.FORBIDDEN_EID || it == FastingKind.FORBIDDEN_TASHREEQ }
}
