@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sakina.app.ui.qibla

import android.graphics.Bitmap
import android.graphics.DashPathEffect
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.sakina.app.core.qibla.GeoRings
import com.sakina.app.core.qibla.MapGeometry
import com.sakina.app.core.qibla.MapShape
import com.sakina.app.core.qibla.Projection
import com.sakina.app.core.qibla.Qibla
import com.sakina.app.data.CompassEngine
import com.sakina.app.data.QiblaMapMode
import com.sakina.app.ui.LocalStrings
import com.sakina.app.ui.MainViewModel
import com.sakina.app.ui.components.NoteText
import com.sakina.app.ui.components.SakinaIcons
import com.sakina.app.ui.components.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.min
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import kotlin.math.sqrt

/**
 * The map's geometry in content pixels (the canvas at zoom 1). It is drawn by software into a bitmap:
 * hardware rendering on older Android skips paths larger than its texture limit, which a coastline
 * reaching beyond the screen easily is.
 */
private class MapScene(
    val world: Boolean,
    val center: Offset,
    val oceanRadius: Float,
    val rings: List<Float>,
    val grid: AndroidPath?,
    val land: AndroidPath,
    val coast: AndroidPath,
    val borders: AndroidPath?,
    val lakes: AndroidPath?,
    val route: AndroidPath,
    val you: Offset,
    val mecca: Offset,
)

private fun AndroidPath.addShapes(shapes: List<MapShape>, toPx: (Float, Float) -> Offset, close: Boolean) {
    for (s in shapes) {
        val pts = s.points
        if (pts.size < 4) continue
        val first = toPx(pts[0], pts[1])
        moveTo(first.x, first.y)
        var i = 2
        while (i < pts.size) {
            val p = toPx(pts[i], pts[i + 1])
            lineTo(p.x, p.y)
            i += 2
        }
        if (close && s.closed) close()
    }
}

private suspend fun buildScene(vm: MainViewModel, mode: QiblaMapMode, lat: Double, lon: Double, size: IntSize): MapScene = withContext(Dispatchers.Default) {
    val w = size.width.toFloat()
    val h = size.height.toFloat()
    val distRad = Qibla.distanceKm(lat, lon) / Qibla.EARTH_RADIUS_KM
    if (mode == QiblaMapMode.CENTERED) {
        val viewR = (distRad * 1.35).coerceIn(0.05, Math.PI)
        val k = (min(w, h) / 2f * 0.92f) / viewR.toFloat()
        val cx = w / 2; val cy = h / 2
        val toPx = { x: Float, y: Float -> Offset(cx + x * k, cy - y * k) }
        val detailed = viewR < 1.2
        val landShapes = MapGeometry.centered(lat, lon, vm.toolsRepo.geo(if (detailed) "land50" else "land110"), polygons = true)
        val land = AndroidPath().apply { fillType = AndroidPath.FillType.EVEN_ODD; addShapes(landShapes.filter { it.closed }, toPx, true) }
        val coast = AndroidPath().apply { addShapes(landShapes, toPx, true) }
        val borders = if (detailed) AndroidPath().apply { addShapes(MapGeometry.centered(lat, lon, vm.toolsRepo.geo("borders50"), polygons = false), toPx, false) } else null
        val lakes = AndroidPath().apply { addShapes(MapGeometry.centered(lat, lon, vm.toolsRepo.geo("lakes110"), polygons = true).filter { it.closed }, toPx, true) }
        // Distance rings every 500, 1000 or 5000 km, by the width of the view.
        val step = if (viewR > 1.0) 5000.0 else if (viewR > 0.4) 1000.0 else 500.0
        val rings = ArrayList<Float>()
        var d = step
        while (d / Qibla.EARTH_RADIUS_KM < minOf(viewR * 1.5, Math.PI)) {
            rings += (d / Qibla.EARTH_RADIUS_KM * k).toFloat()
            d += step
        }
        val route = AndroidPath().apply { addShapes(listOf(MapShape(MapGeometry.centeredPath(lat, lon), false)), toPx, false) }
        val m = Projection.azimuthal(lat, lon, Qibla.KAABA_LAT, Qibla.KAABA_LON) ?: (0.0 to 0.0)
        MapScene(false, Offset(cx, cy), (Math.PI * k).toFloat(), rings, null, land, coast, borders, lakes, route, Offset(cx, cy),
            toPx(m.first.toFloat(), m.second.toFloat()))
    } else {
        // Equirectangular, 2:1, centred on longitude 0.
        val toPx = { x: Float, y: Float -> Offset((x + 180f) / 360f * w, (90f - y) / 180f * h) }
        val landShapes = MapGeometry.world(vm.toolsRepo.geo("land110"), polygons = true)
        val land = AndroidPath().apply { fillType = AndroidPath.FillType.EVEN_ODD; addShapes(landShapes, toPx, true) }
        val coast = AndroidPath().apply { addShapes(landShapes, toPx, true) }
        val lakes = AndroidPath().apply { addShapes(MapGeometry.world(vm.toolsRepo.geo("lakes110"), polygons = true), toPx, true) }
        val grid = AndroidPath()
        for (lo in -150..150 step 30) { val a = toPx(lo.toFloat(), 90f); val b = toPx(lo.toFloat(), -90f); grid.moveTo(a.x, a.y); grid.lineTo(b.x, b.y) }
        for (la in -60..60 step 30) { val a = toPx(-180f, la.toFloat()); val b = toPx(180f, la.toFloat()); grid.moveTo(a.x, a.y); grid.lineTo(b.x, b.y) }
        val route = AndroidPath().apply { addShapes(MapGeometry.worldPath(lat, lon).map { MapShape(it, false) }, toPx, false) }
        MapScene(true, Offset(w / 2, h / 2), 0f, emptyList(), grid, land, coast, null, lakes, route, toPx(lon.toFloat(), lat.toFloat()),
            toPx(Qibla.KAABA_LON.toFloat(), Qibla.KAABA_LAT.toFloat()))
    }
}

private class MapColors(val bg: Int, val ocean: Int, val land: Int, val coast: Int, val border: Int, val grid: Int, val route: Int)

/** A rendered view of the scene and the zoom and pan it was rendered at. */
private class MapFrame(val image: ImageBitmap, val zoom: Float, val pan: Offset)

private suspend fun render(scene: MapScene, size: IntSize, zoom: Float, pan: Offset, c: MapColors, density: Float): MapFrame = withContext(Dispatchers.Default) {
    val bmp = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
    val cv = AndroidCanvas(bmp)
    cv.drawColor(c.bg)
    cv.translate(pan.x, pan.y)
    cv.scale(zoom, zoom, size.width / 2f, size.height / 2f)
    val sw = density / zoom
    fun fill(color: Int) = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply { this.color = color; style = AndroidPaint.Style.FILL }
    fun stroke(color: Int, widthDp: Float, dash: Boolean = false) = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        this.color = color; style = AndroidPaint.Style.STROKE; strokeWidth = widthDp * sw
        strokeJoin = AndroidPaint.Join.ROUND; strokeCap = AndroidPaint.Cap.ROUND
        if (dash) pathEffect = DashPathEffect(floatArrayOf(5f * sw, 5f * sw), 0f)
    }
    if (scene.world) cv.drawRect(0f, 0f, size.width.toFloat(), size.height.toFloat(), fill(c.ocean))
    else cv.drawCircle(scene.center.x, scene.center.y, scene.oceanRadius, fill(c.ocean))
    val gridPaint = stroke(c.grid, 1f, dash = true)
    scene.grid?.let { cv.drawPath(it, gridPaint) }
    scene.rings.forEach { cv.drawCircle(scene.center.x, scene.center.y, it, gridPaint) }
    cv.drawPath(scene.land, fill(c.land))
    scene.lakes?.let { cv.drawPath(it, fill(c.ocean)) }
    cv.drawPath(scene.coast, stroke(c.coast, 0.8f))
    scene.borders?.let { cv.drawPath(it, stroke(c.border, 0.6f)) }
    cv.drawPath(scene.route, stroke(c.route, 3f))
    MapFrame(bmp.asImageBitmap(), zoom, pan)
}

/** Keeps the zoomed content covering the canvas, so the view never leaves the map. */
private fun clampPan(pan: Offset, zoom: Float, size: IntSize): Offset {
    val mx = (zoom - 1f) * size.width / 2f
    val my = (zoom - 1f) * size.height / 2f
    return Offset(pan.x.coerceIn(-mx, mx), pan.y.coerceIn(-my, my))
}

@Composable
fun QiblaMapCard(vm: MainViewModel, lat: Double, lon: Double, mode: QiblaMapMode) {
    val str = LocalStrings.current
    SectionCard(title = str.mapTitle, icon = SakinaIcons.Map) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = mode == QiblaMapMode.CENTERED, onClick = { vm.updateTools { t -> t.copy(qibla = t.qibla.copy(mapMode = QiblaMapMode.CENTERED)) } },
                label = { Text(str.mapCentered) }, modifier = Modifier.testTag("map_mode_centered"))
            FilterChip(selected = mode == QiblaMapMode.WORLD, onClick = { vm.updateTools { t -> t.copy(qibla = t.qibla.copy(mapMode = QiblaMapMode.WORLD)) } },
                label = { Text(str.mapWorld) }, modifier = Modifier.testTag("map_mode_world"))
        }
        Spacer(Modifier.height(8.dp))
        QiblaMap(vm, lat, lon, mode)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Legend(MaterialTheme.colorScheme.primary, str.mapYou)
            Legend(Color(0xFF2B2B2B), str.mapMecca, square = true)
            Legend(MaterialTheme.colorScheme.tertiary, str.mapPath, line = true)
        }
        Spacer(Modifier.height(6.dp))
        Text(if (mode == QiblaMapMode.CENTERED) str.mapCenteredNote else str.mapWorldNote, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("map_note"))
        Spacer(Modifier.height(4.dp))
        NoteText(str.mapZoomHint)
        NoteText(str.mapDataNote, Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun Legend(color: Color, text: String, square: Boolean = false, line: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val shape = if (square) RoundedCornerShape(2.dp) else CircleShape
        Box(Modifier.size(width = if (line) 18.dp else 10.dp, height = if (line) 3.dp else 10.dp).clip(shape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun QiblaMap(vm: MainViewModel, lat: Double, lon: Double, mode: QiblaMapMode) {
    val str = LocalStrings.current
    val density = LocalDensity.current.density
    var size by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember(mode, lat, lon) { mutableFloatStateOf(1f) }
    var pan by remember(mode, lat, lon) { mutableStateOf(Offset.Zero) }
    val scene by produceState<MapScene?>(null, mode, lat, lon, size) {
        value = null
        if (size.width > 0 && size.height > 0) value = buildScene(vm, mode, lat, lon, size)
    }
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val colors = MapColors(
        bg = MaterialTheme.colorScheme.surfaceContainerHigh.toArgb(),
        ocean = (if (dark) Color(0xFF17323B) else Color(0xFFD6E8EE)).toArgb(),
        land = (if (dark) Color(0xFF39463C) else Color(0xFFEFE8D6)).toArgb(),
        coast = (if (dark) Color(0xFF7E8F80) else Color(0xFF9C9076)).toArgb(),
        border = (if (dark) Color(0x8C7E8F80) else Color(0x8C9C9076)).toArgb(),
        grid = (if (dark) Color(0x2EFFFFFF) else Color(0x2E2E5560)).toArgb(),
        route = MaterialTheme.colorScheme.tertiary.toArgb(),
    )
    var frame by remember(mode, lat, lon) { mutableStateOf<MapFrame?>(null) }
    // Re-render crisply once the view has settled; until then the last frame is stretched to follow the fingers.
    LaunchedEffect(scene, zoom, pan, dark) {
        val sc = scene ?: return@LaunchedEffect
        if (frame != null) delay(120)
        frame = render(sc, size, zoom, pan, colors, density)
        if (zoom == 1f && pan == Offset.Zero) Log.i(CompassEngine.TAG, "map drawn mode=$mode")
    }
    val youC = MaterialTheme.colorScheme.primary
    val desc = str.mapTitle
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(if (mode == QiblaMapMode.WORLD) 2f else 1f)
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .onSizeChanged { size = it }
            .pointerInput(mode, lat, lon) {
                // Two fingers zoom and pan; one finger pans only when zoomed in, so the page scrolls over the map.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.any { it.isConsumed }) break
                        val fingers = event.changes.count { it.pressed }
                        if (fingers >= 2 || zoom > 1f) {
                            val z = event.calculateZoom()
                            val p = event.calculatePan()
                            if (z != 1f || p != Offset.Zero) {
                                val centroid = event.calculateCentroid(useCurrent = false)
                                val newZoom = (zoom * z).coerceIn(1f, 12f)
                                val c = Offset(size.width / 2f, size.height / 2f)
                                val next = (pan + (centroid - c - pan) * (1 - newZoom / zoom)) + p
                                zoom = newZoom
                                pan = clampPan(next, newZoom, size)
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .pointerInput(mode, lat, lon) { detectTapGestures(onDoubleTap = { zoom = 1f; pan = Offset.Zero }) }
            .semantics { contentDescription = desc }
            .testTag("qibla_map"),
    ) {
        val f = frame ?: return@Canvas
        val sc = scene ?: return@Canvas
        val s = zoom / f.zoom
        val mid = Offset(size.width / 2f, size.height / 2f)
        withTransform({
            translate(pan.x - f.pan.x * s, pan.y - f.pan.y * s)
            scale(s, s, mid)
        }) { drawImage(f.image) }
        // Markers stay the same size at any zoom.
        fun screen(o: Offset) = mid + (o - mid) * zoom + pan
        val m = screen(sc.mecca)
        val half = 6.dp.toPx()
        drawRect(Color(0xFF2B2B2B), Offset(m.x - half, m.y - half), Size(half * 2, half * 2))
        drawLine(Color(0xFFE8C35A), Offset(m.x - half, m.y - half * 0.35f), Offset(m.x + half, m.y - half * 0.35f), strokeWidth = half * 0.35f)
        val y = screen(sc.you)
        drawCircle(Color.White, 7.dp.toPx(), y)
        drawCircle(youC, 5.dp.toPx(), y)
        if (mode == QiblaMapMode.CENTERED) {
            // North is up: a small marker at the top edge.
            val c = Offset(size.width / 2f, 10.dp.toPx())
            val t = Path().apply { moveTo(c.x, c.y - 6.dp.toPx()); lineTo(c.x - 5.dp.toPx(), c.y + 4.dp.toPx()); lineTo(c.x + 5.dp.toPx(), c.y + 4.dp.toPx()); close() }
            drawPath(t, Color(0xFFB3261E))
        }
    }
}
