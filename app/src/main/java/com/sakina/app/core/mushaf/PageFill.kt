package com.sakina.app.core.mushaf

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A printed page laid out to fill a phone's screen.
 *
 * The green frame is cut out of the white paper and centred (the margins differ between left and right pages, which
 * made pages sit off to one side), and the screen's extra height goes between the fifteen lines: each cut follows a
 * white path between two lines, so no letter or mark is redrawn, squeezed or cut. Only the frame's plain sides are
 * drawn taller. Pages without the usual frame (the first two) are left as printed.
 *
 * Pixels are ARGB ints, as Android's Bitmap gives them.
 */
object PageFill {
    /** Lines on every page of this edition except the first two. */
    const val LINES = 15

    /** The lines never move further apart than this; a taller screen gets the page centred. */
    const val MAX_STRETCH = 1.7f

    /** Where the printed frame is on a page, in page units (PDF points). */
    data class Frame(
        /** The part shown: the frame with the header above it and the page number below, without the paper around. */
        val cropX: Float, val cropY: Float, val cropW: Float, val cropH: Float,
        /** The text area inside the frame's sides. */
        val innerX0: Float, val innerY0: Float, val innerX1: Float, val innerY1: Float,
        /** The frame's outer top: the header line sits between the crop's top and here. */
        val frameTop: Float,
        /** The widest empty stretch of the header line (between the juz and the surah names). */
        val gapX0: Float, val gapX1: Float,
    )

    private fun runs(v: FloatArray, above: Float): List<IntRange> {
        val out = ArrayList<IntRange>()
        var start = -1
        for (i in v.indices) {
            if (v[i] > above && start < 0) start = i
            if (v[i] <= above && start >= 0) { out += start until i; start = -1 }
        }
        if (start >= 0) out += start until v.size
        return out
    }

    private fun lum(c: Int): Int = (((c shr 16) and 255) + ((c shr 8) and 255) + (c and 255)) / 3

    /**
     * Finds the frame on a small drawing of the whole page ([w]×[h] pixels, [unit] page units per pixel): its sides
     * are the columns that are coloured (light green) most of the way down, its top and bottom the rows coloured most
     * of the way across. Null when the page has no such frame or it looks unusual.
     */
    fun detect(px: IntArray, w: Int, h: Int, unit: Float): Frame? {
        if (w < 50 || h < 50 || px.size < w * h) return null
        val frame = BooleanArray(w * h)
        val dark = BooleanArray(w * h)
        for (i in 0 until w * h) {
            val c = px[i]
            val r = (c shr 16) and 255; val g = (c shr 8) and 255; val b = c and 255
            val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
            val l = (r + g + b) / 3
            frame[i] = mx - mn > 14 && l > 140
            dark[i] = l < 150
        }
        val col = FloatArray(w) { x -> var n = 0; for (y in 0 until h) if (frame[y * w + x]) n++; n.toFloat() / h }
        val sides = runs(col, 0.45f)
        if (sides.size < 2) return null
        var l0 = sides.first().first; val l1 = sides.first().last
        val r0 = sides.last().first; var r1 = sides.last().last
        val band = ((l1 - l0 + 1) + (r1 - r0 + 1)) / 2f
        if (band < w * 0.01f || band > w * 0.12f) return null
        // Out to the tips of the scalloped edge.
        while (l0 > 0 && col[l0 - 1] > 0.03f) l0--
        while (r1 < w - 1 && col[r1 + 1] > 0.03f) r1++
        val span = r1 - l0 + 1
        val row = FloatArray(h) { y -> var n = 0; for (x in l0..r1) if (frame[y * w + x]) n++; n.toFloat() / span }
        val bands = runs(row, 0.45f)
        if (bands.size < 2) return null
        var t0 = bands.first().first; val t1 = bands.first().last
        val b0 = bands.last().first; var b1 = bands.last().last
        fun plausible(t: Int) = t > 0.4f * band && t < 2.2f * band
        if (!plausible(t1 - t0 + 1) || !plausible(b1 - b0 + 1)) return null
        while (t0 > 0 && row[t0 - 1] > 0.03f) t0--
        while (b1 < h - 1 && row[b1 + 1] > 0.03f) b1++
        val ix0 = l1 + 1; val iy0 = t1 + 1; val ix1 = r0; val iy1 = b0
        if (ix1 - ix0 < w * 0.55f || iy1 - iy0 < h * 0.55f) return null
        // The header above the frame and the page number below it (ink within the frame's width only: the hizb
        // marks in the outer margin are left out).
        fun inkRow(y: Int): Boolean { for (x in l0..r1) if (dark[y * w + x]) return true; return false }
        var top = t0
        for (y in 0 until t0) if (inkRow(y)) { top = y; break }
        var bot = b1
        for (y in h - 1 downTo b1 + 1) if (inkRow(y)) { bot = y; break }
        // The header's widest gap, for a label of our own.
        var gx0 = l0; var gx1 = r1 + 1
        if (top < t0) {
            val ink = BooleanArray(span) { i -> var hit = false; for (y in top until t0) if (dark[y * w + l0 + i]) { hit = true; break }; hit }
            val first = ink.indexOfFirst { it }; val last = ink.indexOfLast { it }
            if (first >= 0) {
                var best = 0; var bs = 0; var s = -1
                for (i in first..last + 1) {
                    val on = i <= last && ink[i]
                    if (!on && s < 0) s = i
                    if (on && s >= 0) { if (i - s > best) { best = i - s; bs = s }; s = -1 }
                }
                gx0 = l0 + bs; gx1 = l0 + bs + best
            }
        }
        val pad = 0.012f * w
        val cx0 = max(0f, l0 - pad * 0.5f); val cx1 = min(w.toFloat(), r1 + 1 + pad * 0.5f)
        val cy0 = max(0f, top - pad); val cy1 = min(h.toFloat(), bot + 1 + pad)
        return Frame(
            cx0 * unit, cy0 * unit, (cx1 - cx0) * unit, (cy1 - cy0) * unit,
            ix0 * unit, iy0 * unit, ix1 * unit, iy1 * unit,
            t0 * unit, gx0 * unit, gx1 * unit,
        )
    }

    /**
     * The page's pieces at one screen size, in pixels. The frame drawn evenly at [k] pixels per page unit is
     * [width]×[srcH]; its rows are [a] (header and the frame's top), [b] (the text area with the frame's sides) and
     * [c] (the frame's bottom and the page number). On screen the text area takes [bOut] rows.
     */
    class Plan(
        val frame: Frame,
        val width: Int,
        val height: Int,
        val k: Float,
        val srcH: Int,
        val a: Int,
        val b: Int,
        val c: Int,
        val bOut: Int,
        /** Empty rows above the page (on a screen taller than the stretch allows). */
        val yOff: Int,
        /** The text area's columns. */
        val x0: Int,
        val x1: Int,
    ) {
        val stretch: Float get() = bOut.toFloat() / b
        /** The text area's top and bottom in page units, snapped to the rows above. */
        val innerTop: Float get() = frame.cropY + a / k
        val innerBottom: Float get() = frame.cropY + (a + b) / k
        val pageRows: Int get() = a + bOut + c
    }

    /** Null when the page is already as tall as the screen allows (a tablet, landscape): it is then shown as printed. */
    fun plan(f: Frame, width: Int, height: Int): Plan? {
        if (width <= 0 || height <= 0 || f.cropW <= 0f) return null
        val k = width / f.cropW
        val srcH = (f.cropH * k).roundToInt()
        val a = ((f.innerY0 - f.cropY) * k).roundToInt().coerceIn(0, srcH)
        val ab = ((f.innerY1 - f.cropY) * k).roundToInt().coerceIn(a, srcH)
        val b = ab - a
        val c = srcH - ab
        val room = height - a - c
        if (b < LINES * 4 || room < b * 1.03f) return null
        val bOut = min(room, (b * MAX_STRETCH).toInt())
        val yOff = (height - (a + bOut + c)) / 2
        val x0 = ((f.innerX0 - f.cropX) * k).roundToInt().coerceIn(0, width)
        val x1 = ((f.innerX1 - f.cropX) * k).roundToInt().coerceIn(x0, width)
        return Plan(f, width, height, k, srcH, a, b, c, bOut, yOff, x0, x1)
    }

    /**
     * The fourteen cuts between the lines: for each, the cheapest path from one side of the text area to the other
     * through the white between two lines (one row per column, counted from the text area's top). [px] is the frame
     * drawn evenly at the plan's scale, [Plan.width]×[Plan.srcH].
     */
    fun seams(px: IntArray, p: Plan): Array<IntArray> {
        val w = p.width
        val cols = p.x1 - p.x0
        val lh = p.b.toFloat() / LINES
        return Array(LINES - 1) { j ->
            val centre = (j + 1) * lh
            val lo = max(0, (centre - 0.45f * lh).toInt())
            val hi = min(p.b, (centre + 0.45f * lh).toInt())
            val r = hi - lo
            if (r <= 0 || cols <= 0) return@Array IntArray(max(cols, 0)) { centre.toInt() }
            val cost = FloatArray(r * cols)
            for (y in 0 until r) for (x in 0 until cols) {
                val i = (p.a + lo + y) * w + p.x0 + x
                val d = if (i in px.indices) 1f - lum(px[i]) / 255f else 1f
                cost[y * cols + x] = d * d * 100f + 0.02f
            }
            var acc = FloatArray(r) { cost[it * cols] }
            var nxt = FloatArray(r)
            val back = ByteArray(r * cols)
            for (x in 1 until cols) {
                for (y in 0 until r) {
                    var best = acc[y]; var from = 0
                    if (y > 0 && acc[y - 1] + 0.3f < best) { best = acc[y - 1] + 0.3f; from = -1 }
                    if (y < r - 1 && acc[y + 1] + 0.3f < best) { best = acc[y + 1] + 0.3f; from = 1 }
                    nxt[y] = best + cost[y * cols + x]
                    back[y * cols + x] = from.toByte()
                }
                val t = acc; acc = nxt; nxt = t
            }
            var y = 0
            for (i in 1 until r) if (acc[i] < acc[y]) y = i
            val path = IntArray(cols)
            for (x in cols - 1 downTo 0) {
                path[x] = y + lo
                if (x > 0) y += back[y * cols + x]
            }
            path
        }
    }

    /** A plan with its cuts: where each text column's rows land on screen. */
    class Layout(val plan: Plan, val seams: Array<IntArray>) {
        /** Rows added above the first line, at each cut, and (the rest) below the last line. */
        val topPad: Int
        val inserts: IntArray

        init {
            val per = (plan.bOut - plan.b).toFloat() / LINES
            topPad = (per / 2).roundToInt()
            inserts = IntArray(LINES - 1) { j -> (per * (j + 1)).roundToInt() - (per * j).roundToInt() }
        }

        private fun cut(j: Int, col: Int): Int = seams[j].let { s -> if (s.isEmpty()) 0 else s[col.coerceIn(0, s.size - 1)] }.coerceIn(0, plan.b)

        /**
         * The source row (text-area rows, scale k) shown at text row [rel] of column [col]; for added rows, the
         * row whose colour they repeat. Rows come in order, so [Walker] keeps its place.
         */
        inner class Walker(val col: Int) {
            private var j = 0
            private var start = topPad.toFloat() // screen row where piece j starts
            private var from = 0 // its first source row
            private var to = cut(0, col)

            /** Returns the source row, as a float for the in-between rows of a zoomed drawing. */
            fun source(rel: Float): Float {
                if (rel < topPad) return 0.5f
                while (j < LINES - 1 && rel >= start + (to - from) + inserts[j]) {
                    start += (to - from) + inserts[j]
                    from = to
                    j++
                    to = if (j < LINES - 1) max(cut(j, col), from) else plan.b
                }
                val inPiece = rel - start
                return if (inPiece < to - from) from + inPiece
                else if (j < LINES - 1) to + 0.5f // the added rows repeat the cut's own (white) row
                else plan.b - 0.5f
            }
        }

        /**
         * The source rows (scale k, from the frame's top) needed for the screen rows [y0]..[y1] of the filled page
         * (counted from the page's top, below [Plan.yOff]).
         */
        fun sourceRange(y0: Float, y1: Float): Pair<Float, Float> {
            val p = plan
            fun even(y: Float): Float? = when {
                y < p.a -> y
                y >= p.a + p.bOut -> p.a + p.b + (y - p.a - p.bOut)
                else -> null
            }
            var lo = even(y0)
            var hi = even(y1)
            if (lo == null || hi == null) {
                var mn = Float.MAX_VALUE; var mx = -Float.MAX_VALUE
                for (c in 0 until p.x1 - p.x0) {
                    if (lo == null) mn = min(mn, p.a + Walker(c).source(y0 - p.a))
                    if (hi == null) mx = max(mx, p.a + Walker(c).source(y1 - p.a))
                }
                if (lo == null) lo = if (mn == Float.MAX_VALUE) p.a.toFloat() else mn
                if (hi == null) hi = if (mx == -Float.MAX_VALUE) (p.a + p.b).toFloat() else mx
            }
            // Whole rows, so a zoomed drawing samples the same rows as the unzoomed one.
            return max(0f, floor(lo - 1f)) to min(p.srcH.toFloat(), kotlin.math.ceil(hi + 1f))
        }
    }

    /**
     * Draws the filled page into [out] ([vw]×[vh], row by row), seen at scale [s] with the filled page's pixel
     * (X, Y) landing at (X·s + [ox], Y·s + [oy]). [src] is the frame drawn evenly at scale k·s, [vw] wide and aligned
     * with [out]'s columns, starting at source row [srcTop] (scale k) and [srcRows] tall. The frame's sides beside
     * the text are left untouched (they are drawn stretched separately), and so is everything off the page.
     */
    fun compose(L: Layout, s: Float, ox: Float, oy: Float, vw: Int, vh: Int, out: IntArray, src: IntArray, srcRows: Int, srcTop: Float) {
        val p = L.plan
        if (srcRows <= 0) return
        fun pick(row: Float, vx: Int): Int {
            val ty = floor((row - srcTop) * s).toInt().coerceIn(0, srcRows - 1)
            return src[ty * vw + vx]
        }
        val first = max(0, floor(oy + p.yOff * s).toInt())
        val last = min(vh, kotlin.math.ceil(oy + (p.yOff + p.pageRows) * s).toInt())
        for (vx in 0 until vw) {
            val X = (vx + 0.5f - ox) / s
            if (X < 0f || X >= p.width) continue
            val inText = X >= p.x0 && X < p.x1
            val walker = if (inText) L.Walker((X - p.x0).toInt()) else null
            for (vy in first until last) {
                val Y = (vy + 0.5f - oy) / s - p.yOff
                if (Y < 0f || Y >= p.pageRows) continue
                val row = when {
                    Y < p.a -> Y
                    Y >= p.a + p.bOut -> p.a + p.b + (Y - p.a - p.bOut)
                    walker != null -> p.a + walker.source(Y - p.a)
                    else -> continue
                }
                out[vy * vw + vx] = pick(row, vx)
            }
        }
    }
}
