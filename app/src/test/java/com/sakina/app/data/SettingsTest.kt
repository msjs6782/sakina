package com.sakina.app.data

import com.sakina.app.core.format.Lang
import com.sakina.app.core.location.City
import com.sakina.app.core.prayer.CalcMethod
import com.sakina.app.core.prayer.UmmAlQuraIsha
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTest {
    private val riyadh = City("riyadh", "الرياض", "Riyadh", "SA", "السعودية", "Saudi Arabia", 24.68773, 46.72185, "Asia/Riyadh")
    private val jeddah = City("jeddah", "جدة", "Jeddah", "SA", "السعودية", "Saudi Arabia", 21.49012, 39.18624, "Asia/Riyadh")

    @Test
    fun jsonRoundTripKeepsEverything() {
        var s = AppSettings(language = Lang.EN, clock24 = true, method = CalcMethod.EGYPTIAN, ummAlQuraIsha = UmmAlQuraIsha.FIXED_120,
            offsets = OffsetPrefs(fajr = 3, isha = -2), hijriOffset = -1, style = VisualStyle.SAHAR, themeMode = ThemeMode.SUN,
            homeLayout = HomeLayout.DASHBOARD)
        s = SettingsOps.addAndActivate(s, SettingsOps.cityLocation(riyadh))
        s = SettingsOps.startTravel(s, SettingsOps.cityLocation(jeddah), 1000L)
        val json = SettingsJson.encodeToString(AppSettings.serializer(), s)
        assertEquals(s, SettingsJson.decodeFromString(AppSettings.serializer(), json))
    }

    @Test
    fun unknownFieldsAndValuesFallBackToDefaults() {
        val json = """{"language":"EN","style":"FUTURE_STYLE","newField":42,"method":"MUSLIM_WORLD_LEAGUE"}"""
        val s = SettingsJson.decodeFromString(AppSettings.serializer(), json)
        assertEquals(Lang.EN, s.language)
        assertEquals(VisualStyle.OASIS, s.style)
        assertEquals(CalcMethod.MUSLIM_WORLD_LEAGUE, s.method)
    }

    @Test
    fun locationsAndTravel() {
        var s = AppSettings()
        assertNull(s.activeLocation)
        s = SettingsOps.addAndActivate(s, SettingsOps.cityLocation(riyadh))
        s = SettingsOps.addAndActivate(s, SettingsOps.cityLocation(jeddah))
        assertEquals("city:jeddah", s.activeLocation!!.id)
        s = SettingsOps.activate(s, "city:riyadh")
        assertEquals("city:riyadh", s.activeLocation!!.id)

        val dest = SettingsOps.cityLocation(jeddah).copy(id = "trip")
        s = SettingsOps.startTravel(s, dest, 5L)
        assertEquals("trip", s.activeLocation!!.id)
        assertEquals("city:riyadh", s.homeLocation!!.id)
        assertEquals(2, s.locations.size)
        s = SettingsOps.returnHome(s)
        assertNull(s.travel)
        assertEquals("city:riyadh", s.activeLocation!!.id)

        s = SettingsOps.remove(s, "city:riyadh")
        assertEquals("city:jeddah", s.activeLocation!!.id)
    }

    @Test
    fun cardsReorderAndHide() {
        var s = AppSettings()
        assertEquals(listOf(HomeCard.VERSE, HomeCard.SUN, HomeCard.DATE), s.orderedCards.map { it.card })
        s = SettingsOps.moveCard(s, HomeCard.DATE, -1)
        assertEquals(listOf(HomeCard.VERSE, HomeCard.DATE, HomeCard.SUN), s.orderedCards.map { it.card })
        // Settings saved before the verse card existed get it appended.
        val old = AppSettings(homeCards = listOf(CardPref(HomeCard.SUN), CardPref(HomeCard.DATE, visible = false)))
        assertEquals(listOf(HomeCard.SUN, HomeCard.DATE, HomeCard.VERSE), old.orderedCards.map { it.card })
        s = SettingsOps.setCardVisible(s, HomeCard.SUN, false)
        assertTrue(s.orderedCards.first { it.card == HomeCard.SUN }.visible.not())
        s = SettingsOps.moveCard(s, HomeCard.DATE, -1)
        assertEquals(s, SettingsOps.moveCard(s, HomeCard.DATE, -1))
    }
}
