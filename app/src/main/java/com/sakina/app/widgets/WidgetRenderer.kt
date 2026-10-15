package com.sakina.app.widgets

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.sakina.app.R
import com.sakina.app.core.format.Fmt
import com.sakina.app.core.format.Lang
import com.sakina.app.core.prayer.PrayerKey
import com.sakina.app.core.prayer.PrayerSchedule
import com.sakina.app.core.widgets.BlockMeasure
import com.sakina.app.core.widgets.ContentItem
import com.sakina.app.core.widgets.ContentPack
import com.sakina.app.core.widgets.ContentPicker
import com.sakina.app.core.widgets.DawnDuskCard
import com.sakina.app.core.widgets.Dial
import com.sakina.app.core.widgets.Fit
import com.sakina.app.core.widgets.FitLine
import com.sakina.app.core.widgets.PrayerSnapshot
import com.sakina.app.core.widgets.RamadanContext
import com.sakina.app.core.widgets.TextMeasure
import com.sakina.app.core.widgets.WidgetKind
import com.sakina.app.core.widgets.WidgetMath
import com.sakina.app.data.AppSettings
import com.sakina.app.data.SavedLocation
import com.sakina.app.data.WidgetConfig
import com.sakina.app.data.clock24
import com.sakina.app.data.language
import com.sakina.app.data.resolvePlace
import com.sakina.app.ui.Strings
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Everything a widget needs to draw itself at one moment. */
class WidgetEnv(val context: Context, val settings: AppSettings, val now: Instant, val pack: ContentPack?)

/** A widget's views (possibly one per size), a key that changes when anything shown changes, and when to redraw. */
class WidgetRender(val views: RemoteViews, val key: String, val nextChange: Instant)

/** Measures with the same fonts the layouts use, so text is sized to fit before it reaches the launcher. */
private object Typo {
    private val faces = HashMap<Int, Typeface>()
    private val paints = HashMap<Typeface, Paint>()

    @Synchronized
    fun face(context: Context, res: Int): Typeface = faces.getOrPut(res) {
        runCatching { context.resources.getFont(res) }.getOrDefault(Typeface.DEFAULT)
    }

    /** Widths are padded a little: the launcher lays text out in its own process. */
    fun measure(face: Typeface) = TextMeasure { text, size ->
        synchronized(this) {
            val p = paints.getOrPut(face) { Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face } }
            p.textSize = size
            p.measureText(text) * 1.05f + size * 0.1f
        }
    }

    private val heights = HashMap<Typeface, Float>()

    /** Line height per pixel of text size, without font padding (as the layouts set it). */
    fun lineHeight(face: Typeface): Float = synchronized(this) {
        heights.getOrPut(face) {
            val p = Paint().apply { typeface = face; textSize = 100f }
            p.fontMetrics.let { (it.descent - it.ascent) / 100f }
        }
    }

    fun block(face: Typeface, spacing: Float) = BlockMeasure { text, width, size ->
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face; textSize = size }
        StaticLayout.Builder.obtain(text, 0, text.length, tp, width.toInt().coerceAtLeast(1))
            .setLineSpacing(0f, spacing).setIncludePad(true).build().height.toFloat()
    }
}

object WidgetRenderer {
    private const val TAG = "SakinaWidgets"
    private val CELL_NAMES = intArrayOf(R.id.w_n0, R.id.w_n1, R.id.w_n2, R.id.w_n3, R.id.w_n4, R.id.w_n5)
    private val CELL_TIMES = intArrayOf(R.id.w_t0, R.id.w_t1, R.id.w_t2, R.id.w_t3, R.id.w_t4, R.id.w_t5)
    private val CELL_HL = intArrayOf(R.id.w_hl0, R.id.w_hl1, R.id.w_hl2, R.id.w_hl3, R.id.w_hl4, R.id.w_hl5)

    /** Default sizes (dp) when the launcher has not reported any yet. */
    fun defaultSize(kind: WidgetKind): SizeF = when (kind) {
        WidgetKind.MINI -> SizeF(72f, 72f)
        WidgetKind.STRIP -> SizeF(300f, 64f)
        WidgetKind.COUNTDOWN, WidgetKind.ANALOG -> SizeF(150f, 150f)
        WidgetKind.NEXT, WidgetKind.FAJR_MAGHRIB -> SizeF(230f, 140f)
        else -> SizeF(300f, 150f)
    }

    /** A small, the default and a large size (dp), for checking that text fits as widgets are resized. */
    fun sampleSizes(kind: WidgetKind): List<SizeF> = when (kind) {
        WidgetKind.NEXT -> listOf(SizeF(110f, 60f), defaultSize(kind), SizeF(360f, 110f))
        WidgetKind.COUNTDOWN -> listOf(SizeF(110f, 110f), defaultSize(kind), SizeF(250f, 180f))
        WidgetKind.DAY -> listOf(SizeF(180f, 110f), defaultSize(kind), SizeF(160f, 300f))
        WidgetKind.GLASS -> listOf(SizeF(180f, 110f), defaultSize(kind), SizeF(360f, 200f))
        WidgetKind.MINI -> listOf(SizeF(50f, 50f), defaultSize(kind), SizeF(110f, 110f))
        WidgetKind.STRIP -> listOf(SizeF(180f, 40f), defaultSize(kind), SizeF(400f, 80f))
        WidgetKind.DIGITAL -> listOf(SizeF(180f, 90f), defaultSize(kind), SizeF(360f, 200f))
        WidgetKind.ANALOG -> listOf(SizeF(110f, 110f), defaultSize(kind), SizeF(250f, 250f))
        WidgetKind.CONTENT -> listOf(SizeF(180f, 110f), defaultSize(kind), SizeF(360f, 250f))
        WidgetKind.FAJR_MAGHRIB -> listOf(SizeF(110f, 90f), defaultSize(kind), SizeF(360f, 160f))
    }

    fun render(env: WidgetEnv, kind: WidgetKind, cfg: WidgetConfig, id: Int, sizes: List<SizeF>, preview: Boolean = false): WidgetRender {
        val list = sizes.ifEmpty { listOf(defaultSize(kind)) }
        val built = list.map { Build1(env, kind, cfg, id, it.width, it.height, preview).build() }
        val views = when {
            built.size >= 2 && Build.VERSION.SDK_INT >= 31 -> RemoteViews(list.zip(built.map { it.first }).toMap())
            built.size == 2 -> RemoteViews(built[0].first, built[1].first) // landscape, portrait
            else -> built[0].first
        }
        val key = list.joinToString(";") { "${it.width}x${it.height}" } + "#" + built.joinToString("#") { it.second.key }
        val next = built.minOf { it.second.nextChange }
        return WidgetRender(views, key, next)
    }

    private class Meta(val key: String, val nextChange: Instant)

    /** One RemoteViews for one size. */
    private class Build1(
        val env: WidgetEnv,
        val kind: WidgetKind,
        val cfg: WidgetConfig,
        val id: Int,
        val wDp: Float,
        val hDp: Float,
        val preview: Boolean,
    ) {
        val ctx = env.context
        val s = env.settings
        val now = env.now
        val density = ctx.resources.displayMetrics.density
        val langDigits = cfg.language(s)
        val lang = langDigits.first
        val str = Strings(lang, langDigits.second)
        val fmt = Fmt(lang, cfg.clock24(s), langDigits.second)
        val place: SavedLocation? = cfg.resolvePlace(s)
        val zone: ZoneId = place?.zoneId ?: ZoneId.systemDefault()
        val schedule: PrayerSchedule? = place?.let { PrayerSchedule(it.point, zone, s.calcConfig) }
        val glass = kind == WidgetKind.GLASS
        val colors = WidgetPalette.resolve(ctx, cfg, s, now, glass)
        val key = StringBuilder()
        lateinit var rv: RemoteViews

        // Fonts used by the layouts.
        val regular = Typo.face(ctx, R.font.plex_regular)
        val medium = Typo.face(ctx, R.font.plex_medium)
        val semibold = Typo.face(ctx, R.font.plex_semibold)

        /** px per sp at the user's text size and the system font scale. */
        val unit = density * ctx.resources.configuration.fontScale * cfg.textScale.coerceIn(WidgetConfig.SCALES) / 100f
        val pad = (minOf(wDp, hDp) * 0.085f).coerceIn(6f, 14f) * density
        val boxW = wDp * density - 2 * pad
        val boxH = hDp * density - 2 * pad

        fun text(id: Int, value: String) { rv.setTextViewText(id, value); key.append(id).append('=').append(value).append('|') }
        private val lastSize = HashMap<Int, Float>()
        fun size(id: Int, px: Float) {
            lastSize[id] = px
            rv.setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_PX, px)
            key.append(id).append('s').append(px.toInt()).append('|')
        }
        fun color(id: Int, c: Int) { rv.setTextColor(id, c) }
        fun visible(id: Int, on: Boolean, gone: Boolean = true) {
            rv.setViewVisibility(id, if (on) View.VISIBLE else if (gone) View.GONE else View.INVISIBLE)
            key.append(id).append(if (on) 'v' else 'h').append('|')
        }

        fun layoutFor(): Int = when (kind) {
            WidgetKind.NEXT -> if (wDp >= hDp * 1.5f) R.layout.w_next_wide else R.layout.w_next_tall
            WidgetKind.COUNTDOWN -> R.layout.w_countdown
            WidgetKind.DAY -> if (hDp > wDp * 1.1f) R.layout.w_day_tall else R.layout.w_day_wide
            WidgetKind.GLASS -> if (colors.dark) R.layout.w_glass_dark else R.layout.w_glass_light
            WidgetKind.MINI -> R.layout.w_mini
            WidgetKind.STRIP -> R.layout.w_strip
            WidgetKind.DIGITAL -> R.layout.w_digital
            WidgetKind.ANALOG -> if (colors.lightInk) R.layout.w_analog_light else R.layout.w_analog_dark
            WidgetKind.CONTENT -> R.layout.w_content
            WidgetKind.FAJR_MAGHRIB -> R.layout.w_fajr_maghrib
        }

        fun build(): Pair<RemoteViews, Meta> {
            val layout = layoutFor()
            rv = RemoteViews(ctx.packageName, layout)
            key.append(layout).append('|').append(colors).append('|')
            frame()
            var next: Instant = WidgetMath.nextChange(kind, now, zone, null)
            if (kind.needsPlace && (place == null || schedule == null)) {
                message(str.wNoPlace, "locations")
            } else {
                next = when (kind) {
                    WidgetKind.CONTENT -> content()
                    WidgetKind.FAJR_MAGHRIB -> dawnDusk()
                    else -> prayers()
                }
            }
            return rv to Meta(key.toString(), next)
        }

        /** Background, border, direction, padding and the tap target. */
        fun frame() {
            val corner = WidgetConfig.CORNERS.minByOrNull { kotlin.math.abs(it - cfg.corner) } ?: 16
            rv.setImageViewResource(R.id.w_bg, when (corner) { 0 -> R.drawable.w_shape_0; 8 -> R.drawable.w_shape_8; 24 -> R.drawable.w_shape_24; else -> R.drawable.w_shape_16 })
            rv.setInt(R.id.w_bg, "setColorFilter", colors.bg)
            rv.setInt(R.id.w_bg, "setImageAlpha", colors.bgAlpha)
            if (cfg.border > 0) {
                val thick = cfg.border >= 2
                rv.setImageViewResource(R.id.w_border, when (corner) {
                    0 -> if (thick) R.drawable.w_stroke_0_2 else R.drawable.w_stroke_0_1
                    8 -> if (thick) R.drawable.w_stroke_8_2 else R.drawable.w_stroke_8_1
                    24 -> if (thick) R.drawable.w_stroke_24_2 else R.drawable.w_stroke_24_1
                    else -> if (thick) R.drawable.w_stroke_16_2 else R.drawable.w_stroke_16_1
                })
                rv.setInt(R.id.w_border, "setColorFilter", colors.border)
                rv.setViewVisibility(R.id.w_border, View.VISIBLE)
            } else {
                rv.setViewVisibility(R.id.w_border, View.GONE)
            }
            key.append("c").append(corner).append('b').append(cfg.border).append('|')
            rv.setInt(R.id.w_root, "setLayoutDirection", if (lang == Lang.AR) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR)
            val p = pad.toInt()
            rv.setViewPadding(R.id.w_content, p, p, p, p)
            if (!preview) {
                val target = when (kind) {
                    WidgetKind.FAJR_MAGHRIB -> "ramadan"
                    WidgetKind.CONTENT -> "discover"
                    else -> "times"
                }
                rv.setOnClickPendingIntent(R.id.w_root, WidgetIntents.open(ctx, target))
            }
        }

        fun message(text: String, target: String?) {
            visible(R.id.w_content, false)
            visible(R.id.w_msg, true)
            text(R.id.w_msg, text)
            color(R.id.w_msg, colors.text)
            val px = (13f * unit).coerceAtMost(boxW / 12f).coerceAtLeast(9f * density)
            size(R.id.w_msg, px)
            if (!preview && target != null) rv.setOnClickPendingIntent(R.id.w_root, WidgetIntents.open(ctx, target))
        }

        /** A line of text views that are sized together; [drop] > 0 marks lines that may be left out, highest first. */
        inner class Line(
            val ids: IntArray,
            val texts: List<String>,
            val rel: Float,
            val face: Typeface,
            val drop: Int = 0,
            val padPx: Float = 0f,
            val inline: Boolean = false,
            /** Extra views hidden with this line (such as the refresh button with the place). */
            val with: IntArray = IntArray(0),
        ) {
            fun fit() = FitLine(texts, rel, padPx, inline, Typo.measure(face), Typo.lineHeight(face))
        }

        /**
         * Sizes the lines to fit [w]×[h]: all of them if they fit at a readable size, otherwise leaving out the
         * droppable lines from the highest level down. Hidden lines are set gone; the rest get their sizes.
         */
        fun solve(lines: List<Line>, w: Float = boxW, h: Float = boxH, gap: Float = 2f): Float {
            val readable = density * 0.72f * minOf(1f, cfg.textScale / 100f)
            val levels = lines.map { it.drop }.filter { it > 0 }.distinct().sortedDescending()
            var chosen = lines
            var scale: Float? = null
            val measure = Typo.measure(regular)
            val lh = Typo.lineHeight(regular)
            for (cut in listOf(Int.MAX_VALUE) + levels) {
                val keep = lines.filter { it.drop == 0 || it.drop < cut }
                // The refresh button (22 dp) can be taller than the small place text beside it.
                val extra = if (keep.any { R.id.w_refresh in it.with }) 8f * density else 0f
                val sc = Fit.scale(keep.map { it.fit() }, w, h - extra, unit, density * 0.4f, measure, lh, gap)
                if (sc != null) { chosen = keep; scale = sc }
                if (sc != null && sc >= readable) break
            }
            if (scale == null) {
                chosen = lines.filter { it.drop == 0 }
                scale = density * 0.4f
                Log.w(TAG, "fit overflow kind=${kind.id} size=${wDp}x$hDp")
            }
            for (l in lines) {
                val on = l in chosen
                for (v in l.ids) visible(v, on)
                for (v in l.with) visible(v, on)
                if (on) for (v in l.ids) size(v, scale * l.rel)
            }
            return scale
        }

        fun placeLine(rel: Float = 11f, drop: Int = 3, glassFace: Typeface = regular): Line {
            val name = place?.name(lang) ?: ""
            text(R.id.w_place, name)
            color(R.id.w_place, colors.muted)
            if (!preview) rv.setOnClickPendingIntent(R.id.w_refresh, WidgetIntents.refresh(ctx, kind, id))
            rv.setInt(R.id.w_refresh, "setColorFilter", colors.muted)
            return Line(intArrayOf(R.id.w_place), listOf(name), rel, glassFace, drop, padPx = 26f * density, with = intArrayOf(R.id.w_refresh))
        }

        fun remainingText(snap: PrayerSnapshot): String? {
            val m = snap.minutesLeft ?: return null
            return str.wIn(fmt.durationShort(Duration.ofMinutes(m)))
        }

        fun stampText(): String = str.wUpdated(fmt.time(now, zone))

        /** The prayer widgets: next, countdown, day, glass, mini, strip, digital, analog. */
        fun prayers(): Instant {
            val snap = WidgetMath.snapshot(schedule!!, now)
            val next = snap.next
            if (next == null) {
                message(str.wNoTimes, null)
                return WidgetMath.nextMidnight(now, zone)
            }
            val name = str.prayer(next.key)
            val time = fmt.time(next.instant, zone)
            val remain = remainingText(snap) ?: ""
            val stamp = stampText()
            when (kind) {
                WidgetKind.NEXT -> nextPrayer(name, time, remain, stamp)
                WidgetKind.COUNTDOWN -> countdown(snap, name, time, stamp)
                WidgetKind.DAY -> day(snap, name, remain, stamp)
                WidgetKind.GLASS -> glassPanel(snap, name, time, remain, stamp)
                WidgetKind.MINI -> mini(name, next.instant)
                WidgetKind.STRIP -> strip(snap)
                WidgetKind.DIGITAL -> digital(name, time, remain, stamp)
                WidgetKind.ANALOG -> analog(snap, name, time)
                else -> {}
            }
            return WidgetMath.nextChange(kind, now, zone, next.instant)
        }

        fun nextPrayer(name: String, time: String, remain: String, stamp: String) {
            val place = placeLine()
            text(R.id.w_name, name); color(R.id.w_name, colors.accent)
            text(R.id.w_time, time); color(R.id.w_time, colors.text)
            if (wDp >= hDp * 1.5f) {
                val r = "$remain · $stamp"
                text(R.id.w_remain, r); color(R.id.w_remain, colors.muted)
                solve(listOf(
                    place,
                    Line(intArrayOf(R.id.w_name, R.id.w_time), listOf(name, time), 22f, semibold, padPx = 10f * density, inline = true),
                    Line(intArrayOf(R.id.w_remain), listOf(r), 13f, regular, drop = 2),
                ))
            } else {
                text(R.id.w_remain, remain); color(R.id.w_remain, colors.text)
                text(R.id.w_stamp, stamp); color(R.id.w_stamp, colors.muted)
                solve(listOf(
                    place,
                    Line(intArrayOf(R.id.w_name), listOf(name), 20f, semibold),
                    Line(intArrayOf(R.id.w_time), listOf(time), 22f, semibold),
                    Line(intArrayOf(R.id.w_remain), listOf(remain), 13f, regular, drop = 2),
                    Line(intArrayOf(R.id.w_stamp), listOf(stamp), 11f, regular, drop = 2),
                ))
            }
        }

        fun countdown(snap: PrayerSnapshot, name: String, time: String, stamp: String) {
            val place = placeLine()
            val big = fmt.durationShort(Duration.ofMinutes(snap.minutesLeft ?: 0))
            text(R.id.w_big, big); color(R.id.w_big, colors.accent)
            text(R.id.w_stamp, stamp); color(R.id.w_stamp, colors.muted)
            val label = str.wUntil(name)
            // Roomy: "until Asr" above, the time below. Tight: both in one line above.
            text(R.id.w_label, label); color(R.id.w_label, colors.text)
            text(R.id.w_time, time); color(R.id.w_time, colors.text)
            val roomy = listOf(
                place,
                Line(intArrayOf(R.id.w_label), listOf(label), 13f, medium),
                Line(intArrayOf(R.id.w_big), listOf(big), 34f, semibold),
                Line(intArrayOf(R.id.w_time), listOf(time), 15f, medium),
                Line(intArrayOf(R.id.w_stamp), listOf(stamp), 11f, regular),
            )
            val readable = density * 0.72f * minOf(1f, cfg.textScale / 100f)
            val probe = Fit.scale(roomy.filter { it.drop == 0 }.map { it.fit() }, boxW, boxH, unit, density * 0.4f, Typo.measure(regular), Typo.lineHeight(regular), 2f)
            if (probe != null && probe >= readable) {
                solve(roomy)
            } else {
                val merged = "$label · $time"
                text(R.id.w_label, merged)
                visible(R.id.w_time, false)
                solve(listOf(
                    place,
                    Line(intArrayOf(R.id.w_label), listOf(merged), 13f, medium),
                    Line(intArrayOf(R.id.w_big), listOf(big), 34f, semibold),
                    Line(intArrayOf(R.id.w_stamp), listOf(stamp), 11f, regular),
                ))
            }
        }

        fun cellTexts(snap: PrayerSnapshot): Pair<List<String>, List<String>> {
            val names = snap.times.map { str.prayer(it.key) }
            val times = snap.times.map { t -> t.at?.let { fmt.time(it, zone, suffix = false) } ?: "—" }
            for (i in 0 until 6) {
                val wt = snap.times[i]
                text(CELL_NAMES[i], names[i]); text(CELL_TIMES[i], times[i])
                color(CELL_NAMES[i], if (wt.isNext) colors.accent else colors.muted)
                color(CELL_TIMES[i], if (wt.isNext) colors.accent else colors.text)
                rv.setInt(CELL_HL[i], "setColorFilter", colors.soft)
                visible(CELL_HL[i], wt.isNext, gone = false)
            }
            return names to times
        }

        fun dateText(snap: PrayerSnapshot): String {
            val h = com.sakina.app.core.calendar.UmmAlQura.displayed(snap.date, s.hijriOffset)
            return h?.let { fmt.hijri(it, withYear = false) } ?: fmt.gregorian(snap.date, withYear = false)
        }

        fun day(snap: PrayerSnapshot, name: String, remain: String, stamp: String) {
            val placeL = placeLine()
            val placeText = listOfNotNull(place?.name(lang), dateText(snap)).joinToString(" · ")
            text(R.id.w_place, placeText)
            val (names, times) = cellTexts(snap)
            val r = "$name $remain · $stamp"
            text(R.id.w_remain, r); color(R.id.w_remain, colors.muted)
            val tall = hDp > wDp * 1.1f
            val placeLine = Line(placeL.ids, listOf(placeText), 11f, regular, 3, placeL.padPx, with = placeL.with)
            if (tall) {
                val rows = (0 until 6).map { i -> Line(intArrayOf(CELL_NAMES[i], CELL_TIMES[i]), listOf(names[i], times[i]), 13f, medium, padPx = 30f * density, inline = true) }
                solve(listOf(placeLine) + rows + Line(intArrayOf(R.id.w_remain), listOf(r), 11f, regular, drop = 2), gap = 3f)
            } else {
                solve(listOf(
                    placeLine,
                    Line(CELL_NAMES, names, 11f, regular, padPx = 4f * density),
                    Line(CELL_TIMES, times, 14f, semibold, padPx = 4f * density),
                    Line(intArrayOf(R.id.w_remain), listOf(r), 11f, regular, drop = 2),
                ), gap = 3f)
            }
        }

        fun glassPanel(snap: PrayerSnapshot, name: String, time: String, remain: String, stamp: String) {
            val placeL = placeLine(glassFace = regular)
            text(R.id.w_name, name); color(R.id.w_name, colors.text)
            text(R.id.w_time, time); color(R.id.w_time, colors.text)
            val r = "$remain · $stamp"
            text(R.id.w_remain, r); color(R.id.w_remain, colors.muted)
            color(R.id.w_place, colors.muted)
            val (names, times) = cellTexts(snap)
            for (i in 0 until 6) { color(CELL_NAMES[i], if (snap.times[i].isNext) colors.accent else colors.muted); color(CELL_TIMES[i], if (snap.times[i].isNext) colors.accent else colors.text) }
            solve(listOf(
                placeL,
                Line(intArrayOf(R.id.w_name, R.id.w_time), listOf(name, time), 20f, regular, padPx = 10f * density, inline = true),
                Line(intArrayOf(R.id.w_remain), listOf(r), 12f, regular, drop = 2),
                Line(CELL_NAMES, names, 10f, regular, drop = 1, padPx = 4f * density, with = intArrayOf(R.id.w_cells)),
                Line(CELL_TIMES, times, 12f, regular, drop = 1, padPx = 4f * density),
            ), gap = 3f)
        }

        fun mini(name: String, at: Instant) {
            text(R.id.w_name, name); color(R.id.w_name, colors.accent)
            val full = fmt.time(at, zone)
            val short = fmt.time(at, zone, suffix = false)
            val readable = density * 0.72f * minOf(1f, cfg.textScale / 100f)
            val tryFull = Fit.scale(listOf(FitLine(name, 12f, measure = Typo.measure(medium), lineHeight = Typo.lineHeight(medium)), FitLine(full, 20f, measure = Typo.measure(semibold), lineHeight = Typo.lineHeight(semibold))), boxW, boxH, unit, density * 0.4f, Typo.measure(regular), Typo.lineHeight(regular), 1f)
            val t = if (tryFull != null && tryFull >= readable) full else short
            text(R.id.w_time, t); color(R.id.w_time, colors.text)
            solve(listOf(Line(intArrayOf(R.id.w_name), listOf(name), 12f, medium), Line(intArrayOf(R.id.w_time), listOf(t), 20f, semibold)), gap = 1f)
        }

        fun strip(snap: PrayerSnapshot) {
            val (names, times) = cellTexts(snap)
            solve(listOf(
                Line(CELL_NAMES, names, 10.5f, regular, padPx = 4f * density),
                Line(CELL_TIMES, times, 13f, semibold, padPx = 4f * density),
            ), gap = 1f)
        }

        fun digital(name: String, time: String, remain: String, stamp: String) {
            val placeL = placeLine()
            val is24 = cfg.clock24(s)
            val pattern = if (is24) "HH:mm" else "h:mm"
            rv.setCharSequence(R.id.w_clock, "setFormat12Hour", pattern)
            rv.setCharSequence(R.id.w_clock, "setFormat24Hour", pattern)
            rv.setString(R.id.w_clock, "setTimeZone", zone.id)
            rv.setCharSequence(R.id.w_ampm, "setFormat12Hour", "a")
            rv.setCharSequence(R.id.w_ampm, "setFormat24Hour", "a")
            rv.setString(R.id.w_ampm, "setTimeZone", zone.id)
            color(R.id.w_clock, colors.text); color(R.id.w_ampm, colors.muted)
            val today = now.atZone(zone).toLocalDate()
            val h = com.sakina.app.core.calendar.UmmAlQura.displayed(today, s.hijriOffset)
            val date = listOfNotNull(fmt.gregorian(today, withYear = false, withWeekday = true), h?.let { fmt.hijri(it) }).joinToString(" · ")
            text(R.id.w_date, date); color(R.id.w_date, colors.muted)
            val r = "$name $time · $remain"
            text(R.id.w_remain, r); color(R.id.w_remain, colors.accent)
            text(R.id.w_stamp, stamp); color(R.id.w_stamp, colors.muted)
            // The clock's own digits (device language): measure the widest of both digit sets.
            val sample = if (is24) "23:58" else "12:58"
            val clockSample = if (measureWidth(semibold, "٢٣:٥٨") > measureWidth(semibold, sample)) "٢٣:٥٨" else sample
            val ampmSample = if (is24) "" else "PM"
            key.append(zone.id).append(pattern).append('|')
            val clockIds = if (is24) intArrayOf(R.id.w_clock) else intArrayOf(R.id.w_clock, R.id.w_ampm)
            solve(listOf(
                placeL,
                Line(clockIds, listOfNotNull(clockSample, ampmSample.takeIf { it.isNotEmpty() }), 40f, semibold, padPx = 6f * density, inline = true),
                Line(intArrayOf(R.id.w_date), listOf(date), 11f, regular, drop = 1),
                Line(intArrayOf(R.id.w_remain), listOf(r), 13f, medium, drop = 2),
                Line(intArrayOf(R.id.w_stamp), listOf(stamp), 10f, regular, drop = 2),
            ))
            // AM/PM in a smaller size than the hours.
            if (is24) visible(R.id.w_ampm, false) else {
                val clockPx = lastSize[R.id.w_clock]
                if (clockPx != null) size(R.id.w_ampm, clockPx * 0.36f)
            }
        }

        fun measureWidth(face: Typeface, text: String) = Typo.measure(face).width(text, 100f)

        fun analog(snap: PrayerSnapshot, name: String, time: String) {
            val device = ZoneId.systemDefault()
            val clockZone = Dial.clockZone(Build.VERSION.SDK_INT, zone, device)
            if (Build.VERSION.SDK_INT >= 31) rv.setString(R.id.w_clock, "setTimeZone", zone.id)
            val deviceLabel = Dial.showsDeviceClock(Build.VERSION.SDK_INT, zone, device, now)
            val nt = "$name ${fmt.time(snap.next!!.instant, clockZone)}"
            text(R.id.w_name, nt); color(R.id.w_name, colors.accent)
            val sub = if (deviceLabel) str.wDeviceClock else (place?.name(lang) ?: "")
            text(R.id.w_place, sub); color(R.id.w_place, colors.muted)
            // Text gets at most 40% of the height; the face takes the rest.
            solve(listOf(
                Line(intArrayOf(R.id.w_name), listOf(nt), 12f, medium, drop = 2),
                Line(intArrayOf(R.id.w_place), listOf(sub), 10f, regular, drop = if (deviceLabel) 1 else 3),
            ), h = boxH * 0.4f, gap = 1f)
            val marks = snap.times.mapNotNull { t -> t.at?.let { DialMark(Dial.angle(it, clockZone), t.isNext, t.key == PrayerKey.SUNRISE) } }
            val px = minOf(boxW, boxH * 0.8f).toInt()
            rv.setImageViewBitmap(R.id.w_dial, WidgetDial.draw(px, colors, marks))
            key.append("dial").append(marks).append(clockZone.id).append(px).append('|')
        }

        /** The content card: today's item (or a later one) that fits whole at a readable size. */
        fun content(): Instant {
            val pack = env.pack ?: ContentPack()
            val today = now.atZone(zone).toLocalDate()
            val headerH = 22f * density
            val sourcePx = (11f * unit).coerceAtMost(14f * density)
            val sourceH = sourcePx * Typo.lineHeight(regular) + 4f * density
            val textH = boxH - headerH - sourceH
            val ayahFace = Typo.face(ctx, R.font.amiri_quran)
            val dhikrFace = Typo.face(ctx, R.font.amiri_regular)
            val maxPx = 22f * unit
            val minPx = 12.5f * density
            fun sizeFor(item: ContentItem): Float? =
                Fit.block(item.text, boxW, textH, maxPx, minPx, Typo.block(if (item.isAyah) ayahFace else dhikrFace, 1.1f))
            val item = ContentPicker.pick(pack, cfg.contentMode, today, cfg.contentShift) { sizeFor(it) != null }
            if (!preview) rv.setOnClickPendingIntent(R.id.w_next_btn, WidgetIntents.next(ctx, kind, id))
            rv.setInt(R.id.w_next_btn, "setColorFilter", colors.muted)
            if (item == null) {
                message(str.wContentTooSmall, null)
                return WidgetMath.nextMidnight(now, zone)
            }
            val label = if (item.isAyah) str.wLabelAyah else str.wLabelDhikr
            text(R.id.w_label, label); color(R.id.w_label, colors.accent); size(R.id.w_label, sourcePx)
            val textId = if (item.isAyah) R.id.w_text_ayah else R.id.w_text_dhikr
            visible(R.id.w_text_ayah, item.isAyah); visible(R.id.w_text_dhikr, !item.isAyah)
            text(textId, item.text); color(textId, colors.text); size(textId, sizeFor(item)!!)
            val source = if (item.isAyah) str.digits(if (lang == Lang.AR) item.sourceAr else item.sourceEn)
            else listOfNotNull(
                str.digits(if (lang == Lang.AR) item.sourceAr else item.sourceEn),
                (if (lang == Lang.AR) item.gradeAr else item.gradeEn).takeIf { it.isNotBlank() },
                item.count.takeIf { it > 1 }?.let { str.wTimesCount(fmt.num(it)) },
            ).joinToString(" · ")
            text(R.id.w_source, source); color(R.id.w_source, colors.muted); size(R.id.w_source, sourcePx)
            if (!preview) rv.setOnClickPendingIntent(R.id.w_root, WidgetIntents.open(ctx, if (item.isAyah) "ayah:${item.ref}" else "dhikr:${item.ref}"))
            return WidgetMath.nextMidnight(now, zone)
        }

        fun dawnDusk(): Instant {
            val d = DawnDuskCard.of(schedule!!, now, s.hijriOffset)
            val placeL = placeLine()
            val today = now.atZone(zone).toLocalDate()
            val tomorrow = d.date != today
            val fajrL = if (tomorrow) "${str.prayer(PrayerKey.FAJR)} · ${str.tomorrow}" else str.prayer(PrayerKey.FAJR)
            val maghribL = if (tomorrow) "${str.prayer(PrayerKey.MAGHRIB)} · ${str.tomorrow}" else str.prayer(PrayerKey.MAGHRIB)
            val fajrT = d.fajr?.let { fmt.time(it, zone) } ?: "—"
            val maghribT = d.maghrib?.let { fmt.time(it, zone) } ?: "—"
            text(R.id.w_fajr_label, fajrL); color(R.id.w_fajr_label, colors.muted)
            text(R.id.w_maghrib_label, maghribL); color(R.id.w_maghrib_label, colors.muted)
            text(R.id.w_fajr_time, fajrT); color(R.id.w_fajr_time, colors.text)
            text(R.id.w_maghrib_time, maghribT); color(R.id.w_maghrib_time, colors.text)
            val hijri = d.hijri?.let { fmt.hijri(it) } ?: ""
            text(R.id.w_hijri, hijri); color(R.id.w_hijri, colors.text)
            val ctxLine = when (val r = d.ramadan) {
                is RamadanContext.Fasting -> str.wRamadanFasting(fmt.num(r.day), fmt.durationShort(Duration.ofMinutes(r.minutesLeft)))
                is RamadanContext.BeforeFajr -> str.wRamadanBeforeFajr(fmt.num(r.day), fmt.durationShort(Duration.ofMinutes(r.minutesLeft)))
                is RamadanContext.DaysUntil -> str.wRamadanIn(str.daysCount(fmt.num(r.days.toInt()), r.days))
                RamadanContext.None -> ""
            }
            val live = d.ramadan is RamadanContext.Fasting || d.ramadan is RamadanContext.BeforeFajr
            text(R.id.w_ramadan, ctxLine); color(R.id.w_ramadan, colors.accent)
            val stamp = stampText()
            text(R.id.w_stamp, stamp); color(R.id.w_stamp, colors.muted)
            // The update time travels with a remaining time only.
            val lines = buildList {
                add(placeL)
                add(Line(intArrayOf(R.id.w_fajr_label, R.id.w_maghrib_label), listOf(fajrL, maghribL), 12f, medium))
                add(Line(intArrayOf(R.id.w_fajr_time, R.id.w_maghrib_time), listOf(fajrT, maghribT), 20f, semibold, padPx = 4f * density))
                if (hijri.isNotEmpty()) add(Line(intArrayOf(R.id.w_hijri), listOf(hijri), 12f, regular, drop = 1))
                if (ctxLine.isNotEmpty()) add(Line(intArrayOf(R.id.w_ramadan), listOf(ctxLine), 12f, medium, drop = if (live) 0 else 2))
                if (live) add(Line(intArrayOf(R.id.w_stamp), listOf(stamp), 10f, regular))
            }
            solve(lines, gap = 2f)
            if (hijri.isEmpty()) visible(R.id.w_hijri, false)
            if (ctxLine.isEmpty()) visible(R.id.w_ramadan, false)
            if (!live) visible(R.id.w_stamp, false)
            return WidgetMath.nextChange(kind, now, zone, null)
        }
    }
}
