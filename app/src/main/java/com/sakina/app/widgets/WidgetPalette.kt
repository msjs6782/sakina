package com.sakina.app.widgets

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.sakina.app.core.widgets.Contrast
import com.sakina.app.data.AppSettings
import com.sakina.app.data.ThemeMode
import com.sakina.app.data.VisualStyle
import com.sakina.app.data.WidgetConfig
import com.sakina.app.data.WidgetTheme
import com.sakina.app.ui.sunIsUp
import java.time.Instant

/** The colours one widget is drawn with (ARGB). [bgAlpha] is 0–255. */
data class WidgetColors(
    val bg: Int,
    val bgAlpha: Int,
    val text: Int,
    val muted: Int,
    val accent: Int,
    /** Highlight behind the next prayer in tables (opaque; drawn at [softAlpha]). */
    val soft: Int,
    val border: Int,
    val dark: Boolean,
    val softAlpha: Int = 255,
) {
    /** Whether hands and marks drawn over the background should be light. */
    val lightInk: Boolean get() = Contrast.luminance(text) > 0.4
}

object WidgetPalette {
    private data class Base(val bg: Int, val text: Int, val muted: Int, val accent: Int, val soft: Int)

    // The app's colour schemes (ui/theme/Theme.kt): container, on-surface, on-surface-variant, primary, primary container.
    private fun base(style: VisualStyle, dark: Boolean): Base = when (style) {
        VisualStyle.OASIS -> if (!dark) Base(0xFFFBF8F2.toInt(), 0xFF1C1B17.toInt(), 0xFF4A4840.toInt(), 0xFF0E6B5E.toInt(), 0xFFCFEDE5.toInt())
        else Base(0xFF151D1B.toInt(), 0xFFE1E4E0.toInt(), 0xFFBBC5C0.toInt(), 0xFF7FD6C4.toInt(), 0xFF0C4F45.toInt())
        VisualStyle.SAHAR -> if (!dark) Base(0xFFF4F3FA.toInt(), 0xFF1B1B21.toInt(), 0xFF46464F.toInt(), 0xFF4A4FA8.toInt(), 0xFFE0E0FF.toInt())
        else Base(0xFF1B1B23.toInt(), 0xFFE4E1EA.toInt(), 0xFFC7C5D0.toInt(), 0xFFBFC1FF.toInt(), 0xFF32378F.toInt())
        VisualStyle.RAML -> if (!dark) Base(0xFFF8F2EA.toInt(), 0xFF201B16.toInt(), 0xFF51443A.toInt(), 0xFF8A5A2B.toInt(), 0xFFFFDCC0.toInt())
        else Base(0xFF201B17.toInt(), 0xFFEDE0D7.toInt(), 0xFFD6C3B5.toInt(), 0xFFF2B985.toInt(), 0xFF6B4318.toInt())
    }

    private fun dynamicBase(context: Context, dark: Boolean): Base? {
        if (Build.VERSION.SDK_INT < 31) return null
        fun c(id: Int) = context.getColor(id)
        return if (!dark) Base(
            c(android.R.color.system_neutral1_50), c(android.R.color.system_neutral1_900), c(android.R.color.system_neutral2_700),
            c(android.R.color.system_accent1_600), c(android.R.color.system_accent1_100),
        ) else Base(
            c(android.R.color.system_neutral1_900), c(android.R.color.system_neutral1_50), c(android.R.color.system_neutral2_200),
            c(android.R.color.system_accent1_200), c(android.R.color.system_accent1_700),
        )
    }

    fun isDark(context: Context, cfg: WidgetConfig, s: AppSettings, now: Instant): Boolean {
        val system = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        return when (cfg.theme) {
            WidgetTheme.LIGHT -> false
            WidgetTheme.DARK -> true
            WidgetTheme.SYSTEM -> system
            WidgetTheme.APP -> when (s.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> system
                ThemeMode.SUN -> sunIsUp(s, now)?.not() ?: system
            }
        }
    }

    private fun Long.argb() = toInt()
    private fun withAlpha(c: Int, a: Float) = (((c ushr 24) * a).toInt().coerceIn(0, 255) shl 24) or (c and 0xFFFFFF)

    /** Glass panels guard contrast for large text (3:1) against any wallpaper. */
    const val GLASS_TARGET = 3.0

    fun resolve(context: Context, cfg: WidgetConfig, s: AppSettings, now: Instant, glass: Boolean): WidgetColors {
        val dark = isDark(context, cfg, s, now)
        val dynamic = cfg.dynamicColor ?: s.dynamicColor
        val b = (if (dynamic) dynamicBase(context, dark) else null) ?: base(cfg.style ?: s.style, dark)
        var bg = b.bg
        var text = b.text
        var accent = b.accent
        var soft = b.soft
        var softAlpha = 255
        if (glass) {
            // A neutral scrim in the theme's tone, with text of the opposite tone.
            bg = if (dark) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            text = if (dark) 0xFFFFFFFF.toInt() else 0xFF15140F.toInt()
            accent = if (dark) lighten(b.accent) else b.accent
            // A colour filter cannot make the highlight see-through, so it is drawn opaque at a low image alpha.
            soft = if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()
            softAlpha = if (dark) 56 else 30
        }
        cfg.backgroundColor?.let { bg = it.argb() or (0xFF shl 24) }
        cfg.textColor?.let { text = it.argb() or (0xFF shl 24) }
        cfg.accentColor?.let { accent = it.argb() or (0xFF shl 24) }
        val muted = if (cfg.textColor != null || glass) withAlpha(text, 0.78f) else b.muted
        var opacity = cfg.opacity.coerceIn(0, 100)
        if (glass && cfg.contrastGuard) opacity = maxOf(opacity, Contrast.minScrimPercent(text, bg, GLASS_TARGET))
        if (cfg.backgroundColor != null && !glass) { soft = accent; softAlpha = 60 }
        return WidgetColors(bg, opacity * 255 / 100, text, muted, accent, soft, accent, dark, softAlpha)
    }

    /**
     * Colours over a painted sky: white text, a warm gold accent (the user's own text and accent colours still win),
     * and a see-through highlight.
     */
    fun sky(cfg: WidgetConfig, top: Int): WidgetColors {
        val text = cfg.textColor?.let { it.toInt() or (0xFF shl 24) } ?: 0xFFFFFFFF.toInt()
        val accent = cfg.accentColor?.let { it.toInt() or (0xFF shl 24) } ?: 0xFFF3D58B.toInt()
        return WidgetColors(top, 255, text, withAlpha(text, 0.86f), accent, 0xFFFFFFFF.toInt(), accent, dark = true, softAlpha = 60)
    }

    /** Colours over a painted texture: light text on the teal pattern and night lattice, dark ink on paper. */
    fun texture(cfg: WidgetConfig, bg: com.sakina.app.data.WidgetBackground): WidgetColors {
        val paper = bg == com.sakina.app.data.WidgetBackground.PAPER
        val base = when (bg) {
            com.sakina.app.data.WidgetBackground.PAPER -> 0xFFEAD7B2.toInt()
            com.sakina.app.data.WidgetBackground.GEOMETRIC -> 0xFF4C7975.toInt()
            else -> 0xFF24313F.toInt()
        }
        val text = cfg.textColor?.let { it.toInt() or (0xFF shl 24) } ?: if (paper) 0xFF2B2118.toInt() else 0xFFFFFFFF.toInt()
        val accent = cfg.accentColor?.let { it.toInt() or (0xFF shl 24) } ?: if (paper) 0xFF8A4F1D.toInt() else 0xFFF3D58B.toInt()
        return WidgetColors(base, 255, text, withAlpha(text, if (paper) 0.78f else 0.86f), accent,
            if (paper) 0xFF000000.toInt() else 0xFFFFFFFF.toInt(), accent, dark = !paper, softAlpha = if (paper) 28 else 60)
    }

    /** The minimum glass opacity the contrast guard keeps for these colours, in percent. */
    fun glassFloor(context: Context, cfg: WidgetConfig, s: AppSettings, now: Instant): Int {
        val c = resolve(context, cfg.copy(opacity = 0, contrastGuard = false), s, now, glass = true)
        return Contrast.minScrimPercent(c.text, c.bg, GLASS_TARGET)
    }

    private fun lighten(c: Int): Int {
        fun ch(s: Int) = (((c shr s) and 0xFF) + (255 - ((c shr s) and 0xFF)) * 0.45f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** Swatches offered in the widget settings (ARGB). */
    val SWATCHES: List<Long> = listOf(
        0xFFFFFFFF, 0xFFF6F3EC, 0xFFE6E1D6, 0xFF8A8A8A, 0xFF3A3A3A, 0xFF101010,
        0xFF0E6B5E, 0xFF7FD6C4, 0xFF4A4FA8, 0xFFBFC1FF, 0xFF8A5A2B, 0xFFF2B985,
        0xFF9A4F65, 0xFFE5C589, 0xFF1F6E8C, 0xFFB3261E,
    )
}
