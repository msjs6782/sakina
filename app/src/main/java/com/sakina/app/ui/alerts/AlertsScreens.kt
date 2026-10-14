@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sakina.app.ui.alerts

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakina.app.SakinaApp
import com.sakina.app.alerts.AlertState
import com.sakina.app.alerts.AlertsCenter
import com.sakina.app.alerts.SoundLibrary
import com.sakina.app.core.alerts.AlertMode
import com.sakina.app.core.alerts.AlertSettings
import com.sakina.app.core.alerts.AudioRoute
import com.sakina.app.core.alerts.ReminderKind
import com.sakina.app.core.prayer.PrayerKey
import com.sakina.app.data.AppSettings
import com.sakina.app.ui.LocalFmt
import com.sakina.app.ui.LocalStrings
import com.sakina.app.ui.MainViewModel
import com.sakina.app.ui.Route
import com.sakina.app.ui.components.Banner
import com.sakina.app.ui.components.ChoiceRow
import com.sakina.app.ui.components.Divider
import com.sakina.app.ui.components.NavRow
import com.sakina.app.ui.components.NoteText
import com.sakina.app.ui.components.SakinaIcons
import com.sakina.app.ui.components.ScreenBar
import com.sakina.app.ui.components.SectionCard
import com.sakina.app.ui.components.SwitchRow
import com.sakina.app.ui.components.prayerIcon
import com.sakina.app.ui.location.FullScreen
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.ZoneId

@Composable
private fun AlertsSubScreen(title: String, vm: MainViewModel, content: @Composable () -> Unit) {
    FullScreen {
        Column(Modifier.fillMaxSize()) {
            ScreenBar(title, onBack = { vm.pop() })
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                content()
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/** Re-reads permission state whenever the screen resumes (after returning from Android settings). */
@Composable
private fun rememberResumeTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return tick
}

fun updateAlerts(vm: MainViewModel, transform: (AlertSettings) -> AlertSettings) = vm.update { it.copy(alerts = transform(it.alerts)) }

@Composable
fun AlertsScreen(vm: MainViewModel, s: AppSettings) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val context = LocalContext.current
    val app = context.applicationContext as SakinaApp
    val tick = rememberResumeTick()
    val status = remember(tick, s.alerts) { AlertsCenter.status(context) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) AlertsCenter.openNotificationSettings(context)
        AlertsCenter.refresh(context, "permission")
    }
    // Asks for the notification permission the first time an alert is turned on.
    var asked by remember { mutableStateOf(false) }
    LaunchedEffect(s.alerts.anyEnabled, status.notificationPermission) {
        if (s.alerts.anyEnabled && !status.notificationPermission && !asked && Build.VERSION.SDK_INT >= 33) {
            asked = true
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val scheduled by produceState<AlertState.Scheduled?>(null, s.alerts, tick) {
        while (true) {
            value = app.alertState.scheduled()
            delay(2000)
        }
    }
    val zone = s.activeLocation?.zoneId ?: ZoneId.systemDefault()
    var showQuiet by remember { mutableStateOf(false) }

    AlertsSubScreen(str.settingsAlerts, vm) {
        SectionCard(title = str.alertStatus, icon = SakinaIcons.Shield) {
            val sc = scheduled
            if (sc == null || !s.alerts.anyEnabled) {
                Text(if (s.alerts.anyEnabled) str.noScheduled else str.alertsAllOff, style = MaterialTheme.typography.bodyLarge)
            } else {
                Text(str.nextAlert(sc.ids.joinToString("، ") { str.eventLabel(it) }, fmt.time(sc.at, zone) + " · " + fmt.gregorian(sc.at.atZone(zone).toLocalDate(), withYear = false, withWeekday = true)),
                    style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("next_alert"))
                if (!sc.exact) NoteText(str.scheduledInexact) else if (sc.alarmClock) NoteText(str.scheduledAlarmClock)
            }
            Divider()
            PermissionRow(
                str.permNotif, if (status.notificationPermission && !status.notificationsEnabled) str.permNotifOff else str.permNotifBody,
                ok = status.notificationPermission && status.notificationsEnabled, okText = str.allowed, actionText = str.allow,
            ) {
                if (!status.notificationPermission && Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else AlertsCenter.openNotificationSettings(context)
            }
            if (status.exactAlarmsApplicable) {
                PermissionRow(str.permExact, str.permExactBody, ok = status.exactAlarms, okText = str.allowed, actionText = str.allow) {
                    AlertsCenter.openExactAlarmSettings(context)
                }
            }
            PermissionRow(str.permBattery, str.permBatteryBody, ok = status.batteryUnrestricted, okText = str.allowed, actionText = str.allow) {
                AlertsCenter.requestBatteryExemption(context)
            }
            if (status.blockedChannels.isNotEmpty()) {
                Banner(str.channelsBlocked(fmt.num(status.blockedChannels.size)), SakinaIcons.Warning, warning = true)
            }
            TextButton(onClick = { AlertsCenter.openNotificationSettings(context) }) { Text(str.channelsTitle) }
        }

        QuietCard(onOpen = { showQuiet = true })

        SectionCard(title = str.perPrayerTitle, icon = SakinaIcons.Bell, padding = PaddingValues(6.dp)) {
            PrayerKey.prayers.forEach { k ->
                val pa = s.alerts.prayer(k)
                val extras = buildList {
                    val pre = s.alerts.preFor(k)
                    val iq = s.alerts.iqamaFor(k)
                    if (pre > 0) add(str.preThisPrayer + " " + str.minutesValue(fmt.num(pre)))
                    if (iq > 0) add(str.iqamaThisPrayer + " " + str.minutesValue(fmt.num(iq)))
                }
                NavRow(
                    prayerIcon(k), str.prayer(k),
                    (listOf(str.modeShort(pa.mode)) + extras).joinToString(" · "),
                    onClick = { vm.push(Route.PrayerAlert(k)) },
                    modifier = Modifier.testTag("prayer_alert_" + k.name.lowercase()),
                )
            }
        }

        SectionCard(title = str.soundsTitle, icon = SakinaIcons.Music) {
            SoundEditor(str.generalSound, s.alerts.generalSound) { p -> updateAlerts(vm) { it.copy(generalSound = p) } }
            Divider()
            SoundEditor(str.fajrSound, s.alerts.fajrSound) { p -> updateAlerts(vm) { it.copy(fajrSound = p) } }
            Divider()
            Text(str.routeTitle, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            ChoiceRow(s.alerts.audioRoute == AudioRoute.NOTIFICATION, str.routeNotification, str.routeNotificationBody, onClick = { updateAlerts(vm) { it.copy(audioRoute = AudioRoute.NOTIFICATION) } })
            ChoiceRow(s.alerts.audioRoute == AudioRoute.ALARM, str.routeAlarm, str.routeAlarmBody, onClick = { updateAlerts(vm) { it.copy(audioRoute = AudioRoute.ALARM) } })
            TextButton(onClick = { vm.push(Route.Recordings) }) { Text(str.recordingsTitle) }
        }

        SectionCard(title = str.generalPre, icon = SakinaIcons.Clock) {
            MinutesControl(str.generalPre, s.alerts.preMinutes, 0, AlertSettings.MAX_OFFSET, { v -> updateAlerts(vm) { it.copy(preMinutes = v) } })
            Text(str.deliveryStyle, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            StyleSelector(s.alerts.preStyle) { st -> updateAlerts(vm) { it.copy(preStyle = st) } }
            Spacer(Modifier.height(12.dp))
            Divider()
            MinutesControl(str.generalIqama, s.alerts.iqamaMinutes, 0, AlertSettings.MAX_OFFSET, { v -> updateAlerts(vm) { it.copy(iqamaMinutes = v) } })
            Text(str.deliveryStyle, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            StyleSelector(s.alerts.iqamaStyle) { st -> updateAlerts(vm) { it.copy(iqamaStyle = st) } }
            Spacer(Modifier.height(6.dp))
            NoteText(str.iqamaNote)
        }

        SectionCard(title = str.suhoorSetting, icon = SakinaIcons.Fajr) {
            val su = s.alerts.suhoor
            SwitchRow(su.enabled, str.suhoorSetting, str.suhoorNote) { v -> updateAlerts(vm) { it.copy(suhoor = su.copy(enabled = v)) } }
            if (su.enabled) {
                MinutesControl(str.suhoorBefore, su.minutesBeforeFajr, AlertSettings.SUHOOR_MIN, AlertSettings.SUHOOR_MAX, { v -> updateAlerts(vm) { it.copy(suhoor = su.copy(minutesBeforeFajr = v)) } })
                SwitchRow(su.ramadanOnly, str.ramadanOnly) { v -> updateAlerts(vm) { it.copy(suhoor = su.copy(ramadanOnly = v)) } }
                StyleSelector(su.style) { st -> updateAlerts(vm) { it.copy(suhoor = su.copy(style = st)) } }
            }
        }

        SectionCard(padding = PaddingValues(6.dp)) {
            val on = s.alerts.allReminders.count { it.enabled }
            NavRow(SakinaIcons.Repeat, str.settingsReminders, if (on == 0) str.alertsAllOff else str.alertsOnCount(fmt.num(on)), onClick = { vm.push(Route.Reminders) })
        }

        SectionCard(title = str.chCard, icon = SakinaIcons.Info) {
            SwitchRow(s.alerts.ongoingCard, str.cardToggle, str.cardToggleBody) { v -> updateAlerts(vm) { it.copy(ongoingCard = v) } }
        }

        SectionCard(title = str.reliabilityTitle, icon = SakinaIcons.Battery) {
            Text(str.reliabilityBody, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(6.dp))
            SwitchRow(s.alerts.useAlarmClock, str.useAlarmClock, str.useAlarmClockBody) { v -> updateAlerts(vm) { it.copy(useAlarmClock = v) } }
        }

        SectionCard(title = str.testsTitle, icon = SakinaIcons.Flask) {
            NoteText(str.testsBody)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(AlertMode.NOTIFY to str.testNotify, AlertMode.ADHAN to str.testAdhan, AlertMode.EXCERPT to str.testExcerpt).forEach { (mode, label) ->
                    OutlinedButton(onClick = {
                        if (!status.notificationPermission && Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        AlertsCenter.test(context, mode)
                        Toast.makeText(context, str.testScheduled, Toast.LENGTH_SHORT).show()
                    }, modifier = Modifier.testTag("test_" + mode.name.lowercase())) { Text(label) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(str.recentTitle, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val log = remember(scheduled) { app.alertState.recentLog().take(8) }
            if (log.isEmpty()) NoteText(str.recentEmpty)
            log.forEach { e ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(fmt.time(e.at, ZoneId.systemDefault()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(76.dp))
                    Text(str.eventLabel(e.eventId) + " · " + str.outcomeName(e.outcome), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                }
            }
        }
    }
    if (showQuiet) QuietDialog(onDismiss = { showQuiet = false })
}

@Composable
fun QuietCard(onOpen: () -> Unit) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val context = LocalContext.current
    val app = context.applicationContext as SakinaApp
    val quiet by produceState(app.alertState.quietUntil) {
        while (true) {
            value = app.alertState.quietUntil
            delay(1000)
        }
    }
    SectionCard(title = str.quietTitle, icon = SakinaIcons.BellOff) {
        val q = quiet
        if (q != null) {
            Text(str.quietActive(fmt.time(q, ZoneId.systemDefault())), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Button(onClick = { AlertsCenter.endQuiet(context) }, modifier = Modifier.testTag("quiet_end")) { Text(str.quietEndNow) }
        } else {
            NoteText(str.quietBody)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onOpen, modifier = Modifier.testTag("quiet_open")) { Text(str.quietTitle) }
        }
        TextButton(onClick = { AlertsCenter.openSoundSettings(context) }) { Text(str.androidSound) }
    }
}

@Composable
fun QuietDialog(onDismiss: () -> Unit) {
    val str = LocalStrings.current
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(SakinaIcons.BellOff, null) },
        title = { Text(str.quietTitle) },
        text = {
            Column {
                Text(str.quietBody, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    str.quietDurations.forEach { (minutes, label) ->
                        OutlinedButton(onClick = {
                            AlertsCenter.startQuiet(context, Duration.ofMinutes(minutes))
                            onDismiss()
                        }, modifier = Modifier.testTag("quiet_$minutes")) { Text(label) }
                    }
                }
                TextButton(onClick = { AlertsCenter.openSoundSettings(context) }) { Text(str.androidSound) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str.cancel) } },
    )
}

@Composable
fun PrayerAlertScreen(vm: MainViewModel, s: AppSettings, key: PrayerKey) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val pa = s.alerts.prayer(key)
    AlertsSubScreen(str.prayer(key), vm) {
        SectionCard(title = str.atAdhanTime, icon = prayerIcon(key)) {
            AlertMode.entries.forEach { m ->
                ChoiceRow(pa.mode == m, str.modeName(m), str.modeDetail(m), onClick = { updateAlerts(vm) { it.withPrayer(key) { p -> p.copy(mode = m) } } })
            }
            Spacer(Modifier.height(6.dp))
            NoteText(str.unavailableWarning)
        }
        if (pa.mode == AlertMode.ADHAN || pa.mode == AlertMode.EXCERPT) {
            SectionCard(title = str.prayerSound, icon = SakinaIcons.Music) {
                ChoiceRow(pa.sound == null, if (key == PrayerKey.FAJR) str.useFajrSound else str.useGeneral, onClick = { updateAlerts(vm) { it.withPrayer(key) { p -> p.copy(sound = null) } } })
                ChoiceRow(pa.sound != null, str.customForPrayer, onClick = { updateAlerts(vm) { it.withPrayer(key) { p -> p.copy(sound = p.sound ?: it.soundFor(key)) } } })
                val custom = pa.sound
                if (custom != null) {
                    SoundEditor(str.customForPrayer, custom) { sp -> updateAlerts(vm) { it.withPrayer(key) { p -> p.copy(sound = sp) } } }
                }
            }
        }
        OffsetOverride(
            title = str.preThisPrayer, override = pa.preMinutes, general = s.alerts.preMinutes,
            onChange = { v -> updateAlerts(vm) { it.withPrayer(key) { p -> p.copy(preMinutes = v) } } },
        )
        OffsetOverride(
            title = str.iqamaThisPrayer, override = pa.iqamaMinutes, general = s.alerts.iqamaMinutes,
            onChange = { v -> updateAlerts(vm) { it.withPrayer(key) { p -> p.copy(iqamaMinutes = v) } } },
            note = str.iqamaNote,
        )
    }
}

@Composable
private fun OffsetOverride(title: String, override: Int?, general: Int, onChange: (Int?) -> Unit, note: String? = null) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    SectionCard(title = title, icon = SakinaIcons.Clock) {
        val generalText = if (general == 0) str.minutesOff else str.minutesValue(fmt.num(general))
        ChoiceRow(override == null, str.inheritValue(generalText), onClick = { onChange(null) })
        ChoiceRow(override != null, str.customForPrayer, onClick = { onChange(override ?: general) })
        if (override != null) MinutesControl(title, override, 0, AlertSettings.MAX_OFFSET, { onChange(it) })
        if (note != null) NoteText(note)
    }
}

@Composable
fun RemindersScreen(vm: MainViewModel, s: AppSettings) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    var editing by remember { mutableStateOf<ReminderKind?>(null) }
    AlertsSubScreen(str.settingsReminders, vm) {
        NoteText(str.remindersNote)
        s.alerts.allReminders.forEach { r ->
            SectionCard {
                SwitchRow(r.enabled, str.reminderName(r.kind), fmt.clock(r.minuteOfDay / 60, r.minuteOfDay % 60) + " · " + daysSummary(r.days, str, fmt)) { v ->
                    updateAlerts(vm) { it.withReminder(r.kind) { x -> x.copy(enabled = v) } }
                }
                if (r.enabled) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(str.reminderTime, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { editing = r.kind }) { Text(fmt.clock(r.minuteOfDay / 60, r.minuteOfDay % 60)) }
                    }
                    Text(str.reminderDays, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        weekOrder(str.isAr).forEach { d ->
                            FilterChip(
                                selected = d in r.days,
                                onClick = {
                                    updateAlerts(vm) { it.withReminder(r.kind) { x -> x.copy(days = if (d in x.days && x.days.size > 1) x.days - d else x.days + d) } }
                                },
                                label = { Text(fmt.weekday(java.time.DayOfWeek.of(d), short = true)) },
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    StyleSelector(r.style) { st -> updateAlerts(vm) { it.withReminder(r.kind) { x -> x.copy(style = st) } } }
                }
            }
        }
    }
    editing?.let { kind ->
        val r = s.alerts.allReminders.first { it.kind == kind }
        val state = rememberTimePickerState(initialHour = r.minuteOfDay / 60, initialMinute = r.minuteOfDay % 60, is24Hour = s.clock24)
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(str.pickTime) },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    updateAlerts(vm) { it.withReminder(kind) { x -> x.copy(minuteOfDay = state.hour * 60 + state.minute) } }
                    editing = null
                }) { Text(str.save) }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(str.cancel) } },
        )
    }
}

private fun weekOrder(arabic: Boolean): List<Int> = if (arabic) listOf(6, 7, 1, 2, 3, 4, 5) else listOf(1, 2, 3, 4, 5, 6, 7)

private fun daysSummary(days: Set<Int>, str: com.sakina.app.ui.Strings, fmt: com.sakina.app.core.format.Fmt): String =
    if (days.size == 7) str.everyDay else weekOrder(str.isAr).filter { it in days }.joinToString("، ") { fmt.weekday(java.time.DayOfWeek.of(it), short = true) }

@Composable
fun RecordingsScreen(vm: MainViewModel) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val context = LocalContext.current
    AlertsSubScreen(str.recordingsTitle, vm) {
        Banner(str.recordingsNote)
        SoundLibrary.bundled.forEach { b ->
            SectionCard(title = if (str.isAr) b.titleAr else b.titleEn, icon = SakinaIcons.Music) {
                com.sakina.app.ui.components.Badge(str.candidateBadge)
                Spacer(Modifier.height(8.dp))
                Text(if (str.isAr) b.descriptionAr else b.descriptionEn, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Meta(str.authorLabel, b.author)
                Meta(str.licenseLabel, b.license)
                Meta(str.durationLabel, fmt.digits("${b.durationSec / 60}:${(b.durationSec % 60).toString().padStart(2, '0')}"))
                Meta(str.sourceLabel, b.sourceUrl, ltr = true)
                Meta(str.checksumLabel, b.sha256, ltr = true)
                Spacer(Modifier.height(6.dp))
                SoundEditorPreviewOnly(b.id)
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(b.sourceUrl)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }) { Text(str.openSourcePage) }
            }
        }
    }
}

@Composable
private fun Meta(label: String, value: String, ltr: Boolean = false) {
    Column(Modifier.padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if (ltr) "⁦$value⁩" else value, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SoundEditorPreviewOnly(id: String) {
    val str = LocalStrings.current
    val context = LocalContext.current
    val playing by com.sakina.app.alerts.AdhanPreview.playing.collectAsStateWithLifecycle()
    val ref = com.sakina.app.core.alerts.SoundRef(bundled = id)
    val isPlaying = playing == id
    DisposableEffect(Unit) { onDispose { com.sakina.app.alerts.AdhanPreview.stop() } }
    Button(onClick = { com.sakina.app.alerts.AdhanPreview.toggle(context, ref, 0.8f) }) {
        Icon(if (isPlaying) SakinaIcons.StopSq else SakinaIcons.Play, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(if (isPlaying) str.stopPreview else str.preview)
    }
}
