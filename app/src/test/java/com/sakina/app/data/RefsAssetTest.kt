package com.sakina.app.data

import com.sakina.app.core.adhkar.HadithRef
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The verified hadith references of the calendar, Ramadan, night and Friday pages. */
class RefsAssetTest {
    private val refs: Map<String, HadithRef> by lazy {
        SettingsJson.decodeFromString(MapSerializer(String.serializer(), HadithRef.serializer()), File("src/main/assets/refs/refs7.json").readText())
    }

    /** Every id the screens ask for (CalendarScreen.FastingRefs, SeasonsScreens). */
    private val used = listOf(
        "mon_thu_tahari", "monday_born", "mon_thu_amal", "white_days", "arafah", "tasua", "ashura", "shawwal_six", "eid_forbidden", "tashreeq",
        "suhoor", "iftar_time", "hasten_iftar", "qiyam_ramadan", "last_third", "isha_half", "forbidden_times", "duha_rumh", "duha_awwabin",
        "kahf_ten", "salawat_ten", "friday_salawat", "friday_best", "friday_hour",
    )

    @Test
    fun everyUsedReferenceExistsAndIsGraded() {
        for (id in used) assertTrue("missing $id", id in refs)
        assertTrue(refs.getValue("friday_salawat").disputed)
        for ((id, r) in refs) {
            assertTrue(id, r.text.isNotBlank() && !r.text.contains('�') && !r.text.contains('"'))
            assertTrue(id, Regex("[ً-ْ]").containsMatchIn(r.text))
            assertTrue(id, r.grade.en in setOf("Sahih", "Hasan", "Hasan Sahih"))
            assertEquals(id, "https://sunnah.com/${r.book}:${r.number}", r.url)
            // One disputed grading is kept on purpose and shown with every grader's ruling (HadithRefBlock).
            if (id != "friday_salawat") assertFalse("$id graded weak by someone", r.disputed)
        }
    }
}
