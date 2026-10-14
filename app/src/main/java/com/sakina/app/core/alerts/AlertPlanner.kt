package com.sakina.app.core.alerts

import com.sakina.app.core.calendar.UmmAlQura
import com.sakina.app.core.prayer.PrayerKey
import com.sakina.app.core.prayer.PrayerSchedule
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class EventKind { ADHAN, PRE, IQAMA, SUHOOR, REMINDER, TEST }

data class AlertEvent(
    /** Unique per kind, subject and civil date; used to prevent repeats. */
    val id: String,
    val kind: EventKind,
    val at: Instant,
    val date: LocalDate,
    val prayer: PrayerKey? = null,
    /** The related prayer time (the prayer itself for PRE/IQAMA, Fajr for SUHOOR). */
    val prayerAt: Instant? = null,
    val reminder: ReminderKind? = null,
    val mode: AlertMode? = null,
    val style: NotifyStyle? = null,
    val minutes: Int = 0,
)

enum class Decision {
    FIRE,

    /** The system delivered the alarm late: show a silent notification that says so, never a delayed adhan. */
    FIRE_LATE_SILENT,
    SKIP_LATE,
    SKIP_DUPLICATE,
    SKIP_QUIET,
    NOT_DUE,
}

object AlertPlanner {
    /** Events up to this much in the past are still considered when catching up (after a reboot or time change). */
    val LOOKBACK: Duration = Duration.ofMinutes(30)

    /** Events this close in the future are handled with the current alarm (simultaneous events). */
    val LOOKAHEAD: Duration = Duration.ofSeconds(2)

    private val ON_TIME: Duration = Duration.ofMinutes(2)
    private val PRAYER_LATE_LIMIT: Duration = Duration.ofMinutes(30)
    private val IQAMA_LATE_LIMIT: Duration = Duration.ofMinutes(10)
    private val REMINDER_LATE_LIMIT: Duration = Duration.ofMinutes(20)

    fun eventId(kind: EventKind, subject: String, date: LocalDate) = "$kind:$subject:$date"

    /**
     * All events from one day before [from] to [days] days after, sorted by time.
     * [schedule] is null when no place is chosen; then only personal reminders exist (in [zone]).
     */
    fun plan(alerts: AlertSettings, schedule: PrayerSchedule?, zone: ZoneId, from: Instant, days: Int = 8): List<AlertEvent> {
        val out = ArrayList<AlertEvent>()
        val start = from.atZone(zone).toLocalDate().minusDays(1)
        for (i in 0..days) {
            val date = start.plusDays(i.toLong())
            if (schedule != null) {
                val day = schedule.day(date)
                for (key in PrayerKey.prayers) {
                    val t = day[key].instant ?: continue
                    val pa = alerts.prayer(key)
                    if (pa.mode != AlertMode.OFF) {
                        out += AlertEvent(eventId(EventKind.ADHAN, key.name, date), EventKind.ADHAN, t, date, key, t, mode = pa.mode)
                    }
                    val pre = alerts.preFor(key)
                    if (pre > 0) {
                        out += AlertEvent(eventId(EventKind.PRE, key.name, date), EventKind.PRE, t.minusSeconds(pre * 60L), date, key, t,
                            style = alerts.preStyle, minutes = pre)
                    }
                    val iq = alerts.iqamaFor(key)
                    if (iq > 0) {
                        out += AlertEvent(eventId(EventKind.IQAMA, key.name, date), EventKind.IQAMA, t.plusSeconds(iq * 60L), date, key, t,
                            style = alerts.iqamaStyle, minutes = iq)
                    }
                }
                val s = alerts.suhoor
                val fajr = day[PrayerKey.FAJR].instant
                if (s.enabled && fajr != null && (!s.ramadanOnly || UmmAlQura.isRamadan(date) == true)) {
                    val m = s.minutesBeforeFajr.coerceIn(AlertSettings.SUHOOR_MIN, AlertSettings.SUHOOR_MAX)
                    out += AlertEvent(eventId(EventKind.SUHOOR, "FAJR", date), EventKind.SUHOOR, fajr.minusSeconds(m * 60L), date,
                        PrayerKey.FAJR, fajr, style = s.style, minutes = m)
                }
            }
            for (r in alerts.allReminders) {
                if (!r.enabled || !r.appliesOn(date.dayOfWeek)) continue
                val m = r.minuteOfDay.coerceIn(0, 24 * 60 - 1)
                val at = date.atTime(LocalTime.of(m / 60, m % 60)).atZone(zone).toInstant()
                out += AlertEvent(eventId(EventKind.REMINDER, r.kind.name, date), EventKind.REMINDER, at, date, reminder = r.kind, style = r.style)
            }
        }
        return out.sortedWith(compareBy({ it.at }, { it.kind.ordinal }))
    }

    /** Events to handle now: from [LOOKBACK] ago (only when [catchUp]) up to [LOOKAHEAD] ahead. */
    fun due(plan: List<AlertEvent>, now: Instant, catchUp: Boolean): List<AlertEvent> {
        val earliest = if (catchUp) now.minus(LOOKBACK) else now.minusSeconds(5)
        val latest = now.plus(LOOKAHEAD)
        return plan.filter { !it.at.isBefore(earliest) && !it.at.isAfter(latest) }
    }

    /** The next event to set an alarm for: the first one after the handled window that has not fired yet. */
    fun next(plan: List<AlertEvent>, now: Instant, fired: Set<String>): AlertEvent? {
        val after = now.plus(LOOKAHEAD)
        return plan.firstOrNull { it.at.isAfter(after) && it.id !in fired }
    }

    fun decide(event: AlertEvent, now: Instant, fired: Set<String>, quietUntil: Instant?): Decision {
        if (event.at.isAfter(now.plus(LOOKAHEAD))) return Decision.NOT_DUE
        if (event.id in fired) return Decision.SKIP_DUPLICATE
        if (quietUntil != null && now.isBefore(quietUntil)) return Decision.SKIP_QUIET
        val late = Duration.between(event.at, now)
        return when (event.kind) {
            EventKind.ADHAN, EventKind.TEST -> when {
                late <= ON_TIME -> Decision.FIRE
                late <= PRAYER_LATE_LIMIT -> Decision.FIRE_LATE_SILENT
                else -> Decision.SKIP_LATE
            }
            // A pre-alert or suhoor reminder is useless once the prayer time has come.
            EventKind.PRE, EventKind.SUHOOR -> if (event.prayerAt != null && now.isBefore(event.prayerAt)) Decision.FIRE else Decision.SKIP_LATE
            EventKind.IQAMA -> if (late <= IQAMA_LATE_LIMIT) Decision.FIRE else Decision.SKIP_LATE
            EventKind.REMINDER -> if (late <= REMINDER_LATE_LIMIT) Decision.FIRE else Decision.SKIP_LATE
        }
    }
}
