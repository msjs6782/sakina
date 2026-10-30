package com.sakina.app.widgets

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.sakina.app.core.calendar.GridDay
import com.sakina.app.core.calendar.MonthGrid
import com.sakina.app.core.calendar.MonthRef
import com.sakina.app.core.calendar.OccasionDay
import com.sakina.app.core.calendar.Occasions
import com.sakina.app.core.format.Fmt
import com.sakina.app.core.format.Names
import com.sakina.app.core.widgets.Contrast
import com.sakina.app.ui.Strings
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.min

/** The edge path the progress line follows: from the top's middle, clockwise, around the rounded rectangle. */
object WidgetRing {
    fun path(w: Float, h: Float, inset: Float, radius: Float): Path {
        val l = inset; val t = inset; val r = w - inset; val b = h - inset
        val rad = radius.coerceIn(0f, min(r - l, b - t) / 2)
        val p = Path()
        p.moveTo((l + r) / 2, t)
        p.lineTo(r - rad, t)
        if (rad > 0f) p.arcTo(RectF(r - 2 * rad, t, r, t + 2 * rad), -90f, 90f)
        p.lineTo(r, b - rad)
        if (rad > 0f) p.arcTo(RectF(r - 2 * rad, b - 2 * rad, r, b), 0f, 90f)
        p.lineTo(l + rad, b)
        if (rad > 0f) p.arcTo(RectF(l, b - 2 * rad, l + 2 * rad, b), 90f, 90f)
        p.lineTo(l, t + rad)
        if (rad > 0f) p.arcTo(RectF(l, t, l + 2 * rad, t + 2 * rad), 180f, 90f)
        p.lineTo((l + r) / 2, t)
        return p
    }
}

/**
 * The Hijri calendar widget, drawn as one picture: the month's name with its year in gold over a fine gold rule, the
 * weekdays, and the days with today raised (lit, shadowed, gold-edged), Fridays tinted, the white days marked in gold
 * and occasions in the accent; the next occasion underneath when there is room.
 */
object WidgetCalendar {
    class Spec(
        val width: Int,
        val height: Int,
        val density: Float,
        val textScale: Float,
        val str: Strings,
        val fmt: Fmt,
        val month: MonthRef,
        val grid: List<GridDay>,
        val weekdays: List<DayOfWeek>,
        val today: LocalDate,
        val hijriOffset: Int,
        /** The civil day under each Hijri day. */
        val gregorian: Boolean,
        val coming: OccasionDay?,
        val colors: WidgetColors,
        val gold: Int,
        val regular: Typeface,
        val medium: Typeface,
        val bold: Typeface,
        val title: Typeface,
    )

    class Drawn(val bitmap: Bitmap, val description: String)

    private fun alpha(c: Int, a: Float) = ((((c ushr 24) * a).toInt().coerceIn(0, 255)) shl 24) or (c and 0xFFFFFF)

    private fun mix(c: Int, with: Int, t: Float): Int {
        fun ch(v: Int, s: Int) = (v shr s) and 0xFF
        fun m(s: Int) = (ch(c, s) + (ch(with, s) - ch(c, s)) * t).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (m(16) shl 16) or (m(8) shl 8) or m(0)
    }

    private val AR_INITIALS = mapOf(
        DayOfWeek.SATURDAY to "س", DayOfWeek.SUNDAY to "ح", DayOfWeek.MONDAY to "ن", DayOfWeek.TUESDAY to "ث",
        DayOfWeek.WEDNESDAY to "ر", DayOfWeek.THURSDAY to "خ", DayOfWeek.FRIDAY to "ج",
    )

    fun draw(sp: Spec): Drawn {
        val w = sp.width.toFloat()
        val h = sp.height.toFloat()
        val d = sp.density
        val bmp = Bitmap.createBitmap(sp.width, sp.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val str = sp.str
        val fmt = sp.fmt
        val rtl = str.isAr
        val col = sp.colors
        val scale = min(1f, sp.textScale)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)

        // The rows the month uses (four to six weeks).
        val firstIn = sp.grid.indexOfFirst { it.inMonth }.coerceAtLeast(0)
        val lastIn = sp.grid.indexOfLast { it.inMonth }.coerceAtLeast(firstIn)
        val r0 = firstIn / 7
        val rows = lastIn / 7 - r0 + 1
        val footer = sp.coming != null && h > 150f * d
        val units = rows + 1.55f + 0.8f + (if (footer) 1.05f else 0f)
        val u = h / units
        val headerH = 1.55f * u
        val weekH = 0.8f * u
        val cellH = u
        val cellW = w / 7f
        fun colX(i: Int) = if (rtl) w - (i + 1) * cellW else i * cellW

        // ---- The month's name, its year in gold, the civil months on the other side.
        val monthName = Names.hijriMonth(sp.month.month, fmt.lang)
        val year = fmt.num(sp.month.year) + if (rtl) " هـ" else " AH"
        val range = MonthGrid.range(sp.month, sp.hijriOffset)
        val civil = range?.let { r ->
            val a = r.start; val b = r.endInclusive
            fun m(x: LocalDate) = Names.gregorianMonth(x.monthValue, fmt.lang)
            when {
                a.monthValue == b.monthValue && a.year == b.year -> "${m(a)} ${fmt.num(a.year)}"
                a.year == b.year -> "${m(a)} – ${m(b)} ${fmt.num(b.year)}"
                else -> "${m(a)} ${fmt.num(a.year)} – ${m(b)} ${fmt.num(b.year)}"
            }
        } ?: ""
        var ts = min(headerH * 0.5f, w * 0.085f) * scale
        p.typeface = sp.title
        p.textSize = ts
        val gap = ts * 0.3f
        fun titleWidth() = p.measureText(monthName) + gap + p.measureText(year)
        while (titleWidth() > w * 0.98f && ts > 6f * d) { ts *= 0.92f; p.textSize = ts }
        val tw = titleWidth()
        val fmTitle = p.fontMetrics
        val titleBase = headerH * 0.46f - (fmTitle.ascent + fmTitle.descent) / 2
        val nameW = p.measureText(monthName)
        val nameX = if (rtl) w - nameW else 0f
        val yearX = if (rtl) w - nameW - gap - p.measureText(year) else nameW + gap
        p.color = col.text
        c.drawText(monthName, nameX, titleBase, p)
        p.color = sp.gold
        c.drawText(year, yearX, titleBase, p)
        if (civil.isNotEmpty()) {
            p.typeface = sp.regular
            p.textSize = min(ts * 0.5f, headerH * 0.26f)
            val cw = p.measureText(civil)
            if (tw + cw + ts <= w) {
                p.color = col.muted
                val fm = p.fontMetrics
                c.drawText(civil, if (rtl) 0f else w - cw, headerH * 0.46f - (fm.ascent + fm.descent) / 2, p)
            }
        }
        // A fine gold rule with a diamond in its middle.
        val ruleY = headerH - 0.2f * u
        p.style = Paint.Style.STROKE
        p.strokeWidth = maxOf(1f, 0.9f * d)
        p.shader = LinearGradient(0f, 0f, w, 0f, intArrayOf(alpha(sp.gold, 0f), alpha(sp.gold, 0.9f), alpha(sp.gold, 0f)), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawLine(w * 0.04f, ruleY, w * 0.96f, ruleY, p)
        p.shader = null
        p.style = Paint.Style.FILL
        p.color = sp.gold
        val dm = 2.6f * d
        c.drawPath(Path().apply { moveTo(w / 2, ruleY - dm); lineTo(w / 2 + dm, ruleY); lineTo(w / 2, ruleY + dm); lineTo(w / 2 - dm, ruleY); close() }, p)

        // ---- Weekdays: short names when they fit, otherwise initials; Friday in the accent.
        p.textAlign = Paint.Align.CENTER
        val ws = min(weekH * 0.52f, cellW * 0.3f) * scale
        p.textSize = ws
        p.typeface = sp.medium
        val short = sp.weekdays.map { fmt.weekday(it, short = true) }
        val useShort = short.all { p.measureText(it) <= cellW * 0.9f }
        val fmW = p.fontMetrics
        val weekBase = headerH + weekH * 0.5f - (fmW.ascent + fmW.descent) / 2
        sp.weekdays.forEachIndexed { i, day ->
            val label = if (useShort) short[i] else if (rtl) AR_INITIALS.getValue(day) else short[i].take(1)
            val fri = day == DayOfWeek.FRIDAY
            p.typeface = if (fri) sp.bold else sp.medium
            p.color = if (fri) col.accent else col.muted
            c.drawText(label, colX(i) + cellW / 2, weekBase, p)
        }

        // ---- The days.
        val onAccent = if (Contrast.luminance(col.accent) > 0.45) 0xFF10201C.toInt() else 0xFFFFFFFF.toInt()
        val withCivil = sp.gregorian && cellH >= 22f * d
        val ns = min(cellH * (if (withCivil) 0.44f else 0.52f), cellW * 0.42f) * scale
        val gs = ns * 0.52f
        val top = headerH + weekH
        val cell = RectF()
        for (idx in r0 * 7 until minOf(sp.grid.size, (r0 + rows) * 7)) {
            val g = sp.grid[idx]
            if (!g.inMonth) continue
            val hd = g.hijri ?: continue
            val row = idx / 7 - r0
            val x = colX(idx % 7)
            val y = top + row * cellH
            val pad = min(cellW, cellH) * 0.07f
            cell.set(x + pad, y + pad, x + cellW - pad, y + cellH - pad)
            val rr = min(cell.width(), cell.height()) * 0.3f
            val isToday = g.date == sp.today
            val friday = g.date.dayOfWeek == DayOfWeek.FRIDAY
            if (isToday) {
                // Raised: a soft shadow, lit from above, a gold edge.
                p.style = Paint.Style.FILL
                p.color = col.accent
                p.setShadowLayer(3.5f * d, 0f, 1.6f * d, 0x66000000)
                c.drawRoundRect(cell, rr, rr, p)
                p.clearShadowLayer()
                p.shader = LinearGradient(0f, cell.top, 0f, cell.bottom, mix(col.accent, 0xFFFFFFFF.toInt(), 0.24f), mix(col.accent, 0xFF000000.toInt(), 0.12f), Shader.TileMode.CLAMP)
                c.drawRoundRect(cell, rr, rr, p)
                p.shader = null
                p.style = Paint.Style.STROKE
                p.strokeWidth = 1.3f * d
                p.color = sp.gold
                c.drawRoundRect(cell, rr, rr, p)
                p.style = Paint.Style.FILL
            } else if (friday) {
                p.style = Paint.Style.FILL
                p.color = alpha(col.accent or (0xFF shl 24), 0.1f)
                c.drawRoundRect(cell, rr, rr, p)
            }
            val cx = x + cellW / 2
            val numCentre = if (withCivil) y + cellH * 0.42f else y + cellH * 0.5f
            p.textSize = ns
            p.typeface = sp.bold
            p.color = when {
                isToday -> onAccent
                friday -> col.accent
                else -> col.text
            }
            val fmN = p.fontMetrics
            c.drawText(fmt.num(hd.day), cx, numCentre - (fmN.ascent + fmN.descent) / 2, p)
            if (withCivil) {
                p.textSize = gs
                p.typeface = sp.regular
                p.color = if (isToday) alpha(onAccent, 0.85f) else col.muted
                val fmG = p.fontMetrics
                c.drawText(fmt.num(g.date.dayOfMonth), cx, y + cellH * 0.78f - (fmG.ascent + fmG.descent) / 2, p)
            }
            // The white days in gold, occasions in the accent, as small dots in the cell's top corners.
            val dot = maxOf(1.4f * d, min(cellW, cellH) * 0.055f)
            val inStart = if (rtl) cell.right - dot * 2.2f else cell.left + dot * 2.2f
            val inEnd = if (rtl) cell.left + dot * 2.2f else cell.right - dot * 2.2f
            if (hd.day in 13..15) {
                p.color = sp.gold
                c.drawCircle(inStart, cell.top + dot * 2.2f, dot, p)
            }
            if (Occasions.on(g.date, sp.hijriOffset).isNotEmpty()) {
                p.color = if (isToday) onAccent else col.accent
                c.drawCircle(inEnd, cell.top + dot * 2.2f, dot, p)
            }
        }

        // ---- The next occasion.
        var footText = ""
        val coming = sp.coming
        if (footer && coming != null) {
            val days = coming.daysAway
            val whenText = if (days <= 0L) str.today else str.inDays(str.daysCount(fmt.num(days.toInt()), days))
            footText = "${str.occasion(coming.occasion)} · $whenText"
            p.typeface = sp.medium
            p.textSize = min(1.05f * u * 0.42f, w * 0.05f) * scale
            while (p.measureText(footText) > w * 0.9f && p.textSize > 6f * d) p.textSize *= 0.92f
            val fy = top + rows * cellH + 1.05f * u * 0.55f
            val fmF = p.fontMetrics
            val textW = p.measureText(footText)
            p.color = col.text
            c.drawText(footText, w / 2 + (if (rtl) -1 else 1) * (p.textSize * 0.45f), fy - (fmF.ascent + fmF.descent) / 2, p)
            // A gold diamond before it.
            val dx = if (rtl) w / 2 + textW / 2 + p.textSize * 0.15f else w / 2 - textW / 2 - p.textSize * 0.15f
            val r = p.textSize * 0.22f
            p.color = sp.gold
            c.drawPath(Path().apply { moveTo(dx, fy - r); lineTo(dx + r, fy); lineTo(dx, fy + r); lineTo(dx - r, fy); close() }, p)
        }
        p.textAlign = Paint.Align.LEFT

        val todayHijri = sp.grid.firstOrNull { it.date == sp.today }?.hijri
        val description = listOfNotNull("$monthName $year", todayHijri?.let { str.today + " " + fmt.hijri(it) }, footText.takeIf { it.isNotEmpty() }).joinToString("، ")
        return Drawn(bmp, description)
    }
}
