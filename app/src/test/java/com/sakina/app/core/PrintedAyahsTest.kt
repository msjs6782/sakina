package com.sakina.app.core

import com.sakina.app.core.mushaf.PrintedLayout
import com.sakina.app.core.quran.AyahRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The printed pages' ayahs (assets/mushaf/ayahs.tsv, found from the 1441H PDFs). */
class PrintedAyahsTest {
    private val layout by lazy { PrintedLayout.parse(File("src/main/assets/mushaf/ayahs.tsv").readText()) }
    private val meta get() = QuranTest.meta

    @Test
    fun everyAyahIsOnExactlyOnePage() {
        assertEquals(604, layout.pages.size)
        assertEquals(6236, layout.pages.sumOf { it.count })
        for (i in 0 until 603) {
            val p = layout.pages[i]
            val next = layout.pages[i + 1]
            assertEquals("page ${p.page}", meta.global(p.first) + p.count, meta.global(next.first))
        }
        // Every page but the first two has its lines, markers and parts of every ayah.
        for (p in layout.pages.drop(2)) {
            assertTrue("page ${p.page}", p.interactive)
            val seen = p.segments.map { it.ayah }.toSet()
            assertEquals("page ${p.page} ayahs with parts", (0 until p.count).toSet(), seen)
            assertTrue("page ${p.page} parts", p.segments.all { it.x1 > it.x0 })
        }
        assertFalse(layout.page(1).interactive)
    }

    @Test
    fun thisEditionBreaksSomePagesDifferentlyFromTanzil() {
        // Checked by eye on the renders: page 121 opens with 5:78, 145 with 6:131, 533 with 55:42, 586 with 80:41.
        assertEquals(AyahRef(5, 78), layout.page(121).first)
        assertEquals(AyahRef(6, 131), layout.page(145).first)
        assertEquals(AyahRef(55, 42), layout.page(533).first)
        assertEquals(AyahRef(80, 41), layout.page(586).first)
        val differ = layout.pages.filter { it.first != meta.pageStarts[it.page - 1] }.map { it.page }
        assertEquals(25, differ.size)
        // Most pages agree, including those the app's other tests rely on.
        for (p in listOf(1, 2, 3, 50, 187, 295, 300, 582, 603, 604)) assertEquals("page $p", meta.pageStarts[p - 1], layout.page(p).first)
        // The app's structure with these pages.
        val m = meta.withPages(layout.pageStarts)
        assertEquals(586, m.pageOf(m.global(AyahRef(80, 41))))
        assertEquals(585, m.pageOf(m.global(AyahRef(80, 40))))
        assertEquals(30, m.juzOf(m.global(AyahRef(80, 41))))
    }

    @Test
    fun aPointOnThePageFindsItsAyah() {
        val p = layout.page(295) // 18:16–20, five ayahs
        assertEquals(AyahRef(18, 16), p.first)
        assertEquals(5, p.count)
        // The centre of each marker belongs to the ayah it ends.
        p.markers.forEachIndexed { i, m -> assertEquals(i, p.ayahAt((m.x0 + m.x1) / 2, p.lines[m.line].cy)) }
        // The start of the first line is the first ayah; far below the text, nothing.
        val l0 = p.lines[0]
        assertEquals(0, p.ayahAt(l0.x1 - 2f, l0.cy))
        assertNull(p.ayahAt(l0.x1, p.lines[14].cy + p.pitch * 2))
        // An ayah running over lines has a part on each.
        val second = p.segmentsOf(1)
        assertTrue(second.size >= 3)
        assertEquals((second.first().line..second.last().line).toList(), second.map { it.line })
    }

    @Test
    fun headersAndBasmalaHoldNoAyah() {
        val p = layout.page(50) // Al Imran opens with its header and the basmala
        assertEquals("HB", p.kinds!!.substring(0, 2))
        assertTrue(p.segments.none { it.line < 2 })
        val last = layout.page(604) // al-Ikhlas, al-Falaq, an-Nas
        assertEquals(3, last.kinds!!.count { it == 'H' })
        assertEquals(15, last.count)
        // At-Tawbah has no basmala.
        val tawbah = layout.page(187)
        assertEquals('H', tawbah.kinds!![0])
        assertEquals('T', tawbah.kinds[1])
    }
}
