package com.sakina.app.widgets

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.cos
import kotlin.math.sin

/** A prayer mark on the analog face: its angle on the 12-hour dial. Sunrise is drawn hollow. */
data class DialMark(val angle: Float, val isNext: Boolean, val sunrise: Boolean)

/**
 * Draws the analog widget's face (behind the native clock's hands): a disc, hour ticks and the day's prayer
 * marks. Kept small (it travels to the launcher with every update of that widget).
 */
object WidgetDial {
    const val MAX_PX = 320

    fun draw(sizePx: Int, c: WidgetColors, marks: List<DialMark>): Bitmap {
        val size = sizePx.coerceIn(64, MAX_PX)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val cx = size / 2f
        val r = size / 2f - size * 0.02f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        // Face.
        p.style = Paint.Style.FILL
        p.color = (0x99 shl 24) or (c.soft and 0xFFFFFF)
        cv.drawCircle(cx, cx, r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = size * 0.012f
        p.color = (0x66 shl 24) or (c.muted and 0xFFFFFF)
        cv.drawCircle(cx, cx, r, p)
        // Hour ticks; the quarters are longer.
        p.strokeCap = Paint.Cap.ROUND
        for (h in 0 until 12) {
            val a = Math.toRadians(h * 30.0 - 90)
            val major = h % 3 == 0
            val inner = r * (if (major) 0.80f else 0.87f)
            val outer = r * 0.94f
            p.strokeWidth = size * (if (major) 0.022f else 0.012f)
            p.color = if (major) c.text else c.muted
            cv.drawLine(cx + inner * cos(a).toFloat(), cx + inner * sin(a).toFloat(), cx + outer * cos(a).toFloat(), cx + outer * sin(a).toFloat(), p)
        }
        // Prayer marks inside the ticks; the next prayer is larger and in the accent colour.
        for (m in marks.sortedBy { it.isNext }) {
            val a = Math.toRadians(m.angle - 90.0)
            val rr = r * 0.68f
            val x = cx + rr * cos(a).toFloat()
            val y = cx + rr * sin(a).toFloat()
            val dot = size * (if (m.isNext) 0.052f else 0.034f)
            p.color = if (m.isNext) c.accent else c.muted
            if (m.sunrise) {
                p.style = Paint.Style.STROKE
                p.strokeWidth = size * 0.012f
                cv.drawCircle(x, y, dot, p)
            } else {
                p.style = Paint.Style.FILL
                cv.drawCircle(x, y, dot, p)
            }
            if (m.isNext) {
                p.style = Paint.Style.STROKE
                p.strokeWidth = size * 0.01f
                cv.drawCircle(x, y, dot * 1.7f, p)
            }
        }
        return bmp
    }
}
