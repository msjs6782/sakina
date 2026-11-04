package com.sakina.app.core

import com.sakina.app.core.calendar.FastLog
import com.sakina.app.core.calendar.FastMark
import com.sakina.app.core.prayer.PrayerKey
import com.sakina.app.core.prayer.PrayerLog
import com.sakina.app.core.prayer.PrayerStats
import com.sakina.app.core.prayer.Qada
import com.sakina.app.core.prayer.QadaState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class QadaAndFastsTest {
    private val today = LocalDate.of(2026, 11, 4)

    @Test
    fun qadaCountsDownAndNeverBelowZero() {
        var s = QadaState()
        s = Qada.addDay(s, 3) // three whole days
        s = Qada.addOwed(s, PrayerKey.FAJR, 2)
        assertEquals(5, Qada.remaining(s, PrayerKey.FAJR))
        assertEquals(17, Qada.totalRemaining(s))
        s = Qada.makeUp(s, PrayerKey.FAJR)
        s = Qada.makeUp(s, PrayerKey.ISHA)
        assertEquals(4, Qada.remaining(s, PrayerKey.FAJR))
        assertEquals(15, Qada.totalRemaining(s))
        assertEquals(2 * 100 / 17, Qada.percent(s))
        // Nothing more to make up than owed; a count entered by mistake never goes below what was made up.
        repeat(5) { s = Qada.makeUp(s, PrayerKey.ISHA) }
        assertEquals(0, Qada.remaining(s, PrayerKey.ISHA))
        assertEquals(3, Qada.done(s, PrayerKey.ISHA))
        s = Qada.removeOwed(s, PrayerKey.ISHA)
        assertEquals(3, Qada.owed(s, PrayerKey.ISHA))
        s = Qada.undoMakeUp(s, PrayerKey.ISHA)
        assertEquals(1, Qada.remaining(s, PrayerKey.ISHA))
        // Empty entries are dropped.
        assertTrue(Qada.undoMakeUp(QadaState(), PrayerKey.DHUHR).done.isEmpty())
    }

    @Test
    fun prayerStatistics() {
        var log = emptyMap<String, Int>()
        // Five full days, a gap, then two full days; Fajr missed on one more day.
        for (i in listOf(10, 9, 8, 7, 6, 3, 2)) for (k in PrayerKey.prayers) log = PrayerLog.set(log, today.minusDays(i.toLong()), k, true, today)
        for (k in PrayerKey.prayers.drop(1)) log = PrayerLog.set(log, today.minusDays(1), k, true, today)
        assertEquals(5, PrayerStats.bestStreak(log))
        val per = PrayerStats.perPrayer(log, today.minusDays(9), today)
        assertEquals(60, per[PrayerKey.FAJR]) // 6 of 10 days
        assertEquals(70, per[PrayerKey.ISHA])
        val bars = PrayerStats.daily(log, today.minusDays(2), today)
        assertEquals(listOf(5, 4, 0), bars.map { it.second })
        assertEquals(today, bars.last().first)
    }

    @Test
    fun fastsAndRamadanDaysOwed() {
        var log = emptyMap<String, Int>()
        log = FastLog.set(log, today.minusDays(3), FastMark.VOLUNTARY, today)
        log = FastLog.set(log, today.minusDays(1), FastMark.QADA, today)
        assertEquals(FastMark.QADA, FastLog.mark(log, today.minusDays(1)))
        assertEquals(2, FastLog.count(log, today.minusDays(30), today))
        assertEquals(1, FastLog.count(log, today.minusDays(30), today, FastMark.VOLUNTARY))
        // Owed counted from a date: make-up fasts from then on are subtracted, earlier ones are not.
        assertEquals(5, FastLog.qadaRemaining(log, 6, today.minusDays(2)))
        assertEquals(6, FastLog.qadaRemaining(log, 6, today))
        assertEquals(0, FastLog.qadaRemaining(log, 0, null))
        log = FastLog.set(log, today.minusDays(1), null, today)
        assertNull(FastLog.mark(log, today.minusDays(1)))
        // Not in the future, and not on Eid (1 Shawwal 1448 is 9 March 2027) or the days of tashreeq.
        assertFalse(FastLog.canMark(today.plusDays(1), today, 0))
        assertTrue(FastLog.canMark(today, today, 0))
        assertFalse(FastLog.canMark(LocalDate.of(2027, 3, 9), LocalDate.of(2027, 3, 20), 0))
        // Days owed count from 1 Shawwal of the last Ramadan: 1447's ended on 19 March 2026.
        val season = FastLog.seasonStart(today, 0)!!
        assertEquals(com.sakina.app.core.calendar.HijriDate(1447, 10, 1), com.sakina.app.core.calendar.UmmAlQura.displayed(season, 0))
        assertEquals(2026, season.year)
        assertEquals(3, season.monthValue)
        // In Shawwal itself the season is the one just begun.
        assertEquals(LocalDate.of(2027, 3, 9), FastLog.seasonStart(LocalDate.of(2027, 3, 15), 0))
    }
}
