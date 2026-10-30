package com.sakina.app.core

import com.sakina.app.core.prayer.CalcConfig
import com.sakina.app.core.prayer.GeoPoint
import com.sakina.app.core.prayer.PrayerKey
import com.sakina.app.core.prayer.PrayerSchedule
import com.sakina.app.core.widgets.BlockMeasure
import com.sakina.app.core.widgets.ContentItem
import com.sakina.app.core.widgets.ContentMode
import com.sakina.app.core.widgets.ContentPack
import com.sakina.app.core.widgets.ContentPicker
import com.sakina.app.core.widgets.Contrast
import com.sakina.app.core.widgets.DawnDuskCard
import com.sakina.app.core.widgets.Dial
import com.sakina.app.core.widgets.Fit
import com.sakina.app.core.widgets.FitLine
import com.sakina.app.core.widgets.RamadanContext
import com.sakina.app.core.widgets.TextMeasure
import com.sakina.app.core.widgets.WidgetKind
import com.sakina.app.core.widgets.WidgetMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil

class WidgetTest {
    private val riyadh = ZoneId.of("Asia/Riyadh")
    private val schedule = PrayerSchedule(GeoPoint(24.7136, 46.6753), riyadh, CalcConfig())

    @Test
    fun remainingIsRoundedUpAndNeverNegative() {
        val at = Instant.parse("2026-10-14T12:15:00Z")
        assertEquals(1, WidgetMath.minutesLeft(at.minusSeconds(1), at))
        assertEquals(1, WidgetMath.minutesLeft(at.minusSeconds(60), at))
        assertEquals(2, WidgetMath.minutesLeft(at.minusSeconds(61), at))
        assertEquals(0, WidgetMath.minutesLeft(at, at))
        assertEquals(0, WidgetMath.minutesLeft(at.plusSeconds(3600), at))
    }

    @Test
    fun snapshotFollowsTheNextPrayerAndMovesToTomorrowAfterIsha() {
        // 16:20 in Riyadh on 14 Oct 2026: Maghrib is next.
        val afternoon = WidgetMath.snapshot(schedule, Instant.parse("2026-10-14T13:20:00Z"))
        assertEquals(PrayerKey.MAGHRIB, afternoon.next!!.key)
        assertEquals(LocalDate.of(2026, 10, 14), afternoon.date)
        assertEquals(6, afternoon.times.size)
        assertEquals(listOf(PrayerKey.MAGHRIB), afternoon.times.filter { it.isNext }.map { it.key })
        assertEquals(WidgetMath.minutesLeft(afternoon.taken, afternoon.next!!.instant), afternoon.minutesLeft)
        assertTrue(afternoon.complete)
        // 21:30: the next prayer is tomorrow's Fajr and the table shows tomorrow.
        val night = WidgetMath.snapshot(schedule, Instant.parse("2026-10-14T18:30:00Z"))
        assertEquals(PrayerKey.FAJR, night.next!!.key)
        assertEquals(LocalDate.of(2026, 10, 15), night.date)
        assertTrue(night.times.first { it.key == PrayerKey.FAJR }.isNext)
        // At the exact prayer time the snapshot has already moved on.
        val maghrib = afternoon.next!!.instant
        val at = WidgetMath.snapshot(schedule, maghrib)
        assertEquals(PrayerKey.ISHA, at.next!!.key)
        assertTrue(at.minutesLeft!! >= 0)
    }

    @Test
    fun polarDayIsReportedIncomplete() {
        val tromso = PrayerSchedule(GeoPoint(69.6492, 18.9553), ZoneId.of("Europe/Oslo"), CalcConfig())
        val s = WidgetMath.snapshot(tromso, Instant.parse("2026-06-21T10:00:00Z"))
        assertFalse(s.complete)
    }

    @Test
    fun refreshTimes() {
        val now = Instant.parse("2026-10-14T13:20:35Z")
        assertEquals(Instant.parse("2026-10-14T13:21:01Z"), WidgetMath.nextMinute(now))
        assertEquals(Instant.parse("2026-10-14T21:00:01Z"), WidgetMath.nextMidnight(now, riyadh))
        assertEquals(WidgetMath.nextMinute(now), WidgetMath.nextChange(WidgetKind.NEXT, now, riyadh, null))
        val maghrib = Instant.parse("2026-10-14T14:47:00Z")
        assertEquals(maghrib.plusSeconds(1), WidgetMath.nextChange(WidgetKind.ANALOG, now, riyadh, maghrib))
        assertEquals(WidgetMath.nextMidnight(now, riyadh), WidgetMath.nextChange(WidgetKind.CONTENT, now, riyadh, null))
        assertTrue(WidgetKind.entries.count { it.showsRemaining } == 7)
        assertEquals(WidgetKind.FAJR_MAGHRIB, WidgetKind.of("fajr_maghrib"))
        assertEquals(14, WidgetKind.entries.map { it.id }.toSet().size)
        assertFalse(WidgetKind.CALENDAR.needsPlace)
        assertEquals(WidgetMath.nextMidnight(now, riyadh), WidgetMath.nextChange(WidgetKind.CALENDAR, now, riyadh, null))
    }

    @Test
    fun progressFromTheLastPrayerToTheNext() {
        // 16:20 in Riyadh: Asr has passed, Maghrib is next; the time has gone part of the way.
        val now = Instant.parse("2026-10-14T13:20:00Z")
        val s = WidgetMath.snapshot(schedule, now)
        assertEquals(PrayerKey.MAGHRIB, s.next!!.key)
        val asr = schedule.day(LocalDate.of(2026, 10, 14))[PrayerKey.ASR].instant!!
        assertEquals(asr, s.previous)
        val expected = java.time.Duration.between(asr, now).seconds.toFloat() / java.time.Duration.between(asr, s.next!!.instant).seconds
        assertEquals(expected, s.progress!!, 0.001f)
        assertTrue(s.progress!! in 0.3f..0.9f)
        // Just after Isha the line starts again; before Fajr it runs from last night's Isha.
        assertEquals(0f, WidgetMath.progress(asr, s.next!!.instant, asr)!!, 0f)
        assertEquals(1f, WidgetMath.progress(asr, s.next!!.instant, s.next!!.instant.plusSeconds(30))!!, 0f)
        assertNull(WidgetMath.progress(null, s.next!!.instant, now))
        val night = WidgetMath.snapshot(schedule, Instant.parse("2026-10-14T23:30:00Z"))
        assertEquals(PrayerKey.FAJR, night.next!!.key)
        assertTrue(night.progress!! in 0.01f..0.99f)
    }

    @Test
    fun dawnDuskCardAndRamadanContext() {
        // 16:20 on 14 Oct 2026: today's Fajr and Maghrib; Ramadan 1448 starts on 8 Feb 2027.
        val d = DawnDuskCard.of(schedule, Instant.parse("2026-10-14T13:20:00Z"), 0)
        assertEquals(LocalDate.of(2026, 10, 14), d.date)
        assertNotNull(d.fajr); assertNotNull(d.maghrib)
        val until = d.ramadan as RamadanContext.DaysUntil
        assertEquals(LocalDate.of(2027, 2, 8), until.starts)
        assertEquals(117, until.days)
        assertEquals(1448, d.hijri!!.year)
        // After Maghrib the card shows tomorrow.
        assertEquals(LocalDate.of(2026, 10, 15), DawnDuskCard.of(schedule, Instant.parse("2026-10-14T18:30:00Z"), 0).date)
        // 17:30 on 12 Ramadan 1447 (1 Mar 2026): fasting until Maghrib.
        val r = DawnDuskCard.of(schedule, Instant.parse("2026-03-01T14:30:00Z"), 0).ramadan as RamadanContext.Fasting
        assertEquals(12, r.day)
        assertTrue(r.minutesLeft in 1..120)
        // 21:00 that day: before the next fast's Fajr.
        val b = DawnDuskCard.of(schedule, Instant.parse("2026-03-01T18:00:00Z"), 0).ramadan as RamadanContext.BeforeFajr
        assertEquals(13, b.day)
    }

    @Test
    fun dialAnglesAndClockZone() {
        val t = Instant.parse("2026-10-14T12:15:00Z") // 15:15 in Riyadh
        assertEquals(97.5f, Dial.angle(t, riyadh), 0.01f)
        assertEquals(0f, Dial.angle(Instant.parse("2026-10-14T09:00:00Z"), riyadh), 0.01f)
        val london = ZoneId.of("Europe/London")
        assertEquals(riyadh, Dial.clockZone(31, riyadh, london))
        assertEquals(london, Dial.clockZone(30, riyadh, london))
        assertTrue(Dial.showsDeviceClock(26, riyadh, london, t))
        assertFalse(Dial.showsDeviceClock(34, riyadh, london, t))
        assertFalse(Dial.showsDeviceClock(26, riyadh, ZoneId.of("Asia/Kuwait"), t))
    }

    @Test
    fun contrastGuard() {
        val white = 0xFFFFFFFF.toInt(); val black = 0xFF000000.toInt()
        assertEquals(21.0, Contrast.ratio(white, black), 0.01)
        assertEquals(1.0, Contrast.ratio(white, white), 0.001)
        // White text needs a substantial dark scrim to stay readable over a white wallpaper.
        val p = Contrast.minScrimPercent(white, black, 4.5)
        assertTrue(p in 50..60)
        assertTrue(Contrast.worst(white, black, p / 100f) >= 4.5)
        assertTrue(Contrast.worst(white, black, (p - 1) / 100f) < 4.5)
        // Large text (3:1) needs less.
        assertTrue(Contrast.minScrimPercent(white, black, 3.0) < p)
        // Text the same colour as its scrim can never pass.
        assertEquals(100, Contrast.minScrimPercent(black, black, 4.5))
    }

    /** Every character is 0.5 em wide; lines are 1.2 em tall. */
    private val fake = TextMeasure { text, size -> text.length * size * 0.5f }

    @Test
    fun fittingShrinksToTheNarrowestLineAndStack() {
        val lines = listOf(FitLine("العصر", 2f), FitLine("١٢:٣٤ م", 1.5f), FitLine("بعد ١ س ٥ د", 1f))
        // Wide and tall: capped at the wanted scale.
        assertEquals(20f, Fit.scale(lines, 1000f, 1000f, 20f, 4f, fake, 1.2f)!!, 0.001f)
        // Narrow: the widest line is "بعد ١ س ٥ د" (11 chars at 1x, 5.5 em) against 100 px → s ≈ 18.18.
        val s = Fit.scale(lines, 100f, 1000f, 40f, 4f, fake, 1.2f)!!
        assertEquals(100f / 5.5f, s, 0.01f)
        // Short: three lines of 2 + 1.5 + 1 = 4.5 em × 1.2 must fit 54 px → s ≤ 10.
        assertEquals(10f, Fit.scale(lines, 1000f, 54f, 40f, 4f, fake, 1.2f)!!, 0.01f)
        // Columns share the width; padding is taken from each column.
        val strip = listOf(FitLine(listOf("الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"), 1f, padPx = 4f))
        val cs = Fit.scale(strip, 300f, 100f, 50f, 4f, fake, 1.2f)!!
        assertEquals((300f / 6 - 4f) / 3f, cs, 0.01f)
        // Inline texts add up: "العصر" (5) + "١٢:٣٤ م" (7) at 0.5 em plus a 10 px gap must fit 70 px → s ≤ 10.
        assertEquals(10f, Fit.scale(listOf(FitLine(listOf("العصر", "١٢:٣٤ م"), 1f, padPx = 10f, inline = true)), 70f, 100f, 40f, 4f, fake, 1.2f)!!, 0.01f)
        // A line can bring its own font: twice as wide here.
        val wide = TextMeasure { text, size -> text.length * size }
        assertEquals(10f, Fit.scale(listOf(FitLine("abcde", 1f, measure = wide)), 50f, 100f, 40f, 4f, fake, 1.2f)!!, 0.01f)
        // Nothing fits: null, so the caller can drop the line.
        assertNull(Fit.scale(lines, 10f, 10f, 40f, 8f, fake, 1.2f))
    }

    @Test
    fun blockFitting() {
        val m = BlockMeasure { text, width, size -> ceil(text.length * size * 0.5f / width) * size * 1.2f }
        val text = "x".repeat(40)
        // At size 10 the text is 200 px wide: two lines of 100 px → 24 px tall.
        val size = Fit.block(text, 100f, 24f, 30f, 6f, m)!!
        assertTrue(size >= 9.9f && size <= 10.01f)
        assertNull(Fit.block(text, 10f, 10f, 30f, 8f, m))
        assertEquals(30f, Fit.block("x", 100f, 100f, 30f, 6f, m)!!, 0.001f)
    }

    @Test
    fun contentPickerRotatesDailyAndSkipsItemsThatDoNotFit() {
        val pack = ContentPack(items = listOf(
            ContentItem("dhikr", "a", "aaa", "", ""), ContentItem("dhikr", "b", "bbbbbbbbbbbb", "", ""),
            ContentItem("ayah", "1:1", "x", "", ""), ContentItem("ayah", "2:255", "y".repeat(50), "", ""),
        ))
        val mixed = ContentPicker.candidates(pack, ContentMode.MIXED)
        assertEquals(listOf("1:1", "a", "2:255", "b"), mixed.map { it.ref })
        assertEquals(listOf("a", "b"), ContentPicker.candidates(pack, ContentMode.ADHKAR).map { it.ref })
        val day = LocalDate.ofEpochDay(8) // 8 mod 4 = 0
        assertEquals("1:1", ContentPicker.pick(pack, ContentMode.MIXED, day, 0) { true }!!.ref)
        assertEquals("a", ContentPicker.pick(pack, ContentMode.MIXED, day.plusDays(1), 0) { true }!!.ref)
        assertEquals("a", ContentPicker.pick(pack, ContentMode.MIXED, day, 1) { true }!!.ref)
        // Long items are skipped when they do not fit.
        assertEquals("b", ContentPicker.pick(pack, ContentMode.MIXED, day, 2) { it.text.length < 20 }!!.ref)
        assertEquals("1:1", ContentPicker.pick(pack, ContentMode.VERSES, day, 1) { it.text.length < 20 }!!.ref)
        assertNull(ContentPicker.pick(pack, ContentMode.MIXED, day, 0) { false })
        assertNull(ContentPicker.pick(ContentPack(), ContentMode.MIXED, day, 0) { true })
    }

    @Test fun kashidaGoesBeforeTheLastJoinedLetter() {
        val t = com.sakina.app.core.widgets.Kashida
        assertEquals("الاثنيـــن", t.stretch("الاثنين", 3))
        assertEquals("السبـــت", t.stretch("السبت", 3))
        assertEquals("الأحـــد", t.stretch("الأحد", 3))
        assertEquals("الثلاثـــاء", t.stretch("الثلاثاء", 3))
        assertEquals("الأربعـــاء", t.stretch("الأربعاء", 3))
        assertEquals("الخميـــس", t.stretch("الخميس", 3))
        assertEquals("الجمعـــة", t.stretch("الجمعة", 3))
        assertEquals("MONDAY", t.stretch("MONDAY", 3))
        assertEquals(null, t.point("دار"))
    }

    @Test fun kashidaFillsTheWidth() {
        val t = com.sakina.app.core.widgets.Kashida
        // Each letter 10 px, each tatweel 4 px: 7 letters (70) + n*4 <= 100 gives n = 7.
        val w: (String) -> Float = { s -> s.sumOf { c -> if (c == t.TATWEEL) 4.0 else 10.0 }.toFloat() }
        assertEquals("الاثني" + "ـ".repeat(7) + "ن", t.fill("الاثنين", 100f, width = w))
        assertEquals("الاثنين", t.fill("الاثنين", 50f, width = w))
        assertEquals(80, t.fill("الاثنين", 10_000f, width = w).count { it == t.TATWEEL })
    }

    @Test fun kashidaSharedOverAPhrase() {
        val t = com.sakina.app.core.widgets.Kashida
        // The divine name is never stretched.
        assertEquals("سبحـان الله", t.stretchPhrase("سبحان الله", 1))
        assertEquals("سبحـــان الله", t.stretchPhrase("سبحان الله", 3))
        assertEquals("الحمـد لله", t.stretchPhrase("الحمد لله", 1))
        assertEquals("أستغفـر الله", t.stretchPhrase("أستغفر الله", 1))
        val w: (String) -> Float = { s -> s.sumOf { c -> if (c == t.TATWEEL) 4.0 else if (c == ' ') 5.0 else 10.0 }.toFloat() }
        // 9 letters + space = 95; 105 leaves room for two tatweel.
        assertEquals(2, t.fillPhrase("سبحان الله", 105f, width = w).count { it == t.TATWEEL })
    }
}
