@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sakina.app.ui.mushaf

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sakina.app.core.mushaf.MushafManifest
import com.sakina.app.core.quran.ArabicText
import com.sakina.app.data.QuranData
import com.sakina.app.ui.LocalFmt
import com.sakina.app.ui.LocalStrings
import com.sakina.app.ui.components.exposeTestTags
import com.sakina.app.ui.quran.NumberStar
import com.sakina.app.ui.settings.Segmented
import com.sakina.app.ui.theme.Amiri

/** Surahs, juz, hizbs and pages of this edition, with a field to jump to a page. */
@Composable
fun MushafIndexSheet(d: QuranData, available: Set<Int>, current: Int, onDismiss: () -> Unit, onGo: (Int) -> Unit) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.88f).exposeTestTags().padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(str.mushafIndex, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                var text by remember { mutableStateOf("") }
                val p = ArabicText.fold(text).toIntOrNull()?.takeIf { it in 1..MushafManifest.PAGES }
                OutlinedTextField(
                    text, { text = it }, singleLine = true, label = { Text(str.goToPage) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { p?.let(onGo) }),
                    modifier = Modifier.width(150.dp).testTag("mushaf_page_input"),
                )
                TextButton(onClick = { p?.let(onGo) }, enabled = p != null, modifier = Modifier.testTag("mushaf_page_go")) { Text(str.go) }
            }
            Spacer(Modifier.padding(4.dp))
            Segmented(listOf(str.tabSurahs, str.tabJuz, str.tabHizb, str.tabPages), tab) { tab = it }
            Spacer(Modifier.padding(4.dp))
            when (tab) {
                0 -> LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(d.meta.suras, key = { it.index }) { s ->
                        val p = d.meta.pageOf(s.start)
                        IndexRow(onClick = { onGo(p) }, tag = "idx_surah_${s.index}") {
                            NumberStar(s.index, 34.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(s.nameAr, style = MaterialTheme.typography.titleMedium.copy(fontFamily = Amiri, fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                            PageNo(fmt.num(p), p in available)
                        }
                    }
                }
                1 -> LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items((1..30).toList()) { j ->
                        val g = d.meta.juzStartGlobal(j)
                        val p = d.meta.pageOf(g)
                        val r = d.meta.ref(g)
                        IndexRow(onClick = { onGo(p) }, tag = "idx_juz_$j") {
                            NumberStar(j, 34.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(str.juzN(fmt.num(j)), style = MaterialTheme.typography.titleMedium)
                                Text(str.surahAyah(d.meta.surah(r.sura).nameAr, fmt.num(r.aya)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            PageNo(fmt.num(p), p in available)
                        }
                    }
                }
                2 -> LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                    items((1..60).toList()) { h ->
                        val g = d.meta.quarterStartGlobal((h - 1) * 4 + 1)
                        val p = d.meta.pageOf(g)
                        val r = d.meta.ref(g)
                        IndexRow(onClick = { onGo(p) }, tag = "idx_hizb_$h") {
                            NumberStar(h, 34.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(str.hizbN(fmt.num(h)), style = MaterialTheme.typography.titleMedium)
                                Text(str.surahAyah(d.meta.surah(r.sura).nameAr, fmt.num(r.aya)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            PageNo(fmt.num(p), p in available)
                        }
                    }
                }
                else -> {
                    val grid = rememberLazyGridState(initialFirstVisibleItemIndex = (current - 1 - 10).coerceAtLeast(0))
                    LazyVerticalGrid(GridCells.Adaptive(56.dp), state = grid, contentPadding = PaddingValues(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items((1..MushafManifest.PAGES).toList()) { p ->
                            val here = p in available
                            Box(
                                Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp))
                                    .background(
                                        when {
                                            p == current -> MaterialTheme.colorScheme.primary
                                            here -> MaterialTheme.colorScheme.primaryContainer
                                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                                        },
                                    )
                                    .clickable { onGo(p) }.testTag("idx_page_$p"),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    fmt.num(p), style = MaterialTheme.typography.labelLarge,
                                    color = when {
                                        p == current -> MaterialTheme.colorScheme.onPrimary
                                        here -> MaterialTheme.colorScheme.onPrimaryContainer
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IndexRow(onClick: () -> Unit, tag: String, content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 6.dp, horizontal = 4.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** The page number, with a dot when the page is on the device. */
@Composable
private fun PageNo(n: String, here: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (here) Box(Modifier.padding(end = 6.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.primary).padding(3.dp))
        else Box(Modifier.padding(end = 6.dp).background(Color.Transparent).padding(3.dp))
        Text(n, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
