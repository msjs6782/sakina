package com.sakina.app.core.prayer

import com.sakina.app.core.calendar.HijriDate
import com.sakina.app.core.calendar.UmmAlQura
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * A night from the schedule's Maghrib to the next day's Fajr. Instants, not clock times, so a change to
 * or from daylight saving time inside the night is counted correctly.
 */
data class Night(
    /** The civil date on whose evening the night begins. */
    val eveningOf: LocalDate,
    val maghrib: Instant,
    val fajr: Instant,
) {
    val length: Duration get() = Duration.between(maghrib, fajr)
    val midnight: Instant get() = maghrib.plus(length.dividedBy(2))
    val lastThird: Instant get() = maghrib.plus(length.multipliedBy(2).dividedBy(3))

    fun contains(t: Instant) = !t.isBefore(maghrib) && t.isBefore(fajr)
}

object Nights {
    fun of(schedule: PrayerSchedule, eveningOf: LocalDate): Night? {
        val m = schedule.day(eveningOf)[PrayerKey.MAGHRIB].instant ?: return null
        val f = schedule.day(eveningOf.plusDays(1))[PrayerKey.FAJR].instant ?: return null
        if (!f.isAfter(m)) return null
        return Night(eveningOf, m, f)
    }

    /** The night under way at [now], or else the coming one. */
    fun currentOrNext(schedule: PrayerSchedule, now: Instant): Night? {
        val today = schedule.localDate(now)
        of(schedule, today.minusDays(1))?.takeIf { it.contains(now) }?.let { return it }
        return of(schedule, today)
    }
}

/**
 * A practical estimate of the Duha window: from [afterSunrise] minutes after sunrise to [beforeDhuhr]
 * minutes before the schedule's Dhuhr. Margins are user-adjustable estimates, not rulings.
 */
data class DuhaWindow(val start: Instant, val end: Instant) {
    companion object {
        const val DEFAULT_AFTER_SUNRISE = 15
        const val DEFAULT_BEFORE_DHUHR = 10
        val AFTER_RANGE = 5..45
        val BEFORE_RANGE = 5..30

        fun of(day: DayTimes, afterSunrise: Int, beforeDhuhr: Int): DuhaWindow? {
            val sr = day[PrayerKey.SUNRISE].instant ?: return null
            val dh = day[PrayerKey.DHUHR].instant ?: return null
            val s = sr.plusSeconds(afterSunrise.coerceIn(AFTER_RANGE) * 60L)
            val e = dh.minusSeconds(beforeDhuhr.coerceIn(BEFORE_RANGE) * 60L)
            return if (s.isBefore(e)) DuhaWindow(s, e) else null
        }
    }
}

/** One day of Ramadan with its Fajr (start of the fast) and Maghrib (end of the fast). */
data class RamadanDay(val day: Int, val date: LocalDate, val times: DayTimes) {
    val fajr: Instant? get() = times[PrayerKey.FAJR].instant
    val maghrib: Instant? get() = times[PrayerKey.MAGHRIB].instant
}

/** A night of the last ten, beginning at the Maghrib before its day. */
data class TenNight(val night: Int, val beginsEvening: LocalDate, val begins: Instant?, val ends: Instant?) {
    val odd: Boolean get() = night % 2 == 1
}

data class RamadanMonth(val year: Int, val days: List<RamadanDay>) {
    val length: Int get() = days.size
    val first: LocalDate get() = days.first().date
    val last: LocalDate get() = days.last().date

    fun dayOf(date: LocalDate): RamadanDay? = days.firstOrNull { it.date == date }

    /** Nights 21 to the month's last night; night n begins at the Maghrib of day n − 1. */
    fun lastTen(): List<TenNight> = (21..length).map { n ->
        val before = days[n - 2]
        TenNight(n, before.date, before.maghrib, days[n - 1].fajr)
    }
}

/** What the fast is waiting for at a moment in Ramadan. */
sealed interface FastStatus {
    data class UntilFajr(val at: Instant, val day: Int) : FastStatus
    data class UntilMaghrib(val at: Instant, val day: Int) : FastStatus
    data object Ended : FastStatus
    data class NotYet(val starts: LocalDate) : FastStatus
    data object Unavailable : FastStatus
}

object RamadanPlan {
    /**
     * The Ramadan of Hijri [year] in the unadjusted Umm al-Qura calendar, at its real length (29 or 30 days).
     * The display offset does not move it: the app's rules use the unadjusted calendar.
     */
    fun month(year: Int, schedule: PrayerSchedule): RamadanMonth? {
        val len = UmmAlQura.monthLength(year, 9) ?: return null
        val start = UmmAlQura.toGregorian(HijriDate(year, 9, 1)) ?: return null
        return RamadanMonth(year, (1..len).map { i -> start.plusDays(i - 1L).let { RamadanDay(i, it, schedule.day(it)) } })
    }

    /** This year's Ramadan while it lasts, otherwise the next one. */
    fun currentOrNext(schedule: PrayerSchedule, today: LocalDate): RamadanMonth? {
        val h = UmmAlQura.fromGregorian(today) ?: return null
        val year = if (h.month <= 9) h.year else h.year + 1
        return month(year, schedule)
    }

    fun status(m: RamadanMonth, schedule: PrayerSchedule, now: Instant): FastStatus {
        val today = schedule.localDate(now)
        if (today.isBefore(m.first)) {
            // From the Maghrib before the first day, the first fast is the next thing.
            val eve = schedule.day(m.first.minusDays(1))[PrayerKey.MAGHRIB].instant
            val firstFajr = m.days.first().fajr
            return if (today == m.first.minusDays(1) && eve != null && firstFajr != null && !now.isBefore(eve)) FastStatus.UntilFajr(firstFajr, 1)
            else FastStatus.NotYet(m.first)
        }
        val d = m.dayOf(today) ?: return FastStatus.Ended
        val fajr = d.fajr ?: return FastStatus.Unavailable
        val maghrib = d.maghrib ?: return FastStatus.Unavailable
        return when {
            now.isBefore(fajr) -> FastStatus.UntilFajr(fajr, d.day)
            now.isBefore(maghrib) -> FastStatus.UntilMaghrib(maghrib, d.day)
            else -> m.days.getOrNull(d.day)?.fajr?.let { FastStatus.UntilFajr(it, d.day + 1) } ?: FastStatus.Ended
        }
    }
}
