@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sakina.app.ui.quran

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakina.app.core.quran.AyahRef
import com.sakina.app.core.quran.Khatma
import com.sakina.app.core.quran.Revelation
import com.sakina.app.data.QuranData
import com.sakina.app.data.QuranState
import com.sakina.app.ui.LocalFmt
import com.sakina.app.ui.LocalStrings
import com.sakina.app.ui.MainViewModel
import com.sakina.app.ui.Route
import com.sakina.app.ui.components.exposeTestTags
import com.sakina.app.ui.components.Divider
import com.sakina.app.ui.components.SakinaIcons
import com.sakina.app.ui.settings.Segmented
import com.sakina.app.ui.theme.Amiri
import com.sakina.app.ui.theme.AmiriQuran
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun QuranHome(vm: MainViewModel, contentPadding: PaddingValues) {
    val str = LocalStrings.current
    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(str.quranTitle, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.push(Route.QuranSources) }) { Icon(SakinaIcons.Info, str.sourcesTitle) }
        }
        WithQuran(vm) { d -> QuranHomeContent(vm, d) }
    }
}

@Composable
private fun QuranHomeContent(vm: MainViewModel, d: QuranData) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val qs by vm.quranState.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    var pageDialog by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Surface(
                    onClick = { vm.push(Route.QuranSearch()) },
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().testTag("quran_search_bar"),
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(SakinaIcons.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(10.dp))
                        Text(str.quranSearchHint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            qs.lastRead?.let { lr ->
                item { ContinueCard(vm, d, lr.sura, lr.aya, lr.page) }
            }
            item { KhatmaCard(vm, qs) }
            item {
                Segmented(listOf(str.tabSurahs, str.tabJuz, str.tabBookmarks), tab) { tab = it }
            }
            when (tab) {
                0 -> {
                    item {
                        TextButton(onClick = { pageDialog = true }) {
                            Icon(SakinaIcons.Book, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(str.goToPage)
                        }
                    }
                    items(d.meta.suras, key = { "s${it.index}" }) { s ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { vm.push(Route.QuranReader(s.start)) }
                                .padding(vertical = 8.dp, horizontal = 4.dp).testTag("surah_${s.index}"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            NumberStar(s.index)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(s.nameAr, style = MaterialTheme.typography.titleLarge.copy(fontFamily = Amiri, fontWeight = FontWeight.Bold))
                                Text(
                                    (if (s.revelation == Revelation.MECCAN) str.meccan else str.medinan) + " · " + str.ayahCount(fmt.num(s.ayahCount)) + " · " + s.nameTranslit,
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(fmt.num(d.meta.pageOf(s.start)), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                1 -> items((1..30).toList(), key = { "j$it" }) { j -> JuzRow(vm, d, j) }
                else -> {
                    if (qs.bookmarks.isEmpty()) item { Text(str.noBookmarks, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp)) }
                    items(qs.bookmarks.sortedByDescending { it.createdEpochMs }, key = { "b${it.sura}:${it.aya}" }) { b ->
                        val ref = AyahRef(b.sura, b.aya)
                        if (d.meta.isValid(ref)) {
                            val g = d.meta.global(ref)
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { vm.push(Route.QuranReader(g, highlight = true)) }.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(str.surahAyah(d.meta.surah(b.sura).nameAr, fmt.num(b.aya)) + " · " + str.pageN(fmt.num(d.meta.pageOf(g))), style = MaterialTheme.typography.titleSmall)
                                    Text(d.text.ayahs[g], style = MaterialTheme.typography.bodyLarge.copy(fontFamily = AmiriQuran, fontSize = 20.sp, lineHeight = 38.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                IconButton(onClick = {
                                    val removed = b
                                    vm.updateQuran { s -> s.copy(bookmarks = s.bookmarks.filterNot { it.sura == b.sura && it.aya == b.aya }) }
                                    scope.launch {
                                        val r = snackbar.showSnackbar(str.bookmarkRemoved, actionLabel = str.undo)
                                        if (r == SnackbarResult.ActionPerformed) vm.updateQuran { s -> s.copy(bookmarks = s.bookmarks + removed) }
                                    }
                                }) { Icon(SakinaIcons.Trash, str.removeBookmark, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
        SnackbarHost(snackbar)
    }
    if (pageDialog) PageDialog(d, onDismiss = { pageDialog = false }) { p -> pageDialog = false; vm.push(Route.QuranReader(d.meta.pageRange(p).first)) }
}

@Composable
private fun ContinueCard(vm: MainViewModel, d: QuranData, sura: Int, aya: Int, page: Int) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val ref = AyahRef(sura, aya)
    if (!d.meta.isValid(ref)) return
    Surface(
        onClick = { vm.push(Route.QuranReader(d.meta.global(ref))) },
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().testTag("quran_continue"),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(SakinaIcons.Book, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(str.continueReading, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(str.surahAyah(d.meta.surah(sura).nameAr, fmt.num(aya)), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(str.pageN(fmt.num(page)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
private fun KhatmaCard(vm: MainViewModel, qs: QuranState) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val plan = qs.khatma
    Surface(
        onClick = { vm.push(Route.Khatma) },
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().testTag("khatma_card"),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(str.khatmaTitle, style = MaterialTheme.typography.titleMedium)
            if (plan == null) {
                Text(str.khatmaNone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val st = Khatma.status(plan, LocalDate.now())
                LinearProgressIndicator(progress = { st.percent / 100f }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                Text(khatmaLine(str, fmt, st), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

fun khatmaLine(str: com.sakina.app.ui.Strings, fmt: com.sakina.app.core.format.Fmt, st: com.sakina.app.core.quran.KhatmaStatus): String = when {
    st.completed -> str.khatmaDone
    st.overdue -> str.khatmaOverdue(fmt.num(st.behind), st.behind)
    st.today != null && !st.today.isEmpty() -> str.khatmaToday(fmt.num(st.today.first), fmt.num(st.today.last)) +
        " · " + if (st.behind > 0) str.khatmaBehind(fmt.num(st.behind), st.behind) else if (st.ahead > 0) str.khatmaAhead(fmt.num(st.ahead), st.ahead) else str.khatmaOnTrack
    else -> str.khatmaTodayEmpty
}

@Composable
private fun JuzRow(vm: MainViewModel, d: QuranData, j: Int) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    var open by rememberSaveable { mutableStateOf(false) }
    val g = d.meta.juzStartGlobal(j)
    val r = d.meta.ref(g)
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { vm.push(Route.QuranReader(g)) }.padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NumberStar(j)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(str.juzN(fmt.num(j)), style = MaterialTheme.typography.titleMedium)
                Text(str.surahAyah(d.meta.surah(r.sura).nameAr, fmt.num(r.aya)) + " · " + str.pageN(fmt.num(d.meta.pageOf(g))),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { open = !open }) { Text(if (open) "−" else "+", style = MaterialTheme.typography.titleLarge) }
        }
        if (open) {
            for (q in (j - 1) * 8 + 1..j * 8) {
                val qg = d.meta.quarterStartGlobal(q)
                val qr = d.meta.ref(qg)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { vm.push(Route.QuranReader(qg)) }.padding(start = 56.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                ) {
                    Text("۞ " + str.quarterName(q) { fmt.num(it) }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(str.surahAyah(d.meta.surah(qr.sura).nameAr, fmt.num(qr.aya)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Divider()
        }
    }
}

@Composable
fun PageDialog(d: QuranData, onDismiss: () -> Unit, onGo: (Int) -> Unit) {
    val str = LocalStrings.current
    var text by remember { mutableStateOf("") }
    val p = com.sakina.app.core.quran.ArabicText.fold(text).toIntOrNull()?.takeIf { it in 1..d.meta.pageCount }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str.goToPage) },
        text = {
            OutlinedTextField(text, { text = it }, singleLine = true, label = { Text(str.pageNumberHint) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.exposeTestTags().testTag("page_input"))
        },
        confirmButton = { Button(onClick = { p?.let(onGo) }, enabled = p != null) { Text(str.go) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(str.cancel) } },
    )
}
