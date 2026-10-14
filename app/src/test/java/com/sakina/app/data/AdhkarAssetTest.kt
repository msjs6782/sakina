package com.sakina.app.data

import com.sakina.app.core.adhkar.AdhkarLibrary
import com.sakina.app.core.adhkar.Practice
import com.sakina.app.core.alerts.ReminderKind
import com.sakina.app.core.quran.TanzilParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The bundled adhkar library: counts from the specification, complete sources, and texts that resolve. */
class AdhkarAssetTest {
    private val lib: AdhkarLibrary by lazy {
        SettingsJson.decodeFromString(AdhkarLibrary.serializer(), File("src/main/assets/adhkar/adhkar.json").readText())
    }

    @Test
    fun countsMatchTheSpecification() {
        assertEquals(54, lib.items.size)
        assertEquals(11, lib.categories.size)
        assertEquals(5, lib.practices.size)
        assertEquals(lib.items.size, lib.items.map { it.id }.toSet().size)
        assertEquals(lib.categories.map { it.id }.toSet(), lib.items.map { it.cat }.toSet())
    }

    @Test
    fun practicesLinkToTheirReminders() {
        val byId = lib.practices.associateBy { it.id }
        assertEquals("ADHKAR_MORNING", byId[Practice.MORNING]!!.reminder)
        assertEquals("ADHKAR_EVENING", byId[Practice.EVENING]!!.reminder)
        assertEquals("ADHKAR_SLEEP", byId[Practice.SLEEP]!!.reminder)
        assertEquals("ADHKAR_WAKING", byId[Practice.WAKING]!!.reminder)
        assertEquals(null, byId[Practice.AFTER_PRAYER]!!.reminder)
        lib.practices.mapNotNull { it.reminder }.forEach { ReminderKind.valueOf(it) }
        for (p in lib.practices) {
            assertTrue(p.items.isNotEmpty())
            p.items.forEach { assertNotNull("${p.id}: $it", lib.item(it)) }
            assertTrue(lib.itemsOf(p).none { it.unnumbered })
        }
    }

    @Test
    fun everyItemHasTextSourceGradeAndLinks() {
        val marks = Regex("[ً-ْ]")
        for (d in lib.items) {
            assertTrue(d.id, d.parts.isNotEmpty())
            for (p in d.parts) {
                assertTrue(d.id, (p.text != null) != (p.quran != null))
                p.text?.let {
                    assertTrue("${d.id} unvowelled", marks.containsMatchIn(it))
                    assertFalse("${d.id} damaged", it.contains('�') || it.contains('"') || it.contains('‏'))
                }
                assertTrue(p.count >= 0)
            }
            assertTrue(d.id, d.source.book in setOf("bukhari", "muslim", "abudawud", "tirmidhi", "ibnmajah"))
            assertTrue(d.id, d.source.grade.en in setOf("Sahih", "Hasan", "Hasan Sahih"))
            assertTrue(d.id, d.links.any { it.url == "https://sunnah.com/${d.source.book}:${d.source.number}" })
            assertTrue(d.id, d.links.all { it.url.startsWith("https://") })
            assertTrue(d.id, d.ctxAr.isNotBlank() && d.ctxEn.isNotBlank())
        }
    }

    @Test
    fun morningAndEveningWordings() {
        val bika = lib.item("bika_asbahna")!!.parts[0]
        assertTrue(bika.textFor(Practice.MORNING)!!.contains("الْمَصِيرُ"))
        assertTrue(bika.textFor(Practice.EVENING)!!.contains("النُّشُورُ"))
        val mulk = lib.item("asbahna_mulk")!!.parts[0]
        assertTrue(mulk.textFor(Practice.MORNING)!!.startsWith("أَصْبَحْنَا"))
        assertTrue(mulk.textFor(Practice.EVENING)!!.startsWith("أَمْسَيْنَا"))
    }

    @Test
    fun quranPassagesExistInTheBundledText() {
        val meta = TanzilParser.parseMeta(File("src/main/assets/quran/quran-data.xml").readText())
        for (d in lib.items) for (p in d.parts) {
            val q = p.quran ?: continue
            val sura = meta.surah(q.sura)
            assertTrue(d.id, q.from in 1..q.to && q.to <= sura.ayahCount)
        }
        assertEquals(3, lib.item("muawwidhat_3")!!.rounds)
    }

    @Test
    fun searchFindsByTextTitleAndCategory() {
        assertTrue(lib.search("الكرسي").any { it.id == "ayat_kursi_sleep" })
        assertTrue(lib.search("اعوذ بكلمات الله التامات").any { it.id == "stopping_place" })
        assertTrue(lib.search("travel").any { it.id == "travel" })
        assertTrue(lib.search("السفر").any { it.id == "travel" })
        assertTrue(lib.search("Before sleep").size >= 9)
    }
}
