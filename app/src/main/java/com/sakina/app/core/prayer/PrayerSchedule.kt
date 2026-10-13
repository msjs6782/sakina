package com.sakina.app.core.prayer

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class PrayerEvent(val key: PrayerKey, val instant: Instant, val date: LocalDate, val status: TimeStatus)

/** Times for one place and configuration, with small helpers for "next" and "previous" across days. */
class PrayerSchedule(
    val point: GeoPoint,
    val zone: ZoneId,
    val config: CalcConfig,
) {
    private val cache = HashMap<LocalDate, DayTimes>()

    fun day(date: LocalDate): DayTimes = cache.getOrPut(date) { PrayerEngine.compute(date, point, zone, config) }

    fun localDate(now: Instant): LocalDate = now.atZone(zone).toLocalDate()

    fun range(start: LocalDate, days: Int): List<DayTimes> = (0 until days).map { day(start.plusDays(it.toLong())) }

    private fun events(dates: List<LocalDate>, keys: Collection<PrayerKey>): List<PrayerEvent> =
        dates.flatMap { d ->
            day(d).times.filter { it.key in keys && it.instant != null }
                .map { PrayerEvent(it.key, it.instant!!, d, it.status) }
        }.sortedBy { it.instant }

    /** The first event strictly after [now], looking up to [daysAhead] days forward. */
    fun next(now: Instant, keys: Collection<PrayerKey> = PrayerKey.prayers, daysAhead: Int = 3): PrayerEvent? {
        val d = localDate(now)
        val dates = (-1..daysAhead).map { d.plusDays(it.toLong()) }
        return events(dates, keys).firstOrNull { it.instant.isAfter(now) }
    }

    /** The latest event at or before [now], looking up to [daysBack] days back. */
    fun previous(now: Instant, keys: Collection<PrayerKey> = PrayerKey.prayers, daysBack: Int = 3): PrayerEvent? {
        val d = localDate(now)
        val dates = (-daysBack..1).map { d.plusDays(it.toLong()) }
        return events(dates, keys).lastOrNull { !it.instant.isAfter(now) }
    }

    /** Astronomical sunrise or sunset events (no user offsets) after [now]. */
    fun nextSunEvent(now: Instant, sunrise: Boolean, daysAhead: Int = 3): Pair<Instant, LocalDate>? {
        val d = localDate(now)
        for (i in 0..daysAhead) {
            val date = d.plusDays(i.toLong())
            val t = day(date).let { if (sunrise) it.sunrise else it.sunset } ?: continue
            if (t.isAfter(now)) return t to date
        }
        return null
    }
}
