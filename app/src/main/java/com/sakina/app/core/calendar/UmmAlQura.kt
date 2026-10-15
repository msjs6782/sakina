package com.sakina.app.core.calendar

import java.time.DateTimeException
import java.time.LocalDate
import java.time.chrono.HijrahChronology
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

data class HijriDate(val year: Int, val month: Int, val day: Int) : Comparable<HijriDate> {
    override fun compareTo(other: HijriDate) = compareValuesBy(this, other, { it.year }, { it.month }, { it.day })
}

/**
 * The unadjusted Umm al-Qura calendar, as published in the Java time library (HijrahChronology,
 * covering 1300–1600 AH). Every function returns null outside that range instead of guessing.
 *
 * Prayer-time rules use this unadjusted calendar. The user's display offset (−2…+2 days) is applied
 * only where dates are shown, through [displayed].
 */
object UmmAlQura {
    private val chrono: HijrahChronology = HijrahChronology.INSTANCE

    /** The years the published Umm al-Qura data covers. */
    const val MIN_YEAR = 1300
    const val MAX_YEAR = 1600

    fun fromGregorian(date: LocalDate): HijriDate? = try {
        val h = HijrahDate.from(date)
        HijriDate(h.get(ChronoField.YEAR), h.get(ChronoField.MONTH_OF_YEAR), h.get(ChronoField.DAY_OF_MONTH))
    } catch (e: DateTimeException) {
        null
    } catch (e: IndexOutOfBoundsException) {
        null
    }

    // Checked before calling the chronology: outside its data some JDKs throw an index error, not a DateTimeException.
    fun toGregorian(h: HijriDate): LocalDate? = if (h.year !in MIN_YEAR..MAX_YEAR || h.month !in 1..12) null else try {
        LocalDate.from(chrono.date(h.year, h.month, h.day))
    } catch (e: DateTimeException) {
        null
    } catch (e: IndexOutOfBoundsException) {
        null
    }

    fun monthLength(year: Int, month: Int): Int? = if (year !in MIN_YEAR..MAX_YEAR || month !in 1..12) null else try {
        chrono.date(year, month, 1).lengthOfMonth()
    } catch (e: DateTimeException) {
        null
    } catch (e: IndexOutOfBoundsException) {
        null
    }

    /** True or false for dates inside the published range, null outside it. */
    fun isRamadan(date: LocalDate): Boolean? = fromGregorian(date)?.let { it.month == 9 }

    /** The Hijri date shown to the user for a civil date, after the display-only offset. */
    fun displayed(date: LocalDate, offsetDays: Int): HijriDate? = fromGregorian(date.plusDays(offsetDays.toLong()))

    /** Gregorian dates of the month containing [h] (unadjusted). */
    fun monthRange(year: Int, month: Int): ClosedRange<LocalDate>? {
        val start = toGregorian(HijriDate(year, month, 1)) ?: return null
        val len = monthLength(year, month) ?: return null
        return start..start.plusDays(len - 1L)
    }
}
