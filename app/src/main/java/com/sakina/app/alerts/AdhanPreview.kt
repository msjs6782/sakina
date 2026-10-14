package com.sakina.app.alerts

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.sakina.app.core.alerts.SoundRef
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-app preview of a recording on the media stream. Only one preview plays at a time. */
object AdhanPreview {
    private var player: MediaPlayer? = null
    private val _playing = MutableStateFlow<String?>(null)

    /** The key of the sound being previewed, or null. */
    val playing: StateFlow<String?> = _playing.asStateFlow()

    fun key(ref: SoundRef) = ref.fileName ?: ref.bundled ?: ""

    fun toggle(context: Context, ref: SoundRef, volume: Float) {
        val k = key(ref)
        if (_playing.value == k) {
            stop(); return
        }
        stop()
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            when (val r = SoundLibrary.resolve(context, ref).first) {
                is ResolvedSound.Raw -> SoundLibrary.openRaw(context, r.res).use { mp.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                is ResolvedSound.FileSound -> mp.setDataSource(r.file.absolutePath)
            }
            mp.setVolume(volume, volume)
            mp.setOnPreparedListener { it.start() }
            mp.setOnCompletionListener { stop() }
            mp.setOnErrorListener { _, _, _ -> stop(); true }
            mp.prepareAsync()
            player = mp
            _playing.value = k
        } catch (e: Exception) {
            mp.release()
            _playing.value = null
        }
    }

    fun setVolume(volume: Float) {
        player?.let { runCatching { it.setVolume(volume, volume) } }
    }

    fun stop() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        _playing.value = null
    }
}
