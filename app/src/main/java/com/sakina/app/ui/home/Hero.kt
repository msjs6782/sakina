package com.sakina.app.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.sakina.app.core.prayer.DayTimes
import com.sakina.app.core.prayer.PrayerKey
import com.sakina.app.ui.components.MonoDigits
import com.sakina.app.ui.components.prayerIcon
import com.sakina.app.ui.theme.LocalExtras
import com.sakina.app.ui.theme.SkyArt
import com.sakina.app.ui.theme.SkyPeriod
import java.time.Instant
import kotlin.random.Random

fun skyPeriod(day: DayTimes, now: Instant): SkyPeriod {
    fun t(k: PrayerKey) = day[k].instant
    val fajr = t(PrayerKey.FAJR)
    val sunrise = t(PrayerKey.SUNRISE)
    val dhuhr = t(PrayerKey.DHUHR)
    val asr = t(PrayerKey.ASR)
    val maghrib = t(PrayerKey.MAGHRIB)
    val isha = t(PrayerKey.ISHA)
    return when {
        isha != null && now >= isha -> SkyPeriod.NIGHT
        maghrib != null && now >= maghrib -> SkyPeriod.MAGHRIB
        asr != null && now >= asr -> SkyPeriod.ASR
        dhuhr != null && now >= dhuhr -> SkyPeriod.NOON
        sunrise != null && now >= sunrise -> SkyPeriod.MORNING
        fajr != null && now >= fajr -> SkyPeriod.FAJR
        fajr != null -> SkyPeriod.NIGHT
        else -> SkyPeriod.NOON
    }
}

/** The decorated surface behind the next-prayer card: sky gradient, starry indigo, or a flat sand panel. */
@Composable
fun HeroSurface(period: SkyPeriod, modifier: Modifier = Modifier, corner: Dp = 28.dp, decorate: Boolean = true, content: @Composable BoxScope.() -> Unit) {
    val ex = LocalExtras.current
    if (ex.art) SkySurface(period, modifier, corner, content) else GradientSurface(period, modifier, corner, decorate, content)
}

/**
 * The painted sky of the time of day, anchored to the bottom (the skyline) and mirrored in right-to-left layouts so
 * the town sits away from where the text starts, under a veil that keeps the white text readable.
 */
@Composable
private fun SkySurface(period: SkyPeriod, modifier: Modifier, corner: Dp, content: @Composable BoxScope.() -> Unit) {
    val ex = LocalExtras.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val a = SkyArt.scrim(period, ex.dark)
    Box(modifier.clip(RoundedCornerShape(corner)).background(Color(SkyArt.topColor(period))).testTag("hero_sky_${period.name.lowercase()}")) {
        Image(
            painterResource(SkyArt.drawable(period)), null,
            Modifier.matchParentSize().graphicsLayer { if (rtl) scaleX = -1f },
            contentScale = ContentScale.Crop,
            alignment = Alignment.BottomCenter,
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(0f to Color.Black.copy(alpha = a), 0.55f to Color.Black.copy(alpha = a * 0.8f), 1f to Color.Black.copy(alpha = a)),
            ),
        )
        content()
    }
}

@Composable
private fun GradientSurface(period: SkyPeriod, modifier: Modifier, corner: Dp, decorate: Boolean, content: @Composable BoxScope.() -> Unit) {
    val ex = LocalExtras.current
    val shape = RoundedCornerShape(corner)
    var m = modifier.clip(shape).background(ex.heroBrush(period))
    if (!ex.heroIsGradient) m = m.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(m) {
        Canvas(Modifier.matchParentSize()) {
            val archColor = if (ex.heroIsGradient) Color.White.copy(alpha = 0.10f) else ex.gold.copy(alpha = 0.18f)
            // A mihrab arch on the end side of the card.
            val h = size.height * 0.92f
            val w = h * 0.62f
            val left = if (rtl) size.width * 0.04f else size.width - w - size.width * 0.04f
            val bottom = size.height + 2f
            val top = bottom - h
            val path = Path().apply {
                moveTo(left, bottom)
                lineTo(left, top + h * 0.42f)
                cubicTo(left, top + h * 0.2f, left + w * 0.28f, top + h * 0.1f, left + w / 2f, top)
                cubicTo(left + w * 0.72f, top + h * 0.1f, left + w, top + h * 0.2f, left + w, top + h * 0.42f)
                lineTo(left + w, bottom)
            }
            if (decorate) drawPath(path, archColor, style = Stroke(width = 2.dp.toPx()))
            if (ex.stars) {
                val rnd = Random(7)
                repeat(26) {
                    val x = rnd.nextFloat() * size.width
                    val y = rnd.nextFloat() * size.height * 0.75f
                    val r = (0.6f + rnd.nextFloat() * 1.4f) * density
                    drawCircle(Color.White.copy(alpha = 0.25f + rnd.nextFloat() * 0.45f), r, Offset(x, y))
                }
            }
        }
        content()
    }
}

@Composable
fun HeroProgress(fraction: Float, modifier: Modifier = Modifier) {
    val ex = LocalExtras.current
    val track = if (ex.heroIsGradient) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.outlineVariant
    val fill = if (ex.heroIsGradient) Color.White else MaterialTheme.colorScheme.primary
    Canvas(modifier.fillMaxWidth().height(6.dp)) {
        val y = size.height / 2
        val stroke = size.height
        drawLine(track, Offset(0f, y), Offset(size.width, y), strokeWidth = stroke, cap = StrokeCap.Round)
        val f = fraction.coerceIn(0f, 1f)
        if (f > 0f) {
            val rtl = layoutDirection == LayoutDirection.Rtl
            val start = if (rtl) size.width else 0f
            val end = if (rtl) size.width * (1 - f) else size.width * f
            drawLine(fill, Offset(start, y), Offset(end, y), strokeWidth = stroke, cap = StrokeCap.Round)
        }
    }
}

/** Shared hero text block: label, prayer name with icon, absolute time, countdown and progress. */
@Composable
fun HeroNext(
    label: String,
    prayerName: String,
    prayerKey: PrayerKey,
    timeText: String,
    dateNote: String?,
    remainingLabel: String,
    countdown: String,
    progress: Float?,
    sinceText: String?,
    modifier: Modifier = Modifier,
) {
    val ex = LocalExtras.current
    Column(modifier.fillMaxWidth().padding(20.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = ex.heroOnMuted)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(prayerIcon(prayerKey), null, Modifier.size(30.dp), tint = ex.heroOn)
            Spacer(Modifier.width(10.dp))
            Text(prayerName, style = MaterialTheme.typography.headlineLarge, color = ex.heroOn, modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(timeText, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Medium), color = ex.heroOn)
                if (dateNote != null) Text(dateNote, style = MaterialTheme.typography.labelMedium, color = ex.heroOnMuted)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(remainingLabel, style = MaterialTheme.typography.titleSmall, color = ex.heroOnMuted, modifier = Modifier.padding(bottom = 8.dp))
            MonoDigits(countdown, MaterialTheme.typography.displayMedium, ex.heroOn)
        }
        if (progress != null) {
            Spacer(Modifier.height(10.dp))
            HeroProgress(progress)
        }
        if (sinceText != null) {
            Spacer(Modifier.height(8.dp))
            Text(sinceText, style = MaterialTheme.typography.bodySmall, color = ex.heroOnMuted)
        }
    }
}

@Composable
fun HeroBlank(content: @Composable BoxScope.() -> Unit) = Box(Modifier.fillMaxSize(), content = content)
