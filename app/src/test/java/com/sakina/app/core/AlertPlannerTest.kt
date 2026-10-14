package com.sakina.app.core

import com.sakina.app.core.alerts.AlertMode
import com.sakina.app.core.alerts.AlertPlanner
import com.sakina.app.core.alerts.AlertSettings
import com.sakina.app.core.alerts.Decision
import com.sakina.app.core.alerts.EventKind
import com.sakina.app.core.alerts.NotifyStyle
import com.sakina.app.core.alerts.ReminderKind
import com.sakina.app.core.alerts.SuhoorPref
import com.sakina.app.core.calendar.HijriDate
import com.sakina.app.core.calendar.UmmAlQura
import com.sakina.app.core.prayer.CalcConfig
import com.sakina.app.core.prayer.GeoPoint
import com.sakina.app.core.prayer.PrayerKey
import com.sakina.app.core.prayer.PrayerSchedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class AlertPlannerTest {
    private val zone = ZoneId.of("Asia/Riyadh")
    private val schedule = PrayerSchedule(GeoPoint(24.7136, 46.6753), zone, CalcConfig())
    private val date = LocalDate.of(2026, 10, 14)
    private fun at(h: Int, m: Int, d: LocalDate = date) = ZonedDateTime.of(d, LocalTime.of(h, m), zone).toInstant()

    @Test
    fun everythingIsOffByDefault() {
        val s = AlertSettings()
        assertFalse(s.anyEnabled)
        assertTrue(AlertPlanner.plan(s, schedule, zone, at(12, 0)).isEmpty())
        assertTrue(s.allReminders.none { it.enabled })
        assertEquals(8, s.allReminders.size)
    }

    @Test
    fun prayerPreAndIqamaEvents() {
        val s = AlertSettings(preMinutes = 10, iqamaMinutes = 20)
            .withPrayer(PrayerKey.ASR) { it.copy(mode = AlertMode.ADHAN) }
            .withPrayer(PrayerKey.MAGHRIB) { it.copy(mode = AlertMode.SILENT, preMinutes = 0, iqamaMinutes = 5) }
        val plan = AlertPlanner.plan(s, schedule, zone, at(12, 0))
        val asr = schedule.day(date)[PrayerKey.ASR].instant!!
        val maghrib = schedule.day(date)[PrayerKey.MAGHRIB].instant!!
        val today = plan.filter { it.date == date }
        val asrAdhan = today.first { it.kind == EventKind.ADHAN && it.prayer == PrayerKey.ASR }
        assertEquals(asr, asrAdhan.at)
        assertEquals(AlertMode.ADHAN, asrAdhan.mode)
        assertEquals("ADHAN:ASR:2026-10-14", asrAdhan.id)
        assertEquals(asr.minusSeconds(600), today.first { it.kind == EventKind.PRE && it.prayer == PrayerKey.ASR }.at)
        assertEquals(asr.plusSeconds(1200), today.first { it.kind == EventKind.IQAMA && it.prayer == PrayerKey.ASR }.at)
        // Maghrib overrides: no pre-alert, iqama after 5 minutes.
        assertTrue(today.none { it.kind == EventKind.PRE && it.prayer == PrayerKey.MAGHRIB })
        assertEquals(maghrib.plusSeconds(300), today.first { it.kind == EventKind.IQAMA && it.prayer == PrayerKey.MAGHRIB }.at)
        // Pre/iqama apply to every prayer from the general value, even with the adhan off.
        assertTrue(today.any { it.kind == EventKind.PRE && it.prayer == PrayerKey.FAJR })
        assertTrue(today.none { it.kind == EventKind.ADHAN && it.prayer == PrayerKey.FAJR })
        // Sorted and unique.
        assertEquals(plan.sortedBy { it.at }, plan)
        assertEquals(plan.size, plan.map { it.id }.toSet().size)
    }

    @Test
    fun remindersFollowDaysAndCivilTime() {
        val s = AlertSettings()
            .withReminder(ReminderKind.DUHA) { it.copy(enabled = true, minuteOfDay = 9 * 60 + 15, style = NotifyStyle.SILENT) }
            .withReminder(ReminderKind.FRIDAY) { it.copy(enabled = true) }
        val plan = AlertPlanner.plan(s, null, zone, at(12, 0))
        val duha = plan.filter { it.reminder == ReminderKind.DUHA }
        assertEquals(9, duha.size) // yesterday, today and 7 days ahead (covers every weekday)
        assertTrue(duha.all { it.at.atZone(zone).toLocalTime() == LocalTime.of(9, 15) })
        val friday = plan.filter { it.reminder == ReminderKind.FRIDAY }
        assertTrue(friday.isNotEmpty())
        assertTrue(friday.all { it.date.dayOfWeek == DayOfWeek.FRIDAY && it.at.atZone(zone).toLocalTime() == LocalTime.of(10, 0) })
    }

    @Test
    fun reminderInDaylightSavingGapMovesForward() {
        val ny = ZoneId.of("America/New_York")
        val s = AlertSettings().withReminder(ReminderKind.QIYAM) { it.copy(enabled = true, minuteOfDay = 2 * 60 + 30) }
        val plan = AlertPlanner.plan(s, null, ny, ZonedDateTime.of(2026, 3, 7, 12, 0, 0, 0, ny).toInstant(), days = 3)
        val onSwitchDay = plan.first { it.date == LocalDate.of(2026, 3, 8) }
        assertEquals(LocalTime.of(3, 30), onSwitchDay.at.atZone(ny).toLocalTime())
    }

    @Test
    fun suhoorOnlyInRamadanUnlessAskedForEveryDay() {
        val ramadan = UmmAlQura.toGregorian(HijriDate(1447, 9, 1))!!
        val ramadanOnly = AlertSettings(suhoor = SuhoorPref(enabled = true, minutesBeforeFajr = 30))
        val inRamadan = AlertPlanner.plan(ramadanOnly, schedule, zone, at(12, 0, ramadan), days = 2).filter { it.kind == EventKind.SUHOOR }
        val fajr = schedule.day(ramadan)[PrayerKey.FAJR].instant!!
        assertEquals(fajr.minusSeconds(1800), inRamadan.first { it.date == ramadan }.at)
        assertTrue(inRamadan.none { it.date == ramadan.minusDays(1) })
        assertTrue(AlertPlanner.plan(ramadanOnly, schedule, zone, at(12, 0)).none { it.kind == EventKind.SUHOOR })
        val everyDay = AlertSettings(suhoor = SuhoorPref(enabled = true, minutesBeforeFajr = 500, ramadanOnly = false))
        val ev = AlertPlanner.plan(everyDay, schedule, zone, at(12, 0)).first { it.kind == EventKind.SUHOOR && it.date == date }
        assertEquals(120, ev.minutes) // clamped to the allowed range
    }

    @Test
    fun decisionsForLateDuplicateAndQuiet() {
        val s = AlertSettings(preMinutes = 10, iqamaMinutes = 15)
            .withPrayer(PrayerKey.ASR) { it.copy(mode = AlertMode.ADHAN) }
            .withReminder(ReminderKind.DUHA) { it.copy(enabled = true) }
        val plan = AlertPlanner.plan(s, schedule, zone, at(12, 0))
        val adhan = plan.first { it.kind == EventKind.ADHAN && it.date == date }
        val pre = plan.first { it.kind == EventKind.PRE && it.prayer == PrayerKey.ASR && it.date == date }
        val iqama = plan.first { it.kind == EventKind.IQAMA && it.prayer == PrayerKey.ASR && it.date == date }
        val duha = plan.first { it.kind == EventKind.REMINDER && it.date == date }
        val none = emptySet<String>()
        assertEquals(Decision.FIRE, AlertPlanner.decide(adhan, adhan.at.plusSeconds(1), none, null))
        assertEquals(Decision.FIRE_LATE_SILENT, AlertPlanner.decide(adhan, adhan.at.plusSeconds(5 * 60), none, null))
        assertEquals(Decision.SKIP_LATE, AlertPlanner.decide(adhan, adhan.at.plusSeconds(45 * 60), none, null))
        assertEquals(Decision.NOT_DUE, AlertPlanner.decide(adhan, adhan.at.minusSeconds(60), none, null))
        assertEquals(Decision.SKIP_DUPLICATE, AlertPlanner.decide(adhan, adhan.at, setOf(adhan.id), null))
        assertEquals(Decision.SKIP_QUIET, AlertPlanner.decide(adhan, adhan.at, none, adhan.at.plusSeconds(60)))
        assertEquals(Decision.FIRE, AlertPlanner.decide(adhan, adhan.at, none, adhan.at.minusSeconds(1)))
        assertEquals(Decision.FIRE, AlertPlanner.decide(pre, pre.at.plusSeconds(8 * 60), none, null))
        assertEquals(Decision.SKIP_LATE, AlertPlanner.decide(pre, adhan.at, none, null))
        assertEquals(Decision.FIRE, AlertPlanner.decide(iqama, iqama.at.plusSeconds(9 * 60), none, null))
        assertEquals(Decision.SKIP_LATE, AlertPlanner.decide(iqama, iqama.at.plusSeconds(11 * 60), none, null))
        assertEquals(Decision.FIRE, AlertPlanner.decide(duha, duha.at.plusSeconds(19 * 60), none, null))
        assertEquals(Decision.SKIP_LATE, AlertPlanner.decide(duha, duha.at.plusSeconds(21 * 60), none, null))
    }

    @Test
    fun dueWindowAndNextAlarm() {
        val s = AlertSettings().withPrayer(PrayerKey.ASR) { it.copy(mode = AlertMode.NOTIFY) }
            .withPrayer(PrayerKey.MAGHRIB) { it.copy(mode = AlertMode.NOTIFY) }
        val plan = AlertPlanner.plan(s, schedule, zone, at(12, 0))
        val asr = plan.first { it.prayer == PrayerKey.ASR && it.date == date }
        val maghrib = plan.first { it.prayer == PrayerKey.MAGHRIB && it.date == date }
        // At the alarm time only Asr is due.
        assertEquals(listOf(asr), AlertPlanner.due(plan, asr.at, catchUp = false))
        // After a reboot 10 minutes later, catching up still finds Asr; a settings change does not.
        assertEquals(listOf(asr), AlertPlanner.due(plan, asr.at.plusSeconds(600), catchUp = true))
        assertTrue(AlertPlanner.due(plan, asr.at.plusSeconds(600), catchUp = false).isEmpty())
        assertEquals(maghrib, AlertPlanner.next(plan, asr.at, emptySet()))
        assertEquals(asr, AlertPlanner.next(plan, asr.at.minusSeconds(3600), emptySet()))
        // A fired event is never chosen again.
        assertEquals(maghrib, AlertPlanner.next(plan, asr.at.minusSeconds(3600), setOf(asr.id)))
        assertNull(AlertPlanner.next(emptyList(), asr.at, emptySet()))
        assertTrue(Duration.between(asr.at, maghrib.at).toMinutes() > 60)
    }

    @Test
    fun soundAndOffsetsInheritance() {
        val s = AlertSettings(preMinutes = 70, iqamaMinutes = 5)
            .withPrayer(PrayerKey.ISHA) { it.copy(iqamaMinutes = 0) }
        assertEquals(60, s.preFor(PrayerKey.DHUHR))
        assertEquals(0, s.iqamaFor(PrayerKey.ISHA))
        assertEquals(5, s.iqamaFor(PrayerKey.DHUHR))
        assertEquals(s.fajrSound, s.soundFor(PrayerKey.FAJR))
        assertEquals(s.generalSound, s.soundFor(PrayerKey.ASR))
    }
}
