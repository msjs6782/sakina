package com.sakina.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.IconButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.sakina.app.SakinaApp
import com.sakina.app.alerts.AlertsCenter
import com.sakina.app.ui.alerts.QuietDialog
import com.sakina.app.ui.LocalMainVm
import com.sakina.app.core.quran.DailyVerse
import com.sakina.app.ui.quran.ayahEnd
import com.sakina.app.ui.theme.AmiriQuran
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sakina.app.core.calendar.Occasions
import com.sakina.app.core.calendar.UmmAlQura
import com.sakina.app.core.format.Fmt
import com.sakina.app.core.prayer.DayTimes
import com.sakina.app.core.prayer.PrayerEvent
import com.sakina.app.core.prayer.PrayerKey
import com.sakina.app.core.prayer.PrayerSchedule
import com.sakina.app.core.prayer.TimeStatus
import com.sakina.app.data.AppSettings
import com.sakina.app.data.HomeCard
import com.sakina.app.data.HomeLayout
import com.sakina.app.data.SavedLocation
import com.sakina.app.data.SettingsOps
import com.sakina.app.ui.LocalFmt
import com.sakina.app.ui.LocalStrings
import com.sakina.app.ui.MainViewModel
import com.sakina.app.ui.PlacePurpose
import com.sakina.app.ui.Route
import com.sakina.app.ui.Strings
import com.sakina.app.ui.components.Badge
import com.sakina.app.ui.components.Banner
import com.sakina.app.ui.components.MonoDigits
import com.sakina.app.ui.components.NoteText
import com.sakina.app.ui.components.SakinaIcons
import com.sakina.app.ui.components.SectionCard
import com.sakina.app.ui.components.prayerIcon
import com.sakina.app.ui.rememberNow
import com.sakina.app.ui.theme.Amiri
import com.sakina.app.ui.theme.LocalExtras
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Everything the home layouts need for one moment. */
class HomeModel(
    val location: SavedLocation,
    val zone: ZoneId,
    val today: LocalDate,
    val day: DayTimes,
    val next: PrayerEvent?,
    val previous: PrayerEvent?,
    val nextSunrise: Pair<Instant, LocalDate>?,
    val nextSunset: Pair<Instant, LocalDate>?,
    val now: Instant,
) {
    val progress: Float?
        get() {
            val n = next ?: return null
            val p = previous ?: return null
            val total = Duration.between(p.instant, n.instant).seconds.toFloat()
            if (total <= 0f) return null
            return Duration.between(p.instant, now).seconds / total
        }
}

@Composable
fun HomeScreen(vm: MainViewModel, s: AppSettings, contentPadding: PaddingValues) {
    val loc = s.activeLocation
    val now by rememberNow()
    val zone = loc?.zoneId
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        var showQuiet by remember { mutableStateOf(false) }
        val app = LocalContext.current.applicationContext as SakinaApp
        val quiet = remember(now.epochSecond / 2) { app.alertState.quietUntil }
        HomeHeader(s, now, zone, quiet != null) { showQuiet = true }
        LocationStrip(vm, s)
        if (quiet != null) QuietStrip(quiet, zone ?: ZoneId.systemDefault())
        if (showQuiet) QuietDialog(onDismiss = { showQuiet = false })
        if (loc == null || zone == null || !loc.isUsable) {
            NoLocationCard(vm)
            if (s.orderedCards.any { it.card == HomeCard.VERSE && it.visible }) VerseCard(now.atZone(ZoneId.systemDefault()).toLocalDate())
        } else {
            val schedule = remember(loc, s.calcConfig) { PrayerSchedule(loc.point, zone, s.calcConfig) }
            val today = schedule.localDate(now)
            val model = HomeModel(
                location = loc,
                zone = zone,
                today = today,
                day = schedule.day(today),
                next = schedule.next(now),
                previous = schedule.previous(now),
                nextSunrise = schedule.nextSunEvent(now, sunrise = true),
                nextSunset = schedule.nextSunEvent(now, sunrise = false),
                now = now,
            )
            when (s.homeLayout) {
                HomeLayout.BALANCED -> BalancedLayout(model, s)
                HomeLayout.FOCUS -> FocusLayout(model, s)
                HomeLayout.DASHBOARD -> DashboardLayout(model, s)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun HomeHeader(s: AppSettings, now: Instant, zone: ZoneId?, quiet: Boolean, onQuiet: () -> Unit) {
    val fmt = LocalFmt.current
    val str = LocalStrings.current
    val date = now.atZone(zone ?: ZoneId.systemDefault()).toLocalDate()
    val hijri = UmmAlQura.displayed(date, s.hijriOffset)
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                str.appName,
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Amiri, fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            if (hijri != null) Text(fmt.hijri(hijri), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(fmt.gregorian(date, withYear = true, withWeekday = true), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onQuiet, modifier = Modifier.testTag("home_quiet")) {
            Icon(
                if (quiet) SakinaIcons.BellOff else SakinaIcons.Bell, str.quietTitle,
                tint = if (quiet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun QuietStrip(until: Instant, zone: ZoneId) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val context = LocalContext.current
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(SakinaIcons.BellOff, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(10.dp))
            Text(str.quietActive(fmt.time(until, zone)), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.weight(1f))
            TextButton(onClick = { AlertsCenter.endQuiet(context) }) { Text(str.quietEndNow) }
        }
    }
}

@Composable
private fun LocationStrip(vm: MainViewModel, s: AppSettings) {
    val str = LocalStrings.current
    val lang = str.lang
    val travel = s.travel
    if (travel != null) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(SakinaIcons.Suitcase, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(str.travelling + " · " + travel.destination.name(lang), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    s.homeLocation?.let { Text(str.homeIs(it.name(lang)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer) }
                }
                FilledTonalButton(onClick = { vm.update { SettingsOps.returnHome(it) } }) { Text(str.returnHome) }
            }
        }
        return
    }
    if (s.locations.isEmpty()) return
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val activeId = s.activeLocation?.id
        s.locations.forEach { loc ->
            val selected = loc.id == activeId
            Surface(
                onClick = { vm.update { SettingsOps.activate(it, loc.id) } },
                shape = RoundedCornerShape(50),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (selected) {
                        Icon(SakinaIcons.Pin, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(loc.name(lang), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
        Surface(
            onClick = { vm.push(Route.Locations) },
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(SakinaIcons.Plus, str.addPlace, Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun NoLocationCard(vm: MainViewModel) {
    val str = LocalStrings.current
    SectionCard {
        Icon(SakinaIcons.Pin, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text(str.chooseLocationTitle, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(str.chooseLocationBody, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Button(onClick = { vm.push(Route.CityPicker(PlacePurpose.ADD)) }, modifier = Modifier.fillMaxWidth()) {
            Icon(SakinaIcons.Search, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(str.pickCity)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.push(Route.Gps(PlacePurpose.ADD)) }, modifier = Modifier.weight(1f)) {
                Icon(SakinaIcons.Gps, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(str.useGps, maxLines = 1)
            }
            OutlinedButton(onClick = { vm.push(Route.Manual(PlacePurpose.ADD)) }, modifier = Modifier.weight(1f)) {
                Icon(SakinaIcons.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(str.manualEntry, maxLines = 1)
            }
        }
    }
}

// ---------- helpers shared by layouts ----------

private fun dayLabel(str: Strings, fmt: Fmt, date: LocalDate, today: LocalDate): String = when (ChronoUnit.DAYS.between(today, date)) {
    0L -> str.today
    1L -> str.tomorrow
    -1L -> str.yesterday
    else -> fmt.gregorian(date, withYear = false, withWeekday = true)
}

@Composable
private fun NextHero(m: HomeModel) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val period = skyPeriod(m.day, m.now)
    HeroSurface(period, Modifier.fillMaxWidth()) {
        val next = m.next
        if (next == null) {
            Text(str.noTimesToday, color = LocalExtras.current.heroOn, modifier = Modifier.padding(20.dp))
        } else {
            HeroNext(
                label = str.nextPrayer + " · " + m.location.name(str.lang),
                prayerName = str.prayer(next.key),
                prayerKey = next.key,
                timeText = fmt.time(next.instant, m.zone),
                dateNote = if (next.date != m.today) dayLabel(str, fmt, next.date, m.today) else null,
                remainingLabel = str.remaining,
                countdown = fmt.countdown(Duration.between(m.now, next.instant)),
                progress = m.progress,
                sinceText = m.previous?.let { str.sinceAdhan(str.prayer(it.key)) + "  " + fmt.hoursMinutes(Duration.between(it.instant, m.now)) },
            )
        }
    }
}

@Composable
private fun IssuesBanner(day: DayTimes) {
    val str = LocalStrings.current
    if (day.issues.isEmpty()) return
    Banner(text = day.issues.joinToString("\n") { str.issue(it) }, icon = SakinaIcons.Warning, warning = true) {
        Spacer(Modifier.height(4.dp))
        Text(str.checkLocalCalendar, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

@Composable
fun TimeRow(
    key: PrayerKey,
    instant: Instant?,
    status: TimeStatus,
    zone: ZoneId,
    highlighted: Boolean,
    passed: Boolean,
    offset: Int,
    trailing: (@Composable () -> Unit)? = null,
    /** The "in 2 h 5 min" / "3 h ago" chip; beside the time, or under the name when [chipBelow]. */
    chip: (@Composable () -> Unit)? = null,
    chipBelow: Boolean = false,
) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val cs = MaterialTheme.colorScheme
    val bg = if (highlighted) cs.primaryContainer.copy(alpha = 0.7f) else Color.Transparent
    val fg = when {
        highlighted -> cs.onPrimaryContainer
        passed -> cs.onSurfaceVariant
        else -> cs.onSurface
    }
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(bg)
            .then(if (highlighted) Modifier.border(1.5.dp, cs.primary.copy(alpha = 0.45f), shape) else Modifier)
            .padding(horizontal = 12.dp, vertical = if (chip != null) 12.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(prayerIcon(key), null, Modifier.size(22.dp), tint = if (highlighted) fg else cs.primary.copy(alpha = if (passed) 0.6f else 1f))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(str.prayer(key), style = MaterialTheme.typography.titleMedium.copy(fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium), color = fg)
            if (status == TimeStatus.HIGH_LATITUDE_RULE) {
                Text(str.highLatBadge, style = MaterialTheme.typography.labelSmall, color = cs.tertiary)
            }
            if (chip != null && chipBelow) { Spacer(Modifier.height(4.dp)); chip() }
        }
        if (chip != null && !chipBelow) { chip(); Spacer(Modifier.width(10.dp)) }
        if (offset != 0) {
            Badge(fmt.signed(offset) + " " + str.minutesShort)
            Spacer(Modifier.width(8.dp))
        }
        if (instant != null) {
            Text(fmt.time(instant, zone), style = MaterialTheme.typography.titleMedium.copy(fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium), color = fg,
                maxLines = 1, softWrap = false)
        } else {
            Column(horizontalAlignment = Alignment.End) {
                Text("—", style = MaterialTheme.typography.titleMedium, color = fg)
                Text(str.unavailable, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
        if (trailing != null) { Spacer(Modifier.width(6.dp)); trailing() }
    }
}

/** The "I prayed" mark: a ring, filled with a check once marked. */
@Composable
fun PrayedMark(done: Boolean, label: String, tag: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Box(
        Modifier.size(32.dp).clip(androidx.compose.foundation.shape.CircleShape)
            .background(if (done) cs.primary else Color.Transparent)
            .border(1.5.dp, if (done) cs.primary else cs.outline, androidx.compose.foundation.shape.CircleShape)
            .clickable(onClickLabel = label) {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics { contentDescription = label; stateDescription = if (done) "✓" else "" }
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        if (done) Icon(SakinaIcons.Check, null, Modifier.size(18.dp), tint = cs.onPrimary)
    }
}

/**
 * "باقي ٣ س ٢٢ د" before a time, "منذ ٩ س ٩ د" after it. The next prayer's chip is outlined in the accent colour,
 * the later ones are tinted, and the passed ones are quiet.
 */
@Composable
fun TimeChip(now: Instant, at: Instant, next: Boolean, tag: String) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val cs = MaterialTheme.colorScheme
    val r = com.sakina.app.core.format.Relative.of(now, at)
    val text = when {
        r.future -> str.chipIn(fmt.durationShort(r.duration))
        r.isNow -> str.chipNow
        else -> str.chipAgo(fmt.durationShort(r.duration))
    }
    val shape = RoundedCornerShape(50)
    val (bg, fg, line) = when {
        next -> Triple(cs.surfaceContainerLowest, cs.primary, cs.primary)
        r.future -> Triple(cs.primaryContainer.copy(alpha = 0.6f), cs.onPrimaryContainer, cs.primary.copy(alpha = 0.25f))
        else -> Triple(Color.Transparent, cs.onSurfaceVariant, cs.outlineVariant)
    }
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (next) FontWeight.SemiBold else FontWeight.Medium),
        color = fg, maxLines = 1, softWrap = false,
        modifier = Modifier.clip(shape).background(bg).border(if (next) 1.5.dp else 1.dp, line, shape)
            .padding(horizontal = 10.dp, vertical = 4.dp).testTag(tag),
    )
}

@Composable
private fun TodayList(m: HomeModel, s: AppSettings) {
    val str = LocalStrings.current
    SectionCard(title = str.todayTimes, icon = SakinaIcons.Clock, padding = PaddingValues(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 8.dp)) {
      androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        // On narrow screens or with large text the chip goes under the prayer's name.
        val chipBelow = maxWidth < 340.dp || androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.15f
      Column {
        val offsets = s.calcConfig.offsets
        val vm = LocalMainVm.current
        val tools = vm?.tools?.collectAsStateWithLifecycle()?.value
        m.day.times.forEachIndexed { i, t ->
            val isNext = m.next?.let { it.key == t.key && it.date == m.today } ?: false
            val passed = t.instant != null && !t.instant.isAfter(m.now)
            // The prayer log: a mark beside each prayer whose time has come.
            val mark: (@Composable () -> Unit)? = if (s.prayerLog && vm != null && tools != null && passed && t.key in PrayerKey.prayers) ({
                val done = com.sakina.app.core.prayer.PrayerLog.isDone(tools.prayed, m.today, t.key)
                PrayedMark(done, str.markPrayed(str.prayer(t.key)), "prayed_${t.key.name.lowercase()}") {
                    vm.updateTools { it.copy(prayed = com.sakina.app.core.prayer.PrayerLog.toggle(it.prayed, m.today, t.key, m.today)) }
                }
            }) else null
            val chip: (@Composable () -> Unit)? = if (s.prayerChips && t.instant != null) ({
                TimeChip(m.now, t.instant, isNext, "chip_${t.key.name.lowercase()}")
            }) else null
            // Hairlines between rows, except around the highlighted one.
            val nextIdx = m.day.times.indexOfFirst { x -> m.next?.let { it.key == x.key && it.date == m.today } ?: false }
            if (i > 0 && i != nextIdx && i - 1 != nextIdx) androidx.compose.material3.HorizontalDivider(
                Modifier.padding(horizontal = 14.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            )
            TimeRow(t.key, t.instant, t.status, m.zone, isNext, passed, offsets[t.key], mark, chip, chipBelow)
        }
        // The week and month tables (they used to be a tab of their own), and the prayer log.
        if (vm != null) Row(Modifier.fillMaxWidth()) {
            TextButton(onClick = { vm.push(com.sakina.app.ui.Route.Times(com.sakina.app.ui.times.TimesView.WEEK)) }, modifier = Modifier.padding(start = 4.dp).testTag("home_open_times")) {
                Text(str.weekAndMonth)
            }
            Spacer(Modifier.weight(1f))
            if (s.prayerLog && tools != null) TextButton(onClick = { vm.push(com.sakina.app.ui.Route.PrayerLog) }, modifier = Modifier.testTag("home_open_prayer_log")) {
                Text(str.prayedToday(LocalFmt.current.num(com.sakina.app.core.prayer.PrayerLog.count(tools.prayed, m.today))))
            }
        }
      }
      }
    }
}

@Composable
private fun SunCard(m: HomeModel, modifier: Modifier = Modifier) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    SectionCard(modifier, title = str.sunCardTitle, icon = SakinaIcons.Sun) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SunCell(str.nextSunrise, SakinaIcons.Sunrise, m.nextSunrise, m, fmt, str, Modifier.weight(1f))
            SunCell(str.nextSunset, SakinaIcons.Maghrib, m.nextSunset, m, fmt, str, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        NoteText(str.astronomicalNote)
    }
}

@Composable
private fun SunCell(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, ev: Pair<Instant, LocalDate>?, m: HomeModel, fmt: Fmt, str: Strings, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(18.dp), tint = LocalExtras.current.gold)
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        if (ev == null) {
            Text(str.unavailable, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
        } else {
            Text(fmt.time(ev.first, m.zone), style = MaterialTheme.typography.titleLarge)
            Text(dayLabel(str, fmt, ev.second, m.today), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(fmt.gregorian(ev.second, withYear = false), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DateCard(m: HomeModel, s: AppSettings, modifier: Modifier = Modifier) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val hijri = UmmAlQura.displayed(m.today, s.hijriOffset)
    SectionCard(modifier, title = str.dateCardTitle, icon = SakinaIcons.Calendar) {
        if (hijri != null) {
            Text(fmt.hijri(hijri), style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Amiri, fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
        }
        Text(fmt.gregorian(m.today, withYear = true, withWeekday = true), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val todayOcc = Occasions.on(m.today, s.hijriOffset)
        if (todayOcc.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                todayOcc.forEach { Badge(str.todayIs + ": " + str.occasion(it), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) }
            }
        }
        val next = remember(m.today, s.hijriOffset) { Occasions.next(m.today, s.hijriOffset) }
        if (next != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                str.nextOccasion(str.occasion(next.occasion), str.daysCount(fmt.num(next.daysAway.toInt()), next.daysAway)),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(fmt.gregorian(next.date, withYear = true, withWeekday = true), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        NoteText(str.occasionNote)
        LocalMainVm.current?.let { vm ->
            TextButton(onClick = { vm.openCalendar() }, modifier = Modifier.testTag("home_open_calendar")) { Text(str.calendarTitle) }
        }
    }
}

@Composable
private fun Cards(m: HomeModel, s: AppSettings) {
    s.orderedCards.filter { it.visible }.forEach { pref ->
        when (pref.card) {
            HomeCard.DATE -> DateCard(m, s)
            HomeCard.SUN -> SunCard(m)
            HomeCard.VERSE -> VerseCard(m.today)
            HomeCard.QUICK -> QuickCard()
            HomeCard.FRIDAY -> {
                val vm = LocalMainVm.current
                val eve = remember(m.location, s.calcConfig, m.now.epochSecond / 60) {
                    com.sakina.app.ui.seasons.fridayWindow(PrayerSchedule(m.location.point, m.zone, s.calcConfig), m.now)
                }
                if (vm != null && eve != null) com.sakina.app.ui.seasons.FridayHomeCard(vm, eve)
            }
        }
    }
}

/** Four tiles to the tools used most, so they are one tap from home. */
@Composable
private fun QuickCard() {
    val str = LocalStrings.current
    val vm = LocalMainVm.current ?: return
    Row(Modifier.fillMaxWidth().testTag("home_quick"), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(
            Triple(SakinaIcons.Sparkle, str.toolsAdhkar, "quick_adhkar") to { vm.push(com.sakina.app.ui.Route.Adhkar()) },
            Triple(SakinaIcons.Beads, str.toolsTasbih, "quick_tasbih") to { vm.push(com.sakina.app.ui.Route.Tasbih) },
            Triple(SakinaIcons.Kaaba, str.toolsQibla, "quick_qibla") to { vm.push(com.sakina.app.ui.Route.Qibla) },
            Triple(SakinaIcons.Headphones, str.listenTitle, "quick_listen") to { vm.push(com.sakina.app.ui.Route.Listen()) },
        ).forEach { (t, go) ->
            Surface(
                onClick = go, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                modifier = Modifier.weight(1f).testTag(t.third),
            ) {
                Column(Modifier.padding(vertical = 12.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                        Icon(t.first, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(t.second, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

/** Verse of the day from a fixed list of references; the text is the Tanzil Uthmani text. */
@Composable
fun VerseCard(today: LocalDate) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val vm = LocalMainVm.current ?: return
    val data by vm.quran.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadQuran() }
    val d = data
    SectionCard(title = str.verseOfDay, icon = SakinaIcons.Book, modifier = Modifier.testTag("verse_card")) {
        if (d == null) {
            NoteText(str.quranLoading)
        } else {
            val ref = DailyVerse.forDate(today)
            val g = d.meta.global(ref)
            Text(
                d.text.ayahs[g] + " " + ayahEnd(ref.aya),
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = AmiriQuran, fontSize = 24.sp, lineHeight = 48.sp),
                textAlign = TextAlign.Justify,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { vm.openQuran(g, highlight = true) }.testTag("verse_text"),
            )
            Text(str.surahAyah(d.meta.surah(ref.sura).nameAr, fmt.num(ref.aya)), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            NoteText(str.verseNote)
        }
    }
}

// ---------- layouts ----------

@Composable
private fun wide(): Boolean = LocalConfiguration.current.screenWidthDp >= 600

@Composable
private fun BalancedLayout(m: HomeModel, s: AppSettings) {
    if (wide()) {
        // Landscape and large screens: hero and today's times side by side.
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                NextHero(m)
                IssuesBanner(m.day)
            }
            Column(Modifier.weight(1f)) { TodayList(m, s) }
        }
    } else {
        NextHero(m)
        IssuesBanner(m.day)
        TodayList(m, s)
    }
    Cards(m, s)
}

@Composable
private fun FocusLayout(m: HomeModel, s: AppSettings) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val ex = LocalExtras.current
    val period = skyPeriod(m.day, m.now)
    val config = LocalConfiguration.current
    val tall = config.screenHeightDp > 700
    val ring = minOf(300, (config.screenHeightDp * 0.38f).toInt(), config.screenWidthDp - 72).coerceAtLeast(180).dp
    HeroSurface(period, Modifier.fillMaxWidth(), corner = 32.dp) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = if (tall) 32.dp else 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val next = m.next
            Text(m.location.name(str.lang), style = MaterialTheme.typography.labelLarge, color = ex.heroOnMuted)
            if (next == null) {
                Text(str.noTimesToday, color = ex.heroOn, textAlign = TextAlign.Center)
            } else {
                Spacer(Modifier.height(16.dp))
                Box(Modifier.size(ring), contentAlignment = Alignment.Center) {
                    val track = if (ex.heroIsGradient) Color.White.copy(alpha = 0.18f) else MaterialTheme.colorScheme.outlineVariant
                    val fill = if (ex.heroIsGradient) Color.White else MaterialTheme.colorScheme.primary
                    val progress = (m.progress ?: 0f).coerceIn(0f, 1f)
                    Canvas(Modifier.fillMaxSize()) {
                        val stroke = 10.dp.toPx()
                        val inset = stroke / 2
                        val arcSize = Size(size.width - stroke, size.height - stroke)
                        drawArc(track, 135f, 270f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                        drawArc(fill, 135f, 270f * progress, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(prayerIcon(next.key), null, Modifier.size(34.dp), tint = ex.heroOn)
                        Text(str.prayer(next.key), style = MaterialTheme.typography.headlineMedium, color = ex.heroOn)
                        MonoDigits(fmt.countdown(Duration.between(m.now, next.instant), alwaysHours = true), MaterialTheme.typography.displayMedium, ex.heroOn)
                        Text(
                            fmt.time(next.instant, m.zone) + if (next.date != m.today) " · " + dayLabel(str, fmt, next.date, m.today) else "",
                            style = MaterialTheme.typography.titleMedium,
                            color = ex.heroOnMuted,
                        )
                    }
                }
                m.previous?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        str.sinceAdhan(str.prayer(it.key)) + "  " + fmt.hoursMinutes(Duration.between(it.instant, m.now)),
                        style = MaterialTheme.typography.bodySmall, color = ex.heroOnMuted, textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
    IssuesBanner(m.day)
    // Today's six times in a compact 3 × 2 grid.
    m.day.times.chunked(3).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { t ->
                val isNext = m.next?.let { it.key == t.key && it.date == m.today } ?: false
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                        .background(if (isNext) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(str.prayer(t.key), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    Text(t.instant?.let { fmt.time(it, m.zone) } ?: "—", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun DashboardLayout(m: HomeModel, s: AppSettings) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val ex = LocalExtras.current
    val period = skyPeriod(m.day, m.now)
    HeroSurface(period, Modifier.fillMaxWidth(), corner = 24.dp, decorate = false) {
        val next = m.next
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (next == null) {
                Text(str.noTimesToday, color = ex.heroOn)
            } else {
                Column(Modifier.weight(1f)) {
                    Text(str.nextPrayer, style = MaterialTheme.typography.labelMedium, color = ex.heroOnMuted)
                    Text(str.prayer(next.key) + " · " + fmt.time(next.instant, m.zone), style = MaterialTheme.typography.titleLarge, color = ex.heroOn)
                }
                MonoDigits(fmt.countdown(Duration.between(m.now, next.instant)), MaterialTheme.typography.headlineLarge, ex.heroOn)
            }
        }
    }
    IssuesBanner(m.day)
    val offsets = s.calcConfig.offsets
    m.day.times.chunked(if (wide()) 3 else 2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            pair.forEach { t ->
                val isNext = m.next?.let { it.key == t.key && it.date == m.today } ?: false
                val passed = t.instant != null && !t.instant.isAfter(m.now)
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (isNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = if (isNext) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    border = if (isNext) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                    modifier = Modifier.weight(1f),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(prayerIcon(t.key), null, Modifier.size(20.dp), tint = if (isNext) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary.copy(alpha = if (passed) 0.6f else 1f))
                            Spacer(Modifier.width(8.dp))
                            Text(str.prayer(t.key), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                            if (offsets[t.key] != 0) Text(fmt.signed(offsets[t.key]), style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            t.instant?.let { fmt.time(it, m.zone) } ?: "—",
                            style = MaterialTheme.typography.headlineSmall,
                            color = if (isNext) MaterialTheme.colorScheme.onPrimary else if (passed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        )
                        if (t.instant == null) Text(str.unavailable, style = MaterialTheme.typography.labelSmall, color = if (isNext) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.error)
                        if (t.status == TimeStatus.HIGH_LATITUDE_RULE) Text(str.highLatBadge, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
    Cards(m, s)
}

@Composable
fun TextLink(text: String, onClick: () -> Unit) = TextButton(onClick = onClick) { Text(text) }
