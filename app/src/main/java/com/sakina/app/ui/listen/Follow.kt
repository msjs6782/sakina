package com.sakina.app.ui.listen

import android.content.ComponentName
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.sakina.app.audio.PlaybackDiagnostics
import com.sakina.app.audio.RecitationService
import com.sakina.app.core.quran.AyahRef
import com.sakina.app.core.recitation.Reciter
import com.sakina.app.core.recitation.Recording
import com.sakina.app.core.recitation.TimingCheck
import com.sakina.app.core.recitation.TimingMap
import com.sakina.app.core.recitation.TimingParser
import com.sakina.app.data.QuranData
import com.sakina.app.data.TimingState
import com.sakina.app.ui.LocalFmt
import com.sakina.app.ui.LocalStrings
import com.sakina.app.ui.MainViewModel
import com.sakina.app.ui.Route
import com.sakina.app.ui.Strings
import com.sakina.app.ui.components.SakinaIcons
import kotlinx.coroutines.delay

/** Connects to the recitation service while a screen is shown, and reconnects if the session goes away. */
@Composable
fun rememberRecitationController(): MediaController? {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    DisposableEffect(attempt) {
        val token = SessionToken(context, ComponentName(context, RecitationService::class.java))
        val future = MediaController.Builder(context, token)
            .setListener(object : MediaController.Listener {
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

/**
 * The recitation as a reading screen follows it: the recording, whether it plays, and the ayah it is on (from the
 * surah's timing map, when that map is valid and fits the recording; never guessed).
 */
data class Follow(
    val recording: Recording? = null,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    /** Ayah number in the surah, or null when there is no usable timing map (or before ayah 1). */
    val ayah: Int? = null,
    val timing: TimingState = TimingState.Missing,
) {
    /** The global index of the ayah being recited, for highlighting. */
    fun global(d: QuranData): Int? {
        val r = recording ?: return null
        val a = ayah ?: return null
        return d.meta.global(AyahRef(r.sura, a))
    }
}

/** Where a reading screen asked the recitation to start; applied once the timing map and the duration are known. */
private object PendingStart {
    var recordingId: String? = null
    var ayah: Int = 1
}

private fun usableMap(state: TimingState, durationMs: Long): TimingMap? {
    val map = ((state as? TimingState.Ready)?.check as? TimingCheck.Valid)?.map ?: return null
    if (durationMs <= 0) return null
    return (TimingParser.fits(map, durationMs) as? TimingCheck.Valid)?.map
}

/** The recitation state, refreshed four times a second while the screen is shown. */
@Composable
fun rememberFollow(vm: MainViewModel, controller: MediaController?): Follow {
    val repo = vm.recitations
    LaunchedEffect(Unit) { repo.init() }
    val timings by repo.timing.collectAsStateWithLifecycle()
    var f by remember { mutableStateOf(Follow()) }
    LaunchedEffect(controller, timings) {
        while (controller != null) {
            val rec = Recording.parse(controller.currentMediaItem?.mediaId)
            val dur = controller.duration.takeIf { it > 0 } ?: 0
            val pos = controller.currentPosition.coerceAtLeast(0)
            val state = rec?.let { timings[it.id] } ?: TimingState.Missing
            val map = usableMap(state, dur)
            // A start ayah asked for before the map or the duration were known.
            if (rec != null && PendingStart.recordingId == rec.id && map != null) {
                val a = PendingStart.ayah.coerceIn(1, map.ayahs.size)
                PendingStart.recordingId = null
                if (a > 1) controller.seekTo(map.ayahs[a - 1].startMs)
            }
            f = Follow(
                recording = rec,
                playing = controller.isPlaying,
                buffering = controller.playbackState == Player.STATE_BUFFERING,
                positionMs = pos,
                durationMs = dur,
                ayah = map?.ayahAt(pos),
                timing = state,
            )
            delay(250)
        }
    }
    return f
}

/**
 * Plays [sura] with the chosen reciter from ayah [aya]. The timing map is fetched when missing; the start is
 * applied as soon as it is known (from ayah 1 until then).
 */
fun startRecitation(vm: MainViewModel, controller: MediaController, d: QuranData, str: Strings, sura: Int, aya: Int) {
    val reciter = Reciter.byKey(vm.quranState.value.listen.reciter) ?: Reciter.ALAFASY
    val rec = Recording(reciter, sura)
    val repo = vm.recitations
    if (repo.timingState(rec) !is TimingState.Ready) repo.fetchTiming(rec)
    PendingStart.recordingId = rec.id
    PendingStart.ayah = aya
    val s = d.meta.surah(sura)
    val title = str.surahTitle(s.nameAr, s.nameTranslit)
    controller.setMediaItem(
        MediaItem.Builder().setMediaId(rec.id)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(str.reciterName(reciter)).setDisplayTitle(title).build())
            .build(),
    )
    controller.setPlaybackSpeed(vm.quranState.value.listen.speed)
    controller.prepare()
    controller.play()
}

/** Play or pause; at the end or when stopped, start again. */
fun MediaController.toggle() {
    when {
        isPlaying -> pause()
        playbackState == Player.STATE_ENDED -> { seekTo(0); play() }
        playbackState == Player.STATE_IDLE -> { prepare(); play() }
        else -> play()
    }
}

/**
 * The small player under a reading screen: the surah and ayah being recited, play/pause, follow (keep the
 * recited ayah in view) and stop. Tapping it opens the full player.
 */
@Composable
fun FollowBar(vm: MainViewModel, d: QuranData, controller: MediaController, f: Follow, following: Boolean, onFollow: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val rec = f.recording ?: return
    val s = d.meta.surah(rec.sura)
    Surface(
        shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.secondaryContainer, tonalElevation = 3.dp, shadowElevation = 4.dp,
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).testTag("follow_bar"),
    ) {
        Column {
            Row(Modifier.padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledIconButton(onClick = { controller.toggle() }, modifier = Modifier.size(44.dp).testTag("follow_toggle")) {
                    Icon(if (f.playing) SakinaIcons.Pause else SakinaIcons.Play, if (f.playing) str.pause else str.play)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f).clickable { vm.push(Route.Listen(rec.sura)) }) {
                    Text(
                        str.surahTitle(s.nameAr, s.nameTranslit) + (f.ayah?.let { " · " + str.ayahN(fmt.num(it)) } ?: ""),
                        style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.testTag("follow_title"),
                    )
                    val sub = when {
                        f.buffering -> str.buffering
                        f.timing is TimingState.Loading -> str.timingLoading
                        f.ayah == null && f.timing !is TimingState.Ready -> str.followNoTiming
                        else -> str.reciterName(rec.reciter)
                    }
                    Text(sub, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f))
                }
                FilledTonalIconToggleButton(checked = following, onCheckedChange = onFollow, modifier = Modifier.testTag("follow_follow")) {
                    Icon(SakinaIcons.Lines, str.followAyah)
                }
                IconButton(onClick = { controller.stop(); controller.clearMediaItems() }, modifier = Modifier.testTag("follow_stop")) {
                    Icon(SakinaIcons.StopSq, str.stopPlayback, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            val p = if (f.durationMs > 0) (f.positionMs.toFloat() / f.durationMs).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp))
        }
    }
}
