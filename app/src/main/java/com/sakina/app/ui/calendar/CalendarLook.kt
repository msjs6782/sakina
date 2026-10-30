package com.sakina.app.ui.calendar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sakina.app.ui.theme.LocalExtras
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

/*
 * The calendar's raised look: tiles lit from above with a soft shadow beneath, a gold hairline on what matters,
 * and controls that sink a little when pressed.
 */

/** A surface lit from above: lighter at the top, a shade deeper at the bottom. */
internal fun lit(c: Color, dark: Boolean, depth: Float = 1f): Brush = Brush.verticalGradient(
    listOf(
        lerp(c, Color.White, (if (dark) 0.07f else 0.45f) * depth),
        lerp(c, Color.Black, (if (dark) 0.10f else 0.04f) * depth),
    ),
)

/** The gold of the paintings and the clock hands, as a slightly burnished gradient for edges. */
@Composable
internal fun goldEdge(alpha: Float = 1f): Brush {
    val g = LocalExtras.current.gold
    return Brush.linearGradient(listOf(g.copy(alpha = alpha), lerp(g, Color.White, 0.45f).copy(alpha = alpha * 0.7f), g.copy(alpha = alpha)))
}

/** Scales a pressable surface down while it is held, for a tactile press. */
@Composable
internal fun pressScale(source: MutableInteractionSource, pressed: Float = 0.92f): Float {
    val down by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (down) pressed else 1f, spring(dampingRatio = 0.55f, stiffness = 700f), label = "press")
    return s
}

/** A raised card: a soft shadow, a lit surface and a hairline edge. */
@Composable
internal fun Modifier.raisedCard(shape: Shape, elevation: Dp = 8.dp, base: Color? = null, edge: Brush? = null): Modifier {
    val cs = MaterialTheme.colorScheme
    val dark = LocalExtras.current.dark
    val c = base ?: (if (dark) cs.surfaceContainer else cs.surfaceContainerLowest)
    return this
        .shadow(elevation, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.3f))
        .clip(shape)
        .background(lit(c, dark, 0.6f))
        .border(1.dp, edge ?: Brush.linearGradient(listOf(cs.outlineVariant.copy(alpha = 0.7f), cs.outlineVariant.copy(alpha = 0.25f))), shape)
}

/**
 * Hijri / Gregorian as a raised switch: a sunken track with a lit thumb that slides under the chosen calendar.
 * [labels] and [tags] are in order (first at the start side).
 */
@Composable
internal fun RaisedSwitch(labels: List<String>, tags: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val dark = LocalExtras.current.dark
    val track = RoundedCornerShape(50)
    BoxWithConstraints(
        modifier.height(46.dp).clip(track)
            .background(Brush.verticalGradient(listOf(lerp(cs.surfaceContainerHighest, Color.Black, if (dark) 0.15f else 0.06f), cs.surfaceContainerHigh)))
            .border(1.dp, cs.outlineVariant.copy(alpha = 0.6f), track),
    ) {
        val w = maxWidth / labels.size
        val x by animateDpAsState(w * selected, spring(dampingRatio = 0.75f, stiffness = 500f), label = "thumb")
        Box(
            Modifier.offset(x = x).width(w).fillMaxHeight().padding(4.dp)
                .shadow(6.dp, track, ambientColor = cs.primary, spotColor = cs.primary)
                .clip(track).background(lit(cs.primary, dark, 1.2f))
                .border(1.dp, goldEdge(0.55f), track),
        )
        Row(Modifier.fillMaxSize()) {
            labels.forEachIndexed { i, label ->
                val on = i == selected
                val ink by animateColorAsState(if (on) cs.onPrimary else cs.onSurfaceVariant, tween(200), label = "ink")
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(track).clickable(role = Role.Tab) { onSelect(i) }.testTag(tags[i]),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (on) FontWeight.Bold else FontWeight.Medium), color = ink, maxLines = 1)
                }
            }
        }
    }
}

/** A raised gold pill (the "Today" button). */
@Composable
internal fun GoldPill(text: String, tag: String, onClick: () -> Unit) {
    val g = LocalExtras.current.gold
    val shape = RoundedCornerShape(50)
    val source = remember { MutableInteractionSource() }
    val s = pressScale(source)
    Box(
        Modifier.height(46.dp).graphicsLayer { scaleX = s; scaleY = s }
            .shadow(7.dp, shape, ambientColor = g, spotColor = lerp(g, Color.Black, 0.3f))
            .clip(shape)
            .background(Brush.verticalGradient(listOf(lerp(g, Color.White, 0.42f), g, lerp(g, Color.Black, 0.12f))))
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.7f), Color.Transparent)), shape)
            .clickable(interactionSource = source, indication = androidx.compose.material3.ripple(), role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold), color = Color(0xFF3A2A06), maxLines = 1)
    }
}

/** A frosted round button on the painted header. */
@Composable
internal fun GlassButton(onLight: Boolean, tag: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val s = pressScale(source, 0.86f)
    val ink = if (onLight) MaterialTheme.colorScheme.onPrimaryContainer else Color.White
    Box(
        Modifier.size(42.dp).graphicsLayer { scaleX = s; scaleY = s }.clip(CircleShape)
            .background(Brush.verticalGradient(listOf(ink.copy(alpha = 0.22f), ink.copy(alpha = 0.08f))))
            .border(1.dp, Brush.verticalGradient(listOf(ink.copy(alpha = 0.55f), ink.copy(alpha = 0.12f))), CircleShape)
            .clickable(interactionSource = source, indication = androidx.compose.material3.ripple(), role = Role.Button, onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** A thin gold rule with a small diamond in the middle, under the month's name. */
@Composable
internal fun Ornament(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.width(132.dp).height(10.dp)) {
        val y = size.height / 2
        val mid = size.width / 2
        val d = 4.dp.toPx()
        drawLine(Brush.horizontalGradient(listOf(Color.Transparent, color), 0f, mid - d * 2), Offset(0f, y), Offset(mid - d * 2, y), strokeWidth = 1.2.dp.toPx())
        drawLine(Brush.horizontalGradient(listOf(color, Color.Transparent), mid + d * 2, size.width), Offset(mid + d * 2, y), Offset(size.width, y), strokeWidth = 1.2.dp.toPx())
        val p = Path().apply { moveTo(mid, y - d); lineTo(mid + d, y); lineTo(mid, y + d); lineTo(mid - d, y); close() }
        drawPath(p, color)
    }
}

/**
 * The moon of a Hijri day as a small lit sphere: the lit part grows from the crescent after the month begins to the
 * full moon in the middle of the month, then wanes. Approximate (by the day of the month), for decoration.
 */
@Composable
internal fun Moon(hijriDay: Int, modifier: Modifier = Modifier, size: Dp = 46.dp) {
    val age = ((hijriDay - 1).coerceIn(0, 29) + 0.5) / 29.53
    val lit = ((1 - cos(2 * PI * age)) / 2).toFloat()
    val waxing = age < 0.5
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2
        val c = Offset(r, r)
        // A soft glow, the dark disc, then the lit part.
        drawCircle(Brush.radialGradient(listOf(Color(0x55FFF3C9), Color.Transparent), c, r * 1.0f), r, c)
        val rr = r * 0.82f
        drawCircle(Brush.radialGradient(listOf(Color(0xFF3A4566), Color(0xFF1A2138)), c + Offset(-rr * 0.3f, -rr * 0.3f), rr * 1.6f), rr, c)
        val bright = Brush.radialGradient(listOf(Color(0xFFFFF8E1), Color(0xFFF1DFA8), Color(0xFFC9AE6A)), c + Offset(-rr * 0.35f, -rr * 0.4f), rr * 1.7f)
        val k = 2 * lit - 1 // -1 new … 1 full
        val path = Path().apply {
            val box = Rect(c.x - rr, c.y - rr, c.x + rr, c.y + rr)
            // Lit half: the right half while waxing (as seen in the northern hemisphere), the left while waning.
            if (waxing) arcTo(box, -90f, 180f, true) else arcTo(box, 90f, 180f, true)
            // The terminator: an ellipse that adds to (gibbous) or takes from (crescent) the lit half.
            val ex = rr * abs(k)
            val eBox = Rect(c.x - ex, c.y - rr, c.x + ex, c.y + rr)
            val sweep = if (k > 0) 180f else -180f
            if (waxing) arcTo(eBox, 90f, sweep, false) else arcTo(eBox, -90f, sweep, false)
            close()
        }
        drawPath(path, bright)
        // A highlight on the upper edge, for roundness.
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.18f), Color.Transparent), c + Offset(-rr * 0.4f, -rr * 0.45f), rr * 0.9f), rr, c)
    }
}
