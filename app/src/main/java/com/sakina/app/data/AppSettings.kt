package com.sakina.app.data

import com.sakina.app.core.format.Fmt
import com.sakina.app.core.format.Lang
import com.sakina.app.core.prayer.AsrMethod
import com.sakina.app.core.prayer.CalcConfig
import com.sakina.app.core.prayer.CalcMethod
import com.sakina.app.core.prayer.GeoPoint
import com.sakina.app.core.prayer.HighLatitudeRule
import com.sakina.app.core.prayer.PrayerOffsets
import com.sakina.app.core.prayer.UmmAlQuraIsha
import kotlinx.serialization.Serializable
import java.time.ZoneId

enum class VisualStyle { OASIS, SAHAR, RAML }

/** SUN: light between sunrise and Maghrib at the selected place, dark otherwise. */
enum class ThemeMode { SYSTEM, LIGHT, DARK, SUN }

enum class HomeLayout { BALANCED, FOCUS, DASHBOARD }

/** Home cards. Only cards whose content exists in this version are shown in the editor. */
enum class HomeCard { DATE, SUN }

enum class LocationSource { CITY, MANUAL, GPS }

@Serializable
data class CardPref(val card: HomeCard, val visible: Boolean = true)

@Serializable
data class SavedLocation(
    val id: String,
    val nameAr: String,
    val nameEn: String,
    val latitude: Double,
    val longitude: Double,
    val zone: String,
    val source: LocationSource,
    val cityId: String? = null,
    val countryAr: String? = null,
    val countryEn: String? = null,
) {
    fun name(lang: Lang) = if (lang == Lang.AR) nameAr.ifBlank { nameEn } else nameEn.ifBlank { nameAr }
    fun country(lang: Lang) = if (lang == Lang.AR) countryAr else countryEn
    val point: GeoPoint get() = GeoPoint(latitude, longitude)
    val zoneId: ZoneId? get() = runCatching { ZoneId.of(zone) }.getOrNull()
    val isUsable: Boolean get() = point.isValid && zoneId != null
}

@Serializable
data class TravelState(
    val homeId: String,
    val destination: SavedLocation,
    val sinceEpochMillis: Long,
)

@Serializable
data class OffsetPrefs(
    val fajr: Int = 0,
    val sunrise: Int = 0,
    val dhuhr: Int = 0,
    val asr: Int = 0,
    val maghrib: Int = 0,
    val isha: Int = 0,
) {
    fun toCore() = PrayerOffsets(fajr, sunrise, dhuhr, asr, maghrib, isha)

    companion object {
        fun from(o: PrayerOffsets) = OffsetPrefs(o.fajr, o.sunrise, o.dhuhr, o.asr, o.maghrib, o.isha)
    }
}

@Serializable
data class AppSettings(
    val schema: Int = 1,
    val onboardingDone: Boolean = false,
    val language: Lang = Lang.AR,
    val clock24: Boolean = false,
    val arabicDigits: Boolean = true,
    val style: VisualStyle = VisualStyle.OASIS,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    /** Used by the printed Mushaf viewer (later phase); independent of the app theme. */
    val mushafNight: Boolean = false,
    val method: CalcMethod = CalcMethod.UMM_AL_QURA,
    val asr: AsrMethod = AsrMethod.STANDARD,
    val highLatitude: HighLatitudeRule = HighLatitudeRule.MIDDLE_OF_THE_NIGHT,
    val ummAlQuraIsha: UmmAlQuraIsha = UmmAlQuraIsha.AUTO,
    val offsets: OffsetPrefs = OffsetPrefs(),
    /** Display-only shift of the Hijri date (−2…+2). Never changes prayer calculations. */
    val hijriOffset: Int = 0,
    val locations: List<SavedLocation> = emptyList(),
    val activeLocationId: String? = null,
    val travel: TravelState? = null,
    val homeLayout: HomeLayout = HomeLayout.BALANCED,
    val homeCards: List<CardPref> = DEFAULT_CARDS,
) {
    val calcConfig: CalcConfig
        get() = CalcConfig(method, asr, highLatitude, ummAlQuraIsha, offsets.toCore())

    val fmt: Fmt get() = Fmt(language, clock24, arabicDigits)

    /** The place used for times now: the travel destination, else the chosen saved location. */
    val activeLocation: SavedLocation?
        get() = travel?.destination
            ?: locations.firstOrNull { it.id == activeLocationId }
            ?: locations.firstOrNull()

    val homeLocation: SavedLocation?
        get() = travel?.let { t -> locations.firstOrNull { it.id == t.homeId } }

    /** Cards in the user's order, with any new card types appended (visible). */
    val orderedCards: List<CardPref>
        get() {
            val known = homeCards.filter { p -> HomeCard.entries.contains(p.card) }.distinctBy { it.card }
            return known + HomeCard.entries.filter { c -> known.none { it.card == c } }.map { CardPref(it) }
        }

    companion object {
        val DEFAULT_CARDS = listOf(CardPref(HomeCard.SUN), CardPref(HomeCard.DATE))
        const val MAX_LOCATIONS = 20
    }
}
