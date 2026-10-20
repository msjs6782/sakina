package com.sakina.app.data

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/**
 * Draws pages of the original PDFs with Android's PdfRenderer. Nothing is redrawn or re-typeset:
 * the page's own vector content is rasterised at the size of the screen.
 */
object MushafRender {
    /** PdfRenderer instances must not render at the same time on some Android versions. */
    private val lock = Mutex()

    /** Page size in PDF points (all pages of this edition are 382.68 × 547.09). */
    const val PAGE_W = 382.677f
    const val PAGE_H = 547.086f

    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt().coerceAtLeast(24 shl 20)) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }

    /** The part of a page that is drawn, in PDF points (the whole page, or the printed frame without the white paper). */
    data class Crop(val x: Float, val y: Float, val w: Float, val h: Float) {
        val ratio: Float get() = w / h
        companion object { val FULL = Crop(0f, 0f, PAGE_W, PAGE_H) }
    }

    private val crops = java.util.concurrent.ConcurrentHashMap<Int, Crop>()

    fun cachedCrop(page: Int): Crop? = crops[page]

    /**
     * The printed part of a page: everything that is not white paper (frame, header, page number), with a small
     * margin. Found once per page from a small rendering and kept for the session.
     */
    suspend fun crop(file: File, page: Int): Crop {
        crops[page]?.let { return it }
        val w = 240
        val h = (w * PAGE_H / PAGE_W).roundToInt()
        val bmp = draw(file, w, h, null) ?: return Crop.FULL
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        bmp.recycle()
        fun ink(c: Int) = ((c shr 16) and 0xFF) < 228 || ((c shr 8) and 0xFF) < 228 || (c and 0xFF) < 228
        var minX = w; var maxX = -1; var minY = h; var maxY = -1
        for (y in 0 until h) for (x in 0 until w) if (ink(px[y * w + x])) {
            if (x < minX) minX = x; if (x > maxX) maxX = x
            if (y < minY) minY = y; if (y > maxY) maxY = y
        }
        val c = if (maxX < minX || maxY < minY || (maxX - minX) < w / 3) Crop.FULL else {
            val k = PAGE_W / w
            val pad = PAGE_W * 0.012f
            val x0 = (minX * k - pad).coerceAtLeast(0f)
            val y0 = (minY * k - pad).coerceAtLeast(0f)
            val x1 = ((maxX + 1) * k + pad).coerceAtMost(PAGE_W)
            val y1 = ((maxY + 1) * k + pad).coerceAtMost(PAGE_H)
            Crop(x0, y0, x1 - x0, y1 - y0)
        }
        crops[page] = c
        Log.d(MushafRepository.TAG, "crop page $page: ${c.x.roundToInt()},${c.y.roundToInt()} ${c.w.roundToInt()}x${c.h.roundToInt()}")
        return c
    }

    private fun key(page: Int, width: Int, c: Crop) = "$page@$width@${c.x.roundToInt()},${c.y.roundToInt()},${c.w.roundToInt()}"

    fun cached(page: Int, width: Int, c: Crop = Crop.FULL): Bitmap? = cache.get(key(page, width, c))

    /** The page (or its [c] part) at [width] pixels wide. */
    suspend fun page(file: File, page: Int, width: Int, c: Crop = Crop.FULL): Bitmap? {
        cached(page, width, c)?.let { return it }
        val k = width / c.w
        val m = Matrix().apply { setTranslate(-c.x, -c.y); postScale(k, k) }
        val bmp = draw(file, width, (width / c.ratio).roundToInt(), m) ?: return null
        cache.put(key(page, width, c), bmp)
        return bmp
    }

    /**
     * Part of the page at a zoom: a bitmap of the viewport size, with [matrix] mapping page points to
     * viewport pixels. Memory stays the size of the screen whatever the zoom.
     */
    suspend fun region(file: File, width: Int, height: Int, matrix: Matrix): Bitmap? = draw(file, width, height, matrix)

    private suspend fun draw(file: File, width: Int, height: Int, matrix: Matrix?): Bitmap? = withContext(Dispatchers.IO) {
        if (width <= 0 || height <= 0 || !file.isFile) return@withContext null
        lock.withLock {
            try {
                val t0 = System.nanoTime()
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                    PdfRenderer(fd).use { r ->
                        r.openPage(0).use { p ->
                            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(Color.WHITE)
                            val m = matrix ?: Matrix().apply { setScale(width / p.width.toFloat(), height / p.height.toFloat()) }
                            p.render(bmp, null, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            Log.d(MushafRepository.TAG, "rendered ${file.name} ${width}x$height${if (matrix != null) " region" else ""} in ${(System.nanoTime() - t0) / 1_000_000} ms")
                            bmp
                        }
                    }
                }
            } catch (t: Throwable) {
                // A file that PdfRenderer cannot open is reported, not shown.
                Log.w(MushafRepository.TAG, "cannot render ${file.name}: $t")
                null
            }
        }
    }

    fun clear() = cache.evictAll()
}
