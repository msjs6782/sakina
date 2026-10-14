@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sakina.app.ui.quran

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakina.app.data.LastRead
import com.sakina.app.data.QuranData
import com.sakina.app.data.ReaderMode
import com.sakina.app.data.ReaderPrefs
import com.sakina.app.ui.LocalFmt
import com.sakina.app.ui.LocalStrings
import com.sakina.app.ui.MainViewModel
import com.sakina.app.ui.Route
import com.sakina.app.ui.components.DirectionalIcon
import com.sakina.app.ui.components.NoteText
import com.sakina.app.ui.components.SakinaIcons
import com.sakina.app.ui.components.SwitchRow
import com.sakina.app.ui.components.exposeTestTags
import com.sakina.app.ui.location.FullScreen
import com.sakina.app.ui.settings.Segmented
import com.sakina.app.ui.theme.AmiriQuran
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun QuranReaderScreen(vm: MainViewModel, startGlobal: Int, highlight: Boolean) {
    FullScreen {
        WithQuran(vm) { d -> Reader(vm, d, startGlobal.coerceIn(0, d.meta.totalAyahs - 1), highlight) }
    }
}

private fun quranStyle(p: ReaderPrefs, color: Color) = TextStyle(
    fontFamily = AmiriQuran,
    fontSize = p.fontSize.sp,
    lineHeight = (p.fontSize * 2.05).sp,
    color = color,
    textAlign = TextAlign.Justify,
    textDirection = TextDirection.Rtl,
)

@Composable
private fun Reader(vm: MainViewModel, d: QuranData, startGlobal: Int, highlight: Boolean) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val qs by vm.quranState.collectAsStateWithLifecycle()
    val prefs = qs.reader
    var current by remember { mutableIntStateOf(startGlobal) }
    var selected by remember { mutableStateOf<Int?>(null) }
    val marked = remember { if (highlight) startGlobal else -1 }
    var showSettings by remember { mutableStateOf(false) }

    // Remember the reading position (a short pause first, so fast scrolling does not write constantly).
    LaunchedEffect(current) {
        delay(800)
        val r = d.meta.ref(current)
        vm.updateQuran { it.copy(lastRead = LastRead(r.sura, r.aya, d.meta.pageOf(current), System.currentTimeMillis())) }
    }

    Column(Modifier.fillMaxSize()) {
        val r = d.meta.ref(current)
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.pop() }) { DirectionalIcon(SakinaIcons.ArrowStart, str.back) }
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(d.meta.surah(r.sura).let { str.surahTitle(it.nameAr, it.nameTranslit) }, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    str.pageN(fmt.num(d.meta.pageOf(current))) + " · " + str.juzN(fmt.num(d.meta.juzOf(current))),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { vm.switchView(com.sakina.app.data.QuranView.PRINTED, Route.Mushaf(d.meta.pageOf(current))) }, modifier = Modifier.testTag("reader_to_printed")) {
                Icon(SakinaIcons.Page, str.printedView)
            }
            IconButton(onClick = { vm.push(Route.QuranSearch()) }) { Icon(SakinaIcons.Search, str.search) }
            IconButton(onClick = { showSettings = true }, modifier = Modifier.testTag("reader_settings")) { Icon(SakinaIcons.Sliders, str.readerSettings) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        key(prefs.mode) {
            val onAyah: (Int) -> Unit = { selected = it }
            when (prefs.mode) {
                ReaderMode.PAGES -> PagesView(d, prefs, start = current, selected = selected, marked = marked, onAyah = onAyah) { current = it }
                ReaderMode.AYAHS -> AyahsView(d, prefs, start = current, selected = selected, marked = marked, onAyah = onAyah) { current = it }
            }
        }
    }
    selected?.let { g -> AyahSheet(vm, d, qs, g) { selected = null } }
    if (showSettings) ReaderSettingsSheet(vm, prefs) { showSettings = false }
}

@Composable
private fun PagesView(d: QuranData, prefs: ReaderPrefs, start: Int, selected: Int?, marked: Int, onAyah: (Int) -> Unit, onVisible: (Int) -> Unit) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = d.meta.pageOf(start) - 1)
    LaunchedEffect(state) {
        // The first value is the page that was opened: keep the ayah it was opened at (say Ya-Sin 1,
        // not the end of Fatir on the same page) until the reader moves to another page.
        snapshotFlow { state.firstVisibleItemIndex }.drop(1).collect { idx -> onVisible(d.meta.pageRange(idx + 1).first) }
    }
    // A marked ayah (from search or a reference) may sit low on its page: once its line is measured,
    // scroll so it starts near the top instead of leaving it at the bottom of the screen.
    val scope = rememberCoroutineScope()
    var aligned by remember { mutableStateOf(marked < 0) }
    val margin = with(LocalDensity.current) { 24.dp.toPx() }
    val onMarkedY: (Float) -> Unit = { y ->
        if (!aligned) {
            aligned = true
            val offset = (y - margin).roundToInt()
            if (offset > 0) scope.launch { state.scrollToItem(d.meta.pageOf(marked) - 1, offset) }
        }
    }
    LazyColumn(state = state, modifier = Modifier.fillMaxSize().testTag("quran_pages")) {
        items(d.meta.pageCount, key = { it }) { i -> PageBlock(d, i + 1, prefs, selected, marked, onAyah, if (aligned) null else onMarkedY) }
    }
}

@Composable
private fun PageBlock(d: QuranData, page: Int, prefs: ReaderPrefs, selected: Int?, marked: Int, onAyah: (Int) -> Unit, onMarkedY: ((Float) -> Unit)? = null) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val range = d.meta.pageRange(page)
    val ink = MaterialTheme.colorScheme.onSurface
    val hl = MaterialTheme.colorScheme.primaryContainer
    val mk = MaterialTheme.colorScheme.secondaryContainer
    val style = quranStyle(prefs, ink)
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                range.map { d.meta.ref(it).sura }.distinct().joinToString(" · ") { "سورة " + d.meta.surah(it).nameAr },
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f), maxLines = 1,
            )
            Text(str.juzN(fmt.num(d.meta.juzOf(range.first))), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(6.dp))
        // Split the page into runs of one surah each.
        val runs = range.groupBy { d.meta.ref(it).sura }
        for ((sura, ayahs) in runs) {
            if (d.meta.ref(ayahs.first()).aya == 1) {
                SurahBanner(d.meta.surah(sura))
                d.text.basmala[sura]?.let { BasmalaLine(it, prefs.fontSize) }
            }
            val (text, markStart) = remember(ayahs.first(), selected, marked, ink, hl, mk) {
                var at = -1
                val t = buildAnnotatedString {
                    for (g in ayahs) {
                        if (g == marked) at = length
                        val bg = when (g) {
                            selected -> SpanStyle(background = hl)
                            marked -> SpanStyle(background = mk)
                            else -> SpanStyle()
                        }
                        withLink(LinkAnnotation.Clickable("a$g", TextLinkStyles(style = bg)) { onAyah(g) }) {
                            append(d.text.ayahs[g])
                            append(" ")
                            append(ayahEnd(d.meta.ref(g).aya))
                        }
                        append(" ")
                    }
                }
                t to at
            }
            // A plain holder, not state: the layout is only read when the text is placed.
            val layout = remember { arrayOfNulls<TextLayoutResult>(1) }
            Text(
                text, style = style,
                onTextLayout = { layout[0] = it },
                modifier = Modifier.fillMaxWidth().onPlaced { coords ->
                    val l = layout[0]
                    if (markStart >= 0 && onMarkedY != null && l != null) {
                        onMarkedY(coords.positionInParent().y + l.getLineTop(l.getLineForOffset(markStart.coerceAtMost(text.length))))
                    }
                },
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            fmt.num(page), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    }
}

private sealed interface Entry {
    data class Banner(val sura: Int) : Entry
    data class Ayah(val global: Int) : Entry
}

@Composable
private fun AyahsView(d: QuranData, prefs: ReaderPrefs, start: Int, selected: Int?, marked: Int, onAyah: (Int) -> Unit, onVisible: (Int) -> Unit) {
    val entries = remember(d) {
        buildList {
            for (s in d.meta.suras) {
                add(Entry.Banner(s.index))
                for (g in s.start until s.start + s.ayahCount) add(Entry.Ayah(g))
            }
        }
    }
    // Index of an ayah in [entries]: its global index plus one banner per surah up to and including its own.
    val startIndex = start + d.meta.ref(start).sura
    val state = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex }.collect { idx ->
            when (val e = entries.getOrNull(idx)) {
                is Entry.Ayah -> onVisible(e.global)
                is Entry.Banner -> onVisible(d.meta.surah(e.sura).start)
                null -> {}
            }
        }
    }
    val str = LocalStrings.current
    val ink = MaterialTheme.colorScheme.onSurface
    LazyColumn(state = state, modifier = Modifier.fillMaxSize().testTag("quran_ayahs")) {
        items(entries.size, key = { i -> when (val e = entries[i]) { is Entry.Banner -> "b${e.sura}"; is Entry.Ayah -> "a${e.global}" } }) { i ->
            when (val e = entries[i]) {
                is Entry.Banner -> Column(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
                    SurahBanner(d.meta.surah(e.sura))
                    d.text.basmala[e.sura]?.let { BasmalaLine(it, prefs.fontSize) }
                }
                is Entry.Ayah -> {
                    val g = e.global
                    val bg = when (g) {
                        selected -> MaterialTheme.colorScheme.primaryContainer
                        marked -> MaterialTheme.colorScheme.secondaryContainer
                        else -> Color.Transparent
                    }
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp).clip(RoundedCornerShape(14.dp)).background(bg)
                            .clickable { onAyah(g) }.padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Text(d.text.ayahs[g] + " " + ayahEnd(d.meta.ref(g).aya), style = quranStyle(prefs, ink), modifier = Modifier.fillMaxWidth())
                        if (prefs.showTranslation) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Text(d.translation.lines[g], style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                            }
                        }
                        if (prefs.showTafsir) {
                            Text(d.tafsir.lines[g], style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                        }
                    }
                    HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
            }
        }
        item {
            Column(Modifier.padding(18.dp)) {
                if (prefs.showTranslation) NoteText("— " + str.translationSource)
                if (prefs.showTafsir) NoteText("— " + str.tafsirSource)
                NoteText(str.textSource)
            }
        }
    }
}

@Composable
private fun ReaderSettingsSheet(vm: MainViewModel, prefs: ReaderPrefs, onDismiss: () -> Unit) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    var size by remember(prefs.fontSize) { mutableFloatStateOf(prefs.fontSize.toFloat()) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().exposeTestTags().padding(horizontal = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(str.readerSettings, style = MaterialTheme.typography.titleLarge)
            Text(str.viewMode, style = MaterialTheme.typography.labelLarge)
            Segmented(listOf(str.viewPages, str.viewAyahs), if (prefs.mode == ReaderMode.PAGES) 0 else 1) { i ->
                vm.updateQuran { it.copy(reader = it.reader.copy(mode = if (i == 0) ReaderMode.PAGES else ReaderMode.AYAHS)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(str.textSize, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text(fmt.num(size.roundToInt()), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = size, onValueChange = { size = it },
                onValueChangeFinished = { vm.updateQuran { it.copy(reader = it.reader.copy(fontSize = size.roundToInt())) } },
                valueRange = ReaderPrefs.MIN_FONT.toFloat()..ReaderPrefs.MAX_FONT.toFloat(),
            )
            Text("بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ", style = quranStyle(prefs.copy(fontSize = size.roundToInt()), MaterialTheme.colorScheme.onSurface).copy(textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth())
            SwitchRow(prefs.showTranslation, str.showTranslation) { v -> vm.updateQuran { it.copy(reader = it.reader.copy(showTranslation = v)) } }
            SwitchRow(prefs.showTafsir, str.showTafsir) { v -> vm.updateQuran { it.copy(reader = it.reader.copy(showTafsir = v)) } }
            NoteText(str.ayahModeNote)
            Spacer(Modifier.height(16.dp))
        }
    }
}
