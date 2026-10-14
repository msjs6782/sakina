package com.sakina.app.audio

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.PlayerMessage
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.sakina.app.MainActivity
import com.sakina.app.R
import com.sakina.app.SakinaApp
import com.sakina.app.alerts.AlertsCenter
import com.sakina.app.core.recitation.Recording
import com.sakina.app.core.recitation.RepeatCount
import com.sakina.app.core.recitation.RepeatRun
import com.sakina.app.core.recitation.TimingCheck
import com.sakina.app.core.recitation.TimingParser
import com.sakina.app.data.TimingState

/**
 * Plays recitations. Started only when the user plays something; shows Android's media
 * notification and lock-screen controls through a MediaSession.
 *
 * Audio focus is handled here rather than by ExoPlayer, because playback must never resume by
 * itself: losing focus (a call, the adhan, another app) pauses, and regaining it does nothing.
 * A session left paused for ten minutes is ended. There is no playback resumption after a reboot.
 */
@OptIn(UnstableApi::class)
class RecitationService : MediaSessionService() {
    private lateinit var exo: ExoPlayer
    private var session: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private val idleEnd = Runnable { endSession("paused for ${idleEndMs / 1000} s") }
    private lateinit var audio: AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var repeat: RepeatRun? = null
    private var repeatMessage: PlayerMessage? = null
    private var repeatFinished = false
    private var quietRefusedAt = 0L

    private val app get() = application as SakinaApp

    private fun diag(line: String) {
        Log.i(TAG, line)
        PlaybackDiagnostics.log(this, line)
    }

    // ---------------- audio focus ----------------

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (exo.playWhenReady) diag("audio focus lost ($change): paused; it will not resume by itself")
                exo.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> exo.volume = 0.3f
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Only restore the volume after ducking. Never resume playback here.
                exo.volume = 1f
                diag("audio focus regained; playback stays as it is")
            }
        }
    }

    private fun requestFocus(): Boolean {
        val req = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAcceptsDelayedFocusGain(false)
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener(focusListener, handler)
            .build().also { focusRequest = it }
        val granted = audio.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!granted) diag("audio focus refused; not playing")
        return granted
    }

    private fun abandonFocus() {
        focusRequest?.let { audio.abandonAudioFocusRequest(it) }
    }

    /** Every way of starting playback (app, notification, lock screen, headset) passes through here. */
    private fun mayPlay(): Boolean {
        if (AlertsCenter.isQuiet(this)) {
            quietRefusedAt = System.currentTimeMillis()
            diag("play refused: Sakina quiet mode is on")
            publishExtras()
            return false
        }
        return requestFocus()
    }

    private inner class GuardedPlayer(p: Player) : ForwardingPlayer(p) {
        override fun play() {
            if (mayPlay()) super.play()
        }

        override fun setPlayWhenReady(playWhenReady: Boolean) {
            if (!playWhenReady || mayPlay()) super.setPlayWhenReady(playWhenReady)
        }

        override fun stop() {
            clearRepeat(finished = false)
            super.stop()
        }
    }

    // ---------------- lifecycle ----------------

    override fun onCreate() {
        super.onCreate()
        instance = this
        audio = getSystemService(AudioManager::class.java)
        exo = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), false)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(SEEK_MS)
            .setSeekForwardIncrementMs(SEEK_MS)
            .build()
        exo.addListener(listener)
        val provider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(CHANNEL_ID)
            .setChannelName(R.string.recitation_channel)
            .setNotificationId(NOTIFICATION_ID)
            .build()
        provider.setSmallIcon(R.drawable.ic_stat_sakina)
        setMediaNotificationProvider(provider)
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_LISTEN).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, GuardedPlayer(exo))
            .setCallback(callback)
            .setSessionActivity(open)
            .setCustomLayout(seekButtons())
            .build()
        diag("service created")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!exo.playWhenReady || exo.mediaItemCount == 0 || exo.playbackState == Player.STATE_ENDED) {
            endSession("app closed while not playing")
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        clearRepeat(finished = false)
        abandonFocus()
        session?.run {
            player.release()
            release()
        }
        session = null
        if (instance === this) instance = null
        diag("service destroyed")
        super.onDestroy()
    }

    /** Stops playback, empties the player (which removes the notification) and lets the service go. */
    fun endSession(reason: String) {
        diag("session ended: $reason")
        handler.removeCallbacks(idleEnd)
        clearRepeat(finished = false)
        exo.stop()
        exo.clearMediaItems()
        abandonFocus()
        stopSelf()
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            handler.removeCallbacks(idleEnd)
            if (!isPlaying && exo.mediaItemCount > 0) handler.postDelayed(idleEnd, idleEndMs)
            diag(if (isPlaying) "playing ${exo.currentMediaItem?.mediaId} at ${exo.currentPosition} ms, speed ${exo.playbackParameters.speed}" else "paused at ${exo.currentPosition} ms")
        }

        override fun onPlaybackStateChanged(state: Int) {
            diag("state ${listOf("", "IDLE", "BUFFERING", "READY", "ENDED").getOrElse(state) { "$state" }}, duration ${exo.duration}")
            if (state == Player.STATE_ENDED) clearRepeat(finished = false)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            clearRepeat(finished = false)
            repeatFinished = false
            publishExtras()
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            val r = repeat ?: return
            // The user moved outside the repeated range: the repeat no longer applies.
            if (reason == Player.DISCONTINUITY_REASON_SEEK && newPosition.positionMs !in (r.startMs - 300)..r.endMs) {
                diag("seek to ${newPosition.positionMs} ms left the repeated range; repeat cleared")
                clearRepeat(finished = false)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            diag("player error ${error.errorCodeName}: ${error.message}")
        }
    }

    // ---------------- repeat ----------------

    /** Starts repeating ayahs [from]..[to] of the current surah, if its timing map is valid for this recording. */
    private fun startRepeat(from: Int, to: Int, count: RepeatCount): Int {
        val rec = Recording.parse(exo.currentMediaItem?.mediaId) ?: return SessionResult.RESULT_ERROR_BAD_VALUE
        val state = app.recitations.timingState(rec)
        val map = ((state as? TimingState.Ready)?.check as? TimingCheck.Valid)?.map
        if (map == null) {
            diag("repeat refused for ${rec.id}: timing ${com.sakina.app.data.RecitationRepository.describe(state)}")
            return SessionResult.RESULT_ERROR_BAD_VALUE
        }
        val duration = exo.duration
        if (duration == C.TIME_UNSET) return SessionResult.RESULT_ERROR_INVALID_STATE
        val fit = TimingParser.fits(map, duration)
        if (fit is TimingCheck.Invalid) {
            diag("repeat refused for ${rec.id}: ${fit.problem} ${fit.detail}")
            return SessionResult.RESULT_ERROR_BAD_VALUE
        }
        if (from !in 1..map.ayahs.size || to !in from..map.ayahs.size) return SessionResult.RESULT_ERROR_BAD_VALUE
        clearRepeat(finished = false)
        val span = map.span(from, to)
        val run = RepeatRun(from, to, count, span.first, span.last)
        repeat = run
        repeatFinished = false
        exo.seekTo(span.first)
        repeatMessage = exo.createMessage { _, _ -> onRepeatEnd() }
            .setLooper(Looper.getMainLooper())
            .setPosition(span.last)
            .setDeleteAfterDelivery(false)
            .send()
        diag("repeat ${rec.id} ayahs $from-$to x${count.times} (${span.first}-${span.last} ms)")
        if (!exo.playWhenReady && mayPlay()) exo.play()
        publishExtras()
        return SessionResult.RESULT_SUCCESS
    }

    private fun onRepeatEnd() {
        val r = repeat ?: return
        when (r.reachedEnd()) {
            RepeatRun.Action.AGAIN -> {
                diag("repeat pass ${r.played} done; again")
                exo.seekTo(r.startMs)
            }
            RepeatRun.Action.FINISH -> {
                diag("repeat finished after ${r.played} passes; paused at the end of ayah ${r.to}")
                exo.pause()
                clearRepeat(finished = true)
            }
        }
        publishExtras()
    }

    private fun clearRepeat(finished: Boolean) {
        repeatMessage?.cancel()
        repeatMessage = null
        val had = repeat != null
        repeat = null
        repeatFinished = finished
        if (had) publishExtras()
    }

    private fun publishExtras() {
        val r = repeat
        session?.setSessionExtras(Bundle().apply {
            putBoolean(EXTRA_REPEAT_ACTIVE, r != null)
            if (r != null) {
                putInt(EXTRA_REPEAT_FROM, r.from)
                putInt(EXTRA_REPEAT_TO, r.to)
                putInt(EXTRA_REPEAT_COUNT, r.count.times)
                putInt(EXTRA_REPEAT_PLAYED, r.played)
            }
            putBoolean(EXTRA_REPEAT_FINISHED, repeatFinished)
            putLong(EXTRA_QUIET_REFUSED, quietRefusedAt)
        })
    }

    // ---------------- session callback ----------------

    private val callback = object : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(ACTION_REPEAT, Bundle.EMPTY))
                .add(SessionCommand(ACTION_REPEAT_CLEAR, Bundle.EMPTY))
                .build()
            // One surah at a time: no previous/next buttons, only seeking within it.
            val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                .remove(Player.COMMAND_SEEK_TO_NEXT)
                .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(playerCommands)
                .build()
        }

        override fun onAddMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>): ListenableFuture<MutableList<MediaItem>> {
            // Items arrive without a URI; only known recordings are resolved, to a downloaded file or the MP3Quran stream.
            val resolved = mediaItems.mapNotNull { item ->
                val rec = Recording.parse(item.mediaId) ?: return@mapNotNull null
                val uri = app.recitations.playUri(rec)
                diag("load ${rec.id} from ${if (uri.startsWith("file:")) "downloaded file" else uri}")
                item.buildUpon().setUri(uri).build()
            }.toMutableList()
            return Futures.immediateFuture(resolved)
        }

        override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
            val code = when (customCommand.customAction) {
                ACTION_REPEAT -> startRepeat(args.getInt(ARG_FROM), args.getInt(ARG_TO), RepeatCount.of(args.getInt(ARG_COUNT, 1)))
                ACTION_REPEAT_CLEAR -> { clearRepeat(finished = false); SessionResult.RESULT_SUCCESS }
                else -> SessionResult.RESULT_ERROR_NOT_SUPPORTED
            }
            return Futures.immediateFuture(SessionResult(code))
        }
    }

    @Suppress("DEPRECATION")
    private fun seekButtons(): ImmutableList<CommandButton> = ImmutableList.of(
        CommandButton.Builder().setDisplayName(getString(R.string.seek_back)).setIconResId(R.drawable.ic_seek_back).setPlayerCommand(Player.COMMAND_SEEK_BACK).build(),
        CommandButton.Builder().setDisplayName(getString(R.string.seek_forward)).setIconResId(R.drawable.ic_seek_forward).setPlayerCommand(Player.COMMAND_SEEK_FORWARD).build(),
    )

    companion object {
        const val TAG = "SakinaRecitation"
        const val CHANNEL_ID = "recitation"
        const val NOTIFICATION_ID = 4001
        const val SEEK_MS = 10_000L
        const val IDLE_END_MS = 10 * 60 * 1000L
        const val ACTION_REPEAT = "sakina.repeat"
        const val ACTION_REPEAT_CLEAR = "sakina.repeat.clear"
        const val ARG_FROM = "from"
        const val ARG_TO = "to"
        const val ARG_COUNT = "count"
        const val EXTRA_REPEAT_ACTIVE = "repeat_active"
        const val EXTRA_REPEAT_FROM = "repeat_from"
        const val EXTRA_REPEAT_TO = "repeat_to"
        const val EXTRA_REPEAT_COUNT = "repeat_count"
        const val EXTRA_REPEAT_PLAYED = "repeat_played"
        const val EXTRA_REPEAT_FINISHED = "repeat_finished"
        const val EXTRA_QUIET_REFUSED = "quiet_refused"

        @Volatile
        var instance: RecitationService? = null
            private set

        /** Ten minutes; debug builds may shorten it for the emulator tests. */
        @Volatile
        var idleEndMs: Long = IDLE_END_MS

        /** Quiet mode stops Sakina's recitation (it does not come back when quiet ends). */
        fun stopForQuiet(context: Context) {
            instance?.let { s -> s.handler.post { s.endSession("quiet mode") } }
        }
    }
}
