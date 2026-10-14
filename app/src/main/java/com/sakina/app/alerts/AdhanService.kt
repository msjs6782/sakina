package com.sakina.app.alerts

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.sakina.app.SakinaApp
import com.sakina.app.core.alerts.AudioRoute
import com.sakina.app.core.alerts.SoundRef
import com.sakina.app.core.prayer.PrayerKey
import com.sakina.app.data.AppSettings
import com.sakina.app.ui.Strings
import kotlinx.coroutines.runBlocking
import java.time.Instant

/**
 * Plays an adhan recording (or its first 20 seconds) as a foreground media service with a stop action.
 * It takes transient audio focus, so other audio (including Sakina's recitation later) pauses, and it stops for
 * good when focus is lost (for example a phone call). It never resumes on its own.
 */
class AdhanService : Service() {
    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private val handler = Handler(Looper.getMainLooper())
    private var current: Request? = null
    private var finished = false
    private lateinit var audio: AudioManager

    data class Request(
        val prayer: PrayerKey?,
        val prayerAt: Instant?,
        val excerpt: Boolean,
        val sound: SoundRef,
        val volume: Float,
        val route: AudioRoute,
        val test: Boolean,
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        audio = getSystemService(AudioManager::class.java)
    }

    private fun settings(): AppSettings = runBlocking { (applicationContext as SakinaApp).settings.current() }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            finish(Outcome.STOPPED_BY_USER)
            return START_NOT_STICKY
        }
        val s = settings()
        val str = Strings(s.language, s.arabicDigits)
        val req = Request(
            prayer = intent?.getStringExtra(EXTRA_PRAYER)?.let { runCatching { PrayerKey.valueOf(it) }.getOrNull() },
            prayerAt = intent?.getLongExtra(EXTRA_PRAYER_AT, 0L)?.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it) },
            excerpt = intent?.getBooleanExtra(EXTRA_EXCERPT, false) ?: false,
            sound = SoundRef(intent?.getStringExtra(EXTRA_BUNDLED), intent?.getStringExtra(EXTRA_FILE), intent?.getStringExtra(EXTRA_NAME)),
            volume = intent?.getFloatExtra(EXTRA_VOLUME, 0.8f) ?: 0.8f,
            route = intent?.getStringExtra(EXTRA_ROUTE)?.let { runCatching { AudioRoute.valueOf(it) }.getOrNull() } ?: AudioRoute.NOTIFICATION,
            test = intent?.getBooleanExtra(EXTRA_TEST, false) ?: false,
        )
        // A new adhan replaces one that is still playing.
        releasePlayer()
        current = req
        finished = false
        val notifier = Notifier(this, s)
        val title = if (req.prayer != null) str.adhanPlayingTitle(str.prayer(req.prayer)) else str.adhanTestTitle
        val text = (req.prayerAt?.let { s.fmt.time(it, s.activeLocation?.zoneId ?: java.time.ZoneId.systemDefault()) + " · " } ?: "") +
            (if (req.excerpt) str.excerptNote else str.tapStop)
        val n = notifier.adhanPlaying(title, text)
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(Notifier.ID_ADHAN_SERVICE, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            else startForeground(Notifier.ID_ADHAN_SERVICE, n)
        } catch (e: Exception) {
            Log.w(TAG, "startForeground failed", e)
            log(req, "fgs_failed")
            notifier.prayerRecord(req.prayer?.let { str.prayer(it) } ?: str.adhanTestTitle, req.prayerAt, str.playFailedNote, req.prayer?.ordinal ?: 0)
            stopSelf()
            return START_NOT_STICKY
        }
        val app = applicationContext as SakinaApp
        when {
            app.alertState.quietUntil != null && !req.test -> return finishNow(Outcome.QUIET)
            audio.mode == AudioManager.MODE_IN_CALL || audio.mode == AudioManager.MODE_IN_COMMUNICATION -> return finishNow(Outcome.IN_CALL)
            req.route == AudioRoute.NOTIFICATION && audio.ringerMode != AudioManager.RINGER_MODE_NORMAL ->
                return finishNow(if (audio.ringerMode == AudioManager.RINGER_MODE_VIBRATE) Outcome.RINGER_VIBRATE else Outcome.RINGER_SILENT)
            req.volume <= 0f -> return finishNow(Outcome.MUTED)
        }
        val attrs = AudioAttributes.Builder()
            .setUsage(if (req.route == AudioRoute.ALARM) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        if (!requestFocus(attrs)) return finishNow(Outcome.NO_FOCUS)
        val (resolved, fellBack) = SoundLibrary.resolve(this, req.sound)
        try {
            val mp = MediaPlayer()
            player = mp
            mp.setAudioAttributes(attrs)
            mp.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK)
            when (resolved) {
                is ResolvedSound.Raw -> SoundLibrary.openRaw(this, resolved.res).use { mp.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                is ResolvedSound.FileSound -> mp.setDataSource(resolved.file.absolutePath)
            }
            mp.setVolume(req.volume, req.volume)
            mp.setOnPreparedListener {
                it.start()
                log(req, if (fellBack) "playing_fallback_sound" else "playing")
                Log.i(TAG, "AdhanPlayback started excerpt=${req.excerpt} route=${req.route} volume=${req.volume}")
                if (req.excerpt) scheduleExcerptEnd(req.volume)
            }
            mp.setOnCompletionListener { finish(Outcome.COMPLETED) }
            mp.setOnErrorListener { _, what, extra ->
                Log.w(TAG, "MediaPlayer error $what/$extra")
                finish(Outcome.PLAY_ERROR)
                true
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            Log.w(TAG, "Could not start playback", e)
            return finishNow(Outcome.PLAY_ERROR)
        }
        handler.postDelayed({ finish(Outcome.TIMEOUT) }, MAX_PLAY_MS)
        return START_NOT_STICKY
    }

    private fun scheduleExcerptEnd(volume: Float) {
        val fadeStart = EXCERPT_MS - FADE_MS
        val steps = 10
        for (i in 1..steps) {
            handler.postDelayed({ player?.let { p -> runCatching { val v = volume * (1f - i / steps.toFloat()); p.setVolume(v, v) } } }, fadeStart + FADE_MS * i / steps)
        }
        handler.postDelayed({ finish(Outcome.EXCERPT_DONE) }, EXCERPT_MS)
    }

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
        ) {
            // A call, navigation prompt or other audio took over: stop and do not resume later.
            handler.post { finish(Outcome.FOCUS_LOST) }
        }
    }

    private fun requestFocus(attrs: AudioAttributes): Boolean {
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener(focusListener, handler)
            .setWillPauseWhenDucked(true)
            .build()
        focusRequest = req
        return audio.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    enum class Outcome { COMPLETED, EXCERPT_DONE, STOPPED_BY_USER, FOCUS_LOST, IN_CALL, RINGER_SILENT, RINGER_VIBRATE, MUTED, QUIET, NO_FOCUS, PLAY_ERROR, TIMEOUT }

    private fun finishNow(o: Outcome): Int {
        finish(o)
        return START_NOT_STICKY
    }

    private fun finish(o: Outcome) {
        if (finished) return
        finished = true
        handler.removeCallbacksAndMessages(null)
        releasePlayer()
        val req = current
        Log.i(TAG, "AdhanPlayback finished $o")
        if (req != null) {
            log(req, o.name.lowercase())
            val s = settings()
            val str = Strings(s.language, s.arabicDigits)
            val note = when (o) {
                Outcome.IN_CALL -> str.noteInCall
                Outcome.RINGER_SILENT, Outcome.RINGER_VIBRATE -> str.noteRinger
                Outcome.FOCUS_LOST -> str.noteFocusLost
                Outcome.PLAY_ERROR, Outcome.NO_FOCUS -> str.playFailedNote
                Outcome.MUTED -> str.noteMuted
                else -> null
            }
            if (o != Outcome.QUIET && !req.test) {
                Notifier(this, s).prayerRecord(str.prayer(req.prayer ?: PrayerKey.DHUHR), req.prayerAt, note, req.prayer?.ordinal ?: 0,
                    vibrate = o == Outcome.RINGER_VIBRATE)
            }
        }
        current = null
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE) else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }

    private fun log(req: Request, outcome: String) {
        val id = if (req.test) "TEST:adhan" else "ADHAN:${req.prayer}"
        (applicationContext as SakinaApp).alertState.log(id, outcome)
    }

    private fun releasePlayer() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        focusRequest?.let { audio.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (!finished && current != null) log(current!!, "stopped_by_system")
        finished = true
        releasePlayer()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SakinaAdhan"
        const val ACTION_PLAY = "com.sakina.app.PLAY_ADHAN"
        const val ACTION_STOP = "com.sakina.app.STOP_ADHAN"
        private const val EXTRA_PRAYER = "prayer"
        private const val EXTRA_PRAYER_AT = "prayer_at"
        private const val EXTRA_EXCERPT = "excerpt"
        private const val EXTRA_BUNDLED = "bundled"
        private const val EXTRA_FILE = "file"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_VOLUME = "volume"
        private const val EXTRA_ROUTE = "route"
        private const val EXTRA_TEST = "test"
        const val EXCERPT_MS = 20_000L
        private const val FADE_MS = 2_000L
        private const val MAX_PLAY_MS = 7 * 60_000L

        /** Starts playback; returns false when Android refuses to start the service (the caller then posts a notification). */
        fun start(context: Context, req: Request): Boolean {
            val i = Intent(context, AdhanService::class.java).setAction(ACTION_PLAY)
                .putExtra(EXTRA_PRAYER, req.prayer?.name)
                .putExtra(EXTRA_PRAYER_AT, req.prayerAt?.toEpochMilli() ?: 0L)
                .putExtra(EXTRA_EXCERPT, req.excerpt)
                .putExtra(EXTRA_BUNDLED, req.sound.bundled)
                .putExtra(EXTRA_FILE, req.sound.fileName)
                .putExtra(EXTRA_NAME, req.sound.displayName)
                .putExtra(EXTRA_VOLUME, req.volume)
                .putExtra(EXTRA_ROUTE, req.route.name)
                .putExtra(EXTRA_TEST, req.test)
            return try {
                ContextCompat.startForegroundService(context, i)
                true
            } catch (e: Exception) {
                Log.w(TAG, "Android refused to start the adhan service", e)
                false
            }
        }

        /** Stops playback (no effect if nothing plays). A running foreground service may be started directly. */
        fun stop(context: Context) {
            try {
                context.startService(Intent(context, AdhanService::class.java).setAction(ACTION_STOP))
            } catch (e: Exception) {
                context.stopService(Intent(context, AdhanService::class.java))
            }
        }
    }
}
