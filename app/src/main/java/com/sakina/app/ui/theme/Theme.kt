package com.sakina.app.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.sakina.app.R
import com.sakina.app.data.VisualStyle

val Plex = FontFamily(
    Font(R.font.plex_light, FontWeight.Light),
    Font(R.font.plex_regular, FontWeight.Normal),
    Font(R.font.plex_medium, FontWeight.Medium),
    Font(R.font.plex_semibold, FontWeight.SemiBold),
    Font(R.font.plex_bold, FontWeight.Bold),
)

/** Tabular Arabic-Indic and Latin digits (Noto Kufi Arabic, subset) for countdowns. */
val KufiDigits = FontFamily(Font(R.font.kufi_digits, FontWeight.Medium))

val Amiri = FontFamily(
    Font(R.font.amiri_regular, FontWeight.Normal),
    Font(R.font.amiri_bold, FontWeight.Bold),
)

private fun style(size: TextUnit, weight: FontWeight, line: Float = 1.5f) =
    TextStyle(fontFamily = Plex, fontWeight = weight, fontSize = size, lineHeight = size * line)

val SakinaTypography = Typography(
    displayLarge = style(54.sp, FontWeight.Light, 1.25f),
    displayMedium = style(44.sp, FontWeight.Light, 1.25f),
    displaySmall = style(36.sp, FontWeight.Normal, 1.3f),
    headlineLarge = style(30.sp, FontWeight.SemiBold, 1.35f),
    headlineMedium = style(26.sp, FontWeight.SemiBold, 1.35f),
    headlineSmall = style(22.sp, FontWeight.SemiBold, 1.4f),
    titleLarge = style(20.sp, FontWeight.SemiBold),
    titleMedium = style(16.sp, FontWeight.SemiBold),
    titleSmall = style(14.sp, FontWeight.SemiBold),
    bodyLarge = style(16.sp, FontWeight.Normal),
    bodyMedium = style(14.sp, FontWeight.Normal),
    bodySmall = style(12.5.sp, FontWeight.Normal),
    labelLarge = style(14.sp, FontWeight.Medium),
    labelMedium = style(12.5.sp, FontWeight.Medium),
    labelSmall = style(11.sp, FontWeight.Medium),
)

/** Time-of-day periods used to tint the hero card. */
enum class SkyPeriod { NIGHT, FAJR, MORNING, NOON, ASR, MAGHRIB }

@Immutable
data class SakinaExtras(
    val style: VisualStyle,
    val dark: Boolean,
    val heroIsGradient: Boolean,
    val heroOn: Color,
    val heroOnMuted: Color,
    val heroFlat: Color,
    val gold: Color,
    val stars: Boolean,
    val sahar: Pair<Color, Color>,
) {
    fun heroBrush(period: SkyPeriod): Brush = when (style) {
        VisualStyle.OASIS -> {
            val (a, b) = sky(period)
            if (dark) Brush.verticalGradient(listOf(lerp(a, Color.Black, 0.28f), lerp(b, Color.Black, 0.35f)))
            else Brush.verticalGradient(listOf(a, b))
        }
        VisualStyle.SAHAR -> Brush.linearGradient(listOf(sahar.first, sahar.second))
        VisualStyle.RAML -> Brush.verticalGradient(listOf(heroFlat, heroFlat))
    }

    private fun sky(p: SkyPeriod): Pair<Color, Color> = when (p) {
        SkyPeriod.NIGHT -> Color(0xFF0B1D3A) to Color(0xFF203F63)
        SkyPeriod.FAJR -> Color(0xFF2C3A74) to Color(0xFFB9748A)
        SkyPeriod.MORNING -> Color(0xFF1F6E8C) to Color(0xFF5FB2CF)
        SkyPeriod.NOON -> Color(0xFF0E6B5E) to Color(0xFF2E9C8A)
        SkyPeriod.ASR -> Color(0xFF9A5B22) to Color(0xFFD39A45)
        SkyPeriod.MAGHRIB -> Color(0xFF4F2A68) to Color(0xFFD06F55)
    }
}

val LocalExtras = staticCompositionLocalOf {
    SakinaExtras(VisualStyle.OASIS, false, true, Color.White, Color.White.copy(alpha = 0.78f), Color.White, Color(0xFFE5B862), false, Color.Black to Color.Black)
}

private fun oasis(dark: Boolean): ColorScheme = if (!dark) lightColorScheme(
    primary = Color(0xFF0E6B5E), onPrimary = Color.White, primaryContainer = Color(0xFFCFEDE5), onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF8A6A2F), onSecondary = Color.White, secondaryContainer = Color(0xFFF6E2BC), onSecondaryContainer = Color(0xFF2B1E00),
    tertiary = Color(0xFF3B6472), onTertiary = Color.White, tertiaryContainer = Color(0xFFCDE9F5), onTertiaryContainer = Color(0xFF001F28),
    background = Color(0xFFF6F3EC), onBackground = Color(0xFF1C1B17), surface = Color(0xFFF6F3EC), onSurface = Color(0xFF1C1B17),
    surfaceVariant = Color(0xFFE6E1D6), onSurfaceVariant = Color(0xFF4A4840),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFBF8F2), surfaceContainer = Color(0xFFF1EDE4),
    surfaceContainerHigh = Color(0xFFEBE6DC), surfaceContainerHighest = Color(0xFFE5DFD4),
    outline = Color(0xFF7B786E), outlineVariant = Color(0xFFD3CDC0),
) else darkColorScheme(
    primary = Color(0xFF7FD6C4), onPrimary = Color(0xFF00382F), primaryContainer = Color(0xFF0C4F45), onPrimaryContainer = Color(0xFFC8F1E7),
    secondary = Color(0xFFE5C589), onSecondary = Color(0xFF422F00), secondaryContainer = Color(0xFF5C4718), onSecondaryContainer = Color(0xFFFCE6BF),
    tertiary = Color(0xFFA4CDDC), onTertiary = Color(0xFF033542), tertiaryContainer = Color(0xFF224C5A), onTertiaryContainer = Color(0xFFCDE9F5),
    background = Color(0xFF0E1513), onBackground = Color(0xFFE1E4E0), surface = Color(0xFF0E1513), onSurface = Color(0xFFE1E4E0),
    surfaceVariant = Color(0xFF3F4945), onSurfaceVariant = Color(0xFFBBC5C0),
    surfaceContainerLowest = Color(0xFF09100E), surfaceContainerLow = Color(0xFF151D1B), surfaceContainer = Color(0xFF19221F),
    surfaceContainerHigh = Color(0xFF222C29), surfaceContainerHighest = Color(0xFF2C3734),
    outline = Color(0xFF86918C), outlineVariant = Color(0xFF3B4642),
)

private fun sahar(dark: Boolean): ColorScheme = if (!dark) lightColorScheme(
    primary = Color(0xFF4A4FA8), onPrimary = Color.White, primaryContainer = Color(0xFFE0E0FF), onPrimaryContainer = Color(0xFF04006D),
    secondary = Color(0xFF9A4F65), onSecondary = Color.White, secondaryContainer = Color(0xFFFFD9E1), onSecondaryContainer = Color(0xFF3F0020),
    tertiary = Color(0xFF6B5B92), onTertiary = Color.White, tertiaryContainer = Color(0xFFEBDDFF), onTertiaryContainer = Color(0xFF25164A),
    background = Color(0xFFF8F7FC), onBackground = Color(0xFF1B1B21), surface = Color(0xFFF8F7FC), onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE3E1EC), onSurfaceVariant = Color(0xFF46464F),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF4F3FA), surfaceContainer = Color(0xFFEEEDF5),
    surfaceContainerHigh = Color(0xFFE8E7F0), surfaceContainerHighest = Color(0xFFE2E1EA),
    outline = Color(0xFF777680), outlineVariant = Color(0xFFC7C5D0),
) else darkColorScheme(
    primary = Color(0xFFBFC1FF), onPrimary = Color(0xFF1A1D78), primaryContainer = Color(0xFF32378F), onPrimaryContainer = Color(0xFFE0E0FF),
    secondary = Color(0xFFFFB1C5), onSecondary = Color(0xFF5E1135), secondaryContainer = Color(0xFF7B2E4B), onSecondaryContainer = Color(0xFFFFD9E1),
    tertiary = Color(0xFFD3BCFD), onTertiary = Color(0xFF3B2C60), tertiaryContainer = Color(0xFF524377), onTertiaryContainer = Color(0xFFEBDDFF),
    background = Color(0xFF12121A), onBackground = Color(0xFFE4E1EA), surface = Color(0xFF12121A), onSurface = Color(0xFFE4E1EA),
    surfaceVariant = Color(0xFF46464F), onSurfaceVariant = Color(0xFFC7C5D0),
    surfaceContainerLowest = Color(0xFF0D0D14), surfaceContainerLow = Color(0xFF1B1B23), surfaceContainer = Color(0xFF1F1F28),
    surfaceContainerHigh = Color(0xFF292932), surfaceContainerHighest = Color(0xFF34343D),
    outline = Color(0xFF91909A), outlineVariant = Color(0xFF46464F),
)

private fun raml(dark: Boolean): ColorScheme = if (!dark) lightColorScheme(
    primary = Color(0xFF8A5A2B), onPrimary = Color.White, primaryContainer = Color(0xFFFFDCC0), onPrimaryContainer = Color(0xFF2E1500),
    secondary = Color(0xFF5F6F37), onSecondary = Color.White, secondaryContainer = Color(0xFFF3E2C7), onSecondaryContainer = Color(0xFF2B1D0C),
    tertiary = Color(0xFF4F6354), onTertiary = Color.White, tertiaryContainer = Color(0xFFD1E8D5), onTertiaryContainer = Color(0xFF0C1F13),
    background = Color(0xFFFBF7F1), onBackground = Color(0xFF201B16), surface = Color(0xFFFBF7F1), onSurface = Color(0xFF201B16),
    surfaceVariant = Color(0xFFEFE0D3), onSurfaceVariant = Color(0xFF51443A),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF8F2EA), surfaceContainer = Color(0xFFF2EBE2),
    surfaceContainerHigh = Color(0xFFECE5DC), surfaceContainerHighest = Color(0xFFE6DFD6),
    outline = Color(0xFF84746A), outlineVariant = Color(0xFFD6C3B5),
) else darkColorScheme(
    primary = Color(0xFFF2B985), onPrimary = Color(0xFF4B2800), primaryContainer = Color(0xFF6B4318), onPrimaryContainer = Color(0xFFFFDCC0),
    secondary = Color(0xFFC5D497), onSecondary = Color(0xFF2F3810), secondaryContainer = Color(0xFF4A3B26), onSecondaryContainer = Color(0xFFF3E2C7),
    tertiary = Color(0xFFB5CCB9), onTertiary = Color(0xFF213527), tertiaryContainer = Color(0xFF374B3D), onTertiaryContainer = Color(0xFFD1E8D5),
    background = Color(0xFF18130F), onBackground = Color(0xFFEDE0D7), surface = Color(0xFF18130F), onSurface = Color(0xFFEDE0D7),
    surfaceVariant = Color(0xFF51443A), onSurfaceVariant = Color(0xFFD6C3B5),
    surfaceContainerLowest = Color(0xFF120D0A), surfaceContainerLow = Color(0xFF201B17), surfaceContainer = Color(0xFF251F1A),
    surfaceContainerHigh = Color(0xFF2F2924), surfaceContainerHighest = Color(0xFF3A342E),
    outline = Color(0xFF9F8D81), outlineVariant = Color(0xFF51443A),
)

val dynamicColorSupported: Boolean get() = Build.VERSION.SDK_INT >= 31

@Composable
fun SakinaTheme(style: VisualStyle, dark: Boolean, dynamic: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val useDynamic = dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scheme = if (dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        when (style) {
            VisualStyle.OASIS -> oasis(dark)
            VisualStyle.SAHAR -> sahar(dark)
            VisualStyle.RAML -> raml(dark)
        }
    }
    val saharColors = if (useDynamic) {
        scheme.primary.let { lerp(it, Color.Black, if (dark) 0.55f else 0.35f) } to scheme.tertiary.let { lerp(it, Color.Black, if (dark) 0.45f else 0.15f) }
    } else if (dark) Color(0xFF1C1D52) to Color(0xFF4F2B52) else Color(0xFF2E2F7A) to Color(0xFF8E4A7A)
    val extras = SakinaExtras(
        style = style,
        dark = dark,
        heroIsGradient = style != VisualStyle.RAML,
        heroOn = if (style == VisualStyle.RAML) scheme.onSurface else Color.White,
        heroOnMuted = if (style == VisualStyle.RAML) scheme.onSurfaceVariant else Color.White.copy(alpha = 0.8f),
        heroFlat = scheme.surfaceContainerHigh,
        gold = if (dark) Color(0xFFE9C77F) else Color(0xFF9A7428),
        stars = style == VisualStyle.SAHAR,
        sahar = saharColors,
    )
    CompositionLocalProvider(LocalExtras provides extras) {
        MaterialTheme(colorScheme = scheme, typography = SakinaTypography, content = content)
    }
}
