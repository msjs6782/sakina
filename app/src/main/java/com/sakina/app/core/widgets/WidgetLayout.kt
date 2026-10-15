package com.sakina.app.core.widgets

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** WCAG contrast arithmetic for widget colours (ARGB ints). */
object Contrast {
    private fun channel(c: Int): Double {
        val v = c / 255.0
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    fun luminance(argb: Int): Double =
        0.2126 * channel((argb shr 16) and 0xFF) + 0.7152 * channel((argb shr 8) and 0xFF) + 0.0722 * channel(argb and 0xFF)

    fun ratio(a: Int, b: Int): Double {
        val la = luminance(a); val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    /** [fg] drawn with [alpha] over an opaque [bg], blended per channel as the launcher does. */
    fun over(fg: Int, alpha: Float, bg: Int): Int {
        fun mix(s: Int) = (((fg shr s) and 0xFF) * alpha + ((bg shr s) and 0xFF) * (1 - alpha)).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }

    /** The lowest contrast of [text] over [scrim] at [alpha], over any grey wallpaper from black to white. */
    fun worst(text: Int, scrim: Int, alpha: Float): Double =
        (0..255 step 15).minOf { g -> ratio(text, over(scrim, alpha, (0xFF shl 24) or (g shl 16) or (g shl 8) or g)) }

    /**
     * The smallest scrim opacity (in percent) that keeps [text] at [target] contrast whatever the wallpaper;
     * 100 if even an opaque scrim does not reach it.
     */
    fun minScrimPercent(text: Int, scrim: Int, target: Double): Int =
        (0..100).firstOrNull { worst(text, scrim, it / 100f) >= target } ?: 100
}

/** Measures one line of text at a size in pixels (Paint.measureText on the device; a fake in tests). */
fun interface TextMeasure {
    fun width(text: String, sizePx: Float): Float
}

/** Height of wrapped text at a width and size (StaticLayout on the device). */
fun interface BlockMeasure {
    fun height(text: String, widthPx: Float, sizePx: Float): Float
}

/**
 * One line of a widget: [texts] side by side, drawn at [rel] times the widget's scale. By default they share
 * the width in equal columns, each losing [padPx] to padding; [inline] texts instead sit one after another with
 * [padPx] between them. [measure] and [lineHeight] override the defaults for a line set in another font.
 */
data class FitLine(
    val texts: List<String>,
    val rel: Float,
    val padPx: Float = 0f,
    val inline: Boolean = false,
    val measure: TextMeasure? = null,
    val lineHeight: Float? = null,
) {
    constructor(text: String, rel: Float, padPx: Float = 0f, measure: TextMeasure? = null, lineHeight: Float? = null) :
        this(listOf(text), rel, padPx, false, measure, lineHeight)
}

object Fit {
    /**
     * The largest scale in [minScale]..[maxScale] at which every line fits [width] and the stacked lines fit
     * [height] ([lineHeight] is the height of one line per pixel of text size, from the font's metrics;
     * [gapRel] is the space between lines relative to the scale). Returns null if nothing fits at [minScale].
     */
    fun scale(
        lines: List<FitLine>,
        width: Float,
        height: Float,
        maxScale: Float,
        minScale: Float,
        measure: TextMeasure,
        lineHeight: Float,
        gapRel: Float = 0f,
    ): Float? {
        fun fits(s: Float): Boolean {
            var h = 0f
            for ((i, l) in lines.withIndex()) {
                val size = s * l.rel
                val m = l.measure ?: measure
                if (l.inline) {
                    val total = l.texts.sumOf { m.width(it, size).toDouble() } + l.padPx * (l.texts.size - 1)
                    if (total > width) return false
                } else {
                    val col = width / l.texts.size - l.padPx
                    if (col <= 0) return false
                    if (l.texts.any { m.width(it, size) > col }) return false
                }
                h += size * (l.lineHeight ?: lineHeight)
                if (i > 0) h += gapRel * s
            }
            return h <= height
        }
        if (!fits(minScale)) return null
        if (fits(maxScale)) return maxScale
        var lo = minScale; var hi = maxScale
        repeat(18) {
            val mid = (lo + hi) / 2
            if (fits(mid)) lo = mid else hi = mid
        }
        return lo
    }

    /** The largest size in [min]..[max] at which wrapped [text] fits the box; null if it does not fit at [min]. */
    fun block(text: String, width: Float, height: Float, max: Float, min: Float, measure: BlockMeasure): Float? {
        if (width <= 0 || measure.height(text, width, min) > height) return null
        if (measure.height(text, width, max) <= height) return max
        var lo = min; var hi = max
        repeat(16) {
            val mid = (lo + hi) / 2
            if (measure.height(text, width, mid) <= height) lo = mid else hi = mid
        }
        return lo
    }
}
