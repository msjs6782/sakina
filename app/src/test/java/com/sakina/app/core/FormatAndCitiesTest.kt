package com.sakina.app.core

import com.sakina.app.core.calendar.HijriDate
import com.sakina.app.core.calendar.Occasion
import com.sakina.app.core.calendar.Occasions
import com.sakina.app.core.calendar.UmmAlQura
import com.sakina.app.core.format.Fmt
import com.sakina.app.core.format.Lang
import com.sakina.app.core.location.CityParser
import com.sakina.app.core.location.CitySearch
import com.sakina.app.core.location.SearchText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class FormatAndCitiesTest {
    private val riyadh = ZoneId.of("Asia/Riyadh")

    @Test
    fun timesAndDigits() {
        val t = ZonedDateTime.of(2026, 10, 14, 16, 5, 0, 0, riyadh).toInstant()
        assertEquals("٤:٠٥ م", Fmt(Lang.AR, clock24 = false, arabicDigits = true).time(t, riyadh))
        assertEquals("4:05 م", Fmt(Lang.AR, clock24 = false, arabicDigits = false).time(t, riyadh))
        assertEquals("16:05", Fmt(Lang.EN, clock24 = true, arabicDigits = false).time(t, riyadh))
        assertEquals("4:05 PM", Fmt(Lang.EN, clock24 = false, arabicDigits = false).time(t, riyadh))
        assertEquals("4:05", Fmt(Lang.EN, clock24 = false, arabicDigits = false).time(t, riyadh, suffix = false))
        val midnight = ZonedDateTime.of(2026, 10, 14, 0, 30, 0, 0, riyadh).toInstant()
        assertEquals("12:30 AM", Fmt(Lang.EN, false, false).time(midnight, riyadh))
        val f = Fmt(Lang.EN, false, false)
        assertEquals("01:02:03", f.countdown(Duration.ofSeconds(3723)))
        assertEquals("02:03", f.countdown(Duration.ofSeconds(123)))
        assertEquals("00:00", f.countdown(Duration.ofSeconds(-50)))
        assertEquals("2:05", f.hoursMinutes(Duration.ofMinutes(125)))
        assertEquals("\u2066UTC+3\u2069", f.utcOffset(riyadh, t))
        assertEquals("\u2066UTC+3\u2069", Fmt(Lang.AR, false, true).utcOffset(riyadh, t))
        assertEquals("\u2066UTC+5:30\u2069", f.utcOffset(ZoneId.of("Asia/Kolkata"), t))
        assertEquals("14 October 2026", f.gregorian(LocalDate.of(2026, 10, 14)))
        assertEquals("٣ جمادى الأولى ١٤٤٨ هـ", Fmt(Lang.AR, false, true).hijri(HijriDate(1448, 5, 3)))
    }

    @Test
    fun occasions() {
        val from = LocalDate.of(2026, 10, 14)
        val next = Occasions.next(from, 0)!!
        assertTrue(next.daysAway > 0)
        val ramadan = Occasions.ramadanStart(from, 0)!!
        assertEquals(UmmAlQura.toGregorian(HijriDate(1448, 9, 1)), ramadan)
        assertEquals(ramadan.minusDays(1), Occasions.ramadanStart(from, 1))
        val eid = UmmAlQura.toGregorian(HijriDate(1447, 10, 1))!!
        assertTrue(Occasion.EID_AL_FITR in Occasions.on(eid, 0))
        assertTrue(Occasion.EID_AL_FITR in Occasions.on(eid.minusDays(1), 1))
    }

    @Test
    fun citiesLoadAndSearch() {
        val file = File("src/main/assets/cities.tsv")
        val cities = CityParser.parse(file.readText())
        assertTrue(cities.size > 250)
        assertEquals(cities.size, cities.map { it.id }.toSet().size)
        cities.forEach {
            ZoneId.of(it.zone)
            assertTrue(it.id, it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0)
        }
        assertEquals("riyadh", CitySearch.search(cities, "الرياض").first().id)
        assertEquals("riyadh", CitySearch.search(cities, "رياض").first().id)
        assertEquals("jeddah", CitySearch.search(cities, "جده").first().id)
        assertEquals("jeddah", CitySearch.search(cities, "Jeddah").first().id)
        assertEquals("makkah", CitySearch.search(cities, "مكه").first().id)
        assertEquals("madinah", CitySearch.search(cities, "المدينه").first().id)
        assertEquals("saopaulo", CitySearch.search(cities, "sao paulo").first().id)
        assertTrue(CitySearch.search(cities, "السعودية").size >= 30)
        assertEquals("riyadh", CitySearch.nearest(cities, 24.70, 46.70)!!.id)
        assertEquals(null, CitySearch.nearest(cities, 0.0, -30.0))
        assertNotNull(SearchText.normalize("إسطنبول"))
    }

    @Test
    fun relativeChips() {
        val t = java.time.Instant.parse("2026-10-30T12:05:00Z")
        val r = com.sakina.app.core.format.Relative
        // 58 min 44 s left reads as 58 minutes (as the countdown 00:58:44 does).
        assertEquals(com.sakina.app.core.format.Relative(true, 58), r.of(t.minusSeconds(58 * 60 + 44), t))
        // Under a minute left never reads as zero; just passed reads as now; then whole minutes since.
        assertEquals(com.sakina.app.core.format.Relative(true, 1), r.of(t.minusSeconds(20), t))
        assertTrue(r.of(t, t).isNow)
        assertTrue(r.of(t.plusSeconds(59), t).isNow)
        assertEquals(com.sakina.app.core.format.Relative(false, 549), r.of(t.plus(Duration.ofMinutes(549)).plusSeconds(30), t))
        val ar = Fmt(Lang.AR, clock24 = false, arabicDigits = true)
        assertEquals("٩ س ٩ د", ar.durationShort(r.of(t.plus(Duration.ofMinutes(549)), t).duration))
        assertEquals("٥٨ د", ar.durationShort(r.of(t.minusSeconds(58 * 60 + 44), t).duration))
    }
}
