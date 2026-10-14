@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sakina.app.ui.quran

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakina.app.core.quran.ArabicText
import com.sakina.app.core.quran.Khatma
import com.sakina.app.core.quran.KhatmaPlan
import com.sakina.app.data.QuranData
import com.sakina.app.ui.LocalFmt
import com.sakina.app.ui.LocalStrings
import com.sakina.app.ui.MainViewModel
import com.sakina.app.ui.Route
import com.sakina.app.ui.alerts.MinutesControl
import com.sakina.app.ui.components.Banner
import com.sakina.app.ui.components.NoteText
import com.sakina.app.ui.components.SakinaIcons
import com.sakina.app.ui.components.ScreenBar
import com.sakina.app.ui.components.SectionCard
import com.sakina.app.ui.components.exposeTestTags
import com.sakina.app.ui.location.FullScreen
import java.time.LocalDate

@Composable
fun KhatmaScreen(vm: MainViewModel) {
    val str = LocalStrings.current
    FullScreen {
        Column(Modifier.fillMaxSize()) {
            ScreenBar(str.khatmaTitle, onBack = { vm.pop() })
            WithQuran(vm) { d -> KhatmaBody(vm, d) }
        }
    }
}

@Composable
private fun KhatmaBody(vm: MainViewModel, d: QuranData) {
    val qs by vm.quranState.collectAsStateWithLifecycle()
    val plan = qs.khatma
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (plan == null) NewPlan(vm) else ActivePlan(vm, d, plan)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun NewPlan(vm: MainViewModel) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    var days by remember { mutableIntStateOf(30) }
    SectionCard(title = str.khatmaTitle, icon = SakinaIcons.Book) {
        Text(str.khatmaNone, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7, 15, 30, 60, 90).forEach { n ->
                FilterChip(selected = days == n, onClick = { days = n }, label = { Text(fmt.num(n)) })
            }
        }
        MinutesControl(str.khatmaDays, days, Khatma.MIN_DAYS, Khatma.MAX_DAYS, { days = it }, valueText = fmt.num(days))
        val perDay = Khatma.PAGES.toDouble() / days
        Text(str.khatmaPerDay(fmt.digits(if (perDay >= 10) perDay.toInt().toString() else String.format(java.util.Locale.US, "%.1f", perDay))), style = MaterialTheme.typography.bodyMedium)
        Text(str.khatmaFrom + " · " + str.khatmaEnds(fmt.gregorian(LocalDate.now().plusDays(days - 1L), withYear = true, withWeekday = true)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        Button(onClick = { vm.updateQuran { it.copy(khatma = Khatma.create(LocalDate.now(), days)) } }, modifier = Modifier.fillMaxWidth().testTag("khatma_start")) {
            Text(str.khatmaStart)
        }
        Spacer(Modifier.height(6.dp))
        NoteText(str.khatmaManual)
    }
}

@Composable
private fun ActivePlan(vm: MainViewModel, d: QuranData, plan: KhatmaPlan) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val today = LocalDate.now()
    val st = Khatma.status(plan, today)
    var recordDialog by remember { mutableStateOf(false) }
    var resetDialog by remember { mutableStateOf(false) }
    SectionCard(title = str.khatmaTitle, icon = SakinaIcons.Book) {
        Text(
            str.khatmaSpan(fmt.gregorian(plan.start, withYear = false), fmt.gregorian(plan.end, withYear = true), str.daysCount(fmt.num(plan.days), plan.days.toLong())),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LinearProgressIndicator(progress = { st.percent / 100f }, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp))
        Text(
            if (plan.progressPage > 0) str.khatmaProgress(fmt.num(plan.progressPage), fmt.num(st.percent)) else str.khatmaNoProgress(fmt.num(st.percent)),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(6.dp))
        when {
            st.completed -> Banner(str.khatmaDone, SakinaIcons.Check)
            st.dayIndex < 0 -> Banner(str.khatmaNotStarted(fmt.gregorian(plan.start, withYear = true, withWeekday = true)))
            st.overdue -> Banner(str.khatmaOverdue(fmt.num(st.behind), st.behind), SakinaIcons.Warning, warning = true)
            else -> {
                st.today?.takeIf { !it.isEmpty() }?.let { r ->
                    Text(str.khatmaToday(fmt.num(r.first), fmt.num(r.last)), style = MaterialTheme.typography.bodyLarge)
                    if (st.remainingToday > 0) Text(str.khatmaRemainingToday(fmt.num(st.remainingToday), st.remainingToday), style = MaterialTheme.typography.bodyMedium)
                } ?: Text(str.khatmaTodayEmpty, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(4.dp))
                when {
                    st.behind > 0 -> Banner(str.khatmaBehind(fmt.num(st.behind), st.behind), SakinaIcons.Warning, warning = true)
                    st.ahead > 0 -> Text(str.khatmaAhead(fmt.num(st.ahead), st.ahead), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    else -> Text(str.khatmaOnTrack, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (!st.completed) {
            val readFrom = (plan.progressPage + 1).coerceAtMost(Khatma.PAGES)
            Button(onClick = { vm.openPage(readFrom) }, modifier = Modifier.fillMaxWidth()) { Text(str.khatmaReadToday) }
            if (st.today != null && !st.today.isEmpty() && st.remainingToday > 0) {
                OutlinedButton(onClick = { vm.updateQuran { s -> s.copy(khatma = s.khatma?.let { Khatma.finishToday(it, LocalDate.now()) }) } },
                    modifier = Modifier.fillMaxWidth().testTag("khatma_finish_today")) { Text(str.khatmaFinishToday) }
            }
            OutlinedButton(onClick = { recordDialog = true }, modifier = Modifier.fillMaxWidth().testTag("khatma_record")) { Text(str.khatmaRecord) }
        }
        TextButton(onClick = { resetDialog = true }) { Text(str.khatmaReset, color = MaterialTheme.colorScheme.error) }
        NoteText(str.khatmaManual)
    }
    SectionCard(title = str.khatmaSchedule, icon = SakinaIcons.Calendar) {
        for (i in 0 until plan.days) {
            val r = Khatma.portion(plan, i)
            if (r.isEmpty()) continue
            val date = Khatma.dateOf(plan, i)
            val done = plan.progressPage >= r.last
            val isToday = i == st.dayIndex
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(if (isToday) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .clickable { vm.openPage(r.first) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                Text(fmt.gregorian(date, withYear = false, withWeekday = true), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal), modifier = Modifier.weight(1f))
                Text(str.khatmaRange(fmt.num(r.first), fmt.num(r.last)) + if (done) "  ✓" else "", style = MaterialTheme.typography.bodyMedium,
                    color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
    if (recordDialog) {
        var text by remember { mutableStateOf(plan.progressPage.takeIf { it > 0 }?.toString() ?: "") }
        val p = ArabicText.fold(text).toIntOrNull()?.takeIf { it in 0..Khatma.PAGES }
        AlertDialog(
            onDismissRequest = { recordDialog = false },
            title = { Text(str.khatmaRecord) },
            text = {
                OutlinedTextField(text, { text = it }, singleLine = true, label = { Text(str.pageNumberHint) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.exposeTestTags().testTag("khatma_page_input"))
            },
            confirmButton = {
                Button(onClick = {
                    p?.let { page -> vm.updateQuran { s -> s.copy(khatma = s.khatma?.let { Khatma.record(it, page, LocalDate.now()) }) } }
                    recordDialog = false
                }, enabled = p != null) { Text(str.save) }
            },
            dismissButton = { TextButton(onClick = { recordDialog = false }) { Text(str.cancel) } },
        )
    }
    if (resetDialog) {
        AlertDialog(
            onDismissRequest = { resetDialog = false },
            title = { Text(str.khatmaReset) },
            text = { Text(str.khatmaResetQ) },
            confirmButton = { TextButton(onClick = { vm.updateQuran { it.copy(khatma = null) }; resetDialog = false }) { Text(str.delete, color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { resetDialog = false }) { Text(str.cancel) } },
        )
    }
}
