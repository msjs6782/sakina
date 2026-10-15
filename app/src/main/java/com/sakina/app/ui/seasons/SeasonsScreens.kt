@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sakina.app.ui.seasons

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakina.app.core.alerts.ReminderKind
import com.sakina.app.core.prayer.DuhaWindow
import com.sakina.app.core.prayer.FastStatus
import com.sakina.app.core.prayer.Night
import com.sakina.app.core.prayer.Nights
import com.sakina.app.core.prayer.PrayerKey
import com.sakina.app.core.prayer.PrayerSchedule
import com.sakina.app.core.prayer.RamadanMonth
import com.sakina.app.core.prayer.RamadanPlan
import com.sakina.app.core.quran.AyahRef
import com.sakina.app.core.quran.Khatma
import com.sakina.app.data.AppSettings
import com.sakina.app.ui.LocalFmt
import com.sakina.app.ui.LocalStrings
import com.sakina.app.ui.MainViewModel
import com.sakina.app.ui.Route
import com.sakina.app.ui.calendar.RefsCard
import com.sakina.app.ui.calendar.openUrl
import com.sakina.app.ui.components.Banner
import com.sakina.app.ui.components.Divider
import com.sakina.app.ui.components.NavRow
import com.sakina.app.ui.components.NoteText
import com.sakina.app.ui.components.SakinaIcons
import com.sakina.app.ui.components.ScreenBar
import com.sakina.app.ui.components.SectionCard
import com.sakina.app.ui.components.Stepper
import com.sakina.app.ui.location.FullScreen
import com.sakina.app.ui.rememberNow
import com.sakina.app.ui.theme.Amiri
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Composable
private fun SeasonFrame(title: String, vm: MainViewModel, content: @Composable () -> Unit) {
    FullScreen {
        Column(Modifier.fillMaxSize()) {
            ScreenBar(title, onBack = { vm.pop() })
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                content()
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** The place's schedule, or null (with a prompt to choose a place) when there is none. */
@Composable
private fun scheduleOrPrompt(vm: MainViewModel, s: AppSettings): Pair<PrayerSchedule, ZoneId>? {
    val str = LocalStrings.current
    val loc = s.activeLocation
    val zone = loc?.zoneId
    if (loc == null || zone == null || !loc.isUsable) {
        Banner(str.qiblaNoPlace.substringBefore('.') + ".", warning = true)
        FilledTonalButton(onClick = { vm.push(Route.Locations) }) { Text(str.qiblaChoosePlace) }
        return null
    }
    return remember(loc, s.calcConfig) { PrayerSchedule(loc.point, zone, s.calcConfig) } to zone
}

@Composable
private fun TimeLine(label: String, value: String, tag: String? = null, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = if (strong) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            color = if (strong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = if (tag != null) Modifier.testTag(tag) else Modifier)
    }
}

// ---------------------------------------------------------------- Ramadan

@Composable
fun RamadanScreen(vm: MainViewModel, s: AppSettings) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val now by rememberNow()
    val quranState by vm.quranState.collectAsStateWithLifecycle()
    SeasonFrame(str.ramadanTitle, vm) {
        val (schedule, zone) = scheduleOrPrompt(vm, s) ?: return@SeasonFrame
        val today = schedule.localDate(now)
        val month = remember(schedule, today) { RamadanPlan.currentOrNext(schedule, today) }
        if (month == null) {
            NoteText(str.outOfRange)
            return@SeasonFrame
        }
        val status = RamadanPlan.status(month, schedule, now)
        SectionCard(title = str.ramadanOf(fmt.num(month.year)), icon = SakinaIcons.Moon) {
            Text(str.ramadanLength(fmt.num(month.length)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("ramadan_length"))
            Text(fmt.gregorian(month.first, withYear = true, withWeekday = true) + " – " + fmt.gregorian(month.last, withYear = true),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            when (status) {
                is FastStatus.UntilMaghrib -> StatusBlock(str.untilIftar(fmt.countdown(Duration.between(now, status.at), alwaysHours = true)), str.ramadanDayN(fmt.num(status.day)))
                is FastStatus.UntilFajr -> StatusBlock(str.untilImsak(fmt.countdown(Duration.between(now, status.at), alwaysHours = true)), str.ramadanDayN(fmt.num(status.day)))
                is FastStatus.NotYet -> {
                    val days = ChronoUnit.DAYS.between(today, status.starts)
                    StatusBlock(str.ramadanStartsIn(str.daysCount(fmt.num(days.toInt()), days), fmt.gregorian(status.starts, withYear = true, withWeekday = true)), null)
                }
                FastStatus.Ended -> StatusBlock(str.ramadanEnded, null)
                FastStatus.Unavailable -> StatusBlock(str.nightUnavailable, null)
            }
        }
        SectionCard(title = str.ramadanLinks, icon = SakinaIcons.Sparkle) {
            val k = quranState.khatma
            NavRow(SakinaIcons.Book, str.khatmaLink, k?.let { str.progressOf(fmt.num(it.progressPage), fmt.num(Khatma.PAGES)) }, onClick = { vm.push(Route.Khatma) },
                modifier = Modifier.testTag("ramadan_khatma"))
            val su = s.alerts.suhoor
            NavRow(SakinaIcons.Bell, str.suhoorLink, str.suhoorState(su.enabled, fmt.num(su.minutesBeforeFajr)), onClick = { vm.push(Route.Alerts) },
                modifier = Modifier.testTag("ramadan_suhoor"))
            NavRow(SakinaIcons.Sliders, str.cityAndOffsets, s.activeLocation?.name(s.language), onClick = { vm.push(Route.Calc) },
                modifier = Modifier.testTag("ramadan_calc"))
            NoteText(str.goalsLater, Modifier.padding(horizontal = 12.dp))
        }
        Imsakiya(month, today, zone)
        LastTen(month, now, zone)
        SectionCard {
            NoteText(str.ramadanCalcNote)
            if (s.hijriOffset != 0) NoteText(str.ramadanOffsetNote(fmt.signed(s.hijriOffset)), Modifier.padding(top = 4.dp))
        }
        RefsCard(vm, listOf("suhoor", "iftar_time", "hasten_iftar", "qiyam_ramadan"))
    }
}

@Composable
private fun StatusBlock(main: String, sub: String?) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer).padding(14.dp)) {
        Text(main, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.testTag("ramadan_status"))
        if (sub != null) Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun Imsakiya(month: RamadanMonth, today: LocalDate, zone: ZoneId) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    SectionCard(title = str.imsakiya, icon = SakinaIcons.Table) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            listOf(str.dayLabel to 0.7f, str.gregorianLabel to 1.4f, str.imsakColumn to 1f, str.iftarColumn to 1f).forEach { (t, w) ->
                Text(t, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.weight(w))
            }
        }
        month.days.forEach { d ->
            val isToday = d.date == today
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(if (isToday) MaterialTheme.colorScheme.primaryContainer else if (d.day > 20) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(vertical = 6.dp).testTag("imsak_${d.day}"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(fmt.num(d.day), style = MaterialTheme.typography.titleSmall, fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(0.7f))
                Text(fmt.weekday(d.date.dayOfWeek, short = true) + " " + fmt.gregorianShort(d.date), style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1.4f))
                Text(d.fajr?.let { fmt.time(it, zone, suffix = false) } ?: "—", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Text(d.maghrib?.let { fmt.time(it, zone, suffix = false) } ?: "—", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LastTen(month: RamadanMonth, now: Instant, zone: ZoneId) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    SectionCard(title = str.lastTenTitle, icon = SakinaIcons.Isha) {
        month.lastTen().forEachIndexed { i, n ->
            if (i > 0) Divider()
            val current = n.begins != null && n.ends != null && !now.isBefore(n.begins) && now.isBefore(n.ends)
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).testTag("night_${n.night}"), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(str.nightN(fmt.num(n.night)), style = MaterialTheme.typography.titleSmall,
                        color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    Text(str.nightBegins(fmt.weekday(n.beginsEvening.dayOfWeek) + " " + fmt.gregorian(n.beginsEvening, withYear = false)) +
                        (n.begins?.let { " · " + fmt.time(it, zone) } ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (n.odd) com.sakina.app.ui.components.Badge(str.oddNight)
            }
        }
        Spacer(Modifier.height(6.dp))
        NoteText(str.lastTenNote)
    }
}

// ---------------------------------------------------------------- Night and Duha

@Composable
fun NightScreen(vm: MainViewModel, s: AppSettings) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val now by rememberNow()
    SeasonFrame(str.nightTitle, vm) {
        val (schedule, zone) = scheduleOrPrompt(vm, s) ?: return@SeasonFrame
        val night = remember(schedule, schedule.localDate(now), now.epochSecond / 60) { Nights.currentOrNext(schedule, now) }
        if (night == null) Banner(str.nightUnavailable, warning = true) else NightCard(night, now, zone)
        DuhaCard(vm, s, schedule, zone, now)
        RefsCard(vm, listOf("last_third", "isha_half"))
        SectionCard(title = str.reminderLink, icon = SakinaIcons.Bell) {
            listOf(ReminderKind.QIYAM, ReminderKind.WITR, ReminderKind.DUHA).forEach { k ->
                val r = s.alerts.allReminders.first { it.kind == k }
                NavRow(null, str.reminderName(k), if (r.enabled) str.reminderOn(fmt.clock(r.minuteOfDay / 60, r.minuteOfDay % 60)) else str.reminderOffNote.substringBefore('.'),
                    onClick = { vm.push(Route.Reminders) }, modifier = Modifier.testTag("night_rem_${k.name.lowercase()}"))
            }
        }
    }
}

@Composable
private fun NightCard(n: Night, now: Instant, zone: ZoneId) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val current = n.contains(now)
    SectionCard(title = if (current) str.nightNow else str.nightNext, icon = SakinaIcons.Moon) {
        Text(fmt.weekday(n.eveningOf.dayOfWeek) + " " + fmt.gregorian(n.eveningOf, withYear = false), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        TimeLine(str.nightStart, fmt.time(n.maghrib, zone), "night_maghrib")
        TimeLine(str.nightMidnight, fmt.time(n.midnight, zone), "night_midnight", strong = true)
        TimeLine(str.nightLastThird, fmt.time(n.lastThird, zone), "night_last_third", strong = true)
        TimeLine(str.nightEnd, fmt.time(n.fajr, zone), "night_fajr")
        NoteText(str.nightLength(fmt.hoursMinutes(n.length)))
        if (current) {
            Spacer(Modifier.height(8.dp))
            val inLast = !now.isBefore(n.lastThird)
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer).padding(12.dp)) {
                if (inLast) Text(str.inLastThird, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                else Text(str.untilLastThird(fmt.countdown(Duration.between(now, n.lastThird), alwaysHours = true)), style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.testTag("night_until_third"))
                Text(str.untilFajr(fmt.countdown(Duration.between(now, n.fajr), alwaysHours = true)), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.testTag("night_until_fajr"))
            }
        }
        Spacer(Modifier.height(6.dp))
        NoteText(str.nightRangeNote)
    }
}

@Composable
private fun DuhaCard(vm: MainViewModel, s: AppSettings, schedule: PrayerSchedule, zone: ZoneId, now: Instant) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val today = schedule.localDate(now)
    val w = DuhaWindow.of(schedule.day(today), s.duhaAfterSunrise, s.duhaBeforeDhuhr)
    SectionCard(title = str.duhaTitle, icon = SakinaIcons.Sun) {
        if (w == null) NoteText(str.duhaUnavailable)
        else Text(str.duhaRange(fmt.time(w.start, zone), fmt.time(w.end, zone)), style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("duha_range"))
        Spacer(Modifier.height(6.dp))
        Stepper(s.duhaAfterSunrise, str.duhaAfterSunrise, DuhaWindow.AFTER_RANGE.first, DuhaWindow.AFTER_RANGE.last, fmt.num(s.duhaAfterSunrise)) { v ->
            vm.update { it.copy(duhaAfterSunrise = v) }
        }
        Stepper(s.duhaBeforeDhuhr, str.duhaBeforeDhuhr, DuhaWindow.BEFORE_RANGE.first, DuhaWindow.BEFORE_RANGE.last, fmt.num(s.duhaBeforeDhuhr)) { v ->
            vm.update { it.copy(duhaBeforeDhuhr = v) }
        }
        NoteText(str.duhaNote)
    }
    RefsCard(vm, listOf("forbidden_times", "duha_rumh", "duha_awwabin"))
}

// ---------------------------------------------------------------- Friday

private const val KAHF = 18

@Composable
fun FridayScreen(vm: MainViewModel, s: AppSettings) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val context = LocalContext.current
    LaunchedEffect(Unit) { vm.loadQuran() }
    val quran by vm.quran.collectAsStateWithLifecycle()
    SeasonFrame(str.fridayTitle, vm) {
        SectionCard(title = str.fridayKahf, icon = SakinaIcons.Book) {
            FilledTonalButton(onClick = { quran?.let { vm.openQuran(it.meta.global(AyahRef(KAHF, 1))) } }, enabled = quran != null,
                modifier = Modifier.testTag("friday_kahf")) {
                Icon(SakinaIcons.Book, null); Spacer(Modifier.width(8.dp)); Text(str.fridayKahfOpen)
            }
            Spacer(Modifier.height(6.dp))
            NoteText(str.fridayKahfNote)
            TextButton(onClick = { openUrl(context, "https://dorar.net/hadith/search?q=" + android.net.Uri.encode("من قرأ سورة الكهف يوم الجمعة"), str) }) {
                Text(str.linksTitle + " · " + "dorar.net")
            }
        }
        RefsCard(vm, listOf("kahf_ten"))
        SectionCard(title = str.fridaySalawat, icon = SakinaIcons.Sparkle) {
            OutlinedButton(onClick = { vm.push(Route.AdhkarItem("salat_ibrahimiyya")) }, modifier = Modifier.testTag("friday_salawat")) { Text(str.fridaySalawatOpen) }
        }
        RefsCard(vm, listOf("salawat_ten", "friday_salawat", "friday_best", "friday_hour"))
        val r = s.alerts.allReminders.first { it.kind == ReminderKind.FRIDAY }
        SectionCard(title = str.fridayReminder, icon = SakinaIcons.Bell) {
            Text(if (r.enabled) str.reminderOn(fmt.clock(r.minuteOfDay / 60, r.minuteOfDay % 60)) else str.reminderOffNote, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("friday_reminder_state"))
            TextButton(onClick = { vm.push(Route.Reminders) }, modifier = Modifier.testTag("friday_reminder")) { Text(str.reminderSetup) }
        }
    }
}

/** True from Thursday's Maghrib to Friday's Maghrib at the place; returns whether it is the eve. */
fun fridayWindow(schedule: PrayerSchedule, now: Instant): Boolean? {
    val today = schedule.localDate(now)
    val maghrib = schedule.day(today)[PrayerKey.MAGHRIB].instant ?: return null
    return when (today.dayOfWeek) {
        java.time.DayOfWeek.THURSDAY -> if (!now.isBefore(maghrib)) true else null
        java.time.DayOfWeek.FRIDAY -> if (now.isBefore(maghrib)) false else null
        else -> null
    }
}

/** The home card for Friday's eve and day. */
@Composable
fun FridayHomeCard(vm: MainViewModel, eve: Boolean) {
    val str = LocalStrings.current
    LaunchedEffect(Unit) { vm.loadQuran() }
    val quran by vm.quran.collectAsStateWithLifecycle()
    SectionCard(title = str.fridayCardTitle + " · " + str.fridayWindow(eve), icon = SakinaIcons.Sparkle, modifier = Modifier.testTag("home_friday")) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FilledTonalButton(onClick = { quran?.let { vm.openQuran(it.meta.global(AyahRef(KAHF, 1))) } }, enabled = quran != null) { Text(str.fridayKahf) }
            OutlinedButton(onClick = { vm.push(Route.AdhkarItem("salat_ibrahimiyya")) }) { Text(str.fridaySalawat) }
            TextButton(onClick = { vm.push(Route.Friday) }, modifier = Modifier.testTag("home_friday_more")) { Text(str.fridayTitle) }
        }
    }
}
