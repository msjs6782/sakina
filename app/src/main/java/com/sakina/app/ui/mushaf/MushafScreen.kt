@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sakina.app.ui.mushaf

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Matrix
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakina.app.core.mushaf.MushafManifest
import com.sakina.app.core.quran.Khatma
import com.sakina.app.data.LastRead
import com.sakina.app.data.MushafFill
import com.sakina.app.data.MushafNight
import com.sakina.app.data.MushafRender
import com.sakina.app.data.NetState
import com.sakina.app.data.PageDownload
import com.sakina.app.data.QuranData
import com.sakina.app.data.QuranOps
import com.sakina.app.data.QuranView
import com.sakina.app.ui.LocalFmt
import com.sakina.app.ui.LocalStrings
import com.sakina.app.ui.MainViewModel
import com.sakina.app.ui.Route
import com.sakina.app.ui.components.DirectionalIcon
import com.sakina.app.ui.components.SakinaIcons
import com.sakina.app.ui.components.SwitchRow
import com.sakina.app.ui.quran.WithQuran
import com.sakina.app.ui.listen.toggle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Debug builds can open a page already zoomed, for screenshots of the zoomed rendering. */
object MushafDebug {
    var initialZoom: Float = 1f
}

@Composable
fun MushafScreen(vm: MainViewModel, startPage: Int, focus: Int) {
    WithQuran(vm) { d -> MushafViewer(vm, d, startPage.coerceIn(1, MushafManifest.PAGES), focus) }
}

/** Colours behind the page for each filter (the page paper is white). */
private fun backdrop(n: MushafNight) = when (n) {
    MushafNight.OFF -> Color(0xFFFFFFFF)
    MushafNight.DARK -> Color(0xFF161616)
    MushafNight.WARM -> Color(0xFFF2E3C6)
}

@Composable
private fun MushafViewer(vm: MainViewModel, d: QuranData, startPage: Int, focus: Int) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val context = LocalContext.current
    val repo = vm.mushaf
    val qs by vm.quranState.collectAsStateWithLifecycle()
    val available by repo.available.collectAsStateWithLifecycle()
    val downloads by repo.pages.collectAsStateWithLifecycle()
    val prefs = qs.reader
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { repo.init() }

    val pager = rememberPagerState(initialPage = startPage - 1) { MushafManifest.PAGES }
    var barsVisible by remember { mutableStateOf(true) }
    var zoomedPage by remember { mutableIntStateOf(0) }
    var showIndex by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var focusDismissed by remember { mutableStateOf(false) }
    val page = pager.currentPage + 1
    // The recitation, followed while it plays: the page turns to the recited ayah's page.
    val controller = com.sakina.app.ui.listen.rememberRecitationController()
    val follow = com.sakina.app.ui.listen.rememberFollow(vm, controller)
    var following by remember { mutableStateOf(true) }
    val recitedPage = follow.global(d)?.let { d.meta.pageOf(it) }
    LaunchedEffect(recitedPage, following, follow.playing) {
        val p = recitedPage ?: return@LaunchedEffect
        if (following && follow.playing && p != pager.currentPage + 1 && zoomedPage == 0) pager.animateScrollToPage(p - 1)
    }
    val listenHere: () -> Unit = {
        controller?.let { c ->
            val first = d.meta.ref(d.meta.pageRange(page).first)
            if (follow.recording?.sura == first.sura && follow.recording != null) c.toggle()
            else com.sakina.app.ui.listen.startRecitation(vm, c, d, str, first.sura, first.aya)
        }
    }

    // Reading position and the automatic download, once a page has settled.
    LaunchedEffect(pager.settledPage) {
        val p = pager.settledPage + 1
        delay(700)
        val first = d.meta.pageRange(p).first
        val r = d.meta.ref(first)
        vm.updateQuran { it.copy(lastRead = LastRead(r.sura, r.aya, p, System.currentTimeMillis())) }
    }
    LaunchedEffect(pager.settledPage, prefs.autoDownload, available) {
        val p = pager.settledPage + 1
        if (prefs.autoDownload && p !in available && downloads[p] == null && repo.network() != NetState.OFFLINE) repo.requestPage(p)
    }

    val firstRef = d.meta.ref(d.meta.pageRange(page).first)
    val actions = MenuActions(
        bookmark = {
            val had = QuranOps.isBookmarked(qs, firstRef.sura, firstRef.aya)
            vm.updateQuran { QuranOps.toggleBookmark(it, firstRef.sura, firstRef.aya, System.currentTimeMillis()) }
            if (!had) Toast.makeText(context, str.pageBookmarked, Toast.LENGTH_SHORT).show()
        },
        bookmarked = QuranOps.isBookmarked(qs, firstRef.sura, firstRef.aya),
        khatma = if (qs.khatma != null) ({
            vm.updateQuran { s -> s.copy(khatma = s.khatma?.let { Khatma.record(it, page, LocalDate.now()) }) }
            Toast.makeText(context, str.khatmaRecorded, Toast.LENGTH_SHORT).show()
        }) else null,
    )

    ImmersiveBars(visible = barsVisible)
    Box(Modifier.fillMaxSize().background(backdrop(prefs.mushafNight)).testTag("mushaf_viewer")) {
        // The page area stays the same size whether the system bars show or not, so nothing jumps.
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBarsIgnoringVisibility)) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                HorizontalPager(
                    state = pager,
                    beyondViewportPageCount = 1,
                    userScrollEnabled = zoomedPage != page,
                    key = { it },
                    modifier = Modifier.fillMaxSize().testTag("mushaf_pager"),
                ) { index ->
                    val p = index + 1
                    if (p in available) {
                        PageImage(
                            file = repo.file(p), page = p, night = prefs.mushafNight, trim = prefs.mushafTrim, fill = prefs.mushafFill,
                            mark = hizbMark(d, p, str, fmt),
                            initialZoom = if (p == startPage) MushafDebug.initialZoom else 1f,
                            onZoomed = { z -> zoomedPage = if (z) p else if (zoomedPage == p) 0 else zoomedPage },
                            onTap = { barsVisible = !barsVisible },
                        )
                    } else {
                        CompositionLocalProvider(LocalLayoutDirection provides if (str.isAr) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                            MissingPage(vm, d, p, downloads[p], prefs.autoDownload, prefs.mushafNight)
                        }
                    }
                }
            }
        }

        AnimatedVisibility(barsVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            TopBar(vm, d, page, actions, onIndex = { showIndex = true }, onMenu = { menu = true }, menuOpen = menu, onMenuClose = { menu = false })
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            if (focus >= 0 && !focusDismissed && d.meta.pageOf(focus) == page) {
                val r = d.meta.ref(focus)
                Surface(
                    shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer, tonalElevation = 2.dp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().testTag("mushaf_focus"),
                ) {
                    Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(str.focusHere(d.meta.surah(r.sura).nameAr, fmt.num(r.aya)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.push(Route.QuranReader(focus, highlight = true)) }) { Text(str.showInText) }
                        IconButton(onClick = { focusDismissed = true }) { Icon(SakinaIcons.Close, str.close, Modifier.size(18.dp)) }
                    }
                }
            }
            if (controller != null && follow.recording != null && (barsVisible || follow.playing)) {
                com.sakina.app.ui.listen.FollowBar(vm, d, controller, follow, following, onFollow = { following = it })
            }
            AnimatedVisibility(barsVisible, enter = fadeIn(), exit = fadeOut()) {
                BottomBar(vm, page, page in available, playing = follow.playing && follow.recording != null, onListen = listenHere)
            }
        }
    }

    if (showIndex) {
        MushafIndexSheet(d, available, current = page, onDismiss = { showIndex = false }) { target ->
            showIndex = false
            scope.launch { pager.scrollToPage(target - 1) }
        }
    }

}

/** Hides the status and navigation bars while the reading controls are hidden. */
@Composable
private fun ImmersiveBars(visible: Boolean) {
    val view = LocalView.current
    val window = (view.context as? Activity)?.window ?: return
    val controller = remember(window) { WindowCompat.getInsetsController(window, view) }
    LaunchedEffect(visible) {
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (visible) controller.show(WindowInsetsCompat.Type.systemBars()) else controller.hide(WindowInsetsCompat.Type.systemBars())
    }
    DisposableEffect(controller) { onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) } }
}

private class MenuActions(val bookmark: () -> Unit, val bookmarked: Boolean, val khatma: (() -> Unit)?)

@Composable
private fun TopBar(vm: MainViewModel, d: QuranData, page: Int, a: MenuActions, onIndex: () -> Unit, onMenu: () -> Unit, menuOpen: Boolean, onMenuClose: () -> Unit) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val range = d.meta.pageRange(page)
    val suras = range.map { d.meta.ref(it).sura }.distinct()
    val qs by vm.quranState.collectAsStateWithLifecycle()
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f), tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)).fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.pop() }) { DirectionalIcon(SakinaIcons.ArrowStart, str.back) }
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(
                    suras.joinToString(" · ") { d.meta.surah(it).let { s -> str.surahTitle(s.nameAr, s.nameTranslit) } },
                    style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    str.pageN(fmt.num(page)) + " · " + str.juzN(fmt.num(d.meta.juzOf(range.first))) + " · " + str.hizbN(fmt.num(d.meta.hizbOf(range.first))),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                )
            }
            IconButton(onClick = onIndex, modifier = Modifier.testTag("mushaf_index")) { Icon(SakinaIcons.ListIndex, str.mushafIndex) }
            IconButton(onClick = { vm.switchView(QuranView.TEXT, Route.QuranReader(d.meta.pageRange(page).first)) }, modifier = Modifier.testTag("mushaf_to_text")) {
                Icon(SakinaIcons.Lines, str.textView)
            }
            Box {
                IconButton(onClick = onMenu, modifier = Modifier.testTag("mushaf_menu")) { Icon(SakinaIcons.More, null) }
                DropdownMenu(expanded = menuOpen, onDismissRequest = onMenuClose) {
                    Text(str.nightFilter, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    listOf(MushafNight.OFF to str.nightOff, MushafNight.DARK to str.nightDark, MushafNight.WARM to str.nightWarm).forEach { (n, label) ->
                        DropdownMenuItem(
                            text = { Text((if (qs.reader.mushafNight == n) "✓ " else "    ") + label) },
                            onClick = { vm.updateQuran { it.copy(reader = it.reader.copy(mushafNight = n)) }; onMenuClose() },
                            modifier = Modifier.testTag("night_" + n.name.lowercase()),
                        )
                    }
                    androidx.compose.material3.HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text((if (qs.reader.mushafFill) "✓ " else "    ") + str.mushafFill) },
                        onClick = { vm.updateQuran { it.copy(reader = it.reader.copy(mushafFill = !it.reader.mushafFill)) }; onMenuClose() },
                        modifier = Modifier.testTag("mushaf_fill"),
                    )
                    DropdownMenuItem(
                        text = { Text((if (qs.reader.mushafTrim || qs.reader.mushafFill) "✓ " else "    ") + str.mushafTrim) },
                        enabled = !qs.reader.mushafFill,
                        onClick = { vm.updateQuran { it.copy(reader = it.reader.copy(mushafTrim = !it.reader.mushafTrim)) }; onMenuClose() },
                        modifier = Modifier.testTag("mushaf_trim"),
                    )
                    androidx.compose.material3.HorizontalDivider()
                    DropdownMenuItem(text = { Text(if (a.bookmarked) str.unbookmarkPage else str.bookmarkPage) }, leadingIcon = { Icon(SakinaIcons.Bookmark, null) },
                        onClick = { a.bookmark(); onMenuClose() }, modifier = Modifier.testTag("mushaf_bookmark"))
                    a.khatma?.let { k ->
                        DropdownMenuItem(text = { Text(str.khatmaRecordHere(fmt.num(page))) }, leadingIcon = { Icon(SakinaIcons.Check, null) }, onClick = { k(); onMenuClose() })
                    }
                    DropdownMenuItem(text = { Text(str.listenThisSurah) }, leadingIcon = { Icon(SakinaIcons.Headphones, null) },
                        onClick = { onMenuClose(); vm.push(Route.Listen(suras.first())) }, modifier = Modifier.testTag("mushaf_listen"))
                    DropdownMenuItem(text = { Text(str.dlTitle) }, leadingIcon = { Icon(SakinaIcons.Download, null) },
                        onClick = { onMenuClose(); vm.push(Route.MushafDownloads) }, modifier = Modifier.testTag("mushaf_downloads"))
                }
            }
        }
    }
}

@Composable
private fun BottomBar(vm: MainViewModel, page: Int, here: Boolean, playing: Boolean, onListen: () -> Unit) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f), tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)).fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(str.pageOfTotal(fmt.num(page), fmt.num(MushafManifest.PAGES)), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f).testTag("mushaf_page_label"))
            if (here) Text(str.downloadedMark, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            IconButton(onClick = onListen, modifier = Modifier.testTag("mushaf_listen_here")) {
                Icon(if (playing) SakinaIcons.Pause else SakinaIcons.Headphones, str.listenHere)
            }
            IconButton(onClick = { vm.push(Route.MushafDownloads) }) { Icon(SakinaIcons.Download, str.dlTitle) }
        }
    }
}

// ---------------- the page itself ----------------

/** Pinch, pan and double-tap zoom of one page, kept inside the screen. */
@Stable
private class ZoomState {
    var scale by mutableFloatStateOf(1f)
    var tx by mutableFloatStateOf(0f)
    var ty by mutableFloatStateOf(0f)
    var cw = 0f
    var ch = 0f
    var fw = 0f
    var fh = 0f
    val zoomed get() = scale > 1.01f

    fun set(s: Float, x: Float, y: Float) {
        val ns = s.coerceIn(1f, MAX_ZOOM)
        val mx = max(0f, (fw * ns - cw) / 2)
        val my = max(0f, (fh * ns - ch) / 2)
        scale = ns
        tx = x.coerceIn(-mx, mx)
        ty = y.coerceIn(-my, my)
    }

    /** Zooms by [zoom] about [centroid] (container pixels) and moves by [pan]. */
    fun transform(centroid: Offset, pan: Offset, zoom: Float) {
        val ns = (scale * zoom).coerceIn(1f, MAX_ZOOM)
        val f = ns / scale
        val cx = centroid.x - cw / 2
        val cy = centroid.y - ch / 2
        set(ns, cx - (cx - tx) * f + pan.x, cy - (cy - ty) * f + pan.y)
    }

    /** Target of a double tap at [at]: zoom in there, or back out. */
    fun doubleTapTarget(at: Offset): Triple<Float, Float, Float> =
        if (zoomed) Triple(1f, 0f, 0f) else (DOUBLE_TAP_ZOOM).let { s -> Triple(s, (at.x - cw / 2) * (1 - s), (at.y - ch / 2) * (1 - s)) }

    companion object {
        const val MAX_ZOOM = 5f
        const val DOUBLE_TAP_ZOOM = 2.5f
    }
}

private class Detail(val bitmap: Bitmap, val scale: Float, val tx: Float, val ty: Float)

/** What a page shows: its drawing, its size on screen and, when it fills the screen, how it was laid out. */
private class Look(val base: Bitmap, val fw: Float, val fh: Float, val crop: MushafRender.Crop, val filled: MushafFill.Filled?)

private fun fitted(c: MushafRender.Crop, cw: Int, ch: Int): Pair<Float, Float> {
    val fw = if (cw.toFloat() / ch < c.ratio) cw.toFloat() else ch * c.ratio
    return fw to fw / c.ratio
}

/** The look already drawn this session, or null while it is still to be drawn. */
private fun cachedLook(page: Int, fill: Boolean, trim: Boolean, cw: Int, ch: Int): Look? {
    if (cw <= 0 || ch <= 0) return null
    if (fill) {
        MushafFill.cached(page, cw, ch)?.let { return Look(it.bitmap, cw.toFloat(), ch.toFloat(), MushafRender.Crop.FULL, it) }
        val f = MushafFill.cachedFrame(page)
        if (f == null && !MushafFill.knownWithout(page)) return null
        if (f != null && com.sakina.app.core.mushaf.PageFill.plan(f, cw, ch) != null) return null
    }
    val c = if (trim || fill) MushafRender.cachedCrop(page) ?: return null else MushafRender.Crop.FULL
    val (fw, fh) = fitted(c, cw, ch)
    val b = MushafRender.cached(page, fw.roundToInt(), c) ?: return null
    return Look(b, fw, fh, c, null)
}

private suspend fun loadLook(file: File, page: Int, fill: Boolean, trim: Boolean, cw: Int, ch: Int): Look? {
    if (cw <= 0 || ch <= 0) return null
    if (fill) MushafFill.page(file, page, cw, ch)?.let { return Look(it.bitmap, cw.toFloat(), ch.toFloat(), MushafRender.Crop.FULL, it) }
    // As printed: the frame without the paper around it (found once per page), or the whole page.
    val c = if (trim || fill) MushafRender.crop(file, page) else MushafRender.Crop.FULL
    val (fw, fh) = fitted(c, cw, ch)
    val b = MushafRender.page(file, page, fw.roundToInt(), c) ?: return null
    return Look(b, fw, fh, c, null)
}

/** The hizb quarter that starts on a page, as the printed margin names it (the margin is cut when the page fills the screen). */
private fun hizbMark(d: QuranData, page: Int, str: com.sakina.app.ui.Strings, fmt: com.sakina.app.core.format.Fmt): String? {
    val q = d.meta.pageRange(page).firstOrNull { d.meta.startsQuarter(it) }?.let { d.meta.quarterOf(it) } ?: return null
    if (q <= 1) return null
    val hizb = (q - 1) / 4 + 1
    val part = (q - 1) % 4
    return if (part == 0) { if (hizb % 2 == 1) str.juzN(fmt.num((hizb + 1) / 2)) else str.hizbN(fmt.num(hizb)) } else str.hizbPart(part, fmt.num(hizb))
}

@Composable
private fun PageImage(
    file: File, page: Int, night: MushafNight, trim: Boolean, fill: Boolean, mark: String?,
    initialZoom: Float, onZoomed: (Boolean) -> Unit, onTap: () -> Unit,
) {
    val zoom = remember(page) { ZoomState() }
    val scope = rememberCoroutineScope()
    val filter = remember(night) { nightFilter(night) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cwi = constraints.maxWidth
        val chi = constraints.maxHeight
        val cw = cwi.toFloat()
        val ch = chi.toFloat()
        // Filled to the screen (centred, the lines spread to its height), or as printed.
        val look by produceState(cachedLook(page, fill, trim, cwi, chi), file, fill, trim, cwi, chi) {
            value = loadLook(file, page, fill, trim, cwi, chi)
        }
        val l = look
        val fw = l?.fw ?: cw
        val fh = l?.fh ?: ch
        zoom.cw = cw; zoom.ch = ch; zoom.fw = fw; zoom.fh = fh
        LaunchedEffect(initialZoom, l != null) {
            if (initialZoom > 1f && l != null) zoom.set(initialZoom, 0f, -fh * 0.25f)
        }
        LaunchedEffect(zoom.zoomed) { onZoomed(zoom.zoomed) }
        // When the zoom settles, draw the visible part again at full sharpness.
        var detail by remember { mutableStateOf<Detail?>(null) }
        LaunchedEffect(zoom.scale, zoom.tx, zoom.ty, l) {
            if (!zoom.zoomed || l == null) { detail = null; return@LaunchedEffect }
            delay(180)
            val s = zoom.scale; val x = zoom.tx; val y = zoom.ty
            val ox = (cw - fw) / 2 + fw / 2 * (1 - s) + x
            val oy = (ch - fh) / 2 + fh / 2 * (1 - s) + y
            val filled = l.filled
            val bmp = if (filled != null) MushafFill.region(file, filled.layout, s, ox, oy, cwi, chi) else {
                val c = l.crop
                val k = fw / c.w
                MushafRender.region(file, cwi, chi, Matrix().apply { setTranslate(-c.x, -c.y); postScale(k * s, k * s); postTranslate(ox, oy) })
            }
            bmp?.let { detail = Detail(it, s, x, y) }
        }
        val density = LocalDensity.current
        val layer = Modifier.graphicsLayer {
            scaleX = zoom.scale; scaleY = zoom.scale; translationX = zoom.tx; translationY = zoom.ty
        }
        Box(
            Modifier.fillMaxSize()
                .pointerInput(page) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = { at ->
                            val (s1, x1, y1) = zoom.doubleTapTarget(at)
                            val s0 = zoom.scale; val x0 = zoom.tx; val y0 = zoom.ty
                            scope.launch {
                                animate(0f, 1f, animationSpec = tween(220)) { v, _ -> zoom.set(s0 + (s1 - s0) * v, x0 + (x1 - x0) * v, y0 + (y1 - y0) * v) }
                            }
                        },
                    )
                }
                .pointerInput(page) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val fingers = event.changes.count { it.pressed }
                            // One finger on an unzoomed page belongs to the pager (turning pages).
                            if (fingers >= 2 || zoom.zoomed) {
                                val c = event.calculateCentroid(useCurrent = false)
                                val centroid = if (c.isSpecified) c else Offset(cw / 2, ch / 2)
                                zoom.transform(centroid, event.calculatePan(), event.calculateZoom())
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .testTag("mushaf_page_$page"),
            contentAlignment = Alignment.Center,
        ) {
            val size = with(density) { Modifier.size(fw.toDp(), fh.toDp()) }
            if (l != null) {
                Image(
                    l.base.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillBounds, colorFilter = filter,
                    modifier = size.then(layer).then(if (l.filled != null) Modifier.testTag("mushaf_filled") else Modifier),
                )
            }
            val dt = detail
            if (dt != null && dt.scale == zoom.scale && dt.tx == zoom.tx && dt.ty == zoom.ty) {
                Image(dt.bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.FillBounds, colorFilter = filter, modifier = Modifier.fillMaxSize().testTag("mushaf_detail"))
            }
            val filled = l?.filled
            if (filled != null && mark != null) HizbMark(filled, mark, night, size.then(layer))
        }
    }
}

/**
 * The hizb quarter's name in the middle of the header line, where the printed margin had it beside the frame (the
 * margin is cut when the page fills the screen). Left out when the header has no room for it.
 */
@Composable
private fun HizbMark(filled: MushafFill.Filled, text: String, night: MushafNight, modifier: Modifier) {
    val (bg, ink) = when (night) {
        MushafNight.DARK -> Color(0xFF243A2B) to Color(0xFFCFE6D3)
        MushafNight.WARM -> Color(0xFFD9E4C4) to Color(0xFF3B4A2A)
        MushafNight.OFF -> Color(0xFFCDEBD2) to Color(0xFF24553A)
    }
    androidx.compose.ui.layout.Layout(
        content = {
            Text(
                text, color = ink, maxLines = 1, softWrap = false,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                modifier = Modifier.background(bg, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 1.dp).testTag("mushaf_hizb_mark"),
            )
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val pl = measurables.first().measure(androidx.compose.ui.unit.Constraints())
        layout(constraints.maxWidth, constraints.maxHeight) {
            if (pl.width <= filled.headerRoom * 0.92f) {
                pl.place((filled.headerX - pl.width / 2f).roundToInt(), (filled.headerY - pl.height / 2f).roundToInt())
            }
        }
    }
}

/** 4×5 colour matrices, applied right to left: [a] after [b]. */
private fun concat(a: FloatArray, b: FloatArray): FloatArray {
    val r = FloatArray(20)
    for (row in 0 until 4) {
        for (col in 0 until 5) {
            var v = 0f
            for (k in 0 until 4) v += a[row * 5 + k] * b[k * 5 + col]
            if (col == 4) v += a[row * 5 + 4]
            r[row * 5 + col] = v
        }
    }
    return r
}

private fun nightFilter(n: MushafNight): ColorFilter? = when (n) {
    MushafNight.OFF -> null
    MushafNight.DARK -> {
        val invert = floatArrayOf(-1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f)
        // A 180° hue turn after inverting keeps the page's green frame green.
        val hue = floatArrayOf(-0.574f, 1.430f, 0.144f, 0f, 0f, 0.426f, 0.430f, 0.144f, 0f, 0f, 0.426f, 1.430f, -0.856f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
        // Soft white on near-black instead of pure white on black.
        val soften = floatArrayOf(0.84f, 0f, 0f, 0f, 22f, 0f, 0.84f, 0f, 0f, 22f, 0f, 0f, 0.84f, 0f, 22f, 0f, 0f, 0f, 1f, 0f)
        ColorFilter.colorMatrix(ColorMatrix(concat(soften, concat(hue, invert))))
    }
    MushafNight.WARM -> ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(0.95f, 0f, 0f, 0f, 0f, 0f, 0.89f, 0f, 0f, 0f, 0f, 0f, 0.78f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
}

// ---------------- a page that is not on the device ----------------

@Composable
private fun MissingPage(vm: MainViewModel, d: QuranData, page: Int, state: PageDownload?, auto: Boolean, night: MushafNight) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val repo = vm.mushaf
    val entry = repo.manifest.entry(page)
    val range = d.meta.pageRange(page)
    var offline by remember { mutableStateOf(false) }
    val onDark = night == MushafNight.DARK
    Box(Modifier.fillMaxSize().padding(16.dp).testTag("mushaf_missing_$page"), contentAlignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(24.dp), color = if (onDark) Color(0xFF242424) else MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.widthIn(max = 460.dp)) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val ink = if (onDark) Color(0xFFE6E1D8) else MaterialTheme.colorScheme.onSurface
                Icon(SakinaIcons.Page, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                Text(str.pageN(fmt.num(page)), style = MaterialTheme.typography.headlineSmall, color = ink)
                Text(
                    range.map { d.meta.ref(it).sura }.distinct().joinToString(" · ") { d.meta.surah(it).let { su -> str.surahTitle(su.nameAr, su.nameTranslit) } } + " · " + str.juzN(fmt.num(d.meta.juzOf(range.first))),
                    style = MaterialTheme.typography.bodyMedium, color = ink, textAlign = TextAlign.Center,
                )
                Text(str.pageNotHere, style = MaterialTheme.typography.titleMedium, color = ink, textAlign = TextAlign.Center)
                Text(str.pageSize(fmt.bytes(entry.size)), style = MaterialTheme.typography.bodySmall, color = ink.copy(alpha = 0.75f))
                Spacer(Modifier.height(4.dp))
                when (state) {
                    is PageDownload.Running -> {
                        val pct = if (state.total > 0) (state.bytes * 100 / state.total).toInt() else 0
                        LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth().testTag("mushaf_page_progress"))
                        Text(str.downloadingPage(fmt.num(pct)), style = MaterialTheme.typography.bodyMedium, color = ink)
                    }
                    else -> {
                        if (state is PageDownload.Failed) Text(str.fetchFailure(state.failure), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                        if (offline) Text(str.offline, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        Button(onClick = {
                            offline = repo.network() == NetState.OFFLINE
                            if (!offline) repo.requestPage(page)
                        }, modifier = Modifier.fillMaxWidth().testTag("mushaf_download_page_$page")) {
                            Icon(SakinaIcons.Download, null, Modifier.size(18.dp)); Spacer(Modifier.size(8.dp))
                            Text(if (state is PageDownload.Failed || offline) str.retry else str.downloadThisPage)
                        }
                    }
                }
                Box(Modifier.testTag("mushaf_auto_switch_$page")) {
                    SwitchRow(auto, str.autoDownload, str.autoDownloadNote) { v -> vm.updateQuran { it.copy(reader = it.reader.copy(autoDownload = v)) } }
                }
                TextButton(onClick = { vm.push(Route.QuranReader(range.first)) }) { Text(str.readInText) }
                TextButton(onClick = { vm.push(Route.MushafDownloads) }, modifier = Modifier.testTag("mushaf_download_all")) { Text(str.downloadWhole) }
            }
        }
    }
}
