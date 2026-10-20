@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sakina.app.ui.times

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.sakina.app.core.calendar.UmmAlQura
import com.sakina.app.core.prayer.CalcMethod
import com.sakina.app.core.prayer.DayTimes
import com.sakina.app.core.prayer.PrayerKey
import com.sakina.app.core.prayer.PrayerSchedule
import com.sakina.app.core.prayer.TimeStatus
import com.sakina.app.data.AppSettings
import com.sakina.app.ui.LocalFmt
import com.sakina.app.ui.LocalStrings
import com.sakina.app.ui.MainViewModel
import com.sakina.app.ui.PlacePurpose
import com.sakina.app.ui.Route
import com.sakina.app.ui.components.Banner
import com.sakina.app.ui.components.DirectionalIcon
import com.sakina.app.ui.components.NoteText
import com.sakina.app.ui.components.SakinaIcons
import com.sakina.app.ui.components.SectionCard
import com.sakina.app.ui.home.TimeRow
import com.sakina.app.ui.rememberNow
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

enum class TimesView { DAY, WEEK, MONTH }

@Composable
fun TimesRouteScreen(vm: MainViewModel, s: AppSettings, initialView: TimesView) {
    com.sakina.app.ui.location.FullScreen {
        Column(Modifier.fillMaxSize()) {
            com.sakina.app.ui.components.ScreenBar(LocalStrings.current.tabTimes, onBack = { vm.pop() })
            TimesScreen(vm, s, PaddingValues(0.dp), initialView, withTitle = false)
        }
    }
}

@Composable
fun TimesScreen(vm: MainViewModel, s: AppSettings, contentPadding: PaddingValues, initialView: TimesView = TimesView.DAY, withTitle: Boolean = true) {
    val str = LocalStrings.current
    val loc = s.activeLocation
    val zone = loc?.zoneId
    val now by rememberNow(30_000)
    var view by rememberSaveable { mutableStateOf(initialView) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (withTitle) Text(str.tabTimes, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp))
        if (loc == null || zone == null) {
            Banner(str.chooseLocationBody, SakinaIcons.Pin)
            TextButton(onClick = { vm.push(Route.CityPicker(PlacePurpose.ADD)) }) { Text(str.pickCity) }
        } else {
            Text(
                loc.name(str.lang) + " · " + LocalFmt.current.utcOffset(zone, now) + " · " + str.methodName(s.method),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val views = listOf(TimesView.DAY to str.day, TimesView.WEEK to str.week, TimesView.MONTH to str.month30)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                views.forEachIndexed { i, (v, label) ->
                    SegmentedButton(
                        selected = view == v,
                        onClick = { view = v },
                        shape = SegmentedButtonDefaults.itemShape(i, views.size),
                    ) { Text(label, maxLines = 1) }
                }
            }
            val schedule = remember(loc, s.calcConfig) { PrayerSchedule(loc.point, zone, s.calcConfig) }
            val today = schedule.localDate(now)
            when (view) {
                TimesView.DAY -> DayView(schedule, today, s, zone, now)
                TimesView.WEEK -> TableView(schedule, today, 7, s, zone)
                TimesView.MONTH -> TableView(schedule, today, 30, s, zone)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DayView(schedule: PrayerSchedule, today: LocalDate, s: AppSettings, zone: ZoneId, now: java.time.Instant) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    var date by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    val d = LocalDate.ofEpochDay(date)
    val day = schedule.day(d)
    val hijri = UmmAlQura.displayed(d, s.hijriOffset)
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { date -= 1 }) { DirectionalIcon(SakinaIcons.ChevronStart, str.prevDay) }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(fmt.gregorian(d, withYear = true, withWeekday = true), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            if (hijri != null) Text(fmt.hijri(hijri), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = { date += 1 }) { DirectionalIcon(SakinaIcons.ChevronEnd, str.nextDay) }
    }
    if (d != today) {
        TextButton(onClick = { date = today.toEpochDay() }, modifier = Modifier.fillMaxWidth()) { Text(str.today) }
    }
    if (day.issues.isNotEmpty()) {
        Banner(day.issues.joinToString("\n") { str.issue(it) }, SakinaIcons.Warning, warning = true) {
            Spacer(Modifier.height(4.dp))
            Text(str.checkLocalCalendar, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
    SectionCard(padding = PaddingValues(8.dp)) {
        val nextKey = if (d == today) schedule.next(now)?.takeIf { it.date == today }?.key else null
        day.times.forEach { t ->
            val passed = d.isBefore(today) || (d == today && t.instant != null && !t.instant.isAfter(now))
            TimeRow(t.key, t.instant, t.status, zone, t.key == nextKey, passed, s.calcConfig.offsets[t.key])
        }
    }
    if (day.times.any { it.status == TimeStatus.HIGH_LATITUDE_RULE }) {
        Banner(str.highLatExplain, SakinaIcons.Info)
    }
    CalcDetails(day, s)
}

@Composable
private fun CalcDetails(day: DayTimes, s: AppSettings) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    SectionCard(title = str.calcDetails, icon = SakinaIcons.Info) {
        Detail(str.method, str.methodName(s.method) + " — " + str.methodDetail(s.method))
        Detail(str.asrTitle, str.asrName(s.asr))
        Detail(str.highLatTitle, str.highLatName(s.highLatitude))
        if (s.method == CalcMethod.UMM_AL_QURA) {
            day.ishaIntervalMinutes?.let { Detail(str.uqIshaTitle, str.ishaInterval(fmt.num(it), day.ramadan == true && it == 120)) }
        }
        val offsets = s.calcConfig.offsets
        if (!offsets.isZero) {
            Detail(str.offsetsApplied, PrayerKey.entries.filter { offsets[it] != 0 }.joinToString("، ") { str.prayer(it) + " " + fmt.signed(offsets[it]) })
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun TableView(schedule: PrayerSchedule, today: LocalDate, days: Int, s: AppSettings, zone: ZoneId) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val rows = remember(schedule, today, days) { schedule.range(today, days) }
    SectionCard(padding = PaddingValues(vertical = 8.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val density = LocalDensity.current
            val dateCol = 62.dp
            val minCol = with(density) { 44.dp * fontScale.coerceIn(1f, 1.4f) }
            val col: Dp = max(minCol, (maxWidth - dateCol - 8.dp) / 6)
            val totalWidth = dateCol + col * 6 + 8.dp
            val scroll = rememberScrollState()
            val scrollable = totalWidth > maxWidth
            Column(Modifier.horizontalScroll(scroll, enabled = scrollable).width(max(totalWidth, maxWidth))) {
                if (scrollable) {
                    Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(SakinaIcons.Info, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(Modifier.width(6.dp))
                        NoteText(str.tableScrollHint)
                    }
                }
                Row(Modifier.padding(horizontal = 4.dp, vertical = 6.dp)) {
                    Text("", modifier = Modifier.width(dateCol))
                    PrayerKey.entries.forEach {
                        Text(str.prayer(it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.width(col))
                    }
                }
                rows.forEach { day ->
                    val isToday = day.date == today
                    val friday = day.date.dayOfWeek == DayOfWeek.FRIDAY
                    val bg = when {
                        isToday -> MaterialTheme.colorScheme.primaryContainer
                        friday -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                        else -> Color.Transparent
                    }
                    Row(
                        Modifier.padding(horizontal = 4.dp).clip(RoundedCornerShape(10.dp)).background(bg).padding(vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.width(dateCol).padding(start = 4.dp)) {
                            Text(fmt.weekday(day.date.dayOfWeek, short = true), style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium))
                            Text(fmt.gregorianShort(day.date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        day.times.forEach { t ->
                            Text(
                                t.instant?.let { fmt.time(it, zone, suffix = false) } ?: "—",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal),
                                color = if (t.status == TimeStatus.HIGH_LATITUDE_RULE) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(col),
                            )
                        }
                    }
                }
            }
        }
        if (!s.clock24) NoteText(str.tableNoSuffix, Modifier.padding(horizontal = 14.dp, vertical = 6.dp))
        if (rows.any { d -> d.times.any { it.status == TimeStatus.HIGH_LATITUDE_RULE } }) {
            Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(SakinaIcons.Info, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(6.dp))
                NoteText(str.highLatBadge)
            }
        }
    }
}
