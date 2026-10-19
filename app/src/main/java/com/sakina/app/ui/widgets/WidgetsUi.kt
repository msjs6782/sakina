@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sakina.app.ui.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import android.util.SizeF
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.sakina.app.R
import com.sakina.app.SakinaApp
import com.sakina.app.core.location.City
import com.sakina.app.core.location.CitySearch
import com.sakina.app.core.widgets.ContentMode
import com.sakina.app.core.widgets.WidgetKind
import com.sakina.app.data.AppClock
import com.sakina.app.data.AppSettings
import com.sakina.app.data.SettingsOps
import com.sakina.app.data.VisualStyle
import com.sakina.app.data.WidgetBackground
import com.sakina.app.data.WidgetClock
import com.sakina.app.data.WidgetConfig
import com.sakina.app.data.WidgetFont
import com.sakina.app.data.WidgetHighlight
import com.sakina.app.data.WidgetLang
import com.sakina.app.data.WidgetPlaceMode
import com.sakina.app.data.WidgetTheme
import com.sakina.app.ui.LocalStrings
import com.sakina.app.ui.MainViewModel
import com.sakina.app.ui.components.ChoiceRow
import com.sakina.app.ui.components.Divider
import com.sakina.app.ui.components.NoteText
import com.sakina.app.ui.components.ScreenBar
import com.sakina.app.ui.components.SectionCard
import com.sakina.app.ui.components.SwitchRow
import com.sakina.app.widgets.PlacedWidget
import com.sakina.app.widgets.Shortcuts
import com.sakina.app.widgets.WidgetEnv
import com.sakina.app.widgets.WidgetPalette
import com.sakina.app.widgets.WidgetRenderer
import com.sakina.app.widgets.WidgetUpdater
import kotlinx.coroutines.launch

/** Debug builds only: the gallery shows each widget at three sizes (set by the CI's demo extras). */
object WidgetsDebug {
    @Volatile var sizes = false
    /** Opens the gallery with the painted-sky previews (CI screenshots). */
    @Volatile var gallerySky = false
}

/**
 * The colours of the phone's wallpaper (Android 8.1+, no permission needed), for a backdrop that shows a widget's
 * transparency the way the home screen will. Falls back to a neutral gradient.
 */
@Composable
fun wallpaperBrush(): Brush {
    val context = LocalContext.current
    val colors = remember {
        runCatching {
            if (Build.VERSION.SDK_INT < 27) null
            else android.app.WallpaperManager.getInstance(context).getWallpaperColors(android.app.WallpaperManager.FLAG_SYSTEM)?.let { wc ->
                listOfNotNull(wc.primaryColor, wc.secondaryColor, wc.tertiaryColor).map { Color(it.toArgb()) }
            }
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }
    val list = when {
        colors == null -> listOf(Color(0xFF3E6B8A), Color(0xFFD9A066), Color(0xFFF2EEE6))
        colors.size == 1 -> listOf(colors[0], colors[0].copy(alpha = 0.85f))
        else -> colors
    }
    return Brush.linearGradient(list)
}

/** A native preview: the widget's own RemoteViews inflated here, on the wallpaper's colours to show transparency. */
@Composable
fun RemoteViewPreview(views: RemoteViews?, wDp: Float, hDp: Float, tag: String, modifier: Modifier = Modifier, maxHeightDp: Float = Float.MAX_VALUE) {
    val backdrop = wallpaperBrush()
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val avail = maxWidth.value - 24f
        val scale = minOf(1f, avail / wDp, maxHeightDp / hDp)
        Box(
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(backdrop)
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size((wDp * scale).dp, (hDp * scale).dp), contentAlignment = Alignment.Center) {
                AndroidView(
                    factory = { FrameLayout(it) },
                    update = { frame ->
                        frame.removeAllViews()
                        if (views != null) {
                            try {
                                frame.addView(views.apply(frame.context, frame))
                            } catch (e: Exception) {
                                Log.e("SakinaWidgets", "preview failed", e)
                            }
                        }
                    },
                    // The widget is laid out at its real size, then scaled to fit the screen if it is wider.
                    modifier = Modifier
                        .requiredSize(wDp.dp, hDp.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale }
                        .testTag(tag),
                )
            }
        }
    }
}

/** Renders a widget for an in-app preview (no tap actions). */
fun previewViews(app: SakinaApp, s: AppSettings, kind: WidgetKind, cfg: WidgetConfig, size: SizeF): RemoteViews? = try {
    WidgetRenderer.render(WidgetEnv(app, s, AppClock.now(), WidgetUpdater.loadPack(app)), kind, cfg, 0, listOf(size), preview = true).views
} catch (e: Exception) {
    Log.e("SakinaWidgets", "preview render failed for ${kind.id}", e)
    null
}

/** The gallery: native previews of the ten widgets, the ones on the home screen, shortcuts and tiles. */
@Composable
fun WidgetsScreen(vm: MainViewModel, s: AppSettings) {
    val str = LocalStrings.current
    val context = LocalContext.current
    val app = context.applicationContext as SakinaApp
    val scope = rememberCoroutineScope()
    var placed by remember { mutableStateOf<List<PlacedWidget>>(emptyList()) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(tick, s) { placed = WidgetUpdater.placed(context) }
    // Placed widgets can change while the gallery is open (after the launcher's dialog), so look again on resume.
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { tick++; onPauseOrDispose { } }
    val mgr = remember { AppWidgetManager.getInstance(context) }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).imePadding()) {
        ScreenBar(str.widgetsTitle, onBack = { vm.pop() })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionCard(title = str.myWidgets) {
                if (placed.isEmpty()) NoteText(str.noWidgets, Modifier.testTag("widgets_none"))
                placed.forEachIndexed { i, w ->
                    if (i > 0) Divider()
                    val place = w.config.place?.takeIf { w.config.placeMode == WidgetPlaceMode.FIXED }?.name(s.language) ?: str.wPlaceApp
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("placed_${w.kind.id}")) {
                        Text(str.widgetName(w.kind.id), style = MaterialTheme.typography.bodyLarge)
                        // The widget as it is now: its own settings at the size the launcher gave it.
                        val psize = remember(w.id, tick) { WidgetUpdater.sizesOf(context, w.id, w.kind).maxByOrNull { it.height } ?: WidgetRenderer.defaultSize(w.kind) }
                        val pviews = remember(w, s, tick) { previewViews(app, s, w.kind, w.config, psize) }
                        RemoteViewPreview(pviews, psize.width, psize.height, "placed_preview_${w.kind.id}", Modifier.padding(vertical = 6.dp), maxHeightDp = 140f)
                        val sub = if (w.kind.needsPlace) place else when (w.config.contentMode) {
                            ContentMode.MIXED -> str.wContentMixed
                            ContentMode.ADHKAR -> str.wContentAdhkar
                            ContentMode.VERSES -> str.wContentVerses
                        }
                        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                            OutlinedButton(onClick = {
                                context.startActivity(
                                    Intent(context, com.sakina.app.widgets.WidgetConfigActivity::class.java)
                                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, w.id)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }, modifier = Modifier.testTag("placed_settings_${w.kind.id}")) { Text(str.widgetSettings) }
                            OutlinedButton(onClick = {
                                scope.launch {
                                    WidgetUpdater.update(context, "manual:app", only = setOf(w.id), force = true)
                                    Toast.makeText(context, str.refreshed, Toast.LENGTH_SHORT).show()
                                }
                            }, modifier = Modifier.testTag("placed_refresh_${w.kind.id}")) { Text(str.refreshNow) }
                        }
                    }
                }
                if (placed.isNotEmpty()) {
                    Divider()
                    OutlinedButton(onClick = {
                        scope.launch {
                            WidgetUpdater.update(context, "manual:all", force = true)
                            Toast.makeText(context, str.refreshed, Toast.LENGTH_SHORT).show()
                        }
                    }, modifier = Modifier.fillMaxWidth().testTag("widgets_refresh_all")) { Text(str.refreshAll) }
                }
                NoteText(str.wRemainingNote, Modifier.padding(top = 8.dp))
            }

            Text(str.widgetsGallery, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 4.dp))
            NoteText(str.widgetsGalleryNote)
            NoteText(str.wPreviewOnWallpaper)
            // Preview every widget on a plain colour or on the painted sky (a setting of each widget).
            var gallerySky by remember { mutableStateOf(WidgetsDebug.gallerySky) }
            Chips(listOf(false to str.wBgColorMode, true to str.wBgSkyMode), gallerySky, "gallery_bg_") { gallerySky = it }
            for (kind in WidgetKind.entries) {
                val size = WidgetRenderer.defaultSize(kind)
                fun base() = WidgetConfig.defaultsFor(kind.id).let { if (gallerySky) it.copy(background = WidgetBackground.SKY) else it }
                val views = remember(kind, s, tick, gallerySky) { previewViews(app, s, kind, base(), size) }
                SectionCard(modifier = Modifier.testTag("gallery_${kind.id}")) {
                    Text(str.widgetName(kind.id), style = MaterialTheme.typography.titleMedium)
                    Text(str.widgetDesc(kind.id), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    RemoteViewPreview(views, size.width, size.height, "preview_${kind.id}")
                    if (WidgetsDebug.sizes) {
                        for (sz in WidgetRenderer.sampleSizes(kind).filter { it != size }) {
                            val v = remember(kind, s, tick, sz, gallerySky) { previewViews(app, s, kind, base(), sz) }
                            Text("\u2066${sz.width.toInt()}×${sz.height.toInt()} dp\u2069", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
                            RemoteViewPreview(v, sz.width, sz.height, "preview_${kind.id}_${sz.width.toInt()}")
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = {
                        val ok = mgr.isRequestPinAppWidgetSupported &&
                            mgr.requestPinAppWidget(ComponentName(context, WidgetUpdater.providerClass(kind)), null, WidgetUpdater.pinnedCallback(context, kind))
                        Toast.makeText(context, if (ok) str.pinRequested else str.pinUnsupported, Toast.LENGTH_LONG).show()
                        Log.i("SakinaWidgets", "pin requested ${kind.id} ok=$ok")
                    }, modifier = Modifier.fillMaxWidth().testTag("pin_${kind.id}")) { Text(str.addToHome) }
                }
            }
            if (!mgr.isRequestPinAppWidgetSupported) NoteText(str.pinUnsupported)

            ShortcutsCard(s)
            TilesCard()
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ShortcutsCard(s: AppSettings) {
    val str = LocalStrings.current
    val context = LocalContext.current
    var target by remember { mutableStateOf("library") }
    SectionCard(title = str.shortcutsTitle) {
        NoteText(str.shortcutsBody)
        Divider()
        Text(str.pinAdhkarTitle, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
        NoteText(str.pinAdhkarBody)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            for (t in Shortcuts.ADHKAR_TARGETS) {
                FilterChip(
                    selected = target == t,
                    onClick = { target = t },
                    label = { Text(if (t == "library") str.adhkarLibrary else Shortcuts.adhkarLabel(str, t)) },
                    modifier = Modifier.testTag("sc_target_$t"),
                )
            }
        }
        Button(onClick = {
            val ok = Shortcuts.pinAdhkar(context, s, target)
            Toast.makeText(context, if (ok) str.shortcutRequested else str.shortcutUnsupported, Toast.LENGTH_LONG).show()
            Log.i("SakinaShortcuts", "pin requested adhkar_$target ok=$ok")
        }, modifier = Modifier.fillMaxWidth().testTag("pin_adhkar")) { Text(str.pinShortcut) }
    }
}

@Composable
private fun TilesCard() {
    val str = LocalStrings.current
    val context = LocalContext.current
    SectionCard(title = str.tilesTitle) {
        NoteText(str.tilesBody)
        if (Build.VERSION.SDK_INT >= 33) {
            val sbm = context.getSystemService(android.app.StatusBarManager::class.java)
            for ((cls, label, icon, tag) in listOf(
                Quad(com.sakina.app.widgets.TimesTileService::class.java, str.tabTimes, R.drawable.g_times, "tile_add_times"),
                Quad(com.sakina.app.widgets.QiblaTileService::class.java, str.toolsQibla, R.drawable.g_qibla, "tile_add_qibla"),
            )) {
                OutlinedButton(onClick = {
                    sbm.requestAddTileService(ComponentName(context, cls), label, Icon.createWithResource(context, icon), context.mainExecutor) { result ->
                        val msg = when (result) {
                            android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> str.tileAdded
                            android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> str.tileAlready
                            else -> str.tileNotAdded
                        }
                        Log.i("SakinaTiles", "add tile ${cls.simpleName} result=$result")
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).testTag(tag)) { Text(str.addTile(label)) }
            }
        } else {
            NoteText(str.tilesManual, Modifier.padding(top = 6.dp))
        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

// ---------------------------------------------------------------- per-widget settings

@Composable
fun WidgetConfigScreen(
    app: SakinaApp,
    s: AppSettings,
    id: Int,
    kind: WidgetKind,
    initial: WidgetConfig,
    onSave: (WidgetConfig) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
) {
    val str = LocalStrings.current
    val context = LocalContext.current
    var cfg by remember { mutableStateOf(initial) }
    val sizes = remember { WidgetUpdater.sizesOf(context, id, kind) }
    // The portrait size (the tallest the launcher reported), as the widget usually appears on a phone.
    val size = sizes.maxByOrNull { it.height } ?: WidgetRenderer.defaultSize(kind)
    val views = remember(cfg, s) { previewViews(app, s, kind, cfg, size) }
    var cities by remember { mutableStateOf<List<City>>(emptyList()) }
    LaunchedEffect(Unit) { cities = app.cities.all() }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).imePadding()) {
        ScreenBar(str.widgetName(kind.id), onBack = onBack)
        // The preview and the save button stay in view while the settings scroll beneath them.
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RemoteViewPreview(views, size.width, size.height, "wcfg_preview", maxHeightDp = 170f)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onSave(cfg) }, modifier = Modifier.weight(1f).testTag("wcfg_save")) { Text(str.wSave) }
                OutlinedButton(onClick = onRefresh, modifier = Modifier.testTag("wcfg_refresh")) { Text(str.refreshNow) }
            }
            Divider()
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            if (kind.needsPlace) SectionCard(title = str.wPlace) {
                ChoiceRow(cfg.placeMode == WidgetPlaceMode.APP, str.wPlaceApp, s.activeLocation?.name(s.language), onClick = { cfg = cfg.copy(placeMode = WidgetPlaceMode.APP) })
                ChoiceRow(cfg.placeMode == WidgetPlaceMode.FIXED, str.wPlaceFixed, cfg.place?.name(s.language), onClick = {
                    cfg = cfg.copy(placeMode = WidgetPlaceMode.FIXED, place = cfg.place ?: s.activeLocation)
                })
                if (cfg.placeMode == WidgetPlaceMode.FIXED) {
                    NoteText(str.wPlaceKept, Modifier.padding(vertical = 4.dp))
                    if (s.locations.isNotEmpty()) {
                        Text(str.wPlaceSaved, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
                        for (loc in s.locations) {
                            ChoiceRow(cfg.place?.id == loc.id, loc.name(s.language), loc.country(s.language), onClick = { cfg = cfg.copy(place = loc) })
                        }
                    }
                    var q by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = q, onValueChange = { q = it }, singleLine = true,
                        label = { Text(str.wPlaceSearch) },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).testTag("wcfg_city_search"),
                    )
                    if (q.isNotBlank()) {
                        for (c in CitySearch.search(cities, q, limit = 6)) {
                            val loc = SettingsOps.cityLocation(c)
                            ChoiceRow(cfg.place?.id == loc.id, loc.name(s.language), loc.country(s.language), onClick = { cfg = cfg.copy(place = loc) })
                        }
                    }
                }
            }

            ElementsCard(kind, cfg) { cfg = it }

            SectionCard(title = str.wLanguage) {
                Chips(listOf(WidgetLang.APP to str.wFollowApp, WidgetLang.AR to "العربية", WidgetLang.EN to "English"), cfg.lang, "wcfg_lang_") { cfg = cfg.copy(lang = it) }
                if (kind.needsPlace) {
                    Text(str.wClock, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp))
                    Chips(listOf(WidgetClock.APP to str.wFollowApp, WidgetClock.H12 to str.wH12, WidgetClock.H24 to str.wH24), cfg.clock, "wcfg_clock_") { cfg = cfg.copy(clock = it) }
                }
                if (kind == WidgetKind.DIGITAL) NoteText(str.wDigitalNote, Modifier.padding(top = 6.dp))
            }

            SectionCard(title = str.wStyle) {
                Chips(listOf<Pair<VisualStyle?, String>>(null to str.wFollowApp) + VisualStyle.entries.map { it to str.styleName(it) }, cfg.style, "wcfg_style_") { cfg = cfg.copy(style = it) }
                Text(str.wTheme, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp))
                Chips(listOf(WidgetTheme.APP to str.wFollowApp, WidgetTheme.SYSTEM to str.wThemeSystem, WidgetTheme.LIGHT to str.wThemeLight, WidgetTheme.DARK to str.wThemeDark), cfg.theme, "wcfg_theme_") { cfg = cfg.copy(theme = it) }
                SwitchRow(
                    checked = (cfg.dynamicColor ?: s.dynamicColor) && Build.VERSION.SDK_INT >= 31,
                    title = str.wDynamic,
                    detail = if (Build.VERSION.SDK_INT < 31) str.wDynamicNote else null,
                    enabled = Build.VERSION.SDK_INT >= 31,
                ) { cfg = cfg.copy(dynamicColor = it) }
            }

            SectionCard(title = str.wFont) {
                Chips(listOf(WidgetFont.APP to str.wFontApp, WidgetFont.KUFI to str.wFontKufi), cfg.font, "wcfg_font_") { cfg = cfg.copy(font = it) }
            }

            SectionCard(title = str.wTextSize) {
                Text(str.digits("${cfg.textScale}%"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("wcfg_scale_value"))
                Slider(
                    value = cfg.textScale.toFloat(), onValueChange = { cfg = cfg.copy(textScale = kotlin.math.round(it / 10f).toInt() * 10) },
                    valueRange = 80f..140f, steps = 5, modifier = Modifier.testTag("wcfg_scale"),
                )
                NoteText(str.wTextSizeNote)
            }

            if (kind != WidgetKind.GLASS) SectionCard(title = str.wBackground) {
                Chips(listOf(WidgetBackground.COLOR to str.wBgColorMode, WidgetBackground.SKY to str.wBgSkyMode), cfg.background, "wcfg_background_") { cfg = cfg.copy(background = it) }
                if (cfg.background == WidgetBackground.SKY) NoteText(str.wBgSkyNote, Modifier.padding(top = 6.dp))
            }

            val sky = cfg.background == WidgetBackground.SKY && kind != WidgetKind.GLASS
            SectionCard(title = str.wColors) {
                SwatchRow(str.wTextColor, cfg.textColor, "wcfg_text_") { cfg = cfg.copy(textColor = it) }
                if (!sky) SwatchRow(str.wBgColor, cfg.backgroundColor, "wcfg_bg_") { cfg = cfg.copy(backgroundColor = it) }
                SwatchRow(str.wAccentColor, cfg.accentColor, "wcfg_accent_") { cfg = cfg.copy(accentColor = it) }
                if (!sky) {
                    Text("${str.wOpacity}: ${str.digits("${cfg.opacity}")}%", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp))
                    Slider(
                        value = cfg.opacity.toFloat(), onValueChange = { cfg = cfg.copy(opacity = it.toInt()) },
                        valueRange = 0f..100f, modifier = Modifier.testTag("wcfg_opacity"),
                    )
                }
                if (kind == WidgetKind.GLASS) {
                    val floor = remember(cfg, s) { WidgetPalette.glassFloor(context, cfg, s, AppClock.now()) }
                    SwitchRow(cfg.contrastGuard, str.wContrastGuard, str.wContrastGuardNote(str.digits("$floor%"))) { cfg = cfg.copy(contrastGuard = it) }
                }
            }

            SectionCard(title = str.wCorners) {
                Chips(WidgetConfig.CORNERS.map { it to str.digits("$it") }, cfg.corner, "wcfg_corner_") { cfg = cfg.copy(corner = it) }
                Text(str.wBorder, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp))
                Chips(listOf(0 to str.wBorderNone, 1 to str.wBorderThin, 2 to str.wBorderThick), cfg.border, "wcfg_border_") { cfg = cfg.copy(border = it) }
            }

            if (kind == WidgetKind.CONTENT) SectionCard(title = str.wContent) {
                Chips(listOf(ContentMode.MIXED to str.wContentMixed, ContentMode.ADHKAR to str.wContentAdhkar, ContentMode.VERSES to str.wContentVerses), cfg.contentMode, "wcfg_content_") { cfg = cfg.copy(contentMode = it) }
                NoteText(str.wContentNote, Modifier.padding(top = 6.dp))
            }

            if (kind.showsRemaining) NoteText(str.wRemainingNote)
            if (kind == WidgetKind.ANALOG) NoteText(if (Build.VERSION.SDK_INT >= 31) str.wAnalogNoteNew else str.wAnalogNoteOld)
            OutlinedButton(onClick = { cfg = WidgetConfig.defaultsFor(kind.id) }, modifier = Modifier.fillMaxWidth().testTag("wcfg_reset")) { Text(str.wReset) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Which parts the widget shows, for the parts its type has. */
@Composable
private fun ElementsCard(kind: WidgetKind, cfg: WidgetConfig, onChange: (WidgetConfig) -> Unit) {
    val str = LocalStrings.current
    val hasPlace = kind in setOf(WidgetKind.NEXT, WidgetKind.COUNTDOWN, WidgetKind.DAY, WidgetKind.GLASS, WidgetKind.DIGITAL, WidgetKind.ANALOG, WidgetKind.FAJR_MAGHRIB, WidgetKind.DAYNAME)
    val hasRefresh = hasPlace && kind != WidgetKind.ANALOG
    val hasDate = kind in setOf(WidgetKind.DAY, WidgetKind.DIGITAL, WidgetKind.FAJR_MAGHRIB, WidgetKind.DAYNAME)
    val hasRemaining = kind.showsRemaining && kind != WidgetKind.COUNTDOWN
    val hasTable = kind in setOf(WidgetKind.DAY, WidgetKind.GLASS, WidgetKind.STRIP, WidgetKind.DAYNAME)
    if (!hasPlace && !hasDate && !kind.showsRemaining && !hasTable) return
    SectionCard(title = str.wElements, modifier = Modifier.testTag("wcfg_elements")) {
        if (hasPlace) SwitchRow(cfg.showPlace, str.wShowPlace, modifier = Modifier.testTag("wcfg_show_place")) { onChange(cfg.copy(showPlace = it)) }
        if (hasRefresh) SwitchRow(cfg.showRefresh, str.wShowRefresh, modifier = Modifier.testTag("wcfg_show_refresh")) { onChange(cfg.copy(showRefresh = it)) }
        if (hasDate) SwitchRow(cfg.showDate, str.wShowDate, modifier = Modifier.testTag("wcfg_show_date")) { onChange(cfg.copy(showDate = it)) }
        if (hasRemaining) SwitchRow(cfg.showRemaining, str.wShowRemaining, modifier = Modifier.testTag("wcfg_show_remaining")) { onChange(cfg.copy(showRemaining = it)) }
        if (kind.showsRemaining) SwitchRow(cfg.showStamp && (cfg.showRemaining || kind == WidgetKind.COUNTDOWN), str.wShowStamp, enabled = cfg.showRemaining || kind == WidgetKind.COUNTDOWN, modifier = Modifier.testTag("wcfg_show_stamp")) { onChange(cfg.copy(showStamp = it)) }
        if (hasTable) {
            SwitchRow(cfg.showSunrise ?: (kind != WidgetKind.DAYNAME), str.wShowSunrise, modifier = Modifier.testTag("wcfg_show_sunrise")) { onChange(cfg.copy(showSunrise = it)) }
            Text(str.wHighlight, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp))
            Chips(listOf(WidgetHighlight.FILL to str.wHighlightFill, WidgetHighlight.TEXT to str.wHighlightText), cfg.highlight, "wcfg_highlight_") { onChange(cfg.copy(highlight = it)) }
        }
    }
}

@Composable
private fun <T> Chips(options: List<Pair<T, String>>, selected: T, tagPrefix: String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for ((value, label) in options) {
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label) },
                modifier = Modifier.testTag(tagPrefix + (value?.toString() ?: "app").lowercase()),
            )
        }
    }
}

@Composable
private fun SwatchRow(title: String, value: Long?, tagPrefix: String, onSelect: (Long?) -> Unit) {
    val str = LocalStrings.current
    Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            onClick = { onSelect(null) },
            shape = RoundedCornerShape(16.dp),
            color = if (value == null) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.height(32.dp).testTag(tagPrefix + "auto"),
        ) { Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) { Text(str.wAuto, style = MaterialTheme.typography.labelLarge) } }
        for (c in WidgetPalette.SWATCHES) {
            val selected = value == c
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(c.toInt()))
                    .border(BorderStroke(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant), CircleShape)
                    .clickable { onSelect(c) }
                    .testTag(tagPrefix + String.format(java.util.Locale.ROOT, "%06X", c and 0xFFFFFF)),
            )
        }
    }
}
