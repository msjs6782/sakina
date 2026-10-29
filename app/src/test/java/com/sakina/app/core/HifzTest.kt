package com.sakina.app.core

import com.sakina.app.core.quran.Hifz
import com.sakina.app.core.quran.HifzMask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HifzTest {
    @Test fun addAndReview() {
        var items = Hifz.add(emptyList(), 1, 1, 7, today = 100)
        assertEquals(1, items.size)
        assertEquals(101, items[0].dueEpochDay)
        // Adding the same passage again restarts it instead of duplicating it.
        items = Hifz.add(items, 1, 1, 7, today = 105)
        assertEquals(1, items.size)
        assertEquals(106, items[0].dueEpochDay)
        var it = items[0]
        it = Hifz.review(it, remembered = true, today = 106)
        assertEquals(1, it.level); assertEquals(108, it.dueEpochDay)
        it = Hifz.review(it, remembered = true, today = 108)
        assertEquals(2, it.level); assertEquals(112, it.dueEpochDay)
        it = Hifz.review(it, remembered = false, today = 112)
        assertEquals(1, it.level); assertEquals(113, it.dueEpochDay)
        assertEquals(3, it.reviews)
        repeat(20) { _ -> it = Hifz.review(it, true, 200) }
        assertEquals(Hifz.MAX_LEVEL, it.level)
        assertEquals(260, it.dueEpochDay)
    }

    @Test fun dueAndCount() {
        var items = Hifz.add(emptyList(), 1, 1, 7, today = 10)
        items = Hifz.add(items, 112, 1, 4, today = 5)
        items = Hifz.add(items, 1, 5, 7, today = 30)
        assertEquals(listOf("112:1-4", "1:1-7"), Hifz.due(items, today = 20).map { it.id })
        assertEquals(11, Hifz.ayahCount(items))
        assertEquals(2, Hifz.remove(items, "1:5-7").size)
    }

    @Test fun wordsSkipPauseMarks() {
        val ayah = "ذَٰلِكَ ٱلْكِتَٰبُ لَا رَيْبَ ۛ فِيهِ ۛ هُدًى لِّلْمُتَّقِينَ"
        val w = Hifz.words(ayah).map { ayah.substring(it) }
        assertEquals(listOf("ذَٰلِكَ", "ٱلْكِتَٰبُ", "لَا", "رَيْبَ", "فِيهِ", "هُدًى", "لِّلْمُتَّقِينَ"), w)
        assertEquals(6, Hifz.hidden(ayah, HifzMask.FIRST_WORD).size)
        assertEquals(7, Hifz.hidden(ayah, HifzMask.ALL).size)
        assertTrue(Hifz.hidden(ayah, HifzMask.NONE).isEmpty())
    }
}
