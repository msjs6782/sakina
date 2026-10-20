package com.sakina.app.core.widgets

import com.sakina.app.core.calendar.HijriDate
import com.sakina.app.core.calendar.UmmAlQura
import com.sakina.app.core.prayer.FastStatus
import com.sakina.app.core.prayer.PrayerEvent
import com.sakina.app.core.prayer.PrayerKey
import com.sakina.app.core.prayer.PrayerSchedule
import com.sakina.app.core.prayer.RamadanPlan
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** The ten home-screen widget types. [id] is stable: it names the provider and is stored with each instance. */
enum class WidgetKind(val id: String) {
    NEXT("next"),
    COUNTDOWN("countdown"),
    DAY("day"),
    GLASS("glass"),
    MINI("mini"),
    STRIP("strip"),
    DIGITAL("digital"),
    ANALOG("analog"),
    CONTENT("content"),
    FAJR_MAGHRIB("fajr_maghrib"),
    /** The day's name stretched with kashida across the widget, the dates, the five prayers and the time left. */
    DAYNAME("dayname"),
    /** The khatma plan: today's pages, progress and whether the reader is on track. */
    KHATMA("khatma"),
    /** A short dhikr in heavy Kufi, stretched with kashida across the widget (never the divine name). */
    DHIKR("dhikr");

    /** Types that show a remaining time; it is a snapshot refreshed about once a minute and shown with its time. */
    val showsRemaining: Boolean get() = this in setOf(NEXT, COUNTDOWN, DAY, GLASS, DIGITAL, FAJR_MAGHRIB, DAYNAME)

    /** Types whose content depends on prayer times (and so on a place). */
    val needsPlace: Boolean get() = this != CONTENT && this != KHATMA && this != DHIKR

    companion object {
        fun of(id: String): WidgetKind? = entries.firstOrNull { it.id == id }
    }
}

/** One prayer (or sunrise) in a widget's table, with whether it is the next one. */
data class WidgetTime(val key: PrayerKey, val at: Instant?, val isNext: Boolean)

/**
 * What a prayer widget shows at one moment: the next prayer, the whole minutes left (never negative), the
 * day's six times, and when this snapshot was taken. Widgets are not live counters: Android may refresh
 * them late, so the remaining time always travels with [taken].
 */
data class PrayerSnapshot(
    val taken: Instant,
    val date: LocalDate,
    val next: PrayerEvent?,
    val minutesLeft: Long?,
    val times: List<WidgetTime>,
    /** False when the day's times cannot be calculated here (polar day or night). */
    val complete: Boolean,
)

object WidgetMath {
    /** Whole minutes until [at], rounded up so "0" never shows before the time; zero once it has passed. */
    fun minutesLeft(now: Instant, at: Instant): Long {
        val s = Duration.between(now, at).seconds
        return if (s <= 0) 0 else (s + 59) / 60
    }

    fun snapshot(schedule: PrayerSchedule, now: Instant): PrayerSnapshot {
        val today = schedule.localDate(now)
        val next = schedule.next(now)
        // After Isha the table moves to the next day once that day's Fajr is the next prayer.
        val shown = if (next != null && next.date.isAfter(today) && next.key == PrayerKey.FAJR) next.date else today
        val day = schedule.day(shown)
        val times = PrayerKey.entries.map { k ->
            val t = day[k]
            WidgetTime(k, t.instant, next != null && next.key == k && next.date == shown)
        }
        return PrayerSnapshot(now, shown, next, next?.let { minutesLeft(now, it.instant) }, times, day.allAvailable)
    }

    /** The next whole minute (plus a second, so the minute has surely turned when the update runs). */
    fun nextMinute(now: Instant): Instant = now.truncatedTo(ChronoUnit.MINUTES).plusSeconds(61)

    /** The next local midnight in [zone] (plus a second), when dates on a widget change. */
    fun nextMidnight(now: Instant, zone: ZoneId): Instant =
        now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().plusSeconds(1)

    /**
     * When a widget's content next changes: every minute for types with a remaining time; otherwise at the next
     * prayer or at midnight, whichever comes first.
     */
    fun nextChange(kind: WidgetKind, now: Instant, zone: ZoneId, nextPrayer: Instant?): Instant {
        if (kind.showsRemaining) return nextMinute(now)
        var t = nextMidnight(now, zone)
        if (nextPrayer != null && nextPrayer.isAfter(now) && nextPrayer.isBefore(t)) t = nextPrayer.plusSeconds(1)
        return t
    }
}

/** The Fajr, Maghrib, Hijri and Ramadan card. */
data class DawnDusk(
    val date: LocalDate,
    val fajr: Instant?,
    val maghrib: Instant?,
    /** Unadjusted Umm al-Qura date shifted by the user's display offset. */
    val hijri: HijriDate?,
    val ramadan: RamadanContext,
)

sealed interface RamadanContext {
    data class Fasting(val day: Int, val untilMaghrib: Instant, val minutesLeft: Long) : RamadanContext
    data class BeforeFajr(val day: Int, val fajr: Instant, val minutesLeft: Long) : RamadanContext
    data class DaysUntil(val days: Long, val starts: LocalDate) : RamadanContext
    data object None : RamadanContext
}

object DawnDuskCard {
    /** Today's Fajr and Maghrib until Maghrib has passed, then tomorrow's. */
    fun of(schedule: PrayerSchedule, now: Instant, hijriOffset: Int): DawnDusk {
        val today = schedule.localDate(now)
        val maghrib = schedule.day(today)[PrayerKey.MAGHRIB].instant
        val date = if (maghrib != null && !now.isBefore(maghrib)) today.plusDays(1) else today
        val day = schedule.day(date)
        val month = RamadanPlan.currentOrNext(schedule, today)
        val ctx = when (val st = month?.let { RamadanPlan.status(it, schedule, now) }) {
            is FastStatus.UntilMaghrib -> RamadanContext.Fasting(st.day, st.at, WidgetMath.minutesLeft(now, st.at))
            is FastStatus.UntilFajr -> RamadanContext.BeforeFajr(st.day, st.at, WidgetMath.minutesLeft(now, st.at))
            is FastStatus.NotYet -> RamadanContext.DaysUntil(ChronoUnit.DAYS.between(today, st.starts), st.starts)
            else -> RamadanContext.None
        }
        return DawnDusk(date, day[PrayerKey.FAJR].instant, day[PrayerKey.MAGHRIB].instant, UmmAlQura.displayed(today, hijriOffset), ctx)
    }
}

/** A 12-hour dial: angles of times, and which clock the analog widget can show. */
object Dial {
    /** Degrees clockwise from 12 o'clock for [t] read in [zone]. */
    fun angle(t: Instant, zone: ZoneId): Float {
        val z = t.atZone(zone)
        val h = (z.hour % 12) + z.minute / 60.0 + z.second / 3600.0
        return (h * 30.0).toFloat()
    }

    /** Android 12 added a time zone to the native analog clock; before it the clock always shows device time. */
    fun clockZone(sdk: Int, place: ZoneId, device: ZoneId): ZoneId = if (sdk >= 31) place else device

    /** Whether the analog widget must say it shows the device's clock (older Android, place in another offset now). */
    fun showsDeviceClock(sdk: Int, place: ZoneId, device: ZoneId, now: Instant): Boolean =
        sdk < 31 && place.rules.getOffset(now) != device.rules.getOffset(now)
}
