@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sakina.app.ui.qibla

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakina.app.core.qibla.AlignmentTracker
import com.sakina.app.core.qibla.CompassIssue
import com.sakina.app.core.qibla.CompassMath
import com.sakina.app.core.qibla.CompassReading
import com.sakina.app.core.qibla.CompassSource
import com.sakina.app.core.qibla.MagneticField
import com.sakina.app.core.qibla.Qibla
import com.sakina.app.core.qibla.Wmm
import com.sakina.app.data.AppClock
import com.sakina.app.data.AppSettings
import com.sakina.app.data.CompassEngine
import com.sakina.app.ui.LocalFmt
import com.sakina.app.ui.LocalStrings
import com.sakina.app.ui.MainViewModel
import com.sakina.app.ui.Route
import com.sakina.app.ui.components.Banner
import com.sakina.app.ui.components.NoteText
import com.sakina.app.ui.components.SakinaIcons
import com.sakina.app.ui.components.ScreenBar
import com.sakina.app.ui.components.SectionCard
import com.sakina.app.ui.components.SwitchRow
import com.sakina.app.ui.location.FullScreen
import kotlinx.coroutines.delay
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private fun displayRotation(context: Context): Int =
    if (Build.VERSION.SDK_INT >= 30) (context as? Activity)?.display?.rotation ?: Surface.ROTATION_0
    else @Suppress("DEPRECATION") (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation

private fun buzz(context: Context) {
    val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
    if (v?.hasVibrator() == true) v.vibrate(VibrationEffect.createOneShot(70L, VibrationEffect.DEFAULT_AMPLITUDE))
}

@Composable
fun QiblaScreen(vm: MainViewModel, s: AppSettings) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val context = LocalContext.current
    val loc = s.activeLocation
    val tools by vm.tools.collectAsStateWithLifecycle()
    val wmm by vm.toolsRepo.wmm.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.toolsRepo.loadWmm() }

    FullScreen {
        Column(Modifier.fillMaxSize()) {
            ScreenBar(str.qiblaTitle, onBack = { vm.pop() })
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (loc == null || !loc.point.isValid) {
                    Banner(str.qiblaNoPlace, warning = true)
                    Button(onClick = { vm.push(Route.Locations) }, modifier = Modifier.testTag("qibla_choose_place")) { Text(str.qiblaChoosePlace) }
                    return@Column
                }
                val lat = loc.latitude
                val lon = loc.longitude
                val bearing = Qibla.bearing(lat, lon)
                val distance = Qibla.distanceKm(lat, lon)
                val near = distance < Qibla.NEAR_KM
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(SakinaIcons.Pin, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text(str.qiblaFrom(loc.name(s.language)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).testTag("qibla_place"))
                    TextButton(onClick = { vm.push(Route.Locations) }) { Text(str.qiblaChangePlace) }
                }
                SectionCard {
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text(str.qiblaBearing, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (near) {
                                Text(str.qiblaNear, style = MaterialTheme.typography.bodyMedium)
                            } else {
                                Text(fmt.decimal(bearing, 1) + "°", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.testTag("qibla_bearing"))
                                Text(str.qiblaBearingValue(str.compassPoint(Qibla.compassPoint(bearing))), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            Text(str.qiblaDistance, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(str.distanceKm(fmt.num(roundKm(distance))), style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("qibla_distance"))
                        }
                    }
                }
                val now = AppClock.now()
                val date = now.atZone(ZoneOffset.UTC).toLocalDate()
                val year = Wmm.decimalYear(date.year, date.dayOfYear, date.lengthOfYear())
                val field = remember(wmm, lat, lon, date) { wmm?.field(lat, lon, 0.0, year) }
                if (!near) CompassCard(vm, bearing, field, wmm?.isValidAt(year) ?: true, tools.qibla.vibrateOnAlign)
                QiblaMapCard(vm, lat, lon, tools.qibla.mapMode)
                SectionCard(title = str.qiblaAccuracyTitle, icon = SakinaIcons.Info) {
                    NoteText(str.qiblaCalcNote)
                    if (!near && loc.source != com.sakina.app.data.LocationSource.GPS) {
                        val deg = Math.toDegrees(kotlin.math.atan(10.0 / distance))
                        Spacer(Modifier.height(6.dp))
                        NoteText(str.qiblaSensitivity(fmt.num(10), fmt.decimal(deg, if (deg < 1) 2 else 1) + "°"))
                    }
                    if (field != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(str.declination(fmt.decimal(abs(field.declination), 1) + "°", field.declination >= 0) + " · WMM2025",
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("qibla_declination"))
                        NoteText(str.trueNorthNote)
                    }
                    Spacer(Modifier.height(6.dp))
                    NoteText(str.qiblaCompassNote)
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

private fun roundKm(km: Double): Int = when {
    km < 10 -> km.roundToInt()
    km < 1000 -> (km / 5).roundToInt() * 5
    else -> (km / 10).roundToInt() * 10
}

/** A compass state with values rounded so an unchanged reading does not redraw the screen. */
private data class Shown(val reading: CompassReading?, val stale: Boolean)

@Composable
private fun CompassCard(vm: MainViewModel, bearing: Double, field: MagneticField?, modelValid: Boolean, vibrateOnAlign: Boolean) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val context = LocalContext.current
    val engine = remember { CompassEngine(context.applicationContext) { displayRotation(context) } }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_RESUME -> engine.start()
                Lifecycle.Event.ON_PAUSE -> engine.stop()
                else -> {}
            }
        }
        owner.lifecycle.addObserver(obs)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) engine.start()
        onDispose {
            owner.lifecycle.removeObserver(obs)
            engine.stop()
        }
    }
    var shown by remember { mutableStateOf(Shown(null, false)) }
    LaunchedEffect(engine) {
        while (true) {
            val r = engine.raw.value?.reading(SystemClock.elapsedRealtime())
            shown = Shown(
                r?.copy(
                    magneticHeading = (r.magneticHeading * 2).roundToInt() / 2.0,
                    tiltDegrees = r.tiltDegrees.roundToInt().toDouble(),
                    headingAccuracy = r.headingAccuracy?.roundToInt()?.toDouble(),
                    fieldMicroTesla = r.fieldMicroTesla?.roundToInt()?.toDouble(),
                    ageMillis = 0,
                ),
                stale = r != null && r.ageMillis > CompassMath.STALE_MS,
            )
            delay(100)
        }
    }
    val reading = shown.reading?.let { if (shown.stale) it.copy(ageMillis = CompassMath.STALE_MS + 1) else it }
    val source = if (engine.running) engine.source else engine.best()
    val expected = field?.total?.div(1000.0)
    val issues = when {
        source == CompassSource.NONE -> CompassMath.issues(null, expected, field?.zone, modelValid)
        // Still waiting for the first reading: not a missing sensor.
        reading == null -> CompassMath.issues(null, expected, field?.zone, modelValid) - CompassIssue.NO_SENSOR
        else -> CompassMath.issues(reading, expected, field?.zone, modelValid)
    }
    val trueHeading = reading?.let { Qibla.norm360(it.magneticHeading + (field?.declination ?: 0.0)) }
    val offset = trueHeading?.let { Qibla.diff(bearing, it) }
    val reliable = reading != null && !CompassMath.blocksAlignment(issues)
    val tracker = remember { AlignmentTracker() }
    LaunchedEffect(offset, reliable) {
        if (offset != null && tracker.update(offset, reliable)) {
            Log.i(CompassEngine.TAG, "aligned offset=$offset")
            if (vibrateOnAlign) buzz(context)
        }
    }
    val aligned = reliable && offset != null && abs(offset) <= CompassMath.ALIGN_DEG

    SectionCard(title = str.compassTitle, icon = SakinaIcons.Compass) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CompassDial(trueHeading, bearing, aligned, Modifier.size(260.dp).testTag("compass_dial"))
        }
        Spacer(Modifier.height(10.dp))
        when {
            source == CompassSource.NONE -> {}
            reading == null -> Text(str.compassWaiting, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("compass_state"))
            aligned -> Text(str.compassAligned, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("compass_state"))
            offset != null -> {
                val d = fmt.decimal(abs(offset), 0) + "°"
                Text(if (offset > 0) str.turnRight(d) else str.turnLeft(d), style = MaterialTheme.typography.titleMedium,
                    color = if (reliable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("compass_state"))
            }
        }
        if (trueHeading != null) {
            Text(str.phoneHeading(fmt.decimal(trueHeading, 0) + "° " + str.compassPoint(Qibla.compassPoint(trueHeading))),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("compass_heading"))
        }
        Spacer(Modifier.height(8.dp))
        issues.forEach { i ->
            Banner(str.compassIssue(i), icon = SakinaIcons.Warning, warning = CompassMath.blocksAlignment(setOf(i)),
                modifier = Modifier.padding(vertical = 3.dp).testTag("compass_issue_" + i.name.lowercase()))
        }
        if (source != CompassSource.NONE) NoteText(str.compassHowTo, Modifier.padding(top = 4.dp))
        NoteText(str.compassSource(source), Modifier.padding(top = 4.dp).testTag("compass_source"))
        if (source != CompassSource.NONE) {
            SwitchRow(vibrateOnAlign, str.vibrateAlign) { on -> vm.updateTools { t -> t.copy(qibla = t.qibla.copy(vibrateOnAlign = on)) } }
        }
    }
}

@Composable
private fun CompassDial(trueHeading: Double?, bearing: Double, aligned: Boolean, modifier: Modifier) {
    val str = LocalStrings.current
    val ring = MaterialTheme.colorScheme.outlineVariant
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val kaaba = if (aligned) primary else MaterialTheme.colorScheme.tertiary
    val face = MaterialTheme.colorScheme.surfaceContainerHigh
    val north = MaterialTheme.colorScheme.error
    val desc = str.compassTitle
    val measurer = rememberTextMeasurer()
    val labels = if (str.isAr) listOf("ش", "ق", "ج", "غ") else listOf("N", "E", "S", "W")
    val labelStyle = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
    Canvas(modifier.semantics { contentDescription = desc }) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2 * 0.92f
        drawCircle(face, r, c)
        drawCircle(ring, r, c, style = Stroke(2.dp.toPx()))
        // The dial turns so its north points to true north; without a compass it stays north-up.
        val rot = -(trueHeading ?: 0.0).toFloat()
        rotate(rot, c) {
            for (deg in 0 until 360 step 10) {
                val major = deg % 90 == 0
                val a = Math.toRadians(deg.toDouble())
                val r1 = r * if (major) 0.80f else 0.88f
                drawLine(if (major) ink else muted, pt(c, r1, a), pt(c, r * 0.96f, a), strokeWidth = (if (major) 3f else 1.5f).dp.toPx(), cap = StrokeCap.Round)
            }
            // North marker.
            val n = Path().apply {
                moveTo(c.x, c.y - r * 0.78f); lineTo(c.x - r * 0.06f, c.y - r * 0.62f); lineTo(c.x + r * 0.06f, c.y - r * 0.62f); close()
            }
            drawPath(n, north)
            // Cardinal letters, turning with the dial like a compass card.
            labels.forEachIndexed { i, text ->
                val layout = measurer.measure(text, labelStyle.copy(color = if (i == 0) north else muted))
                rotate(i * 90f, c) {
                    drawText(layout, topLeft = Offset(c.x - layout.size.width / 2f, c.y - r * 0.60f))
                }
            }
            // Qibla: a line from the centre to the Kaaba mark at the bearing.
            val a = Math.toRadians(bearing)
            drawLine(kaaba, c, pt(c, r * 0.70f, a), strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
            kaabaMark(pt(c, r * 0.70f, a), r * 0.09f, kaaba)
        }
        // Fixed pointer: the direction the top of the phone faces.
        if (trueHeading != null) {
            val p = Path().apply { moveTo(c.x, c.y - r * 1.0f); lineTo(c.x - r * 0.07f, c.y - r * 0.86f); lineTo(c.x + r * 0.07f, c.y - r * 0.86f); close() }
            drawPath(p, if (aligned) primary else ink)
        }
        drawCircle(ink, 4.dp.toPx(), c)
    }
}

private fun pt(c: Offset, r: Float, a: Double) = Offset(c.x + r * sin(a).toFloat(), c.y - r * cos(a).toFloat())

private fun DrawScope.kaabaMark(at: Offset, half: Float, color: Color) {
    drawRect(color, Offset(at.x - half, at.y - half), androidx.compose.ui.geometry.Size(half * 2, half * 2))
    drawLine(Color(0xFFE8C35A), Offset(at.x - half, at.y - half * 0.35f), Offset(at.x + half, at.y - half * 0.35f), strokeWidth = half * 0.35f)
}
