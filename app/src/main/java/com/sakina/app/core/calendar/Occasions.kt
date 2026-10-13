package com.sakina.app.core.calendar

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Islamic occasions calculated from the Umm al-Qura calendar, shifted by the user's display offset.
 * The start of a month can differ locally depending on moon sighting; the UI always says so.
 */
enum class Occasion(val month: Int, val day: Int, val lastDay: Int = day) {
    HIJRI_NEW_YEAR(1, 1),
    ASHURA(1, 10),
    RAMADAN_START(9, 1),
    EID_AL_FITR(10, 1),
    DHUL_HIJJAH_TEN(12, 1, 10),
    ARAFAH(12, 9),
    EID_AL_ADHA(12, 10),
    TASHREEQ(12, 11, 13),
}

data class OccasionDay(val occasion: Occasion, val date: LocalDate, val daysAway: Long)

object Occasions {
    /** Occasions that include [date] (displayed calendar). */
    fun on(date: LocalDate, offsetDays: Int): List<Occasion> {
        val h = UmmAlQura.displayed(date, offsetDays) ?: return emptyList()
        return Occasion.entries.filter { h.month == it.month && h.day in it.day..it.lastDay }
    }

    /** The next occasion starting after [from] (not counting ongoing ones), within about 13 months. */
    fun next(from: LocalDate, offsetDays: Int, limitDays: Int = 400): OccasionDay? {
        var d = from.plusDays(1)
        repeat(limitDays) {
            val h = UmmAlQura.displayed(d, offsetDays) ?: return null
            val o = Occasion.entries.firstOrNull { h.month == it.month && h.day == it.day }
            if (o != null) return OccasionDay(o, d, ChronoUnit.DAYS.between(from, d))
            d = d.plusDays(1)
        }
        return null
    }

    /** Ramadan's first civil day in the displayed calendar for the Hijri year of [from] or the next one. */
    fun ramadanStart(from: LocalDate, offsetDays: Int): LocalDate? {
        val h = UmmAlQura.displayed(from, offsetDays) ?: return null
        val year = if (h.month <= 9) h.year else h.year + 1
        val start = UmmAlQura.toGregorian(HijriDate(year, 9, 1)) ?: return null
        return start.minusDays(offsetDays.toLong())
    }
}
