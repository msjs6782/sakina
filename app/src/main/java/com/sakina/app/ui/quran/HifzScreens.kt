@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sakina.app.ui.quran

import android.os.Bundle
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.session.SessionCommand
import com.sakina.app.audio.RecitationService
import com.sakina.app.core.quran.AyahRef
import com.sakina.app.core.quran.Hifz
import com.sakina.app.core.quran.HifzItem
import com.sakina.app.core.quran.HifzMask
import com.sakina.app.core.recitation.RepeatCount
import com.sakina.app.core.recitation.TimingCheck
import com.sakina.app.data.AppClock
import com.sakina.app.data.QuranData
import com.sakina.app.data.TimingState
import com.sakina.app.ui.LocalFmt
import com.sakina.app.ui.LocalStrings
import com.sakina.app.ui.MainViewModel
import com.sakina.app.ui.Route
import com.sakina.app.ui.components.NoteText
import com.sakina.app.ui.components.SakinaIcons
import com.sakina.app.ui.components.ScreenBar
import com.sakina.app.ui.components.SectionCard
import com.sakina.app.ui.listen.FollowBar
import com.sakina.app.ui.listen.rememberFollow
import com.sakina.app.ui.listen.rememberRecitationController
import com.sakina.app.ui.listen.startRecitation
import com.sakina.app.ui.location.FullScreen
import com.sakina.app.ui.settings.Segmented
import com.sakina.app.ui.theme.Amiri
import com.sakina.app.ui.theme.AmiriQuran
import java.time.ZoneId

private fun todayEpoch(): Long = AppClock.now().atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

/** The memorisation home: what is due today, every passage with its next review, and a new passage. */
@Composable
fun HifzScreen(vm: MainViewModel) {
    val str = LocalStrings.current
    FullScreen {
        Column(Modifier.fillMaxSize()) {
            ScreenBar(str.hifzTitle, onBack = { vm.pop() })
            WithQuran(vm) { d -> HifzHome(vm, d) }
        }
    }
}

@Composable
private fun HifzHome(vm: MainViewModel, d: QuranData) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val qs by vm.quranState.collectAsStateWithLifecycle()
    val today = todayEpoch()
    val due = Hifz.due(qs.hifz, today)
    var ask by remember { mutableStateOf<HifzItem?>(null) }
    LazyColumn(Modifier.fillMaxSize().testTag("hifz_home"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Text(str.hifzTitle, style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Amiri, fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(str.hifzSummary(fmt.num(due.size), fmt.num(Hifz.ayahCount(qs.hifz))), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.testTag("hifz_summary"))
                }
            }
        }
        item { NewPassage(vm, d) }
        item { Text(str.hifzDue, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp, top = 6.dp)) }
        if (due.isEmpty()) item { NoteText(if (qs.hifz.isEmpty()) str.hifzNone else str.hifzNoneDue, Modifier.padding(horizontal = 4.dp)) }
        items(due, key = { "d" + it.id }) { PassageRow(d, it, today, onOpen = { vm.push(Route.HifzPractice(it.sura, it.from, it.to)) }, onDelete = null) }
        if (qs.hifz.isNotEmpty()) {
            item { Text(str.hifzAll, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp, top = 10.dp)) }
            items(qs.hifz.sortedWith(compareBy({ it.sura }, { it.from })), key = { "a" + it.id }) {
                PassageRow(d, it, today, onOpen = { vm.push(Route.HifzPractice(it.sura, it.from, it.to)) }, onDelete = { ask = it })
            }
        }
    }
    ask?.let { item ->
        AlertDialog(
            onDismissRequest = { ask = null },
            text = { Text(str.hifzDelete + " · " + str.hifzRange(d.meta.surah(item.sura).nameAr, fmt.num(item.from), fmt.num(item.to))) },
            confirmButton = { TextButton(onClick = { vm.updateQuran { it.copy(hifz = Hifz.remove(it.hifz, item.id)) }; ask = null }) { Text(str.delete, color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { ask = null }) { Text(str.cancel) } },
        )
    }
}

@Composable
private fun PassageRow(d: QuranData, item: HifzItem, today: Long, onOpen: () -> Unit, onDelete: (() -> Unit)?) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val days = (item.dueEpochDay - today).toInt()
    Surface(onClick = onOpen, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().testTag("hifz_item_${item.id}")) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            NumberStar(item.sura, 34.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(str.hifzRange(d.meta.surah(item.sura).nameAr, fmt.num(item.from), fmt.num(item.to)), style = MaterialTheme.typography.titleMedium.copy(fontFamily = Amiri, fontWeight = FontWeight.Bold))
                Text(
                    str.hifzLevel(fmt.num(item.level + 1)) + " · " + str.hifzNext(if (days <= 0) str.hifzToday else str.hifzInDays(str.daysCount(fmt.num(days), days.toLong()))),
                    style = MaterialTheme.typography.bodySmall, color = if (days <= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onDelete != null) IconButton(onClick = onDelete) { Icon(SakinaIcons.Trash, str.hifzDelete, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** Pick a surah and a passage, then start. */
@Composable
private fun NewPassage(vm: MainViewModel, d: QuranData) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    var sura by remember { mutableIntStateOf(1) }
    var from by remember { mutableIntStateOf(1) }
    var to by remember { mutableIntStateOf(d.meta.surah(1).ayahCount) }
    var pick by remember { mutableStateOf(false) }
    val count = d.meta.surah(sura).ayahCount
    SectionCard(title = str.hifzNew, icon = SakinaIcons.Book, modifier = Modifier.testTag("hifz_new")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(str.hifzSurah, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { pick = true }, modifier = Modifier.testTag("hifz_pick_surah")) { Text(fmt.num(sura) + ". " + d.meta.surah(sura).nameAr) }
        }
        Stepper(str.hifzFrom, from, 1, count, "hifz_from") { from = it; if (to < it) to = it }
        Stepper(str.hifzTo, to, from, count, "hifz_to") { to = it }
        Button(onClick = { vm.push(Route.HifzPractice(sura, from, to)) }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).testTag("hifz_start")) { Text(str.hifzStart) }
    }
    if (pick) {
        AlertDialog(
            onDismissRequest = { pick = false },
            title = { Text(str.hifzSurah) },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(d.meta.suras, key = { it.index }) { s ->
                        Text(
                            fmt.num(s.index) + ". " + s.nameAr, style = MaterialTheme.typography.titleMedium.copy(fontFamily = Amiri),
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable {
                                sura = s.index; from = 1; to = minOf(s.ayahCount, 10); pick = false
                            }.padding(vertical = 8.dp, horizontal = 6.dp).testTag("hifz_surah_${s.index}"),
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pick = false }) { Text(str.cancel) } },
        )
    }
}

@Composable
private fun Stepper(label: String, value: Int, min: Int, max: Int, tag: String, onChange: (Int) -> Unit) {
    val fmt = LocalFmt.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange((value - 1).coerceAtLeast(min)) }, enabled = value > min, modifier = Modifier.testTag("${tag}_minus")) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Text(fmt.num(value), style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(48.dp).testTag("${tag}_value"), textAlign = TextAlign.Center)
        IconButton(onClick = { onChange((value + 1).coerceAtMost(max)) }, enabled = value < max, modifier = Modifier.testTag("${tag}_plus")) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}

/**
 * One memorisation session: the passage with words hidden as chosen (tap an ayah to show or hide it), the audio
 * repeated over the passage, and the review at the end.
 */
@Composable
fun HifzPracticeScreen(vm: MainViewModel, sura: Int, from: Int, to: Int) {
    val str = LocalStrings.current
    FullScreen {
        WithQuran(vm) { d ->
            val count = d.meta.surah(sura).ayahCount
            val a = from.coerceIn(1, count)
            val b = to.coerceIn(a, count)
            Column(Modifier.fillMaxSize()) {
                val fmt = LocalFmt.current
                ScreenBar(str.hifzRange(d.meta.surah(sura).nameAr, fmt.num(a), fmt.num(b)), onBack = { vm.pop() })
                HifzPractice(vm, d, sura, a, b)
            }
        }
    }
}

@Composable
private fun HifzPractice(vm: MainViewModel, d: QuranData, sura: Int, from: Int, to: Int) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val context = LocalContext.current
    val qs by vm.quranState.collectAsStateWithLifecycle()
    val mask = qs.hifzMask
    var revealed by remember(mask) { mutableStateOf(emptySet<Int>()) }
    val controller = rememberRecitationController()
    val follow = rememberFollow(vm, controller)
    val recited = follow.global(d)
    var wantRepeat by remember { mutableStateOf(false) }
    // The repeat starts once this surah is loaded and its timing map fits the recording.
    LaunchedEffect(wantRepeat, follow.recording, follow.durationMs, follow.timing) {
        if (!wantRepeat) return@LaunchedEffect
        val c = controller ?: return@LaunchedEffect
        val ready = (follow.timing as? TimingState.Ready)?.check is TimingCheck.Valid
        if (follow.recording?.sura == sura && follow.durationMs > 0 && ready) {
            c.sendCustomCommand(
                SessionCommand(RecitationService.ACTION_REPEAT, Bundle.EMPTY),
                Bundle().apply {
                    putInt(RecitationService.ARG_FROM, from); putInt(RecitationService.ARG_TO, to)
                    putInt(RecitationService.ARG_COUNT, qs.listen.repeatTimes)
                },
            )
            wantRepeat = false
        }
    }
    val existing = qs.hifz.firstOrNull { it.sura == sura && it.from == from && it.to == to }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth().testTag("hifz_practice"), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(str.hifzShow, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Segmented(listOf(str.hifzMaskNone, str.hifzMaskFirst, str.hifzMaskAll), mask.ordinal) { i ->
                        vm.updateQuran { it.copy(hifzMask = HifzMask.entries[i]) }
                    }
                    if (mask != HifzMask.NONE) NoteText(str.hifzTapReveal)
                }
            }
            items((from..to).toList(), key = { it }) { aya ->
                val g = d.meta.global(AyahRef(sura, aya))
                val text = d.text.ayahs[g]
                val open = aya in revealed
                val hidden = if (open) emptyList() else Hifz.hidden(text, mask)
                val blank = MaterialTheme.colorScheme.surfaceContainerHighest
                val annotated = remember(text, hidden, blank) {
                    buildAnnotatedString {
                        append(text)
                        for (r in hidden) addStyle(SpanStyle(color = Color.Transparent, background = blank), r.first, r.last + 1)
                        append(" ")
                        append(ayahEnd(aya))
                    }
                }
                val bg = if (g == recited) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bg)
                        .clickable { revealed = if (open) revealed - aya else revealed + aya }
                        .padding(horizontal = 14.dp, vertical = 10.dp).testTag("hifz_ayah_$aya"),
                ) {
                    Text(
                        annotated,
                        style = MaterialTheme.typography.bodyLarge.copy(fontFamily = AmiriQuran, fontSize = 24.sp, lineHeight = 50.sp, textAlign = TextAlign.Justify),
                        modifier = Modifier.fillMaxWidth().testTag(if (hidden.isEmpty()) "hifz_shown_$aya" else "hifz_masked_$aya"),
                    )
                }
            }
            item {
                SectionCard(title = str.hifzListen, icon = SakinaIcons.Repeat, modifier = Modifier.padding(top = 6.dp).testTag("hifz_audio")) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RepeatCount.entries.forEach { c ->
                            FilterChip(selected = qs.listen.repeatTimes == c.times, onClick = { vm.updateQuran { it.copy(listen = it.listen.copy(repeatTimes = c.times)) } },
                                label = { Text(str.repeatTimesLabel(c.times)) }, modifier = Modifier.testTag("hifz_x${if (c.times < 0) "inf" else c.times}"))
                        }
                    }
                    Button(onClick = {
                        val c = controller ?: return@Button
                        if (follow.recording?.sura != sura) startRecitation(vm, c, d, str, sura, from)
                        wantRepeat = true
                    }, enabled = controller != null, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).testTag("hifz_play")) { Text(str.hifzPlayRange) }
                    val timingOk = (follow.timing as? TimingState.Ready)?.check is TimingCheck.Valid
                    if (!timingOk || follow.recording?.sura != sura) NoteText(str.hifzTimingNote, Modifier.padding(top = 4.dp))
                }
            }
        }
        if (controller != null && follow.recording != null) FollowBar(vm, d, controller, follow, following = true, onFollow = {})
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = {
                record(vm, sura, from, to, existing, remembered = false)
                Toast.makeText(context, if (existing == null) str.hifzAdded else str.hifzSaved, Toast.LENGTH_SHORT).show()
                vm.pop()
            }, modifier = Modifier.weight(1f).testTag("hifz_again")) { Text(str.hifzAgain) }
            Button(onClick = {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                record(vm, sura, from, to, existing, remembered = true)
                Toast.makeText(context, if (existing == null) str.hifzAdded else str.hifzSaved, Toast.LENGTH_SHORT).show()
                vm.pop()
            }, modifier = Modifier.weight(1f).testTag("hifz_done")) { Text(str.hifzRemembered) }
        }
    }
}

/** A new passage is added (due tomorrow); a known one records the review. */
private fun record(vm: MainViewModel, sura: Int, from: Int, to: Int, existing: HifzItem?, remembered: Boolean) {
    val today = todayEpoch()
    vm.updateQuran { s ->
        if (existing == null) s.copy(hifz = Hifz.add(s.hifz, sura, from, to, today))
        else s.copy(hifz = s.hifz.map { if (it.id == existing.id) Hifz.review(it, remembered, today) else it })
    }
}
