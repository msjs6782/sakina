package com.sakina.app.data

import com.sakina.app.core.adhkar.AdhkarLibrary
import com.sakina.app.core.quran.AyahRef
import com.sakina.app.core.quran.DailyVerse
import com.sakina.app.core.quran.TanzilParser
import com.sakina.app.core.widgets.ContentItem
import com.sakina.app.core.widgets.ContentMode
import com.sakina.app.core.widgets.ContentPack
import com.sakina.app.core.format.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The content widget's texts are verbatim copies of the bundled sources; widget settings survive a round trip. */
class WidgetAssetTest {
    private val pack: ContentPack by lazy {
        SettingsJson.decodeFromString(ContentPack.serializer(), File("src/main/assets/widgets/content.json").readText())
    }

    @Test
    fun versesMatchTheBundledTanzilText() {
        val meta = TanzilParser.parseMeta(File("src/main/assets/quran/quran-data.xml").readText())
        val text = TanzilParser.parseUthmaniXml(File("src/main/assets/quran/quran-uthmani.xml").readText(), meta)
        val verses = pack.items.filter { it.isAyah }
        assertTrue(verses.size >= 30)
        val daily = DailyVerse.refs.map { "${it.sura}:${it.aya}" }.toSet()
        for (v in verses) {
            val (su, ay) = v.ref.split(':').map { it.toInt() }
            assertEquals(v.ref, text.ayahs[meta.global(AyahRef(su, ay))], v.text)
            assertTrue(v.ref, v.ref in daily)
            assertTrue(v.ref, v.sourceAr.endsWith(" $ay") && v.sourceAr.startsWith("سورة " + meta.surah(su).nameAr))
            assertTrue(v.ref, v.sourceEn.endsWith(" $su:$ay"))
        }
    }

    @Test
    fun adhkarMatchTheLibraryAndAreNotTiedToATime() {
        val lib = SettingsJson.decodeFromString(AdhkarLibrary.serializer(), File("src/main/assets/adhkar/adhkar.json").readText())
        val adhkar = pack.items.filter { it.type == ContentItem.TYPE_DHIKR }
        assertTrue(adhkar.size >= 8)
        val timed = setOf("morning_evening", "sleep", "waking", "after_prayer", "in_prayer", "food", "travel", "home", "adhan_wudu_masjid")
        for (a in adhkar) {
            val d = lib.item(a.ref)!!
            assertEquals(1, d.parts.size)
            assertEquals(a.ref, d.parts[0].text, a.text)
            assertFalse(a.ref, d.cat in timed)
            assertEquals(d.source.ar, a.sourceAr)
            assertEquals(d.source.en, a.sourceEn)
            assertEquals(d.source.grade.en, a.gradeEn)
            assertEquals(maxOf(1, d.parts[0].count), a.count)
        }
        assertEquals(pack.items.size, pack.items.map { it.type + it.ref }.toSet().size)
    }

    @Test
    fun widgetSettingsRoundTripAndResolve() {
        val riyadh = SavedLocation("city:riyadh", "الرياض", "Riyadh", 24.7136, 46.6753, "Asia/Riyadh", LocationSource.CITY)
        val jeddah = SavedLocation("city:jeddah", "جدة", "Jeddah", 21.5433, 39.1728, "Asia/Riyadh", LocationSource.CITY)
        val cfg = WidgetConfig(
            kind = "next", placeMode = WidgetPlaceMode.FIXED, place = jeddah, lang = WidgetLang.EN, style = VisualStyle.SAHAR,
            theme = WidgetTheme.DARK, clock = WidgetClock.H24, textScale = 120, backgroundColor = 0xFF101010, opacity = 40,
            corner = 24, border = 2, contentMode = ContentMode.VERSES,
        )
        val state = WidgetsState(mapOf("7" to cfg))
        val back = SettingsJson.decodeFromString(WidgetsState.serializer(), SettingsJson.encodeToString(WidgetsState.serializer(), state))
        assertEquals(state, back)
        // Unknown fields from a newer version are ignored.
        val json = SettingsJson.encodeToString(WidgetsState.serializer(), state).replace("\"kind\":\"next\"", "\"kind\":\"next\",\"future\":1")
        assertEquals(state, SettingsJson.decodeFromString(WidgetsState.serializer(), json))
        // A fixed place stays when the app's place or travel changes; following the app tracks travel.
        val s = AppSettings(locations = listOf(riyadh), activeLocationId = riyadh.id)
        assertEquals(jeddah, cfg.resolvePlace(s))
        val travelling = s.copy(travel = TravelState(riyadh.id, jeddah.copy(id = "trip:x"), 0))
        assertEquals(jeddah, cfg.resolvePlace(travelling))
        assertEquals("trip:x", cfg.copy(placeMode = WidgetPlaceMode.APP).resolvePlace(travelling)!!.id)
        assertNull(WidgetConfig(placeMode = WidgetPlaceMode.APP).resolvePlace(AppSettings()))
        // Language, digits and clock.
        assertEquals(Lang.EN to false, cfg.language(s))
        assertEquals(Lang.AR to true, cfg.copy(lang = WidgetLang.AR).language(s.copy(language = Lang.EN, arabicDigits = false)))
        assertEquals(Lang.AR to false, cfg.copy(lang = WidgetLang.APP).language(s.copy(arabicDigits = false)))
        assertTrue(cfg.clock24(s))
        assertFalse(cfg.copy(clock = WidgetClock.APP).clock24(s))
        assertEquals(WidgetConfig.GLASS_OPACITY, WidgetConfig.defaultsFor("glass").opacity)
    }
}
