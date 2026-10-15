package com.sakina.app.core

import com.sakina.app.core.alerts.AlertPlanner
import com.sakina.app.core.alerts.AlertSettings
import com.sakina.app.core.alerts.Decision
import com.sakina.app.core.alerts.EventKind
import com.sakina.app.core.calendar.CalendarKind
import com.sakina.app.core.calendar.CalendarPrefs
import com.sakina.app.core.calendar.DateConvert
import com.sakina.app.core.calendar.FastingDays
import com.sakina.app.core.calendar.FastingKind
import com.sakina.app.core.calendar.FastingReminderRules
import com.sakina.app.core.calendar.FastingReminders
import com.sakina.app.core.calendar.HijriDate
import com.sakina.app.core.calendar.Intervals
import com.sakina.app.core.calendar.MonthGrid
import com.sakina.app.core.calendar.MonthRef
import com.sakina.app.core.calendar.PersonalOccasion
import com.sakina.app.core.calendar.PersonalOccasionOps
import com.sakina.app.core.calendar.UmmAlQura
import com.sakina.app.core.prayer.CalcConfig
import com.sakina.app.core.prayer.DuhaWindow
import com.sakina.app.core.prayer.FastStatus
import com.sakina.app.core.prayer.GeoPoint
import com.sakina.app.core.prayer.Nights
import com.sakina.app.core.prayer.PrayerKey
import com.sakina.app.core.prayer.PrayerSchedule
import com.sakina.app.core.prayer.RamadanPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.Period
import java.time.ZoneId
import java.time.ZonedDateTime

class CalendarTest {
    private val zone = ZoneId.of("Asia/Riyadh")
    private val schedule = PrayerSchedule(GeoPoint(24.7136, 46.6753), zone, CalcConfig())
    private fun at(d: LocalDate, h: Int, m: Int) = ZonedDateTime.of(d, LocalTime.of(h, m), zone).toInstant()

    @Test
    fun hijriMonthGridHasSixWeeksAndTheWholeMonth() {
        for (offset in -2..2) {
            for (m in 1..12) {
                val ref = MonthRef(CalendarKind.HIJRI, 1447, m)
                val grid = MonthGrid.build(ref, offset, DayOfWeek.SATURDAY)!!
                assertEquals(42, grid.size)
                assertEquals(DayOfWeek.SATURDAY, grid.first().date.dayOfWeek)
                val inMonth = grid.filter { it.inMonth }
                assertEquals(UmmAlQura.monthLength(1447, m), inMonth.size)
                assertEquals((1..inMonth.size).toList(), inMonth.map { it.hijri!!.day })
                assertTrue(inMonth.all { it.hijri!!.month == m && it.hijri!!.year == 1447 })
                // Consecutive days.
                grid.zipWithNext().forEach { (a, b) -> assertEquals(a.date.plusDays(1), b.date) }
            }
        }
        // The display offset moves the Hijri month to an earlier civil day.
        val r0 = MonthGrid.range(MonthRef(CalendarKind.HIJRI, 1447, 9), 0)!!
        val r1 = MonthGrid.range(MonthRef(CalendarKind.HIJRI, 1447, 9), 1)!!
        assertEquals(r0.start.minusDays(1), r1.start)
        val g = MonthGrid.build(MonthRef(CalendarKind.GREGORIAN, 2026, 2), 0, DayOfWeek.MONDAY)!!
        assertEquals(28, g.count { it.inMonth })
        assertEquals(DayOfWeek.MONDAY, g.first().date.dayOfWeek)
        assertEquals(MonthRef(CalendarKind.HIJRI, 1448, 1), MonthRef(CalendarKind.HIJRI, 1447, 12).plus(1))
        assertEquals(MonthRef(CalendarKind.GREGORIAN, 2025, 12), MonthRef(CalendarKind.GREGORIAN, 2026, 1).plus(-1))
        assertEquals(13, MonthRef(CalendarKind.HIJRI, 1447, 1).distanceTo(MonthRef(CalendarKind.HIJRI, 1448, 2)))
        assertFalse(MonthRef(CalendarKind.HIJRI, 1601, 1).isSupported)
        assertNull(MonthGrid.build(MonthRef(CalendarKind.HIJRI, 1601, 1), 0, DayOfWeek.SATURDAY))
    }

    @Test
    fun conversionRoundTripsAndRejectsMissingDays() {
        var d = LocalDate.of(2020, 1, 1)
        while (d.year < 2031) {
            for (offset in listOf(-1, 0, 2)) {
                val h = DateConvert.gregorianToHijri(d, offset)!!
                assertEquals(d, DateConvert.hijriToGregorian(h, offset))
            }
            d = d.plusDays(3)
        }
        val short = (1..12).first { UmmAlQura.monthLength(1447, it) == 29 }
        assertNull(DateConvert.hijriToGregorian(HijriDate(1447, short, 30), 0))
        assertNull(DateConvert.hijriToGregorian(HijriDate(1447, 1, 0), 0))
    }

    @Test
    fun intervals() {
        val i = Intervals.between(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 1, 1), 0)
        assertEquals(59, i.days)
        assertEquals(8, i.weeks)
        assertEquals(3, i.weekDays)
        assertEquals(Period.of(0, 2, 0), i.gregorian)
        val a = DateConvert.hijriToGregorian(HijriDate(1447, 7, 10), 0)!!
        val b = DateConvert.hijriToGregorian(HijriDate(1448, 9, 12), 0)!!
        assertEquals(Triple(1, 2, 2), Intervals.between(a, b, 0).hijri)
    }

    private fun civil(h: HijriDate) = DateConvert.hijriToGregorian(h, 0)!!

    @Test
    fun fastingDaysAndForbiddenDays() {
        assertEquals(listOf(FastingKind.FORBIDDEN_EID), FastingDays.on(civil(HijriDate(1447, 10, 1)), 0))
        assertEquals(listOf(FastingKind.FORBIDDEN_EID), FastingDays.on(civil(HijriDate(1447, 12, 10)), 0))
        // The 13th of Dhul-Hijjah is a day of Tashreeq, not a white day.
        assertEquals(listOf(FastingKind.FORBIDDEN_TASHREEQ), FastingDays.on(civil(HijriDate(1447, 12, 13)), 0))
        assertEquals(listOf(FastingKind.RAMADAN), FastingDays.on(civil(HijriDate(1447, 9, 14)), 0))
        assertTrue(FastingKind.ARAFAH in FastingDays.on(civil(HijriDate(1447, 12, 9)), 0))
        assertTrue(FastingKind.ASHURA in FastingDays.on(civil(HijriDate(1448, 1, 10)), 0))
        assertTrue(FastingKind.TASUA in FastingDays.on(civil(HijriDate(1448, 1, 9)), 0))
        assertTrue(FastingKind.WHITE_DAY in FastingDays.on(civil(HijriDate(1448, 2, 14)), 0))
        assertTrue(FastingKind.SHAWWAL in FastingDays.on(civil(HijriDate(1447, 10, 2)), 0))
        val monday = LocalDate.of(2026, 10, 19)
        assertTrue(FastingKind.MONDAY in FastingDays.on(monday, 0))
        // With a display offset the white days move with the shown date.
        val shown13 = DateConvert.hijriToGregorian(HijriDate(1448, 2, 13), 1)!!
        assertTrue(FastingKind.WHITE_DAY in FastingDays.on(shown13, 1))
        // Reminders skip Ramadan and forbidden days.
        val prefs = FastingReminders(mondayThursday = true, whiteDays = true)
        assertEquals(listOf(FastingKind.MONDAY), FastingReminderRules.kindsFor(monday, prefs, 0))
        val ramadanMonday = (1..30).map { civil(HijriDate(1447, 9, minOf(it, UmmAlQura.monthLength(1447, 9)!!))) }.first { it.dayOfWeek == DayOfWeek.MONDAY }
        assertTrue(FastingReminderRules.kindsFor(ramadanMonday, prefs, 0).isEmpty())
        assertTrue(FastingReminderRules.kindsFor(civil(HijriDate(1447, 12, 13)), prefs, 0).isEmpty())
        assertTrue(FastingReminderRules.kindsFor(monday, FastingReminders(whiteDays = true), 0).isEmpty())
    }

    @Test
    fun personalOccasionsRepeat() {
        val leap = PersonalOccasion("a", "x", epochDay = LocalDate.of(2024, 2, 29).toEpochDay(), repeat = PersonalOccasion.Repeat.YEARLY_GREGORIAN)
        assertTrue(leap.occursOn(LocalDate.of(2025, 2, 28), 0))
        assertTrue(leap.occursOn(LocalDate.of(2028, 2, 29), 0))
        assertFalse(leap.occursOn(LocalDate.of(2028, 2, 28), 0))
        assertFalse(leap.occursOn(LocalDate.of(2023, 2, 28), 0)) // before it was set
        val short = (1..12).first { UmmAlQura.monthLength(1448, it) == 29 }
        val start = civil(HijriDate(1447, short, 1))
        val h30 = PersonalOccasion("b", "y", epochDay = start.toEpochDay(), repeat = PersonalOccasion.Repeat.YEARLY_HIJRI, hijriMonth = short, hijriDay = 30)
        assertTrue(h30.occursOn(civil(HijriDate(1448, short, 29)), 0))
        val once = PersonalOccasion("c", "z", epochDay = LocalDate.of(2026, 10, 20).toEpochDay())
        assertTrue(once.occursOn(LocalDate.of(2026, 10, 20), 0))
        assertFalse(once.occursOn(LocalDate.of(2027, 10, 20), 0))
        var list = PersonalOccasionOps.upsert(emptyList(), once)
        list = PersonalOccasionOps.upsert(list, leap)
        list = PersonalOccasionOps.upsert(list, once.copy(title = "z2"))
        assertEquals(listOf("z2", "x"), list.map { it.title })
        val removed = PersonalOccasionOps.remove(list, "c")
        assertEquals(listOf("z2", "x"), PersonalOccasionOps.restore(removed, list[0], 0).map { it.title })
        assertEquals(PersonalOccasion.DEFAULT_NOTIFY_MINUTE, once.notifyMinute)
    }

    @Test
    fun calendarAlertsInThePlan() {
        val today = LocalDate.of(2026, 10, 14) // a Wednesday
        val o = PersonalOccasion("o1", "Visit", epochDay = today.plusDays(2).toEpochDay(), minuteOfDay = 18 * 60, notify = true)
        val cal = CalendarPrefs(occasions = listOf(o, o.copy(id = "o2", notify = false)), fasting = FastingReminders(mondayThursday = true))
        val plan = AlertPlanner.plan(AlertSettings(), schedule, zone, at(today, 12, 0), calendar = cal, hijriOffset = 0)
        val occ = plan.filter { it.kind == EventKind.OCCASION }
        assertEquals(1, occ.size)
        assertEquals(at(today.plusDays(2), 18, 0), occ[0].at)
        assertEquals("o1", occ[0].subject)
        // Thursday the 15th: reminded on Wednesday evening at 21:00.
        val fast = plan.filter { it.kind == EventKind.FASTING }
        val thu = fast.first { it.subject!!.startsWith(today.plusDays(1).toString()) }
        assertEquals(at(today, 21, 0), thu.at)
        assertTrue(thu.subject!!.endsWith("|THURSDAY"))
        assertTrue(fast.all { e -> val d = LocalDate.parse(e.subject!!.substringBefore('|')); d.dayOfWeek == DayOfWeek.MONDAY || d.dayOfWeek == DayOfWeek.THURSDAY })
        assertEquals(Decision.FIRE, AlertPlanner.decide(thu, thu.at.plusSeconds(60), emptySet(), null))
        assertEquals(Decision.SKIP_QUIET, AlertPlanner.decide(thu, thu.at, emptySet(), thu.at.plusSeconds(600)))
        // Same-day reminders fall on the fasting day itself; nothing without opt-in.
        val same = AlertPlanner.plan(AlertSettings(), schedule, zone, at(today, 12, 0), calendar = cal.copy(fasting = FastingReminders(mondayThursday = true, eveningBefore = false, minuteOfDay = 7 * 60)))
        assertTrue(same.filter { it.kind == EventKind.FASTING }.all { LocalDate.parse(it.subject!!.substringBefore('|')) == it.date })
        assertTrue(AlertPlanner.plan(AlertSettings(), schedule, zone, at(today, 12, 0), calendar = CalendarPrefs()).isEmpty())
    }

    @Test
    fun nightMidnightAndLastThird() {
        val d = LocalDate.of(2026, 10, 14)
        val n = Nights.of(schedule, d)!!
        assertEquals(schedule.day(d)[PrayerKey.MAGHRIB].instant, n.maghrib)
        assertEquals(schedule.day(d.plusDays(1))[PrayerKey.FAJR].instant, n.fajr)
        assertEquals(n.length.dividedBy(2), Duration.between(n.maghrib, n.midnight))
        assertTrue(n.lastThird.isAfter(n.midnight) && n.lastThird.isBefore(n.fajr))
        // Before Fajr the current night is the one that began the evening before.
        assertEquals(d.minusDays(1), Nights.currentOrNext(schedule, at(d, 2, 0))!!.eveningOf)
        assertEquals(d, Nights.currentOrNext(schedule, at(d, 12, 0))!!.eveningOf)
        assertEquals(d, Nights.currentOrNext(schedule, at(d, 23, 0))!!.eveningOf)
        // A night with a daylight-saving change is measured in real time (New York, 8 March 2026).
        val ny = ZoneId.of("America/New_York")
        val nys = PrayerSchedule(GeoPoint(40.7128, -74.006), ny, CalcConfig())
        val dst = Nights.of(nys, LocalDate.of(2026, 3, 7))!!
        val wall = Duration.between(dst.maghrib.atZone(ny).toLocalDateTime(), dst.fajr.atZone(ny).toLocalDateTime())
        assertEquals(wall.minusHours(1), dst.length)
    }

    @Test
    fun duhaWindow() {
        val day = schedule.day(LocalDate.of(2026, 10, 14))
        val w = DuhaWindow.of(day, 15, 10)!!
        assertEquals(day[PrayerKey.SUNRISE].instant!!.plusSeconds(900), w.start)
        assertEquals(day[PrayerKey.DHUHR].instant!!.minusSeconds(600), w.end)
        assertEquals(day[PrayerKey.SUNRISE].instant!!.plusSeconds(45 * 60L), DuhaWindow.of(day, 999, 10)!!.start)
    }

    @Test
    fun ramadanImsakiyaAndLastTen() {
        val m = RamadanPlan.month(1447, schedule)!!
        assertEquals(UmmAlQura.monthLength(1447, 9), m.length)
        assertEquals(UmmAlQura.toGregorian(HijriDate(1447, 9, 1)), m.first)
        assertTrue(m.days.all { it.fajr != null && it.maghrib != null && it.fajr!!.isBefore(it.maghrib) })
        val ten = m.lastTen()
        assertEquals(m.length - 20, ten.size)
        assertEquals(21, ten.first().night)
        assertEquals(m.days[19].date, ten.first().beginsEvening)
        assertEquals(m.days[19].maghrib, ten.first().begins)
        assertEquals(listOf(21, 23, 25, 27, 29), ten.filter { it.odd }.map { it.night })
        // Status through a Ramadan day.
        val d5 = m.days[4]
        assertEquals(FastStatus.UntilFajr(d5.fajr!!, 5), RamadanPlan.status(m, schedule, d5.fajr!!.minusSeconds(60)))
        assertEquals(FastStatus.UntilMaghrib(d5.maghrib!!, 5), RamadanPlan.status(m, schedule, d5.maghrib!!.minusSeconds(60)))
        assertEquals(FastStatus.UntilFajr(m.days[5].fajr!!, 6), RamadanPlan.status(m, schedule, d5.maghrib!!.plusSeconds(60)))
        val eve = schedule.day(m.first.minusDays(1))[PrayerKey.MAGHRIB].instant!!
        assertEquals(FastStatus.UntilFajr(m.days[0].fajr!!, 1), RamadanPlan.status(m, schedule, eve.plusSeconds(60)))
        assertEquals(FastStatus.NotYet(m.first), RamadanPlan.status(m, schedule, eve.minusSeconds(3600 * 24)))
        assertEquals(FastStatus.Ended, RamadanPlan.status(m, schedule, m.days.last().maghrib!!.plusSeconds(60)))
        // Outside Ramadan the next one is chosen; inside, the current one.
        assertEquals(1448, RamadanPlan.currentOrNext(schedule, LocalDate.of(2026, 10, 14))!!.year)
        assertEquals(1447, RamadanPlan.currentOrNext(schedule, m.days[10].date)!!.year)
        assertNotNull(RamadanPlan.currentOrNext(schedule, m.last.plusDays(1)))
    }
}
