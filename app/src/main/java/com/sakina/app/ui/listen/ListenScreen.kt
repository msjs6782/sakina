@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sakina.app.ui.listen

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.sakina.app.alerts.AlertsCenter
import com.sakina.app.audio.PlaybackDiagnostics
import com.sakina.app.audio.RecitationService
import com.sakina.app.core.recitation.LibraryLimits
import com.sakina.app.core.recitation.Reciter
import com.sakina.app.core.recitation.Recording
import com.sakina.app.core.recitation.RepeatCount
import com.sakina.app.core.recitation.SPEEDS
import com.sakina.app.core.recitation.TimingCheck
import com.sakina.app.core.recitation.TimingParser
import com.sakina.app.data.NetState
import com.sakina.app.data.QuranData
import com.sakina.app.data.RecordingDownload
import com.sakina.app.data.TimingState
import com.sakina.app.ui.LocalFmt
import com.sakina.app.ui.LocalStrings
import com.sakina.app.ui.MainViewModel
import com.sakina.app.ui.components.Banner
import com.sakina.app.ui.components.DirectionalIcon
import com.sakina.app.ui.components.NoteText
import com.sakina.app.ui.components.SakinaIcons
import com.sakina.app.ui.components.ScreenBar
import com.sakina.app.ui.components.SectionCard
import com.sakina.app.ui.components.SwitchRow
import com.sakina.app.ui.components.exposeTestTags
import com.sakina.app.ui.location.FullScreen
import com.sakina.app.ui.quran.NumberStar
import com.sakina.app.ui.quran.WithQuran
import com.sakina.app.ui.theme.Amiri
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration

/** Connects to the recitation service while the screen is shown. */
@Composable
private fun rememberController(): MediaController? {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    DisposableEffect(attempt) {
        val token = SessionToken(context, ComponentName(context, RecitationService::class.java))
        val future = MediaController.Builder(context, token)
            .setListener(object : MediaController.Listener {
                // If the session goes away (the service ended), connect again so the screen stays live.
                override fun onDisconnected(controller: MediaController) {
                    PlaybackDiagnostics.log(context, "screen controller disconnected; reconnecting")
                    attempt++
                }
            })
            .buildAsync()
        future.addListener({ controller = runCatching { future.get() }.getOrNull() }, MoreExecutors.directExecutor())
        onDispose {
            MediaController.releaseFuture(future)
            controller = null
        }
    }
    return controller
}

/** A snapshot of the player, refreshed a few times a second. */
private data class PlayerView(
    val recording: Recording? = null,
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val speed: Float = 1f,
    val extras: Bundle = Bundle.EMPTY,
)

private fun MediaController.view(): PlayerView = PlayerView(
    recording = Recording.parse(currentMediaItem?.mediaId),
    isPlaying = isPlaying,
    buffering = playbackState == Player.STATE_BUFFERING,
    positionMs = currentPosition.coerceAtLeast(0),
    durationMs = duration.takeIf { it > 0 } ?: 0,
    speed = playbackParameters.speed,
    extras = sessionExtras,
)

@Composable
fun ListenScreen(vm: MainViewModel, focusSura: Int) {
    val str = LocalStrings.current
    FullScreen {
        Column(Modifier.fillMaxSize()) {
            ScreenBar(str.listenTitle, onBack = { vm.pop() })
            WithQuran(vm) { d -> ListenBody(vm, d, focusSura) }
        }
    }
}

@Composable
private fun ListenBody(vm: MainViewModel, d: QuranData, focusSura: Int) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val context = LocalContext.current
    val repo = vm.recitations
    LaunchedEffect(Unit) { repo.init() }
    val qs by vm.quranState.collectAsStateWithLifecycle()
    val downloaded by repo.downloaded.collectAsStateWithLifecycle()
    val downloads by repo.downloads.collectAsStateWithLifecycle()
    val timing by repo.timing.collectAsStateWithLifecycle()
    val reciter = Reciter.byKey(qs.listen.reciter) ?: Reciter.ALAFASY
    val controller = rememberController()
    var pv by remember { mutableStateOf(PlayerView()) }
    LaunchedEffect(controller) {
        while (controller != null) {
            pv = controller.view()
            delay(250)
        }
    }

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = if (focusSura in 1..114) focusSura + 1 else 0)
    val scope = rememberCoroutineScope()

    fun play(r: Recording) {
        val c = controller ?: return
        // Bring the player into view.
        scope.launch { listState.animateScrollToItem(1) }
        if (pv.recording == r && c.playbackState != Player.STATE_IDLE && c.playbackState != Player.STATE_ENDED) {
            c.play()
            return
        }
        val title = str.surahTitle(d.meta.surah(r.sura).nameAr, d.meta.surah(r.sura).nameTranslit)
        val item = MediaItem.Builder()
            .setMediaId(r.id)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(str.reciterName(r.reciter)).setDisplayTitle(title).build())
            .build()
        c.setMediaItem(item)
        c.setPlaybackSpeed(qs.listen.speed)
        c.prepare()
        c.play()
    }

    LazyColumn(Modifier.fillMaxSize().testTag("listen_list"), state = listState, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(str.reciterLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Reciter.entries.forEach { r ->
                        FilterChip(
                            selected = r == reciter,
                            onClick = { vm.updateQuran { it.copy(listen = it.listen.copy(reciter = r.key)) } },
                            label = { Text(str.reciterName(r)) },
                            modifier = Modifier.testTag("reciter_${r.key}"),
                        )
                    }
                }
                NoteText(str.recordingSet + " · " + str.libraryUse(fmt.bytes(downloaded.values.sum()), fmt.bytes(LibraryLimits.LIBRARY, 0)))
            }
        }
        item { PlayerCard(vm, d, controller, pv, timing, qs.listen.repeatTimes) }
        items((1..114).toList(), key = { "s$it" }) { s ->
            val rec = Recording(reciter, s)
            SurahRow(vm, d, rec, playing = pv.recording == rec && pv.isPlaying, downloaded = rec.id in downloaded, dl = downloads[rec.id], onPlay = { play(rec) }, onPause = { controller?.pause() })
        }
        item { DiagnosticsCard(context) }
        item { NoteText(str.listenSources, Modifier.padding(vertical = 8.dp)) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SurahRow(vm: MainViewModel, d: QuranData, rec: Recording, playing: Boolean, downloaded: Boolean, dl: RecordingDownload?, onPlay: () -> Unit, onPause: () -> Unit) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val repo = vm.recitations
    val s = d.meta.surah(rec.sura)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { if (playing) onPause() else onPlay() }
            .padding(vertical = 4.dp, horizontal = 4.dp).testTag("listen_surah_${rec.sura}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NumberStar(rec.sura, 36.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(s.nameAr, style = MaterialTheme.typography.titleMedium.copy(fontFamily = Amiri, fontWeight = FontWeight.Bold))
            val info = str.recordingInfo(fmt.countdown(Duration.ofMillis(repo.sources.durationMs(rec))), fmt.bytes(repo.sources.bytes(rec)))
            Text(
                info + if (downloaded) " · " + str.downloadedLabel else "",
                style = MaterialTheme.typography.bodySmall,
                color = if (downloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (dl is RecordingDownload.Failed) Text(str.recordingFailure(dl.reason), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("listen_fail_${rec.sura}"))
        }
        when {
            dl is RecordingDownload.Running -> Box(contentAlignment = Alignment.Center, modifier = Modifier.size(48.dp)) {
                val p = if (dl.total > 0) (dl.bytes.toFloat() / dl.total).coerceIn(0f, 1f) else 0f
                CircularProgressIndicator(progress = { p }, modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
                IconButton(onClick = { repo.cancel(rec) }, modifier = Modifier.testTag("listen_cancel_${rec.sura}")) { Icon(SakinaIcons.Close, str.cancelDownload, Modifier.size(18.dp)) }
            }
            downloaded -> IconButton(onClick = { DeleteHolder.ask = rec }, modifier = Modifier.testTag("listen_delete_${rec.sura}")) {
                Icon(SakinaIcons.Trash, str.deleteRecording, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> IconButton(onClick = {
                repo.clearFailure(rec)
                repo.download(rec, vm.mushaf.network() != NetState.OFFLINE)
            }, modifier = Modifier.testTag("listen_download_${rec.sura}")) { Icon(SakinaIcons.Download, str.downloadRecording) }
        }
        FilledIconButton(onClick = { if (playing) onPause() else onPlay() }, modifier = Modifier.testTag("listen_play_${rec.sura}")) {
            Icon(if (playing) SakinaIcons.Pause else SakinaIcons.Play, if (playing) str.pause else str.play)
        }
    }
    if (DeleteHolder.ask == rec) {
        AlertDialog(
            onDismissRequest = { DeleteHolder.ask = null },
            text = { Text(str.deleteRecording + " · " + s.nameAr + " · " + str.reciterName(rec.reciter)) },
            confirmButton = {
                TextButton(onClick = {
                    DeleteHolder.ask = null
                    vm.deleteRecording(rec)
                }, modifier = Modifier.exposeTestTags().testTag("listen_delete_ok")) { Text(str.delete, color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { DeleteHolder.ask = null }) { Text(str.cancel) } },
        )
    }
}

/** Which recording's delete question is open (one at a time). */
private object DeleteHolder {
    var ask by mutableStateOf<Recording?>(null)
}

@Composable
private fun PlayerCard(vm: MainViewModel, d: QuranData, controller: MediaController?, pv: PlayerView, timing: Map<String, TimingState>, repeatTimes: Int) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val context = LocalContext.current
    val rec = pv.recording
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().testTag("listen_player")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (rec == null || controller == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(SakinaIcons.Headphones, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(str.nothingPlaying, style = MaterialTheme.typography.bodyMedium)
                }
                QuietNotice(vm, pv)
                return@Column
            }
            val s = d.meta.surah(rec.sura)
            Text(str.surahTitle(s.nameAr, s.nameTranslit), style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("listen_now_title"))
            Text(
                str.reciterName(rec.reciter) + " · " + (if (vm.recitations.isDownloaded(rec)) str.fromDevice else str.streaming) + if (pv.buffering) " · " + str.buffering else "",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            var dragging by remember { mutableStateOf<Float?>(null) }
            val dur = pv.durationMs.coerceAtLeast(1)
            Slider(
                value = dragging ?: (pv.positionMs.toFloat() / dur).coerceIn(0f, 1f),
                onValueChange = { dragging = it },
                onValueChangeFinished = { dragging?.let { controller.seekTo((it * dur).toLong()) }; dragging = null },
                modifier = Modifier.testTag("listen_seek"),
            )
            Row {
                Text(fmt.countdown(Duration.ofMillis(pv.positionMs)), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f).testTag("listen_position"))
                Text(fmt.countdown(Duration.ofMillis(pv.durationMs)), style = MaterialTheme.typography.labelMedium)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { controller.stop(); controller.clearMediaItems() }, modifier = Modifier.testTag("listen_stop")) { Icon(SakinaIcons.StopSq, str.stopPlayback) }
                // Back and forward arrows follow the reading direction of the screen's language.
                IconButton(onClick = { controller.seekBack() }, modifier = Modifier.testTag("listen_back")) { DirectionalIcon(SakinaIcons.Rewind, str.back10) }
                FilledIconButton(onClick = {
                    when {
                        pv.isPlaying -> controller.pause()
                        controller.playbackState == Player.STATE_ENDED -> { controller.seekTo(0); controller.play() }
                        controller.playbackState == Player.STATE_IDLE -> { controller.prepare(); controller.play() }
                        else -> controller.play()
                    }
                }, modifier = Modifier.size(64.dp).testTag("listen_toggle")) {
                    Icon(if (pv.isPlaying) SakinaIcons.Pause else SakinaIcons.Play, if (pv.isPlaying) str.pause else str.play, Modifier.size(30.dp))
                }
                IconButton(onClick = { controller.seekForward() }, modifier = Modifier.testTag("listen_forward")) { DirectionalIcon(SakinaIcons.FastForward, str.forward10) }
                Spacer(Modifier.size(48.dp))
            }
            Text(str.speedLabel, style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SPEEDS.forEach { sp ->
                    FilterChip(
                        selected = kotlin.math.abs(pv.speed - sp) < 0.01f,
                        onClick = { controller.setPlaybackSpeed(sp); vm.updateQuran { it.copy(listen = it.listen.copy(speed = sp)) } },
                        label = { Text(speedText(fmt, sp)) },
                        modifier = Modifier.testTag("speed_$sp"),
                    )
                }
            }
            QuietNotice(vm, pv)
            RepeatPanel(vm, d, controller, rec, pv, timing[rec.id] ?: TimingState.Missing, repeatTimes)
        }
    }
}

@Composable
private fun QuietNotice(vm: MainViewModel, pv: PlayerView) {
    val str = LocalStrings.current
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(pv.extras.getLong(RecitationService.EXTRA_QUIET_REFUSED)) { tick++ }
    if (tick >= 0 && AlertsCenter.isQuiet(context)) {
        Banner(str.quietBlocksPlay, SakinaIcons.BellOff, modifier = Modifier.testTag("listen_quiet")) {
            TextButton(onClick = { AlertsCenter.endQuiet(context); tick++ }) { Text(str.endQuiet) }
        }
    }
}

@Composable
private fun RepeatPanel(vm: MainViewModel, d: QuranData, controller: MediaController, rec: Recording, pv: PlayerView, state: TimingState, repeatTimes: Int) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val count = d.meta.surah(rec.sura).ayahCount
    var from by remember(rec) { mutableIntStateOf(1) }
    var to by remember(rec) { mutableIntStateOf(1) }
    val active = pv.extras.getBoolean(RecitationService.EXTRA_REPEAT_ACTIVE)
    Column(Modifier.fillMaxWidth().padding(top = 4.dp).testTag("repeat_panel"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(SakinaIcons.Repeat, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(str.repeatTitle, style = MaterialTheme.typography.titleSmall)
        }
        // A map is usable only if it is well formed and fits this recording's real length.
        val map = ((state as? TimingState.Ready)?.check as? TimingCheck.Valid)?.map
        val fit = if (map != null && pv.durationMs > 0) TimingParser.fits(map, pv.durationMs) else null
        when {
            state is TimingState.Missing -> {
                NoteText(str.timingNeeded)
                OutlinedButton(onClick = { vm.recitations.fetchTiming(rec) }, modifier = Modifier.testTag("timing_download")) { Text(str.timingDownload) }
            }
            state is TimingState.Loading -> NoteText(str.timingLoading)
            state is TimingState.Failed -> {
                Text(str.timingFailed, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { vm.recitations.fetchTiming(rec) }, modifier = Modifier.testTag("timing_download")) { Text(str.retry) }
            }
            state is TimingState.Ready && state.check is TimingCheck.Invalid ->
                Banner(str.timingInvalid((state.check as TimingCheck.Invalid).problem), SakinaIcons.Warning, warning = true, modifier = Modifier.testTag("timing_invalid"))
            fit is TimingCheck.Invalid -> Banner(str.timingInvalid(fit.problem), SakinaIcons.Warning, warning = true, modifier = Modifier.testTag("timing_invalid"))
            map != null && fit == null -> NoteText(str.timingWaitAudio)
            map != null -> {
                map.ayahAt(pv.positionMs)?.let { Text(str.currentAyah(fmt.num(it)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("current_ayah")) }
                if (active) {
                    val total = pv.extras.getInt(RecitationService.EXTRA_REPEAT_COUNT)
                    Text(
                        str.repeatProgress(fmt.num(pv.extras.getInt(RecitationService.EXTRA_REPEAT_FROM)), fmt.num(pv.extras.getInt(RecitationService.EXTRA_REPEAT_TO)),
                            fmt.num(pv.extras.getInt(RecitationService.EXTRA_REPEAT_PLAYED) + 1), if (total > 0) fmt.num(total) else null),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("repeat_status"),
                    )
                    OutlinedButton(onClick = { controller.sendCustomCommand(SessionCommand(RecitationService.ACTION_REPEAT_CLEAR, Bundle.EMPTY), Bundle.EMPTY) }, modifier = Modifier.testTag("repeat_stop")) { Text(str.repeatStop) }
                } else {
                    if (pv.extras.getBoolean(RecitationService.EXTRA_REPEAT_FINISHED)) NoteText(str.repeatDone)
                    AyahStepper(str.repeatFrom, from, 1, count, "repeat_from") { from = it; if (to < it) to = it }
                    AyahStepper(str.repeatTo, to, from, count, "repeat_to") { to = it }
                    Text(str.repeatTimes, style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RepeatCount.entries.forEach { c ->
                            FilterChip(selected = repeatTimes == c.times, onClick = { vm.updateQuran { it.copy(listen = it.listen.copy(repeatTimes = c.times)) } },
                                label = { Text(str.repeatTimesLabel(c.times)) }, modifier = Modifier.testTag("repeat_x${if (c.times < 0) "inf" else c.times}"))
                        }
                    }
                    Button(onClick = {
                        controller.sendCustomCommand(
                            SessionCommand(RecitationService.ACTION_REPEAT, Bundle.EMPTY),
                            Bundle().apply {
                                putInt(RecitationService.ARG_FROM, from); putInt(RecitationService.ARG_TO, to); putInt(RecitationService.ARG_COUNT, repeatTimes)
                            },
                        )
                    }, modifier = Modifier.fillMaxWidth().testTag("repeat_start")) { Text(str.repeatStart) }
                }
            }
        }
    }
}

@Composable
private fun AyahStepper(label: String, value: Int, min: Int, max: Int, tag: String, onChange: (Int) -> Unit) {
    val fmt = LocalFmt.current
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange((value - 1).coerceAtLeast(min)) }, enabled = value > min, modifier = Modifier.testTag("${tag}_minus")) { Text("−", style = MaterialTheme.typography.titleLarge) }
        Text(fmt.num(value), style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(44.dp).testTag("${tag}_value"), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        IconButton(onClick = { onChange((value + 1).coerceAtMost(max)) }, enabled = value < max, modifier = Modifier.testTag("${tag}_plus")) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
private fun DiagnosticsCard(context: Context) {
    val str = LocalStrings.current
    var on by remember { mutableStateOf(PlaybackDiagnostics.isEnabled(context)) }
    var preview by remember { mutableStateOf<String?>(null) }
    SectionCard(title = str.diagTitle, icon = SakinaIcons.Flask, modifier = Modifier.padding(top = 8.dp)) {
        Box(Modifier.testTag("diag_switch")) {
            SwitchRow(on, str.diagSwitch, str.diagNote) { v -> PlaybackDiagnostics.setEnabled(context, v); on = v }
        }
        if (on) {
            TextButton(onClick = { preview = PlaybackDiagnostics.report(context) }, modifier = Modifier.testTag("diag_preview")) { Text(str.diagPreview) }
        }
    }
    preview?.let { text ->
        AlertDialog(
            onDismissRequest = { preview = null },
            title = { Text(str.diagTitle) },
            text = {
                // The report is technical English text: always left to right.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()).exposeTestTags().testTag("diag_report")) {
                        Text(text, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, textDirection = TextDirection.Ltr))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                    context.startActivity(Intent.createChooser(send, str.diagShare).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text(str.diagShare) }
            },
            dismissButton = {
                TextButton(onClick = { PlaybackDiagnostics.clear(context); preview = null }) { Text(str.diagClear) }
            },
        )
    }
}

private fun speedText(fmt: com.sakina.app.core.format.Fmt, sp: Float): String {
    val t = when (sp) {
        0.75f -> "0.75"
        1f -> "1"
        1.25f -> "1.25"
        else -> "1.5"
    }
    return fmt.digits(if (fmt.arabicDigits) t.replace('.', '٫') else t) + "×"
}
