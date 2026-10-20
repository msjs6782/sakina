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

/**
 * Stretches an Arabic word with tatweel (kashida) to fill a width, as in a calligraphic day name. The tatweel
 * goes before the last letter that the previous letter joins: الاثنيـــن، السبـــت، الثلاثـــاء، الجمعـــة.
 */
object Kashida {
    const val TATWEEL = 'ـ'

    /** Letters that do not join the letter after them (they still join the one before). */
    private const val RIGHT_JOINING = "آأؤإاةدذرزوءٱ"

    private fun joinsNext(c: Char) = c in 'ؠ'..'ي' && c != TATWEEL && c !in RIGHT_JOINING
    private fun isLetter(c: Char) = c in 'ؠ'..'ي' && c != TATWEEL

    /** The divine name keeps its ligature: it is never stretched. */
    private val NEVER = setOf("الله", "لله", "بالله", "والله", "اللهم")

    /** Index where tatweel can go (before word[i]), or null when the word cannot be stretched (no joins, or Latin). */
    fun point(word: String): Int? {
        if (word in NEVER) return null
        for (i in word.length - 1 downTo 1) {
            if (isLetter(word[i]) && joinsNext(word[i - 1])) return i
        }
        return null
    }

    fun stretch(word: String, count: Int): String {
        val at = point(word) ?: return word
        return word.substring(0, at) + TATWEEL.toString().repeat(count.coerceAtLeast(0)) + word.substring(at)
    }

    /** [phrase] with [count] tatweel shared out over its words (round robin, from the last word back). */
    fun stretchPhrase(phrase: String, count: Int): String {
        val words = phrase.split(' ')
        val can = words.indices.filter { point(words[it]) != null }
        if (can.isEmpty() || count <= 0) return phrase
        val per = IntArray(words.size)
        for (k in 0 until count) per[can[can.size - 1 - (k % can.size)]]++
        return words.mapIndexed { i, w -> if (per[i] > 0) stretch(w, per[i]) else w }.joinToString(" ")
    }

    /** The longest stretch of [phrase] (tatweel over all its words) whose [width] stays within [target]. */
    fun fillPhrase(phrase: String, target: Float, max: Int = 120, width: (String) -> Float): String {
        if (phrase.split(' ').none { point(it) != null }) return phrase
        if (width(phrase) > target) return phrase
        var lo = 0
        var hi = max
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (width(stretchPhrase(phrase, mid)) <= target) lo = mid else hi = mid - 1
        }
        return stretchPhrase(phrase, lo)
    }

    /** The longest stretch of [word] whose [width] stays within [target] (at most [max] tatweel). */
    fun fill(word: String, target: Float, max: Int = 80, width: (String) -> Float): String {
        if (point(word) == null) return word
        var lo = 0
        var hi = max
        if (width(stretch(word, 0)) > target) return word
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (width(stretch(word, mid)) <= target) lo = mid else hi = mid - 1
        }
        return stretch(word, lo)
    }
}
