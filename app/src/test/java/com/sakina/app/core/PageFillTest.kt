package com.sakina.app.core

import com.sakina.app.core.mushaf.PageFill
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** The printed page laid out to fill a phone screen, on small renderings of real pages (480 px wide). */
class PageFillTest {
    private val unit = 382.677f / 480f

    private class Img(val px: IntArray, val w: Int, val h: Int)

    /** Pages stored as "w h\n" and RGB bytes, gzipped (no image decoder in Android unit tests). */
    private fun load(page: Int): Img {
        val bytes = java.util.zip.GZIPInputStream(javaClass.getResourceAsStream("/mushaf/page$page.rgb.gz") ?: error("missing page $page")).use { it.readBytes() }
        val nl = bytes.indexOf('\n'.code.toByte())
        val (w, h) = String(bytes, 0, nl).trim().split(' ').map { it.toInt() }
        val px = IntArray(w * h) { i ->
            val o = nl + 1 + i * 3
            (0xFF shl 24) or ((bytes[o].toInt() and 255) shl 16) or ((bytes[o + 1].toInt() and 255) shl 8) or (bytes[o + 2].toInt() and 255)
        }
        return Img(px, w, h)
    }

    private fun lum(c: Int) = (((c shr 16) and 255) + ((c shr 8) and 255) + (c and 255)) / 3

    /** The frame drawn evenly at the plan's scale (here about one pixel per pixel of the rendering). */
    private fun source(img: Img, p: PageFill.Plan): IntArray {
        val f = p.frame
        val out = IntArray(p.width * p.srcH) { -1 }
        for (y in 0 until p.srcH) for (x in 0 until p.width) {
            val sx = floor((f.cropX + (x + 0.5f) / p.k) / unit).toInt()
            val sy = floor((f.cropY + (y + 0.5f) / p.k) / unit).toInt()
            if (sx in 0 until img.w && sy in 0 until img.h) out[y * p.width + x] = img.px[sy * img.w + sx]
        }
        return out
    }

    @Test
    fun findsTheFrameOnRightAndLeftPagesAndCentresIt() {
        for (page in listOf(295, 50, 604)) {
            val img = load(page)
            val f = PageFill.detect(img.px, img.w, img.h, unit)
            assertNotNull("frame on page $page", f); f!!
            // The frame's sides are equally far from the crop's edges: no more sitting off to one side.
            val left = f.innerX0 - f.cropX
            val right = f.cropX + f.cropW - f.innerX1
            assertTrue("page $page sides $left / $right", abs(left - right) < 3f)
            assertTrue("page $page text width ${f.innerX1 - f.innerX0}", f.innerX1 - f.innerX0 in 240f..280f)
            assertTrue("page $page text height ${f.innerY1 - f.innerY0}", f.innerY1 - f.innerY0 in 400f..450f)
            assertTrue("header above the frame on page $page", f.frameTop - f.cropY > 8f)
        }
        // A right-hand page has its frame towards the left of the paper, and the hizb mark beside it is left out.
        val odd = PageFill.detect(load(295).px, 480, 687, unit)!!
        assertTrue(odd.cropX + odd.cropW / 2 < 382.677f / 2)
        assertTrue("the hizb mark is not in the crop", (odd.cropX + odd.cropW) / unit < 405f)
        val even = PageFill.detect(load(50).px, 480, 687, unit)!!
        assertTrue(even.cropX + even.cropW / 2 > 382.677f / 2)
        // The header's middle is free (between the juz and the surah names).
        assertTrue(odd.gapX1 - odd.gapX0 > 60f)
    }

    @Test
    fun theOpeningPagesAreLeftAsPrinted() {
        val img = load(1)
        assertNull(PageFill.detect(img.px, img.w, img.h, unit))
    }

    @Test
    fun aTallScreenSpreadsTheLinesAndAWideOneDoesNot() {
        val img = load(295)
        val f = PageFill.detect(img.px, img.w, img.h, unit)!!
        val w = (f.cropW / unit).roundToInt()
        val p = PageFill.plan(f, w, (w * 2.1f).toInt())!!
        assertEquals(w * 2.1f, (p.a + p.bOut + p.c + 2 * p.yOff).toFloat(), 2f)
        assertTrue("stretch ${p.stretch}", p.stretch > 1.2f && p.stretch <= PageFill.MAX_STRETCH)
        // A very tall screen: the lines spread no further than the limit and the page is centred.
        val tall = PageFill.plan(f, w, w * 4)!!
        assertEquals(PageFill.MAX_STRETCH, tall.stretch, 0.01f)
        assertTrue(tall.yOff > 0)
        // Landscape or a tablet: as printed.
        assertNull(PageFill.plan(f, w, (w * 1.3f).toInt()))
    }

    @Test
    fun theCutsRunThroughTheWhiteBetweenLines() {
        for (page in listOf(295, 50, 604)) {
            val img = load(page)
            val f = PageFill.detect(img.px, img.w, img.h, unit)!!
            val w = (f.cropW / unit).roundToInt()
            val p = PageFill.plan(f, w, (w * 2.1f).toInt())!!
            val src = source(img, p)
            val seams = PageFill.seams(src, p)
            assertEquals(PageFill.LINES - 1, seams.size)
            val cols = p.x1 - p.x0
            for ((j, s) in seams.withIndex()) {
                assertEquals(cols, s.size)
                val inked = (0 until cols).count { x -> lum(src[(p.a + s[x]) * p.width + p.x0 + x]) < 160 }
                assertTrue("page $page cut $j crosses ink in $inked of $cols columns", inked <= cols / 100)
                if (j > 0) assertTrue("cuts in order", (0 until cols).all { x -> s[x] > seams[j - 1][x] })
            }
        }
    }

    @Test
    fun fillingAddsOnlyWhiteAndZoomingDrawsTheSameRows() {
        val img = load(295)
        val f = PageFill.detect(img.px, img.w, img.h, unit)!!
        val w = (f.cropW / unit).roundToInt()
        val h = (w * 2.1f).toInt()
        val p = PageFill.plan(f, w, h)!!
        val src = source(img, p)
        val L = PageFill.Layout(p, PageFill.seams(src, p))
        assertTrue("room is shared out", L.topPad + L.inserts.sum() <= p.bOut - p.b)
        val out = IntArray(w * h)
        PageFill.compose(L, 1f, 0f, 0f, w, h, out, src, p.srcH, 0f)
        // Every ink pixel of the text area is still there once (only white rows were added).
        fun ink(a: IntArray, width: Int, y0: Int, y1: Int) = (y0 until y1).sumOf { y -> (p.x0 until p.x1).count { x -> lum(a[y * width + x]) < 120 } }
        val before = ink(src, w, p.a, p.a + p.b)
        val after = ink(out, w, p.yOff + p.a, p.yOff + p.a + p.bOut)
        assertTrue("ink $before -> $after", abs(after - before) <= before / 100)
        // Header and page number unchanged.
        for (y in 0 until p.a) for (x in 0 until w) assertEquals(src[y * w + x], out[(p.yOff + y) * w + x])
        for (y in 0 until p.c) for (x in 0 until w) assertEquals(src[(p.a + p.b + y) * w + x], out[(p.yOff + p.a + p.bOut + y) * w + x])

        // Zoomed ×2 on the middle of the page: the same pixels, each drawn twice as large.
        val s = 2f
        val ox = -(w / 2).toFloat(); val oy = -(h / 2).toFloat()
        val (lo, hi) = L.sourceRange((0 - oy) / s - p.yOff, (h - oy) / s - p.yOff)
        val rows = ceil((hi - lo) * s).toInt() + 1
        val src2 = IntArray(w * rows) { i ->
            val ty = i / w; val vx = i % w
            val sy = floor(lo + (ty + 0.5f) / s).toInt().coerceIn(0, p.srcH - 1)
            val sx = floor((vx + 0.5f - ox) / s).toInt().coerceIn(0, w - 1)
            src[sy * w + sx]
        }
        val out2 = IntArray(w * h)
        PageFill.compose(L, s, ox, oy, w, h, out2, src2, rows, lo)
        val expected = IntArray(w * h) { i ->
            val vy = i / w; val vx = i % w
            out[floor((vy + 0.5f - oy) / s).toInt() * w + floor((vx + 0.5f - ox) / s).toInt()]
        }
        assertArrayEquals(expected, out2)
    }
}
