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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakina.app.core.prayer.PrayerKey
import com.sakina.app.core.prayer.PrayerLog
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
