package com.sakina.app.core.calendar

import com.sakina.app.core.alerts.NotifyStyle
import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * A user's own occasion on a fixed date: once, or every year on the same Gregorian date or the same
 * Hijri date (displayed calendar; a 30th falls on the 29th in a 29-day month).
 */
@Serializable
data class PersonalOccasion(
    val id: String,
    val title: String,
    val note: String = "",
    /** The date as entered, as a civil epoch day. */
    val epochDay: Long,
    val repeat: Repeat = Repeat.NONE,
    /** For yearly Hijri repeats: the Hijri month and day as entered. */
    val hijriMonth: Int = 0,
    val hijriDay: Int = 0,
    /** Optional civil time, minutes after midnight. */
    val minuteOfDay: Int? = null,
    val notify: Boolean = false,
    val style: NotifyStyle = NotifyStyle.NORMAL,
) {
    enum class Repeat { NONE, YEARLY_GREGORIAN, YEARLY_HIJRI }

    val date: LocalDate get() = LocalDate.ofEpochDay(epochDay)

    fun occursOn(d: LocalDate, hijriOffset: Int): Boolean = when (repeat) {
        Repeat.NONE -> d.toEpochDay() == epochDay
        Repeat.YEARLY_GREGORIAN -> !d.isBefore(date) && d.monthValue == date.monthValue &&
            (d.dayOfMonth == date.dayOfMonth || (date.monthValue == 2 && date.dayOfMonth == 29 && !d.isLeapYear && d.dayOfMonth == 28))
        Repeat.YEARLY_HIJRI -> {
            val h = UmmAlQura.displayed(d, hijriOffset)
            if (h == null || d.isBefore(date) || h.month != hijriMonth) false
            else {
                val len = UmmAlQura.monthLength(h.year, h.month) ?: 30
                h.day == minOf(hijriDay, len)
            }
        }
    }

    /** Notification time on a day it occurs: its own time, or 09:00 when it has none. */
    val notifyMinute: Int get() = minuteOfDay ?: DEFAULT_NOTIFY_MINUTE

    companion object {
        const val DEFAULT_NOTIFY_MINUTE = 9 * 60
        const val MAX_TITLE = 80
        const val MAX_NOTE = 500
        const val MAX_COUNT = 300
    }
}

/** Optional reminders for Monday/Thursday fasting and the white days, on the day or the evening before. */
@Serializable
data class FastingReminders(
    val mondayThursday: Boolean = false,
    val whiteDays: Boolean = false,
    val minuteOfDay: Int = 21 * 60,
    val eveningBefore: Boolean = true,
    val style: NotifyStyle = NotifyStyle.NORMAL,
) {
    val anyEnabled: Boolean get() = mondayThursday || whiteDays
}

@Serializable
data class CalendarPrefs(
    val kind: CalendarKind = CalendarKind.HIJRI,
    /** The optional list view; never switched on by itself (for example by a larger font). */
    val listMode: Boolean = false,
    val occasions: List<PersonalOccasion> = emptyList(),
    val fasting: FastingReminders = FastingReminders(),
)

object PersonalOccasionOps {
    fun on(list: List<PersonalOccasion>, d: LocalDate, hijriOffset: Int) = list.filter { it.occursOn(d, hijriOffset) }

    fun upsert(list: List<PersonalOccasion>, o: PersonalOccasion): List<PersonalOccasion> {
        val i = list.indexOfFirst { it.id == o.id }
        return if (i >= 0) list.toMutableList().also { it[i] = o } else (list + o).takeLast(PersonalOccasion.MAX_COUNT)
    }

    fun remove(list: List<PersonalOccasion>, id: String) = list.filterNot { it.id == id }

    /** Puts an occasion back where it was (undo of a delete). */
    fun restore(list: List<PersonalOccasion>, o: PersonalOccasion, index: Int): List<PersonalOccasion> =
        if (list.any { it.id == o.id }) list else list.toMutableList().also { it.add(index.coerceIn(0, it.size), o) }
}

/** Which fasting reminders apply to a day (the day being reminded about), honouring Ramadan and forbidden days. */
object FastingReminderRules {
    fun kindsFor(day: LocalDate, prefs: FastingReminders, hijriOffset: Int): List<FastingKind> {
        val kinds = FastingDays.on(day, hijriOffset)
        if (FastingDays.isForbidden(kinds) || FastingKind.RAMADAN in kinds) return emptyList()
        return kinds.filter {
            (prefs.mondayThursday && (it == FastingKind.MONDAY || it == FastingKind.THURSDAY)) ||
                (prefs.whiteDays && it == FastingKind.WHITE_DAY)
        }
    }
}
