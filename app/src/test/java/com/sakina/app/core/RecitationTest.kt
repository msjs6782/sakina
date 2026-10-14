package com.sakina.app.core

import com.sakina.app.core.recitation.LibraryLimits
import com.sakina.app.core.recitation.Reciter
import com.sakina.app.core.recitation.RecitationSources
import com.sakina.app.core.recitation.Recording
import com.sakina.app.core.recitation.RepeatCount
import com.sakina.app.core.recitation.RepeatRun
import com.sakina.app.core.recitation.TimingCheck
import com.sakina.app.core.recitation.TimingParser
import com.sakina.app.core.recitation.TimingProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RecitationTest {
    private val sources by lazy { RecitationSources.parse(File("src/main/assets/recitations/sources.tsv").readText()) }
    private fun fixture(name: String) = javaClass.classLoader!!.getResource("timing/$name.json")!!.readText()
    private fun ayahCount(sura: Int) = QuranTest.meta.surah(sura).ayahCount

    @Test
    fun recordingsAndUrls() {
        assertEquals("https://cdn.mp3quran.net/audio/mishary-alafasy/r1/001.mp3", Reciter.ALAFASY.url(1))
        assertEquals("https://cdn.mp3quran.net/audio/mahmoud-husary/r1/114.mp3", Reciter.HUSARY.url(114))
        assertEquals("https://www.mp3quran.net/api/v3/ayat_timing?surah=2&read=112", Reciter.MINSHAWI.timingUrl(2))
        assertEquals(Recording(Reciter.MINSHAWI, 36), Recording.parse("minshawi:36"))
        assertNull(Recording.parse("minshawi:115"))
        assertNull(Recording.parse("someone:1"))
        assertEquals("husary:2", Recording(Reciter.HUSARY, 2).id)
    }

    @Test
    fun everyRecordingFitsTheSingleRecordingLimit() {
        for (r in Reciter.entries) {
            var total = 0L
            for (s in 1..114) {
                val rec = Recording(r, s)
                val b = sources.bytes(rec)
                assertTrue("$rec size", b in 1..LibraryLimits.PER_RECORDING)
                assertTrue("$rec duration", sources.durationMs(rec) > 10_000)
                total += b
            }
            // A whole recording set is larger than the library limit, so the limit matters.
            assertTrue("$r total $total", total > LibraryLimits.LIBRARY)
        }
        assertEquals(51_966L, sources.durationMs(Recording(Reciter.MINSHAWI, 1)))
    }

    @Test
    fun libraryLimits() {
        assertEquals(LibraryLimits.Check.OK, LibraryLimits.check(100L shl 20, 0))
        assertEquals(LibraryLimits.Check.OK, LibraryLimits.check(256L shl 20, 0))
        assertEquals(LibraryLimits.Check.TOO_BIG, LibraryLimits.check((256L shl 20) + 1, 0))
        assertEquals(LibraryLimits.Check.LIBRARY_FULL, LibraryLimits.check(100L shl 20, 950L shl 20))
        assertEquals(LibraryLimits.Check.OK, LibraryLimits.check(74L shl 20, 950L shl 20))
        assertEquals(LibraryLimits.Check.UNKNOWN_SIZE, LibraryLimits.check(-1, 0))
    }

    @Test
    fun validTimingMapsAreAccepted() {
        val fatiha = TimingParser.parse(fixture("123_001"), 1, 7) as TimingCheck.Valid
        assertEquals(7, fatiha.map.ayahs.size)
        assertNull(fatiha.map.preambleEndMs)
        assertEquals(6420L..11120L, fatiha.map.span(1, 1))
        assertEquals(1, fatiha.map.ayahAt(7000))
        assertNull(fatiha.map.ayahAt(100))
        val baqara = TimingParser.parse(fixture("123_002"), 2, ayahCount(2)) as TimingCheck.Valid
        assertEquals(286, baqara.map.ayahs.size)
        assertTrue(TimingParser.fits(baqara.map, sources.durationMs(Recording(Reciter.ALAFASY, 2))) is TimingCheck.Valid)
        // An introduction (ayah 0: isti'adha and basmala) before ayah 1 is allowed.
        val minshawi = TimingParser.parse(fixture("112_001"), 1, 7) as TimingCheck.Valid
        assertEquals(7204L, minshawi.map.preambleEndMs)
        assertEquals(7204L, minshawi.map.ayahs.first().startMs)
        assertTrue(TimingParser.fits(minshawi.map, sources.durationMs(Recording(Reciter.MINSHAWI, 1))) is TimingCheck.Valid)
    }

    @Test
    fun brokenTimingMapsDisableRepeat() {
        // Husary's Al-Fatiha map numbers 6 ayahs instead of 7.
        assertEquals(TimingProblem.NUMBERING, (TimingParser.parse(fixture("118_001"), 1, 7) as TimingCheck.Invalid).problem)
        // Minshawi's At-Tawba map lacks an ayah.
        assertEquals(TimingProblem.NUMBERING, (TimingParser.parse(fixture("112_009"), 9, 129) as TimingCheck.Invalid).problem)
        // Well formed, but longer than the recording it belongs to.
        val tur = TimingParser.parse(fixture("112_052"), 52, ayahCount(52)) as TimingCheck.Valid
        assertEquals(TimingProblem.PAST_AUDIO_END, (TimingParser.fits(tur.map, sources.durationMs(Recording(Reciter.MINSHAWI, 52))) as TimingCheck.Invalid).problem)
        val anbiya = TimingParser.parse(fixture("118_021"), 21, ayahCount(21)) as TimingCheck.Valid
        assertEquals(TimingProblem.PAST_AUDIO_END, (TimingParser.fits(anbiya.map, sources.durationMs(Recording(Reciter.HUSARY, 21))) as TimingCheck.Invalid).problem)
        // Hand-made faults.
        assertEquals(TimingProblem.NOT_A_LIST, (TimingParser.parse("""{"error":"x"}""", 1, 7) as TimingCheck.Invalid).problem)
        val two = """[{"ayah":1,"start_time":0,"end_time":500},{"ayah":2,"start_time":400,"end_time":900}]"""
        assertEquals(TimingProblem.OVERLAP, (TimingParser.parse(two, 108, 2) as TimingCheck.Invalid).problem)
        val back = """[{"ayah":1,"start_time":600,"end_time":500},{"ayah":2,"start_time":700,"end_time":900}]"""
        assertEquals(TimingProblem.END_BEFORE_START, (TimingParser.parse(back, 108, 2) as TimingCheck.Invalid).problem)
        val order = """[{"ayah":1,"start_time":600,"end_time":700},{"ayah":2,"start_time":600,"end_time":900}]"""
        assertEquals(TimingProblem.ORDER, (TimingParser.parse(order, 108, 2) as TimingCheck.Invalid).problem)
        val pre = """[{"ayah":1,"start_time":0,"end_time":500},{"ayah":0,"start_time":500,"end_time":600}]"""
        assertEquals(TimingProblem.PREAMBLE, (TimingParser.parse(pre, 108, 1) as TimingCheck.Invalid).problem)
        val short = TimingParser.parse("""[{"ayah":1,"start_time":0,"end_time":5000}]""", 108, 1) as TimingCheck.Valid
        assertEquals(TimingProblem.SHORT_OF_AUDIO, (TimingParser.fits(short.map, 120_000) as TimingCheck.Invalid).problem)
    }

    @Test
    fun timingSourceIsTiedToTheRecordingSet() {
        val reads = """[{"id":112,"name":"x","rewaya":"y","folder_url":"https:\/\/cdn.mp3quran.net\/audio\/muhammad-minshawi\/r1\/","soar_count":114},""" +
            """{"id":123,"name":"z","folder_url":"https://cdn.mp3quran.net/audio/mishary-alafasy/r1/","soar_count":114}]"""
        assertEquals(Reciter.MINSHAWI.server, TimingParser.folderOf(reads, 112))
        assertEquals(Reciter.ALAFASY.server, TimingParser.folderOf(reads, 123))
        assertNull(TimingParser.folderOf(reads, 118))
    }

    @Test
    fun repeatPlaysTheRangeTheChosenNumberOfTimes() {
        fun run(c: RepeatCount): Int {
            val r = RepeatRun(1, 3, c, 0, 1000)
            var plays = 1
            while (r.reachedEnd() == RepeatRun.Action.AGAIN && plays < 50) plays++
            return plays
        }
        assertEquals(1, run(RepeatCount.ONCE))
        assertEquals(3, run(RepeatCount.THREE))
        assertEquals(5, run(RepeatCount.FIVE))
        assertEquals(50, run(RepeatCount.FOREVER))
    }
}
