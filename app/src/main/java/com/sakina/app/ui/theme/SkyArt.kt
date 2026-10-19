package com.sakina.app.ui.theme

import com.sakina.app.R

/**
 * The painted sky of each time of day (generated with Higgsfield in one style; see docs/ART.md). Used behind the
 * prayer card and, by choice, behind widgets. The veil keeps white text at 4.5:1 over the part of the painting the
 * text covers, measured by tools/art/prepare.py.
 */
object SkyArt {
    fun drawable(p: SkyPeriod): Int = when (p) {
        SkyPeriod.FAJR -> R.drawable.sky_fajr
        SkyPeriod.MORNING -> R.drawable.sky_morning
        SkyPeriod.NOON -> R.drawable.sky_noon
        SkyPeriod.ASR -> R.drawable.sky_asr
        SkyPeriod.MAGHRIB -> R.drawable.sky_maghrib
        SkyPeriod.NIGHT -> R.drawable.sky_night
    }

    /** A 3:1 band from the bottom of the painting, for wide widgets (the launcher crops from the centre). */
    fun wideDrawable(p: SkyPeriod): Int = when (p) {
        SkyPeriod.FAJR -> R.drawable.sky_fajr_wide
        SkyPeriod.MORNING -> R.drawable.sky_morning_wide
        SkyPeriod.NOON -> R.drawable.sky_noon_wide
        SkyPeriod.ASR -> R.drawable.sky_asr_wide
        SkyPeriod.MAGHRIB -> R.drawable.sky_maghrib_wide
        SkyPeriod.NIGHT -> R.drawable.sky_night_wide
    }

    /** Black veil, 0–1: the measured value with a light floor, a little more in the dark theme. */
    fun scrim(p: SkyPeriod, dark: Boolean): Float {
        val measured = when (p) {
            SkyPeriod.FAJR -> 0.12f
            SkyPeriod.MORNING -> 0.47f
            SkyPeriod.NOON -> 0.37f
            SkyPeriod.ASR -> 0.37f
            SkyPeriod.MAGHRIB -> 0.06f
            SkyPeriod.NIGHT -> 0.0f
        }
        return (maxOf(measured, 0.14f) + if (dark) 0.08f else 0f).coerceAtMost(0.7f)
    }

    /** The colour along the painting's top edge, to extend the sky above it in tall widgets. */
    fun topColor(p: SkyPeriod): Int = when (p) {
        SkyPeriod.FAJR -> 0xFF032453.toInt()
        SkyPeriod.MORNING -> 0xFF5A9ED6.toInt()
        SkyPeriod.NOON -> 0xFF045D8D.toInt()
        SkyPeriod.ASR -> 0xFF427CAD.toInt()
        SkyPeriod.MAGHRIB -> 0xFF052851.toInt()
        SkyPeriod.NIGHT -> 0xFF071E43.toInt()
    }
}
