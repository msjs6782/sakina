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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.sakina.app.core.quran.RefTarget
import com.sakina.app.core.quran.SearchHit
import com.sakina.app.core.quran.SearchResult
import com.sakina.app.data.QuranData
import com.sakina.app.ui.LocalFmt
import com.sakina.app.ui.LocalStrings
import com.sakina.app.ui.MainViewModel
import com.sakina.app.ui.Route
import com.sakina.app.ui.components.NoteText
import com.sakina.app.ui.components.SakinaIcons
import com.sakina.app.ui.components.ScreenBar
import com.sakina.app.ui.location.FullScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun QuranSearchScreen(vm: MainViewModel, initial: String = "") {
    val str = LocalStrings.current
    FullScreen {
        Column(Modifier.fillMaxSize()) {
            ScreenBar(str.searchTitle, onBack = { vm.pop() })
            WithQuran(vm) { d -> SearchBody(vm, d, initial) }
        }
    }
}

/** Where a reference result leads: the first ayah to open. */
private fun target(d: QuranData, t: RefTarget): Int = when (t) {
    is RefTarget.Ayah -> d.meta.global(t.ref)
    is RefTarget.SurahStart -> d.meta.surah(t.sura).start
    is RefTarget.Page -> d.meta.pageRange(t.page).first
    is RefTarget.Juz -> d.meta.juzStartGlobal(t.juz)
}

@Composable
private fun SearchBody(vm: MainViewModel, d: QuranData, initial: String) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    var query by rememberSaveable { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val result by produceState<SearchResult?>(null, query) {
        delay(250)
        value = withContext(Dispatchers.Default) { d.search.search(query) }
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text(str.quranSearchHint, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(SakinaIcons.Search, null) },
            trailingIcon = if (query.isNotEmpty()) ({ IconButton(onClick = { query = "" }) { Icon(SakinaIcons.Close, str.close) } }) else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(focus).testTag("quran_search_input"),
        )
        val r = result
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (r == null || query.isBlank()) {
                item { NoteText(str.searchNote) }
            } else {
                r.reference?.let { t ->
                    item {
                        val g = target(d, t)
                        val ref = d.meta.ref(g)
                        val label = when (t) {
                            is RefTarget.Page -> str.pageN(fmt.num(t.page))
                            is RefTarget.Juz -> str.juzN(fmt.num(t.juz))
                            else -> str.surahAyah(d.meta.surah(ref.sura).nameAr, fmt.num(ref.aya))
                        }
                        Surface(
                            onClick = { if (t is RefTarget.Page) vm.openPage(t.page) else vm.openQuran(g, highlight = t is RefTarget.Ayah) },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth().testTag("search_reference"),
                        ) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(SakinaIcons.Book, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                Spacer(Modifier.width(10.dp))
                                Text(str.openReference + ": " + label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                    }
                }
                // A reference such as "2:255" usually matches no text; the card above is the answer then.
                if (r.total > 0 || r.reference == null) item {
                    Text(
                        if (r.total == 0) str.searchNone else str.searchCount(fmt.num(r.total), r.total) + if (r.hits.size < r.total) " · " + str.searchShown(fmt.num(r.hits.size), fmt.num(r.total)) else "",
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(r.hits, key = { it.global }) { h -> HitRow(vm, d, h) }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

private fun snippet(text: String, range: IntRange, highlight: SpanStyle, window: Int = 70): AnnotatedString {
    val from = (range.first - window).coerceAtLeast(0).let { s -> if (s > 0) text.lastIndexOf(' ', s).let { if (it < 0) s else it + 1 } else 0 }
    val to = (range.last + 1 + window).coerceAtMost(text.length).let { e -> if (e < text.length) text.indexOf(' ', e).let { if (it < 0) e else it } else e }
    return buildAnnotatedString {
        if (from > 0) append("… ")
        append(text.substring(from, range.first.coerceAtLeast(from)))
        withStyle(highlight) { append(text.substring(range.first.coerceIn(from, to), (range.last + 1).coerceIn(from, to))) }
        append(text.substring((range.last + 1).coerceIn(from, to), to))
        if (to < text.length) append(" …")
    }
}

@Composable
private fun HitRow(vm: MainViewModel, d: QuranData, h: SearchHit) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val ref = d.meta.ref(h.global)
    val hl = SpanStyle(background = MaterialTheme.colorScheme.tertiaryContainer, fontWeight = FontWeight.SemiBold)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { vm.openQuran(h.global, highlight = true) }
            .padding(horizontal = 6.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(str.surahAyah(d.meta.surah(ref.sura).nameAr, fmt.num(ref.aya)), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            if (h.count > 1) Text("×" + fmt.num(h.count), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (h.inTranslation) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(snippet(d.translation.lines[h.global], h.first, hl), style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Text(snippet(d.search.plain[h.global], h.first, hl), style = MaterialTheme.typography.bodyLarge)
        }
    }
}
