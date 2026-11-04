package com.sakina.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakina.app.core.prayer.PrayerKey
import com.sakina.app.core.prayer.PrayerLog
import com.sakina.app.core.prayer.PrayerStats
import com.sakina.app.core.prayer.Qada
import com.sakina.app.core.prayer.QadaState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.draw.clip
import com.sakina.app.data.AppClock
import com.sakina.app.data.AppSettings
import com.sakina.app.ui.LocalFmt
import com.sakina.app.ui.LocalStrings
import com.sakina.app.ui.MainViewModel
import com.sakina.app.ui.components.NoteText
import com.sakina.app.ui.components.ScreenBar
import com.sakina.app.ui.location.FullScreen
import com.sakina.app.ui.theme.Amiri
import java.time.ZoneId

/**
 * The prayer log: today, the days in a row, the week and the month, and the last two weeks as a grid (any mark can
 * be corrected). Calm on purpose: nothing is counted against the user.
 */
@Composable
fun PrayerLogScreen(vm: MainViewModel, s: AppSettings) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val tools by vm.tools.collectAsStateWithLifecycle()
    val log = tools.prayed
    val zone = s.activeLocation?.zoneId ?: ZoneId.systemDefault()
    val today = AppClock.now().atZone(zone).toLocalDate()
    FullScreen {
        Column(Modifier.fillMaxSize()) {
            ScreenBar(str.prayerLogTitle, onBack = { vm.pop() })
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp).testTag("prayer_log"),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(str.prayedToday(fmt.num(PrayerLog.count(log, today))),
                            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Amiri, fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.testTag("prayer_log_today"))
                        Text(str.prayerStreak(fmt.num(PrayerLog.streak(log, today))), color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodyMedium)
                        Text(str.prayerWeekPercent(fmt.num(PrayerLog.percent(log, today.minusDays(6), today))), color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodyMedium)
                        Text(str.prayerMonthPercent(fmt.num(PrayerLog.percent(log, today.minusDays(29), today))), color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (log.isEmpty()) NoteText(str.prayerLogEmpty)
                MonthStats(log, today)
                QadaCard(vm, tools.qada)
                Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 10.dp, vertical = 12.dp)) {
                        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.width(96.dp))
                            PrayerKey.prayers.forEach { k ->
                                Text(str.prayer(k), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f))
                            }
                        }
                        for (i in 0 until 14) {
                            val d = today.minusDays(i.toLong())
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.width(96.dp)) {
                                    Text(if (i == 0) str.today else fmt.weekday(d.dayOfWeek), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                                    Text(fmt.gregorian(d, withYear = false), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                                PrayerKey.prayers.forEach { k ->
                                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                        PrayedMark(PrayerLog.isDone(log, d, k), str.markPrayed(str.prayer(k)) + " · " + fmt.gregorian(d, withYear = false), "log_${d}_${k.name.lowercase()}") {
                                            vm.updateTools { it.copy(prayed = PrayerLog.toggle(it.prayed, d, k, today)) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                NoteText(str.prayerLogNote)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/** The last thirty days as bars (gold for a full day), each prayer's share, and the best run of full days. */
@Composable
private fun MonthStats(log: Map<String, Int>, today: java.time.LocalDate) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val cs = MaterialTheme.colorScheme
    val ex = com.sakina.app.ui.theme.LocalExtras.current
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val days = androidx.compose.runtime.remember(log, today) { PrayerStats.daily(log, today.minusDays(29), today) }
    val per = androidx.compose.runtime.remember(log, today) { PrayerStats.perPrayer(log, today.minusDays(29), today) }
    Surface(shape = RoundedCornerShape(22.dp), color = cs.surfaceContainerLow, modifier = Modifier.fillMaxWidth().testTag("prayer_stats")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(str.prayerStatsTitle, style = MaterialTheme.typography.titleMedium.copy(fontFamily = Amiri, fontWeight = FontWeight.Bold), color = cs.primary)
            // Thirty bars, today at the reading end.
            androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(86.dp).testTag("prayer_stats_bars")) {
                val n = days.size
                val slot = size.width / n
                val bw = slot * 0.62f
                val r = androidx.compose.ui.geometry.CornerRadius(bw / 2)
                days.forEachIndexed { i, (_, c) ->
                    val x = if (rtl) size.width - (i + 1) * slot + (slot - bw) / 2 else i * slot + (slot - bw) / 2
                    drawRoundRect(cs.outlineVariant.copy(alpha = 0.35f), androidx.compose.ui.geometry.Offset(x, 0f), androidx.compose.ui.geometry.Size(bw, size.height), r)
                    if (c > 0) {
                        val h = size.height * c / 5f
                        drawRoundRect(if (c == 5) ex.gold else cs.primary, androidx.compose.ui.geometry.Offset(x, size.height - h), androidx.compose.ui.geometry.Size(bw, h), r)
                    }
                }
            }
            Text(str.prayerBars, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            PrayerKey.prayers.forEach { k ->
                val v = per[k] ?: 0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(str.prayer(k), style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(64.dp), maxLines = 1)
                    Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(50)).background(cs.outlineVariant.copy(alpha = 0.35f))) {
                        Box(Modifier.fillMaxWidth(v / 100f).fillMaxHeight().clip(RoundedCornerShape(50)).background(if (v == 100) ex.gold else cs.primary))
                    }
                    Text(if (str.isAr) fmt.num(v) + "٪" else "$v%", style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.End, modifier = Modifier.width(48.dp).testTag("prayer_share_${k.name.lowercase()}"))
                }
            }
            Text(str.prayerBestStreak(fmt.num(PrayerStats.bestStreak(log))), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("prayer_best_streak"))
        }
    }
}

/** Missed prayers to make up: what is owed of each, a tap for each one made up, and the progress. */
@Composable
private fun QadaCard(vm: MainViewModel, q: QadaState) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val cs = MaterialTheme.colorScheme
    var last by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<PrayerKey?>(null) }
    Surface(shape = RoundedCornerShape(22.dp), color = cs.surfaceContainerLow, modifier = Modifier.fillMaxWidth().testTag("qada_card")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(str.qadaTitle, style = MaterialTheme.typography.titleMedium.copy(fontFamily = Amiri, fontWeight = FontWeight.Bold), color = cs.primary, modifier = Modifier.weight(1f))
                last?.let { k ->
                    androidx.compose.material3.TextButton(onClick = { vm.updateTools { it.copy(qada = Qada.undoMakeUp(it.qada, k)) }; last = null }, modifier = Modifier.testTag("qada_undo")) { Text(str.undo) }
                }
            }
            if (Qada.totalOwed(q) == 0) NoteText(str.qadaEmpty)
            else {
                Text(str.qadaSummary(fmt.num(Qada.totalRemaining(q)), fmt.num(Qada.totalDone(q)), fmt.num(Qada.percent(q))), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("qada_summary"))
                androidx.compose.material3.LinearProgressIndicator(progress = { Qada.percent(q) / 100f }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)))
            }
            PrayerKey.prayers.forEach { k ->
                val rem = Qada.remaining(q, k)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(str.prayer(k), style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(64.dp), maxLines = 1)
                    Text(fmt.num(rem), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = if (rem > 0) cs.onSurface else cs.onSurfaceVariant,
                        modifier = Modifier.weight(1f).testTag("qada_left_${k.name.lowercase()}"))
                    androidx.compose.material3.IconButton(onClick = { vm.updateTools { it.copy(qada = Qada.removeOwed(it.qada, k)) } }, modifier = Modifier.testTag("qada_minus_${k.name.lowercase()}")) { Text("−", style = MaterialTheme.typography.titleLarge) }
                    androidx.compose.material3.IconButton(onClick = { vm.updateTools { it.copy(qada = Qada.addOwed(it.qada, k)) } }, modifier = Modifier.testTag("qada_plus_${k.name.lowercase()}")) { Text("+", style = MaterialTheme.typography.titleLarge) }
                    androidx.compose.material3.FilledTonalButton(
                        onClick = { vm.updateTools { it.copy(qada = Qada.makeUp(it.qada, k)) }; last = k },
                        enabled = rem > 0,
                        modifier = Modifier.testTag("qada_done_${k.name.lowercase()}"),
                    ) { Text(str.qadaMadeUp) }
                }
            }
            androidx.compose.material3.OutlinedButton(onClick = { vm.updateTools { it.copy(qada = Qada.addDay(it.qada)) } }, modifier = Modifier.fillMaxWidth().testTag("qada_add_day")) { Text(str.qadaAddDay) }
            NoteText(str.qadaNote)
        }
    }
}

