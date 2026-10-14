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

    fun cached(page: Int, width: Int): Bitmap? = cache.get("$page@$width")

    /** The whole page at [width] pixels wide. */
    suspend fun page(file: File, page: Int, width: Int): Bitmap? {
        cached(page, width)?.let { return it }
        val bmp = draw(file, width, (width * PAGE_H / PAGE_W).roundToInt(), null) ?: return null
        cache.put("$page@$width", bmp)
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
