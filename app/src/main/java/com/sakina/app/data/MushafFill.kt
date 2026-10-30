package com.sakina.app.data

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.pdf.PdfRenderer
import android.util.Log
import android.util.LruCache
import com.sakina.app.core.mushaf.PageFill
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Printed pages laid out to fill the screen (see [PageFill]): drawn from the PDF's own vectors in pieces, the text
 * area at its printed size with white added between the lines, the frame's sides drawn taller.
 */
object MushafFill {
    /** A page filled to one screen size. */
    class Filled(val bitmap: Bitmap, val layout: PageFill.Layout) {
        private val p get() = layout.plan

        /** The middle of the header line, in pixels of the filled page (where a hizb label can go). */
        val headerX: Float get() = ((p.frame.gapX0 + p.frame.gapX1) / 2 - p.frame.cropX) * p.k
        val headerY: Float get() = p.yOff + (p.frame.frameTop - p.frame.cropY) * p.k / 2
        val headerRoom: Float get() = (p.frame.gapX1 - p.frame.gapX0) * p.k
    }

    private val frames = ConcurrentHashMap<Int, PageFill.Frame>()
    private val without = ConcurrentHashMap.newKeySet<Int>()
    private val layouts = LruCache<String, PageFill.Layout>(16)

    private fun key(page: Int, w: Int, h: Int) = "fill:$page@${w}x$h"

    fun cachedFrame(page: Int): PageFill.Frame? = frames[page]

    /** True once the page is known to have no usual frame (it is shown as printed). */
    fun knownWithout(page: Int): Boolean = page in without

    fun cached(page: Int, w: Int, h: Int): Filled? {
        val k = key(page, w, h)
        val l = layouts.get(k) ?: return null
        val b = MushafRender.cachedBitmap(k) ?: return null
        return Filled(b, l)
    }

    /** The frame of a page, found once from a small drawing of it. */
    suspend fun frame(file: File, page: Int): PageFill.Frame? {
        frames[page]?.let { return it }
        if (page in without) return null
        val w = 480
        val h = (w * MushafRender.PAGE_H / MushafRender.PAGE_W).roundToInt()
        val bmp = MushafRender.whole(file, w, h) ?: return null
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        bmp.recycle()
        val f = PageFill.detect(px, w, h, MushafRender.PAGE_W / w)
        if (f == null) without += page else frames[page] = f
        Log.d(MushafRepository.TAG, "frame page $page: " + (f?.let { "text ${it.innerX0.roundToInt()},${it.innerY0.roundToInt()} to ${it.innerX1.roundToInt()},${it.innerY1.roundToInt()}" } ?: "none, shown as printed"))
        return f
    }

    /** The page filled to [w]×[h] pixels; null when it is better shown as printed (no frame, or a wide screen). */
    suspend fun page(file: File, page: Int, w: Int, h: Int): Filled? {
        cached(page, w, h)?.let { return it }
        val f = frame(file, page) ?: return null
        val p = PageFill.plan(f, w, h) ?: return null
        val made = MushafRender.withPage(file, "${w}x$h fill") { pg ->
            // The frame drawn evenly: its text area gives the cuts, and it is the source of everything but the sides.
            val src = Bitmap.createBitmap(w, p.srcH, Bitmap.Config.ARGB_8888)
            src.eraseColor(Color.WHITE)
            pg.render(src, null, Matrix().apply { setTranslate(-f.cropX, -f.cropY); postScale(p.k, p.k) }, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            val sp = IntArray(w * p.srcH)
            src.getPixels(sp, 0, w, 0, 0, w, p.srcH)
            src.recycle()
            val layout = PageFill.Layout(p, PageFill.seams(sp, p))
            Filled(compose(pg, layout, 1f, 0f, 0f, w, h, sp, p.srcH, 0f), layout)
        } ?: return null
        Log.d(MushafRepository.TAG, "filled page $page: lines ${"%.2f".format(java.util.Locale.ROOT, p.stretch)}× apart, ${p.yOff} rows above")
        val k = key(page, w, h)
        layouts.put(k, made.layout)
        MushafRender.keep(k, made.bitmap)
        return made
    }

    /**
     * The filled page zoomed: a [vw]×[vh] drawing where the filled page's pixel (X, Y) lands at (X·s + ox, Y·s + oy),
     * drawn again from the PDF at full sharpness. Memory stays the size of the screen whatever the zoom.
     */
    suspend fun region(file: File, layout: PageFill.Layout, s: Float, ox: Float, oy: Float, vw: Int, vh: Int): Bitmap? {
        val p = layout.plan
        val y0 = ((0f - oy) / s - p.yOff).coerceIn(0f, p.pageRows.toFloat())
        val y1 = ((vh - oy) / s - p.yOff).coerceIn(0f, p.pageRows.toFloat())
        val (lo, hi) = layout.sourceRange(y0, y1)
        val rows = (ceil((hi - lo) * s).toInt() + 1).coerceIn(1, 4 * vh)
        return MushafRender.withPage(file, "${vw}x$vh fill region") { pg ->
            val k = p.k * s
            val src = Bitmap.createBitmap(vw, rows, Bitmap.Config.ARGB_8888)
            src.eraseColor(Color.WHITE)
            pg.render(src, null, Matrix().apply { setTranslate(-p.frame.cropX, -p.frame.cropY); postScale(k, k); postTranslate(ox, -lo * s) }, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            val sp = IntArray(vw * rows)
            src.getPixels(sp, 0, vw, 0, 0, vw, rows)
            src.recycle()
            compose(pg, layout, s, ox, oy, vw, vh, sp, rows, lo)
        }
    }

    /** The frame's sides beside the text, drawn taller, then everything else placed from the even drawing [sp]. */
    private fun compose(pg: PdfRenderer.Page, layout: PageFill.Layout, s: Float, ox: Float, oy: Float, vw: Int, vh: Int, sp: IntArray, rows: Int, srcTop: Float): Bitmap {
        val p = layout.plan
        val out = Bitmap.createBitmap(vw, vh, Bitmap.Config.ARGB_8888)
        out.eraseColor(Color.WHITE)
        val top = oy + (p.yOff + p.a) * s
        val bottom = oy + (p.yOff + p.a + p.bOut) * s
        val clip = Rect(0, max(0, floor(top).toInt()), vw, min(vh, ceil(bottom).toInt()))
        val sidesShown = ox + p.x0 * s > 0f || ox + p.x1 * s < vw
        if (!clip.isEmpty && sidesShown) {
            val k = p.k * s
            val m = Matrix().apply { setTranslate(-p.frame.cropX, -p.innerTop); postScale(k, k * p.stretch); postTranslate(ox, top) }
            pg.render(out, clip, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        }
        val op = IntArray(vw * vh)
        out.getPixels(op, 0, vw, 0, 0, vw, vh)
        PageFill.compose(layout, s, ox, oy, vw, vh, op, sp, rows, srcTop)
        out.setPixels(op, 0, vw, 0, 0, vw, vh)
        return out
    }
}
