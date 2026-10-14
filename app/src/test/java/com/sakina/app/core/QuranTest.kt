package com.sakina.app.core

import com.sakina.app.core.quran.ArabicText
import com.sakina.app.core.quran.AyahRef
import com.sakina.app.core.quran.DailyVerse
import com.sakina.app.core.quran.Khatma
import com.sakina.app.core.quran.QuranSearch
import com.sakina.app.core.quran.RefTarget
import com.sakina.app.core.quran.ReferenceParser
import com.sakina.app.core.quran.SajdaType
import com.sakina.app.core.quran.TanzilParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate

class QuranTest {
    private fun asset(name: String) = File("src/main/assets/quran/$name").readText(Charsets.UTF_8)

    companion object {
        private val dir = File("src/main/assets/quran")
        val meta by lazy { TanzilParser.parseMeta(File(dir, "quran-data.xml").readText()) }
        val text by lazy { TanzilParser.parseUthmaniXml(File(dir, "quran-uthmani.xml").readText(), meta) }
        val simple by lazy { TanzilParser.parseTxt2("simple", File(dir, "quran-simple-clean.txt").readText(), meta) }
        val itani by lazy { TanzilParser.parseTxt2("en.itani", File(dir, "en.itani.txt").readText(), meta) }
        val jalalayn by lazy { TanzilParser.parseTxt2("ar.jalalayn", File(dir, "ar.jalalayn.txt").readText(), meta) }
        val search by lazy { QuranSearch(meta, simple, itani) }
    }

    @Test
    fun shippedFilesAreTheVerbatimTanzilDownloads() {
        val manifest = File("../data/quran_sources.json").readText()
        val entries = Regex(""""file": "([^"]+)",\s*"url": "[^"]+",\s*"bytes": (\d+),\s*"sha256": "([0-9a-f]+)"""").findAll(manifest).toList()
        assertEquals(5, entries.size)
        for (m in entries) {
            val bytes = File(dir, m.groupValues[1]).readBytes()
            val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            assertEquals(m.groupValues[1], m.groupValues[3], sha)
        }
        // The Tanzil terms ask that the copyright block stays in every copy.
        assertTrue(asset("quran-uthmani.xml").contains("PLEASE DO NOT REMOVE OR CHANGE THIS COPYRIGHT BLOCK"))
        assertTrue(asset("quran-simple-clean.txt").contains("PLEASE DO NOT REMOVE OR CHANGE THIS COPYRIGHT BLOCK"))
    }

    @Test
    fun structureMatchesTheMushaf() {
        assertEquals(114, meta.suras.size)
        assertEquals(6236, meta.totalAyahs)
        assertEquals(6236, text.ayahs.size)
        assertEquals(30, meta.juzStarts.size)
        assertEquals(240, meta.quarterStarts.size)
        assertEquals(604, meta.pageCount)
        assertEquals(15, meta.sajdas.size)
        assertEquals(7, meta.surah(1).ayahCount)
        assertEquals(286, meta.surah(2).ayahCount)
        assertEquals("البقرة", meta.surah(2).nameAr)
        assertEquals(AyahRef(2, 255), meta.ref(meta.global(AyahRef(2, 255))))
        for (g in listOf(0, 6, 7, 6235, 3000)) assertEquals(g, meta.global(meta.ref(g)))
        assertEquals(1, meta.pageOf(0))
        assertEquals(2, meta.pageOf(meta.global(AyahRef(2, 1))))
        assertEquals(604, meta.pageOf(6235))
        assertEquals(42, meta.pageOf(meta.global(AyahRef(2, 255))))
        assertEquals(2, meta.juzOf(meta.global(AyahRef(2, 142))))
        assertEquals(30, meta.juzOf(meta.global(AyahRef(78, 1))))
        assertEquals(60, meta.hizbOf(6235))
        assertEquals(SajdaType.OBLIGATORY, meta.sajdaAt(meta.global(AyahRef(32, 15)))?.type)
        // Every page range is contiguous and covers the whole text.
        var next = 0
        for (p in 1..604) {
            val r = meta.pageRange(p)
            assertEquals(next, r.first)
            next = r.last + 1
        }
        assertEquals(6236, next)
    }

    @Test
    fun basmalasAreSeparateAndCorrect() {
        // Surah 1: the basmala is ayah 1. Surah 9: none. Every other surah: shown above ayah 1, not inside it.
        assertFalse(text.basmala.containsKey(1))
        assertFalse(text.basmala.containsKey(9))
        assertEquals(112, text.basmala.size)
        assertTrue(text.ayahs[0].startsWith("بِسْمِ"))
        for (s in 2..114) {
            if (s == 9) continue
            val first = text.ayahs[meta.surah(s).start]
            assertFalse("surah $s ayah 1 must not repeat the basmala", ArabicText.fold(first).startsWith("بسم الله"))
        }
        // Tanzil marks the basmala of surahs 95 and 97 with a shadda, as in the printed Madinah mushaf.
        assertTrue(text.basmala.getValue(95).startsWith("\u0628\u0651\u0650"))
        assertTrue(text.basmala.getValue(97).startsWith("\u0628\u0651\u0650"))
        assertTrue(text.basmala.getValue(96).startsWith("\u0628\u0650\u0633"))
        assertEquals(text.basmala.getValue(2), text.basmala.getValue(114))
        // Surah 27 keeps the basmala inside ayah 30, as part of the text.
        assertTrue(text.ayahs[meta.global(AyahRef(27, 30))].contains("بِسْمِ"))
    }

    @Test
    fun translationAndTafsirCoverEveryAyah() {
        assertEquals(6236, itani.lines.size)
        assertEquals(6236, jalalayn.lines.size)
        assertTrue(itani.lines.all { it.isNotBlank() })
        assertTrue(itani.header.any { it.contains("Talal Itani") })
        assertTrue(jalalayn.header.any { it.contains("تفسير الجلالين") })
        assertTrue(itani.lines[meta.global(AyahRef(2, 255))].contains("God"))
    }

    @Test
    fun searchArabicEnglishAndReferences() {
        val r = search.search("الله لا إله إلا هو الحي القيوم")
        assertTrue(r.hits.any { meta.ref(it.global) == AyahRef(2, 255) })
        assertTrue(r.hits.any { meta.ref(it.global) == AyahRef(3, 2) })
        // Hamza and alef forms do not matter.
        assertEquals(search.search("الرحمن الرحيم").total, search.search("الرحمٰن الرحيم").total)
        // The basmala before ayah 1 is not a match; the basmala inside 27:30 and 1:1 are.
        val b = search.search("بسم الله الرحمن الرحيم")
        assertEquals(listOf(AyahRef(1, 1), AyahRef(27, 30)), b.hits.map { meta.ref(it.global) })
        val e = search.search("throne")
        assertTrue(e.total > 10)
        assertTrue(e.hits.all { it.inTranslation })
        assertEquals(RefTarget.Ayah(AyahRef(2, 255)), search.search("2:255").reference)
        val refs = ReferenceParser(meta)
        assertEquals(RefTarget.Ayah(AyahRef(2, 255)), refs.parse("٢:٢٥٥"))
        assertEquals(RefTarget.Ayah(AyahRef(2, 255)), refs.parse("البقرة 255"))
        assertEquals(RefTarget.Ayah(AyahRef(2, 255)), refs.parse("سورة البقرة آية ٢٥٥"))
        assertEquals(RefTarget.Ayah(AyahRef(2, 255)), refs.parse("al-baqarah 255"))
        assertEquals(RefTarget.SurahStart(18), refs.parse("الكهف"))
        assertEquals(RefTarget.SurahStart(18), refs.parse("Kahf"))
        assertEquals(RefTarget.SurahStart(36), refs.parse("يس"))
        assertEquals(RefTarget.SurahStart(14), refs.parse("إبراهيم"))
        assertEquals(RefTarget.Page(50), refs.parse("صفحة 50"))
        assertEquals(RefTarget.Page(604), refs.parse("page 604"))
        assertEquals(RefTarget.Juz(30), refs.parse("جزء 30"))
        assertEquals(RefTarget.SurahStart(114), refs.parse("114"))
        assertNull(refs.parse("2:287"))
        assertNull(refs.parse("115"))
        assertEquals("بسم الله", ArabicText.fold("بِسْمِ ٱللَّهِ"))
    }

    @Test
    fun khatmaScheduleAndProgress() {
        val start = LocalDate.of(2026, 10, 1)
        var plan = Khatma.create(start, 30)
        val portions = (0 until 30).map { Khatma.portion(plan, it) }
        assertEquals(1, portions.first().first)
        assertEquals(604, portions.last().last)
        portions.zipWithNext().forEach { (a, b) -> assertEquals(a.last + 1, b.first) }
        assertTrue(portions.all { it.count() in 20..21 })
        // Day 5 (Oct 6) with nothing recorded: behind by the first five portions.
        var s = Khatma.status(plan, LocalDate.of(2026, 10, 6))
        assertEquals(5, s.dayIndex)
        assertEquals(portions[4].last, s.behind)
        assertEquals(portions[5], s.today)
        plan = Khatma.record(plan, portions[5].last, LocalDate.of(2026, 10, 6))
        s = Khatma.status(plan, LocalDate.of(2026, 10, 6))
        assertEquals(0, s.behind)
        assertEquals(0, s.remainingToday)
        plan = Khatma.finishToday(plan, LocalDate.of(2026, 10, 7))
        assertEquals(portions[6].last, plan.progressPage)
        assertEquals(2, plan.log.size)
        // After the end without finishing: overdue.
        s = Khatma.status(plan, LocalDate.of(2026, 11, 5))
        assertTrue(s.overdue)
        assertEquals(604 - plan.progressPage, s.behind)
        plan = Khatma.record(plan, 9999, LocalDate.of(2026, 11, 5))
        assertTrue(Khatma.status(plan, LocalDate.of(2026, 11, 5)).completed)
        assertEquals(100, Khatma.status(plan, LocalDate.of(2026, 11, 5)).percent)
        // Longer than 604 days is not allowed; short plans have big portions.
        assertEquals(365, Khatma.create(start, 1000).days)
        assertEquals(1..604, Khatma.portion(Khatma.create(start, 1), 0))
    }

    @Test
    fun dailyVersesAreValidReferences() {
        assertTrue(DailyVerse.refs.all { meta.isValid(it) })
        assertEquals(DailyVerse.refs.size, DailyVerse.refs.toSet().size)
        assertTrue(meta.isValid(DailyVerse.forDate(LocalDate.of(2026, 10, 14))))
    }
}
