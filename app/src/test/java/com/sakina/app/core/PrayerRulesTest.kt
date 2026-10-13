package com.sakina.app.core

import com.sakina.app.core.calendar.HijriDate
import com.sakina.app.core.calendar.UmmAlQura
import com.sakina.app.core.prayer.CalcConfig
import com.sakina.app.core.prayer.CalcMethod
import com.sakina.app.core.prayer.DayIssue
import com.sakina.app.core.prayer.GeoPoint
import com.sakina.app.core.prayer.PrayerEngine
import com.sakina.app.core.prayer.PrayerKey
import com.sakina.app.core.prayer.PrayerOffsets
import com.sakina.app.core.prayer.PrayerSchedule
import com.sakina.app.core.prayer.TimeStatus
import com.sakina.app.core.prayer.UmmAlQuraIsha
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class PrayerRulesTest {
    private val riyadh = GeoPoint(24.7136, 46.6753)
    private val riyadhZone = ZoneId.of("Asia/Riyadh")

    private fun ishaGap(date: LocalDate, policy: UmmAlQuraIsha): Long {
        val d = PrayerEngine.compute(date, riyadh, riyadhZone, CalcConfig(ummAlQuraIsha = policy))
        return Duration.between(d[PrayerKey.MAGHRIB].instant, d[PrayerKey.ISHA].instant).toMinutes()
    }

    @Test
    fun ummAlQuraIshaIs120InRamadanAnd90Outside() {
        val firstOfRamadan = UmmAlQura.toGregorian(HijriDate(1447, 9, 1))!!
        val lastOfRamadan = UmmAlQura.toGregorian(HijriDate(1447, 9, UmmAlQura.monthLength(1447, 9)!!))!!
        assertEquals(LocalDate.of(2026, 2, 18), firstOfRamadan)
        assertEquals(90, ishaGap(firstOfRamadan.minusDays(1), UmmAlQuraIsha.AUTO))
        assertEquals(120, ishaGap(firstOfRamadan, UmmAlQuraIsha.AUTO))
        assertEquals(120, ishaGap(lastOfRamadan, UmmAlQuraIsha.AUTO))
        assertEquals(90, ishaGap(lastOfRamadan.plusDays(1), UmmAlQuraIsha.AUTO))
        assertEquals(90, ishaGap(firstOfRamadan, UmmAlQuraIsha.FIXED_90))
        assertEquals(120, ishaGap(firstOfRamadan.minusDays(1), UmmAlQuraIsha.FIXED_120))
        val day = PrayerEngine.compute(firstOfRamadan, riyadh, riyadhZone, CalcConfig())
        assertEquals(true, day.ramadan)
        assertEquals(120, day.ishaIntervalMinutes)
    }

    @Test
    fun offsetsMoveOnlyTheirPrayerAndAreClamped() {
        val date = LocalDate.of(2026, 10, 14)
        val base = PrayerEngine.compute(date, riyadh, riyadhZone, CalcConfig())
        val shifted = PrayerEngine.compute(date, riyadh, riyadhZone, CalcConfig(offsets = PrayerOffsets(fajr = 5, isha = -3)))
        assertEquals(5, Duration.between(base[PrayerKey.FAJR].instant, shifted[PrayerKey.FAJR].instant).toMinutes())
        assertEquals(-3, Duration.between(base[PrayerKey.ISHA].instant, shifted[PrayerKey.ISHA].instant).toMinutes())
        assertEquals(base[PrayerKey.DHUHR].instant, shifted[PrayerKey.DHUHR].instant)
        assertEquals(base[PrayerKey.MAGHRIB].instant, shifted[PrayerKey.MAGHRIB].instant)
        assertEquals(60, PrayerOffsets().with(PrayerKey.ASR, 500)[PrayerKey.ASR])
        assertEquals(-60, PrayerOffsets(dhuhr = -90)[PrayerKey.DHUHR])
    }

    @Test
    fun timesAreInOrderAndLocalHoursAreSensibleAroundTheWorld() {
        val places = listOf(
            Triple(GeoPoint(-36.8485, 174.7633), "Pacific/Auckland", "Auckland"),
            Triple(GeoPoint(1.8721, -157.4278), "Pacific/Kiritimati", "Kiritimati"),
            Triple(GeoPoint(-14.2756, -170.7020), "Pacific/Pago_Pago", "Pago Pago"),
            Triple(GeoPoint(21.3069, -157.8583), "Pacific/Honolulu", "Honolulu"),
            Triple(GeoPoint(-13.8333, -171.7500), "Pacific/Apia", "Apia"),
            Triple(GeoPoint(64.1466, -21.9426), "Atlantic/Reykjavik", "Reykjavik"),
            Triple(GeoPoint(24.7136, 46.6753), "Asia/Riyadh", "Riyadh"),
        )
        for ((point, zoneId, name) in places) {
            val zone = ZoneId.of(zoneId)
            var d = LocalDate.of(2026, 1, 1)
            while (d.year == 2026) {
                val day = PrayerEngine.compute(d, point, zone, CalcConfig(method = CalcMethod.MUSLIM_WORLD_LEAGUE))
                val available = day.times.filter { it.available }
                available.zipWithNext().forEach { (a, b) -> assertTrue("$name $d ${a.key}<${b.key}", a.instant!! < b.instant!!) }
                val dhuhr = day[PrayerKey.DHUHR].instant!!.atZone(zone)
                assertEquals("$name $d dhuhr date", d, dhuhr.toLocalDate())
                assertTrue("$name $d dhuhr hour ${dhuhr.hour}", dhuhr.hour in 11..14)
                d = d.plusDays(5)
            }
        }
    }

    @Test
    fun daylightSavingIsFollowed() {
        val ny = GeoPoint(40.7128, -74.0060)
        val zone = ZoneId.of("America/New_York")
        val before = PrayerEngine.compute(LocalDate.of(2026, 3, 7), ny, zone, CalcConfig(method = CalcMethod.NORTH_AMERICA))
        val after = PrayerEngine.compute(LocalDate.of(2026, 3, 8), ny, zone, CalcConfig(method = CalcMethod.NORTH_AMERICA))
        assertEquals(12, before[PrayerKey.DHUHR].instant!!.atZone(zone).hour)
        assertEquals(13, after[PrayerKey.DHUHR].instant!!.atZone(zone).hour)
        val gap = Duration.between(before[PrayerKey.DHUHR].instant, after[PrayerKey.DHUHR].instant).toMinutes()
        assertTrue("one solar day apart, got $gap", gap in 1438..1442)
    }

    @Test
    fun highLatitudeRuleIsMarkedAndPolarDaysAreWithheld() {
        val oslo = PrayerEngine.compute(LocalDate.of(2026, 6, 21), GeoPoint(59.9139, 10.7522), ZoneId.of("Europe/Oslo"),
            CalcConfig(method = CalcMethod.MUSLIM_WORLD_LEAGUE))
        assertEquals(TimeStatus.HIGH_LATITUDE_RULE, oslo[PrayerKey.FAJR].status)
        assertEquals(TimeStatus.HIGH_LATITUDE_RULE, oslo[PrayerKey.ISHA].status)
        assertTrue(oslo.allAvailable)

        val tromso = GeoPoint(69.6492, 18.9553)
        val zone = ZoneId.of("Europe/Oslo")
        val summer = PrayerEngine.compute(LocalDate.of(2026, 6, 21), tromso, zone, CalcConfig(method = CalcMethod.MUSLIM_WORLD_LEAGUE))
        assertTrue(DayIssue.NO_SUNSET in summer.issues)
        assertNull(summer[PrayerKey.SUNRISE].instant)
        assertNull(summer[PrayerKey.MAGHRIB].instant)
        assertNull(summer[PrayerKey.ISHA].instant)
        assertNull(summer[PrayerKey.FAJR].instant)
        assertEquals(TimeStatus.UNAVAILABLE, summer[PrayerKey.MAGHRIB].status)
        assertNotNull(summer[PrayerKey.DHUHR].instant)

        val winter = PrayerEngine.compute(LocalDate.of(2026, 12, 21), tromso, zone, CalcConfig(method = CalcMethod.MUSLIM_WORLD_LEAGUE))
        assertTrue(DayIssue.NO_SUNRISE in winter.issues)
        assertNull(winter[PrayerKey.SUNRISE].instant)
        assertFalse(winter.allAvailable)
    }

    @Test
    fun scheduleFindsNextAndPreviousAcrossMidnight() {
        val schedule = PrayerSchedule(riyadh, riyadhZone, CalcConfig())
        val date = LocalDate.of(2026, 10, 14)
        val today = schedule.day(date)
        val lateNight = ZonedDateTime.of(date, java.time.LocalTime.of(23, 30), riyadhZone).toInstant()
        val next = schedule.next(lateNight)!!
        assertEquals(PrayerKey.FAJR, next.key)
        assertEquals(date.plusDays(1), next.date)
        val prev = schedule.previous(lateNight)!!
        assertEquals(PrayerKey.ISHA, prev.key)
        assertEquals(today[PrayerKey.ISHA].instant, prev.instant)

        val atDhuhr = today[PrayerKey.DHUHR].instant!!
        assertEquals(PrayerKey.DHUHR, schedule.previous(atDhuhr)!!.key)
        assertEquals(PrayerKey.ASR, schedule.next(atDhuhr)!!.key)
        val sunrise = schedule.nextSunEvent(lateNight, sunrise = true)!!
        assertEquals(date.plusDays(1), sunrise.second)
    }

    @Test
    fun ummAlQuraCalendarKnownDates() {
        assertEquals(LocalDate.of(2024, 3, 11), UmmAlQura.toGregorian(HijriDate(1445, 9, 1)))
        assertEquals(LocalDate.of(2024, 7, 7), UmmAlQura.toGregorian(HijriDate(1446, 1, 1)))
        assertEquals(HijriDate(1448, 5, 3), UmmAlQura.fromGregorian(LocalDate.of(2026, 10, 14)))
        assertEquals(HijriDate(1448, 5, 5), UmmAlQura.displayed(LocalDate.of(2026, 10, 14), 2))
        assertNull(UmmAlQura.fromGregorian(LocalDate.of(2200, 1, 1)))
        var d = LocalDate.of(2025, 1, 1)
        while (d.year < 2028) {
            val h = UmmAlQura.fromGregorian(d)!!
            assertEquals(d, UmmAlQura.toGregorian(h))
            assertTrue(UmmAlQura.monthLength(h.year, h.month) in 29..30)
            d = d.plusDays(1)
        }
    }
}
