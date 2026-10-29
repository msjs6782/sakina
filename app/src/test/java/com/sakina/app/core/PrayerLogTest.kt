package com.sakina.app.core

import com.sakina.app.core.alerts.AlertPlanner
import com.sakina.app.core.alerts.AlertSettings
import com.sakina.app.core.alerts.Decision
import com.sakina.app.core.alerts.EventKind
import com.sakina.app.core.alerts.SilencePrefs
import com.sakina.app.core.prayer.PrayerKey
import com.sakina.app.core.prayer.PrayerLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate

class PrayerLogTest {
    private val today = LocalDate.of(2026, 10, 29)

    @Test fun markCountStreak() {
        var log = emptyMap<String, Int>()
        for (k in PrayerKey.prayers) log = PrayerLog.set(log, today.minusDays(1), k, true, today)
        for (k in PrayerKey.prayers) log = PrayerLog.set(log, today.minusDays(2), k, true, today)
        log = PrayerLog.toggle(log, today, PrayerKey.FAJR, today)
        assertTrue(PrayerLog.isDone(log, today, PrayerKey.FAJR))
        assertFalse(PrayerLog.isDone(log, today, PrayerKey.DHUHR))
        assertFalse(PrayerLog.isDone(log, today, PrayerKey.SUNRISE))
        assertEquals(1, PrayerLog.count(log, today))
        // Today is still open: the streak counts the two full days before it.
        assertEquals(2, PrayerLog.streak(log, today))
        for (k in PrayerKey.prayers) log = PrayerLog.set(log, today, k, true, today)
        assertEquals(3, PrayerLog.streak(log, today))
        assertEquals(100, PrayerLog.percent(log, today.minusDays(2), today))
        assertEquals(75, PrayerLog.percent(log, today.minusDays(3), today))
        // Unmarking everything removes the day; very old days are dropped.
        log = PrayerLog.toggle(log, today, PrayerKey.FAJR, today)
        assertEquals(4, PrayerLog.count(log, today))
        val old = PrayerLog.set(mapOf("2024-01-01" to 31), today, PrayerKey.ASR, true, today)
        assertFalse("2024-01-01" in old)
        assertEquals(mapOf(today.toString() to 4), old)
    }

    @Test fun silenceEventsAreNeverLostOnTheWayBack() {
        val s = AlertSettings(silence = SilencePrefs(enabled = true, afterMinutes = 5, minutes = 20, prayers = setOf(PrayerKey.DHUHR)))
        val e = AlertPlanner.plan(s, null, java.time.ZoneId.of("Asia/Riyadh"), java.time.Instant.parse("2026-10-29T08:00:00Z"))
        assertTrue(e.none { it.kind == EventKind.SILENCE_ON }) // no place, no prayer times
        val on = com.sakina.app.core.alerts.AlertEvent("SILENCE_ON:DHUHR:x", EventKind.SILENCE_ON, java.time.Instant.parse("2026-10-29T09:00:00Z"), today, PrayerKey.DHUHR, minutes = 20)
        val off = on.copy(id = "SILENCE_OFF:DHUHR:x", kind = EventKind.SILENCE_OFF, at = on.at.plus(Duration.ofMinutes(20)))
        assertEquals(Decision.FIRE, AlertPlanner.decide(on, on.at.plus(Duration.ofMinutes(10)), emptySet(), null))
        assertEquals(Decision.SKIP_LATE, AlertPlanner.decide(on, on.at.plus(Duration.ofMinutes(25)), emptySet(), null))
        // The way back fires even in quiet mode and hours late.
        assertEquals(Decision.FIRE, AlertPlanner.decide(off, off.at.plus(Duration.ofHours(3)), emptySet(), off.at.plus(Duration.ofHours(5))))
    }

    @Test fun tasbihWeek() {
        val t = com.sakina.app.core.adhkar.TasbihLog
        var log = mapOf("2026-07-01" to 5)
        repeat(3) { log = t.bump(log, today) }
        log = t.bump(log, today.minusDays(2))
        assertFalse("2026-07-01" in log)
        val w = t.week(log, today)
        assertEquals(7, w.size)
        assertEquals(today to 3, w.last())
        assertEquals(1, w[4].second)
    }
}
