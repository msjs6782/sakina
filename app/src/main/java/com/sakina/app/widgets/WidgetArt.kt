package com.sakina.app.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.LruCache
import com.sakina.app.R
import com.sakina.app.core.widgets.Contrast
import com.sakina.app.data.WidgetWordLook
import com.sakina.app.ui.theme.SkyPeriod
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The 3D diorama behind widgets: a white mosque, palms and dunes at the six times of day (generated with Higgsfield in
 * one scene, see docs/ART.md). The veils keep white text at 4.5:1, measured by tools/art/prepare_3d.py.
 */
object SceneArt {
    fun drawable(p: SkyPeriod): Int = when (p) {
        SkyPeriod.FAJR -> R.drawable.scene_fajr
        SkyPeriod.MORNING -> R.drawable.scene_morning
        SkyPeriod.NOON -> R.drawable.scene_noon
        SkyPeriod.ASR -> R.drawable.scene_asr
        SkyPeriod.MAGHRIB -> R.drawable.scene_maghrib
        SkyPeriod.NIGHT -> R.drawable.scene_night
    }

    /** A 3:1 band from the bottom, for wide widgets. */
    fun wideDrawable(p: SkyPeriod): Int = when (p) {
        SkyPeriod.FAJR -> R.drawable.scene_fajr_wide
        SkyPeriod.MORNING -> R.drawable.scene_morning_wide
        SkyPeriod.NOON -> R.drawable.scene_noon_wide
        SkyPeriod.ASR -> R.drawable.scene_asr_wide
        SkyPeriod.MAGHRIB -> R.drawable.scene_maghrib_wide
        SkyPeriod.NIGHT -> R.drawable.scene_night_wide
    }

    /** Black veil, 0–1: the measured value with the same light floor as the painted sky. */
    fun scrim(p: SkyPeriod): Float = maxOf(0.14f, when (p) {
        SkyPeriod.FAJR -> 0f
        SkyPeriod.MORNING -> 0.45f
        SkyPeriod.NOON -> 0.38f
        SkyPeriod.ASR -> 0.38f
        SkyPeriod.MAGHRIB -> 0.30f
        SkyPeriod.NIGHT -> 0f
    })

    fun topColor(p: SkyPeriod): Int = when (p) {
        SkyPeriod.FAJR -> 0xFF161962.toInt()
        SkyPeriod.MORNING -> 0xFF8AB9E8.toInt()
        SkyPeriod.NOON -> 0xFF097CEC.toInt()
        SkyPeriod.ASR -> 0xFF6F93BE.toInt()
        SkyPeriod.MAGHRIB -> 0xFFC56978.toInt()
        SkyPeriod.NIGHT -> 0xFF010C27.toInt()
    }
}

/**
 * The glass panel in relief: a rim lit from the top left and shaded at the bottom right, light across the top and a
 * soft diagonal sheen. Over the wallpaper it is drawn as a see-through layer; over a painting the panel floats above
 * it, with the painting frosted behind the glass and a shadow beneath.
 */
object WidgetGlass {
    private val pictures = LruCache<Int, Bitmap>(3)

    /** A painting decoded once (at half size when the widget is small enough). */
    @Synchronized
    fun picture(context: Context, res: Int, minWidth: Int): Bitmap? {
        val sample = if (minWidth <= 640) 2 else 1
        val key = res * 4 + sample
        pictures.get(key)?.let { return it }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val bmp = runCatching { BitmapFactory.decodeResource(context.resources, res, opts) }.getOrNull() ?: return null
        pictures.put(key, bmp)
        return bmp
    }

    /** The part of [src] that fills a [w]×[h] box, as an image view's centre crop. */
    fun centerCrop(src: Bitmap, w: Int, h: Int): Rect {
        val sr = src.width.toFloat() / src.height
        val dr = w.toFloat() / h
        return if (sr > dr) {
            val cw = (src.height * dr).roundToInt().coerceIn(1, src.width)
            val x = (src.width - cw) / 2
            Rect(x, 0, x + cw, src.height)
        } else {
            val ch = (src.width / dr).roundToInt().coerceIn(1, src.height)
            val y = (src.height - ch) / 2
            Rect(0, y, src.width, y + ch)
        }
    }

    private fun roundRect(r: RectF, radius: Float) = Path().apply { addRoundRect(r, radius, radius, Path.Direction.CW) }

    /** The see-through layer over a glass panel on the wallpaper ([d] is pixels per dp in this bitmap). */
    fun overlay(w: Int, h: Int, d: Float, corner: Float, dark: Boolean): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val r = RectF(0f, 0f, w.toFloat(), h.toFloat())
        c.save()
        c.clipPath(roundRect(r, corner))
        sheen(c, r, dark)
        c.restore()
        rim(c, r, corner, d, dark)
        return out
    }

    /**
     * The panel floating over a painting: the painting fills the widget, the glass is inset by [inset] with a shadow
     * under it, the painting frosted behind it under a veil of at least [veil] (raised for white text to keep 3:1 when
     * [guard]), the light and the rim on top. Returns the picture and the veil used.
     */
    fun panel(pic: Bitmap, w: Int, h: Int, d: Float, corner: Float, inset: Float, dark: Boolean, veil: Float, guard: Boolean, back: Int): Pair<Bitmap, Float> {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val full = RectF(0f, 0f, w.toFloat(), h.toFloat())
        val filter = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        c.save()
        c.clipPath(roundRect(full, corner))
        c.drawColor(back)
        c.drawBitmap(pic, centerCrop(pic, w, h), Rect(0, 0, w, h), filter)
        val r = RectF(inset, inset, w - inset, h - inset)
        val rr = (corner - inset * 0.5f).coerceAtLeast(6f * d)
        // The painting behind the glass, blurred: scaled down, box-blurred, and drawn back up smoothly.
        val sw = max(4, (r.width() / (7f * d)).roundToInt())
        val sh = max(4, (r.height() / (7f * d)).roundToInt())
        val small = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888)
        Canvas(small).drawBitmap(out, Rect(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt()), Rect(0, 0, sw, sh), filter)
        boxBlur(small, 1, 3)
        var a = veil.coerceIn(0f, 0.95f)
        if (guard && dark) a = max(a, guardFor(small))
        // The shadow the panel casts on the painting.
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x73000000; maskFilter = BlurMaskFilter(5f * d, BlurMaskFilter.Blur.NORMAL) }
        c.drawRoundRect(RectF(r).apply { offset(0f, 2.2f * d) }, rr, rr, shadow)
        c.save()
        c.clipPath(roundRect(r, rr))
        c.drawBitmap(small, null, r, filter)
        c.drawColor(((a * 255).roundToInt() shl 24) or (if (dark) 0x000000 else 0xFFFFFF))
        sheen(c, r, dark)
        c.restore()
        rim(c, r, rr, d, dark)
        c.restore()
        small.recycle()
        return out to a
    }

    /** The black veil that brings the 85th-percentile brightness behind white text to 3:1. */
    private fun guardFor(b: Bitmap): Float {
        val px = IntArray(b.width * b.height)
        b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        val lum = px.map { Contrast.luminance(it) }.sorted()
        return Contrast.veilForWhite(lum[(lum.size * 0.85f).toInt().coerceAtMost(lum.size - 1)], 3.0).toFloat()
    }

    /** Light across the top, a soft diagonal streak, and a little shade at the bottom. */
    private fun sheen(c: Canvas, r: RectF, dark: Boolean) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, r.top, 0f, r.top + r.height() * 0.55f, Color.argb(if (dark) 46 else 80, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(r, p)
        val bw = r.width() * 0.2f
        val slant = r.height() * 0.75f
        for ((at, width, alpha) in listOf(Triple(0.16f, bw, if (dark) 30 else 44), Triple(0.42f, bw * 0.35f, if (dark) 20 else 30))) {
            val x0 = r.left + r.width() * at
            val streak = Path().apply {
                moveTo(x0 + slant, r.top); lineTo(x0 + slant + width, r.top); lineTo(x0 + width, r.bottom); lineTo(x0, r.bottom); close()
            }
            // Across the streak (at right angles to it): clear, then light, then clear.
            val len = kotlin.math.hypot(r.height(), slant)
            val nx = r.height() / len
            val ny = slant / len
            val across = width * r.height() / len
            val sx = x0 + slant * 0.5f
            val sy = r.centerY()
            p.shader = LinearGradient(sx, sy, sx + nx * across, sy + ny * across,
                intArrayOf(Color.TRANSPARENT, Color.argb(alpha, 255, 255, 255), Color.TRANSPARENT), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
            c.drawPath(streak, p)
        }
        p.shader = LinearGradient(0f, r.bottom - r.height() * 0.35f, 0f, r.bottom, Color.TRANSPARENT, Color.argb(if (dark) 46 else 22, 0, 0, 0), Shader.TileMode.CLAMP)
        c.drawRect(r, p)
    }

    /** The bevel: a bright edge from the top left fading toward the bottom right, and a darker line just inside it below. */
    private fun rim(c: Canvas, r: RectF, radius: Float, d: Float, dark: Boolean) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.2f * d }
        val o = RectF(r).apply { inset(p.strokeWidth / 2, p.strokeWidth / 2) }
        p.shader = LinearGradient(o.left, o.top, o.right, o.bottom,
            intArrayOf(Color.argb(230, 255, 255, 255), Color.argb(90, 255, 255, 255), Color.argb(26, 255, 255, 255), Color.argb(110, 255, 255, 255)),
            floatArrayOf(0f, 0.35f, 0.75f, 1f), Shader.TileMode.CLAMP)
        c.drawRoundRect(o, radius, radius, p)
        val inner = RectF(r).apply { inset(2.1f * d, 2.1f * d) }
        p.strokeWidth = 1f * d
        p.shader = LinearGradient(0f, inner.top, 0f, inner.bottom,
            intArrayOf(Color.argb(if (dark) 60 else 110, 255, 255, 255), Color.TRANSPARENT, Color.argb(if (dark) 80 else 36, 0, 0, 0)),
            floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        val ir = (radius - 2.1f * d).coerceAtLeast(2f * d)
        c.drawRoundRect(inner, ir, ir, p)
    }

    /** A box blur of radius [r], [passes] times over (close to a Gaussian after three). */
    fun boxBlur(b: Bitmap, r: Int, passes: Int) {
        val w = b.width
        val h = b.height
        val src = IntArray(w * h)
        b.getPixels(src, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)
        val n = 2 * r + 1
        fun pass(from: IntArray, to: IntArray, len: Int, lines: Int, step: Int, lineStep: Int) {
            for (l in 0 until lines) {
                val base = l * lineStep
                for (i in 0 until len) {
                    var a = 0; var rr = 0; var g = 0; var bb = 0
                    for (k in -r..r) {
                        val c = from[base + (i + k).coerceIn(0, len - 1) * step]
                        a += c ushr 24; rr += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; bb += c and 0xFF
                    }
                    to[base + i * step] = ((a / n) shl 24) or ((rr / n) shl 16) or ((g / n) shl 8) or (bb / n)
                }
            }
        }
        repeat(passes) {
            pass(src, tmp, w, h, 1, w)
            pass(tmp, src, h, w, w, 1)
        }
        b.setPixels(src, 0, w, 0, 0, w, h)
    }
}

/**
 * The large word of the day-name and dhikr widgets in relief: an extruded side below and to the right, a soft shadow,
 * and a face in gold (or the text colour) lit from above. Drawn here so it looks the same on every launcher.
 */
object WidgetWord {
    /** How far the word stands out, in pixels, at [px] text size. */
    fun depth(px: Float, look: WidgetWordLook, d: Float): Float = if (look == WidgetWordLook.FLAT) 0f else (px * 0.07f).coerceIn(1.2f * d, 7f * d)
    private fun blur(depth: Float, d: Float) = if (depth == 0f) 0f else depth * 0.9f + d

    /** Room the relief adds around the plain word: across and down. */
    fun extra(px: Float, look: WidgetWordLook, d: Float): Pair<Float, Float> {
        val dp = depth(px, look, d)
        val b = blur(dp, d)
        return (dp * 0.4f + 2 * b) to (dp + 2 * b)
    }

    private fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(s: Int) = (((a shr s) and 0xFF) * (1 - t) + ((b shr s) and 0xFF) * t).roundToInt().coerceIn(0, 255)
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    fun draw(value: String, px: Float, face: Typeface, look: WidgetWordLook, ink: Int, darkBackground: Boolean, d: Float): Bitmap {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face; textSize = px }
        val fm = p.fontMetrics
        val depth = depth(px, look, d)
        val blur = blur(depth, d)
        val tw = ceil(p.measureText(value).toDouble()).toFloat() + 2f
        val th = ceil((fm.descent - fm.ascent).toDouble()).toFloat()
        val w = (tw + depth * 0.4f + 2 * blur).toInt().coerceIn(1, 2400)
        val h = (th + depth + 2 * blur).toInt().coerceIn(1, 800)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val x = blur + 1f
        val y = blur - fm.ascent
        if (look == WidgetWordLook.FLAT) {
            p.color = ink
            c.drawText(value, x, y, p)
            return bmp
        }
        val gold = look == WidgetWordLook.GOLD
        val lightInk = Contrast.luminance(ink) > 0.4
        val sideNear: Int
        val sideFar: Int
        val faceColors: IntArray
        val faceStops: FloatArray
        if (gold) {
            if (darkBackground) {
                faceColors = intArrayOf(0xFFFFF6DA.toInt(), 0xFFF5D88C.toInt(), 0xFFD4A445.toInt(), 0xFFB98426.toInt(), 0xFFEBC56A.toInt(), 0xFFFFE7A3.toInt())
                sideNear = 0xFF9C6A1C.toInt(); sideFar = 0xFF4F320A.toInt()
            } else {
                faceColors = intArrayOf(0xFFF4D58A.toInt(), 0xFFDCAB4C.toInt(), 0xFFA8761F.toInt(), 0xFF8A5E14.toInt(), 0xFFB98426.toInt(), 0xFFD9A84A.toInt())
                sideNear = 0xFF6E4A10.toInt(); sideFar = 0xFF3A2506.toInt()
            }
            faceStops = floatArrayOf(0f, 0.28f, 0.52f, 0.66f, 0.86f, 1f)
        } else {
            val top = if (lightInk) mix(ink, 0xFFFFFFFF.toInt(), 0.35f) else mix(ink, 0xFFFFFFFF.toInt(), 0.28f)
            faceColors = intArrayOf(top, ink, ink, mix(ink, 0xFF000000.toInt(), 0.12f))
            faceStops = floatArrayOf(0f, 0.45f, 0.8f, 1f)
            sideNear = mix(ink, 0xFF000000.toInt(), if (lightInk) 0.45f else 0.55f)
            sideFar = mix(ink, 0xFF000000.toInt(), if (lightInk) 0.75f else 0.8f)
        }
        // The shadow on the background.
        val shadow = Paint(p).apply {
            color = if (darkBackground) 0x8C000000.toInt() else 0x59000000
            maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
        }
        c.drawText(value, x + depth * 0.55f, y + depth + blur * 0.25f, shadow)
        // The side: layer upon layer from the back to the face, darker further back.
        val steps = ceil(depth.toDouble()).toInt().coerceAtLeast(1)
        for (i in steps downTo 1) {
            val t = i.toFloat() / steps
            p.color = mix(sideNear, sideFar, t)
            c.drawText(value, x + depth * 0.4f * t, y + depth * t, p)
        }
        // A crisp edge around the face, then the face lit from above.
        val edge = Paint(p).apply { style = Paint.Style.STROKE; strokeWidth = 0.7f * d; color = sideNear; strokeJoin = Paint.Join.ROUND }
        c.drawText(value, x, y, edge)
        p.color = Color.BLACK
        p.shader = LinearGradient(0f, y + fm.ascent * 0.82f, 0f, y + fm.descent * 0.6f, faceColors, faceStops, Shader.TileMode.CLAMP)
        c.drawText(value, x, y, p)
        return bmp
    }
}
