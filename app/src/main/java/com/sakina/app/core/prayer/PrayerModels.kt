package com.sakina.app.core.prayer

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class PrayerKey {
    FAJR, SUNRISE, DHUHR, ASR, MAGHRIB, ISHA;

    val isPrayer: Boolean get() = this != SUNRISE

    companion object {
        val prayers = listOf(FAJR, DHUHR, ASR, MAGHRIB, ISHA)
    }
}

/**
 * The supported calculation methods. Angles are below the horizon. [dhuhrMinutes] is the conventional
 * margin after the sun's zenith that these methods add to Dhuhr.
 */
enum class CalcMethod(
    val fajrAngle: Double,
    val ishaAngle: Double,
    val ishaIntervalMinutes: Int,
    val dhuhrMinutes: Int,
) {
    UMM_AL_QURA(18.5, 0.0, 90, 0),
    MUSLIM_WORLD_LEAGUE(18.0, 17.0, 0, 1),
    EGYPTIAN(19.5, 17.5, 0, 1),
    KARACHI(18.0, 18.0, 0, 1),
    NORTH_AMERICA(15.0, 15.0, 0, 1),
}

/** Asr begins when an object's shadow reaches one (standard) or two (Hanafi) times its length beyond the noon shadow. */
enum class AsrMethod(val shadowFactor: Double) { STANDARD(1.0), HANAFI(2.0) }

/** How Fajr and Isha are limited when twilight lasts all night or the angle is not reached. */
enum class HighLatitudeRule { MIDDLE_OF_THE_NIGHT, SEVENTH_OF_THE_NIGHT, TWILIGHT_ANGLE }

/**
 * Umm al-Qura's interval between Maghrib and Isha. AUTO uses 120 minutes on civil days that fall in
 * Ramadan of the unadjusted Umm al-Qura calendar and 90 minutes otherwise.
 */
enum class UmmAlQuraIsha { AUTO, FIXED_90, FIXED_120 }

data class PrayerOffsets(
    val fajr: Int = 0,
    val sunrise: Int = 0,
    val dhuhr: Int = 0,
    val asr: Int = 0,
    val maghrib: Int = 0,
    val isha: Int = 0,
) {
    operator fun get(key: PrayerKey): Int = when (key) {
        PrayerKey.FAJR -> fajr
        PrayerKey.SUNRISE -> sunrise
        PrayerKey.DHUHR -> dhuhr
        PrayerKey.ASR -> asr
        PrayerKey.MAGHRIB -> maghrib
        PrayerKey.ISHA -> isha
    }.coerceIn(MIN, MAX)

    fun with(key: PrayerKey, minutes: Int): PrayerOffsets {
        val m = minutes.coerceIn(MIN, MAX)
        return when (key) {
            PrayerKey.FAJR -> copy(fajr = m)
            PrayerKey.SUNRISE -> copy(sunrise = m)
            PrayerKey.DHUHR -> copy(dhuhr = m)
            PrayerKey.ASR -> copy(asr = m)
            PrayerKey.MAGHRIB -> copy(maghrib = m)
            PrayerKey.ISHA -> copy(isha = m)
        }
    }

    val isZero: Boolean get() = PrayerKey.entries.all { get(it) == 0 }

    companion object {
        const val MIN = -60
        const val MAX = 60
    }
}

data class CalcConfig(
    val method: CalcMethod = CalcMethod.UMM_AL_QURA,
    val asr: AsrMethod = AsrMethod.STANDARD,
    val highLatitude: HighLatitudeRule = HighLatitudeRule.MIDDLE_OF_THE_NIGHT,
    val ummAlQuraIsha: UmmAlQuraIsha = UmmAlQuraIsha.AUTO,
    val offsets: PrayerOffsets = PrayerOffsets(),
)

data class GeoPoint(val latitude: Double, val longitude: Double) {
    val isValid: Boolean
        get() = latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0
}

enum class TimeStatus {
    /** Calculated directly from the method's definition. */
    CALCULATED,

    /** The twilight angle was not reached or was later than the rule allows; the high-latitude rule set the time. */
    HIGH_LATITUDE_RULE,

    /** Cannot be calculated for this day and place (e.g. the sun does not rise or set). */
    UNAVAILABLE,
}

data class PrayerTime(val key: PrayerKey, val instant: Instant?, val status: TimeStatus) {
    val available: Boolean get() = instant != null
}

enum class DayIssue {
    /** The sun stays above the horizon all day. */
    NO_SUNSET,

    /** The sun stays below the horizon all day. */
    NO_SUNRISE,

    /** The next day's sunrise is missing, so the night length is unknown. */
    NIGHT_UNKNOWN,

    /** Times came out of order (very close to the polar circles); they are withheld. */
    OUT_OF_ORDER,

    /** The date is outside the published Umm al-Qura range used for the Ramadan rule. */
    HIJRI_OUT_OF_RANGE,
}

data class DayTimes(
    val date: LocalDate,
    val zone: ZoneId,
    val config: CalcConfig,
    val times: List<PrayerTime>,
    /** Astronomical sunrise and sunset, without the user's offsets. */
    val sunrise: Instant?,
    val sunset: Instant?,
    /** For interval-based Isha: the interval actually used, in minutes. */
    val ishaIntervalMinutes: Int?,
    /** Whether the civil date is in Ramadan in the unadjusted Umm al-Qura calendar (null if unknown). */
    val ramadan: Boolean?,
    val issues: Set<DayIssue>,
) {
    operator fun get(key: PrayerKey): PrayerTime = times.first { it.key == key }
    val allAvailable: Boolean get() = times.all { it.available }
}
