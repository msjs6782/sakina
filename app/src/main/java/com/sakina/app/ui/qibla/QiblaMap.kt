@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sakina.app.ui.qibla

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
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
import com.sakina.app.data.QiblaMapMode
import com.sakina.app.ui.LocalStrings
import com.sakina.app.ui.MainViewModel
import com.sakina.app.ui.components.NoteText
import com.sakina.app.ui.components.SakinaIcons
import com.sakina.app.ui.components.SectionCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.min
import kotlin.math.sqrt

/** Paths in pixels for one canvas size, ready to draw. */
private class MapPaths(
    val ocean: Path,
    val land: Path,
    val coast: Path,
    val borders: Path?,
    val lakes: Path?,
    val grid: Path,
    val route: Path,
    val you: Offset,
    val mecca: Offset,
)

private fun Path.addShapes(shapes: List<MapShape>, toPx: (Float, Float) -> Offset, close: Boolean) {
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

private suspend fun buildPaths(vm: MainViewModel, mode: QiblaMapMode, lat: Double, lon: Double, size: IntSize): MapPaths = withContext(Dispatchers.Default) {
    val w = size.width.toFloat()
    val h = size.height.toFloat()
    val distRad = Qibla.distanceKm(lat, lon) / Qibla.EARTH_RADIUS_KM
    if (mode == QiblaMapMode.CENTERED) {
        val viewR = (distRad * 1.35).coerceIn(0.05, Math.PI)
        val k = (min(w, h) / 2f * 0.92f) / viewR.toFloat()
        val cx = w / 2; val cy = h / 2
        val toPx = { x: Float, y: Float -> Offset(cx + x * k, cy - y * k) }
        val detailed = viewR < 1.2
        val landRings: GeoRings = vm.toolsRepo.geo(if (detailed) "land50" else "land110")
        val landShapes = MapGeometry.centered(lat, lon, landRings, polygons = true)
        val land = Path().apply { fillType = PathFillType.EvenOdd; addShapes(landShapes.filter { it.closed }, toPx, true) }
        val coast = Path().apply { addShapes(landShapes, toPx, true) }
        val borders = if (detailed) Path().apply { addShapes(MapGeometry.centered(lat, lon, vm.toolsRepo.geo("borders50"), polygons = false), toPx, false) } else null
        val lakes = Path().apply { addShapes(MapGeometry.centered(lat, lon, vm.toolsRepo.geo("lakes110"), polygons = true).filter { it.closed }, toPx, true) }
        val ocean = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(cx, cy), (Math.PI * k).toFloat())) }
        // Distance rings every 1000 km (or 5000 km when the view is wide).
        val step = if (viewR > 1.0) 5000.0 else if (viewR > 0.4) 1000.0 else 500.0
        val grid = Path()
        var d = step
        while (d / Qibla.EARTH_RADIUS_KM < minOf(viewR * 1.5, Math.PI)) {
            grid.addOval(androidx.compose.ui.geometry.Rect(Offset(cx, cy), (d / Qibla.EARTH_RADIUS_KM * k).toFloat()))
            d += step
        }
        val routePts = MapGeometry.centeredPath(lat, lon)
        val route = Path().apply { addShapes(listOf(MapShape(routePts, false)), toPx, false) }
        val m = Projection.azimuthal(lat, lon, Qibla.KAABA_LAT, Qibla.KAABA_LON) ?: (0.0 to 0.0)
        MapPaths(ocean, land, coast, borders, lakes, grid, route, Offset(cx, cy), toPx(m.first.toFloat(), m.second.toFloat()))
    } else {
        // Equirectangular, 2:1, centred on longitude 0.
        val toPx = { x: Float, y: Float -> Offset((x + 180f) / 360f * w, (90f - y) / 180f * h) }
        val landShapes = MapGeometry.world(vm.toolsRepo.geo("land110"), polygons = true)
        val land = Path().apply { fillType = PathFillType.EvenOdd; addShapes(landShapes, toPx, true) }
        val coast = Path().apply { addShapes(landShapes, toPx, true) }
        val lakes = Path().apply { addShapes(MapGeometry.world(vm.toolsRepo.geo("lakes110"), polygons = true), toPx, true) }
        val ocean = Path().apply { addRect(androidx.compose.ui.geometry.Rect(0f, 0f, w, h)) }
        val grid = Path()
        for (lo in -150..150 step 30) { val a = toPx(lo.toFloat(), 90f); val b = toPx(lo.toFloat(), -90f); grid.moveTo(a.x, a.y); grid.lineTo(b.x, b.y) }
        for (la in -60..60 step 30) { val a = toPx(-180f, la.toFloat()); val b = toPx(180f, la.toFloat()); grid.moveTo(a.x, a.y); grid.lineTo(b.x, b.y) }
        val route = Path().apply { addShapes(MapGeometry.worldPath(lat, lon).map { MapShape(it, false) }, toPx, false) }
        MapPaths(ocean, land, coast, null, lakes, grid, route, toPx(lon.toFloat(), lat.toFloat()), toPx(Qibla.KAABA_LON.toFloat(), Qibla.KAABA_LAT.toFloat()))
    }
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
    var size by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember(mode, lat, lon) { mutableFloatStateOf(1f) }
    var pan by remember(mode, lat, lon) { mutableStateOf(Offset.Zero) }
    val paths by produceState<MapPaths?>(null, mode, lat, lon, size) {
        value = null
        if (size.width > 0 && size.height > 0) value = buildPaths(vm, mode, lat, lon, size)
    }
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val oceanC = if (dark) Color(0xFF17323B) else Color(0xFFD6E8EE)
    val landC = if (dark) Color(0xFF39463C) else Color(0xFFEFE8D6)
    val coastC = if (dark) Color(0xFF7E8F80) else Color(0xFF9C9076)
    val borderC = coastC.copy(alpha = 0.55f)
    val gridC = (if (dark) Color.White else Color(0xFF2E5560)).copy(alpha = 0.18f)
    val routeC = MaterialTheme.colorScheme.tertiary
    val youC = MaterialTheme.colorScheme.primary
    val bg = MaterialTheme.colorScheme.surfaceContainerHigh
    val desc = str.mapTitle
    Canvas(
        Modifier
            .fillMaxWidth()
            .aspectRatio(if (mode == QiblaMapMode.WORLD) 2f else 1f)
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .onSizeChanged { size = it }
            .pointerInput(mode, lat, lon) {
                detectTransformGestures { centroid, p, z, _ ->
                    val newZoom = (zoom * z).coerceIn(1f, 12f)
                    // Keep the point under the fingers in place while zooming.
                    val c = Offset(this.size.width / 2f, this.size.height / 2f)
                    pan = (pan + (centroid - c - pan) * (1 - newZoom / zoom)) + p
                    zoom = newZoom
                    if (zoom == 1f) pan = Offset.Zero
                }
            }
            .pointerInput(mode, lat, lon) { detectTapGestures(onDoubleTap = { zoom = 1f; pan = Offset.Zero }) }
            .semantics { contentDescription = desc }
            .testTag("qibla_map"),
    ) {
        val pth = paths ?: return@Canvas
        val sw = 1f / zoom
        withTransform({
            translate(pan.x, pan.y)
            scale(zoom, zoom, Offset(size.width / 2f, size.height / 2f))
        }) {
            drawPath(pth.ocean, oceanC)
            drawPath(pth.grid, gridC, style = Stroke(1.dp.toPx() * sw, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f * sw, 6f * sw))))
            drawPath(pth.land, landC)
            pth.lakes?.let { drawPath(it, oceanC) }
            drawPath(pth.coast, coastC, style = Stroke(0.8.dp.toPx() * sw, join = StrokeJoin.Round))
            pth.borders?.let { drawPath(it, borderC, style = Stroke(0.6.dp.toPx() * sw)) }
            drawPath(pth.route, routeC, style = Stroke(3.dp.toPx() * sw, cap = StrokeCap.Round, join = StrokeJoin.Round))
            val half = 6.dp.toPx() * sw
            drawRect(Color(0xFF2B2B2B), Offset(pth.mecca.x - half, pth.mecca.y - half), Size(half * 2, half * 2))
            drawLine(Color(0xFFE8C35A), Offset(pth.mecca.x - half, pth.mecca.y - half * 0.35f), Offset(pth.mecca.x + half, pth.mecca.y - half * 0.35f), strokeWidth = half * 0.35f)
            drawCircle(Color.White, 7.dp.toPx() * sw, pth.you)
            drawCircle(youC, 5.dp.toPx() * sw, pth.you)
        }
        if (mode == QiblaMapMode.CENTERED) {
            // North is up: a small marker at the top edge.
            val c = Offset(size.width / 2f, 10.dp.toPx())
            val t = Path().apply { moveTo(c.x, c.y - 6.dp.toPx()); lineTo(c.x - 5.dp.toPx(), c.y + 4.dp.toPx()); lineTo(c.x + 5.dp.toPx(), c.y + 4.dp.toPx()); close() }
            drawPath(t, Color(0xFFB3261E))
        }
    }
}

