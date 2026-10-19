package com.sakina.app.data

import android.content.Context
import android.util.Log
import com.sakina.app.core.recitation.LibraryLimits
import com.sakina.app.core.recitation.Reciter
import com.sakina.app.core.recitation.RecitationSources
import com.sakina.app.core.recitation.Recording
import com.sakina.app.core.recitation.TimingCheck
import com.sakina.app.core.recitation.TimingParser
import com.sakina.app.core.recitation.TimingProblem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

sealed interface RecordingDownload {
    data class Running(val bytes: Long, val total: Long) : RecordingDownload
    data class Failed(val reason: DownloadFailure, val detail: String = "") : RecordingDownload
}

enum class DownloadFailure { OFFLINE, NETWORK, TOO_BIG, LIBRARY_FULL, UNKNOWN_SIZE, NOT_AUDIO, STORAGE, NO_SPACE }

/** The state of a surah's timing map for repeat. */
sealed interface TimingState {
    data object Missing : TimingState
    data object Loading : TimingState
    data class Ready(val check: TimingCheck) : TimingState
    data class Failed(val reason: DownloadFailure, val detail: String = "") : TimingState
}

/**
 * Recitations kept by the app: downloaded MP3 files (one per surah and reciter) and their MP3Quran
 * timing maps, in the app's private storage. Nothing is fetched unless the user asks.
 */
class RecitationRepository(private val context: Context, private val scope: CoroutineScope) {
    val sources: RecitationSources by lazy {
        RecitationSources.parse(context.assets.open("recitations/sources.tsv").bufferedReader().use { it.readText() })
    }
    private val dir = File(context.filesDir, "recitations")
    private val timingDir = File(dir, "timing")

    fun file(r: Recording) = File(dir, "${r.reciter.key}/%03d.mp3".format(java.util.Locale.ROOT, r.sura))
    private fun timingFile(r: Recording) = File(timingDir, "${r.reciter.timingRead}/%03d.json".format(java.util.Locale.ROOT, r.sura))

    private val _downloaded = MutableStateFlow<Map<String, Long>>(emptyMap())
    /** Downloaded recordings: id -> size in bytes. */
    val downloaded: StateFlow<Map<String, Long>> = _downloaded.asStateFlow()

    private val _downloads = MutableStateFlow<Map<String, RecordingDownload>>(emptyMap())
    val downloads: StateFlow<Map<String, RecordingDownload>> = _downloads.asStateFlow()

    private val _timing = MutableStateFlow<Map<String, TimingState>>(emptyMap())
    val timing: StateFlow<Map<String, TimingState>> = _timing.asStateFlow()

    private val jobs = ConcurrentHashMap<String, Job>()
    private val initLock = Mutex()
    @Volatile private var ready = false
    @Volatile private var readsFolders: Map<Int, String>? = null

    suspend fun init() {
        if (ready) return
        initLock.withLock {
            if (ready) return
            withContext(Dispatchers.IO) {
                dir.mkdirs()
                val found = HashMap<String, Long>()
                for (rc in Reciter.entries) {
                    File(dir, rc.key).listFiles()?.forEach { f ->
                        if (f.name.endsWith(".part")) f.delete()
                        else f.name.removeSuffix(".mp3").toIntOrNull()?.takeIf { it in 1..114 }?.let { s -> found[Recording(rc, s).id] = f.length() }
                    }
                }
                _downloaded.value = found
                val t = HashMap<String, TimingState>()
                for (rc in Reciter.entries) {
                    for (s in 1..114) {
                        val r = Recording(rc, s)
                        val f = timingFile(r)
                        if (f.isFile) t[r.id] = TimingState.Ready(TimingParser.parse(f.readText(), s, AYAH_COUNTS[s - 1]))
                    }
                }
                _timing.value = t
            }
            ready = true
        }
    }

    fun isDownloaded(r: Recording) = r.id in _downloaded.value
    fun libraryBytes(): Long = _downloaded.value.values.sum()

    /** What the player should play: the local file when downloaded, otherwise the stream. */
    fun playUri(r: Recording): String = file(r).takeIf { isDownloaded(r) && it.isFile }?.let { android.net.Uri.fromFile(it).toString() } ?: r.reciter.url(r.sura)

    private fun open(url: String, method: String = "GET"): HttpURLConnection {
        var u = URL(url)
        var redirects = 0
        while (true) {
            if (u.protocol != "https") throw IOException("refusing non-HTTPS address $u")
            val c = (u.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = method
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept-Encoding", "identity")
            }
            val code = c.responseCode
            if (code in 300..399 && redirects < 5) {
                val loc = c.getHeaderField("Location") ?: throw IOException("redirect without location")
                c.disconnect()
                u = URL(u, loc)
                redirects++
                continue
            }
            return c
        }
    }

    /** Downloads one recording, with its timing map. Refuses recordings over the limits before fetching anything. */
    fun download(r: Recording, online: Boolean) {
        if (isDownloaded(r) || jobs[r.id]?.isActive == true) return
        if (!online) {
            _downloads.update { it + (r.id to RecordingDownload.Failed(DownloadFailure.OFFLINE)) }
            return
        }
        _downloads.update { it + (r.id to RecordingDownload.Running(0, sources.bytes(r))) }
        jobs[r.id] = scope.launch(Dispatchers.IO) {
            init()
            val target = file(r)
            val part = File(target.path + ".part")
            val failure: RecordingDownload.Failed? = try {
                val c = open(r.reciter.url(r.sura))
                try {
                    if (c.responseCode != 200) throw IOException("HTTP ${c.responseCode}")
                    val size = c.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
                    when (LibraryLimits.check(size, libraryBytes())) {
                        LibraryLimits.Check.TOO_BIG -> RecordingDownload.Failed(DownloadFailure.TOO_BIG, "$size")
                        LibraryLimits.Check.LIBRARY_FULL -> RecordingDownload.Failed(DownloadFailure.LIBRARY_FULL, "$size")
                        LibraryLimits.Check.UNKNOWN_SIZE -> RecordingDownload.Failed(DownloadFailure.UNKNOWN_SIZE)
                        LibraryLimits.Check.OK -> {
                            if (freeBytes() < size + SPACE_MARGIN) {
                                RecordingDownload.Failed(DownloadFailure.NO_SPACE, "$size")
                            } else {
                                target.parentFile?.mkdirs()
                                var got = 0L
                                var last = 0L
                                var head = ByteArray(0)
                                c.inputStream.use { input ->
                                    FileOutputStream(part).use { out ->
                                        val buf = ByteArray(64 * 1024)
                                        while (true) {
                                            if (!isActive) throw kotlinx.coroutines.CancellationException()
                                            val n = input.read(buf)
                                            if (n < 0) break
                                            if (got == 0L) head = buf.copyOf(minOf(n, 4))
                                            got += n
                                            if (got > size || got > LibraryLimits.PER_RECORDING) throw IOException("more data than announced")
                                            out.write(buf, 0, n)
                                            if (debugSlowMs > 0) Thread.sleep(debugSlowMs)
                                            if (got - last > 256 * 1024) {
                                                last = got
                                                _downloads.update { it + (r.id to RecordingDownload.Running(got, size)) }
                                            }
                                        }
                                        out.fd.sync()
                                    }
                                }
                                when {
                                    got != size -> RecordingDownload.Failed(DownloadFailure.NETWORK, "got $got of $size")
                                    !looksLikeMp3(head) -> RecordingDownload.Failed(DownloadFailure.NOT_AUDIO)
                                    !part.renameTo(target) -> RecordingDownload.Failed(DownloadFailure.STORAGE)
                                    else -> {
                                        _downloaded.update { it + (r.id to size) }
                                        Log.i(TAG, "downloaded ${r.id}: $size bytes; library ${libraryBytes()} bytes")
                                        null
                                    }
                                }
                            }
                        }
                    }
                } finally {
                    c.disconnect()
                }
            } catch (x: kotlinx.coroutines.CancellationException) {
                part.delete()
                _downloads.update { it - r.id }
                Log.i(TAG, "download cancelled ${r.id}")
                jobs.remove(r.id)
                return@launch
            } catch (x: IOException) {
                RecordingDownload.Failed(if (x.message?.contains("ENOSPC") == true) DownloadFailure.STORAGE else DownloadFailure.NETWORK, x.toString())
            }
            part.delete()
            if (failure != null) Log.w(TAG, "download ${r.id} failed: ${failure.reason} ${failure.detail}")
            _downloads.update { if (failure == null) it - r.id else it + (r.id to failure) }
            jobs.remove(r.id)
            // The timing map comes with the recording (small; failures leave repeat unavailable, not the download).
            if (failure == null) fetchTiming(r)
        }
    }

    fun cancel(r: Recording) {
        jobs[r.id]?.cancel()
    }

    suspend fun delete(r: Recording) = withContext(Dispatchers.IO) {
        cancel(r)
        file(r).delete()
        _downloaded.update { it - r.id }
        _downloads.update { it - r.id }
        Log.i(TAG, "deleted ${r.id}; library ${libraryBytes()} bytes")
    }

    fun clearFailure(r: Recording) = _downloads.update { if (it[r.id] is RecordingDownload.Failed) it - r.id else it }

    private fun looksLikeMp3(head: ByteArray): Boolean =
        head.size >= 3 && (String(head, 0, 3, Charsets.ISO_8859_1) == "ID3" || ((head[0].toInt() and 0xff) == 0xff && (head[1].toInt() and 0xe0) == 0xe0))

    fun freeBytes(): Long = runCatching { android.os.StatFs(context.filesDir.path).availableBytes }.getOrDefault(0L)

    // ---------------- timing maps ----------------

    fun timingState(r: Recording): TimingState = _timing.value[r.id] ?: TimingState.Missing

    /** Fetches and checks the timing map of one surah (at the user's request, or with a download). */
    fun fetchTiming(r: Recording) {
        if (_timing.value[r.id] == TimingState.Loading) return
        _timing.update { it + (r.id to TimingState.Loading) }
        scope.launch(Dispatchers.IO) {
            val state: TimingState = try {
                val folder = readsFolder(r.reciter.timingRead)
                if (folder != r.reciter.server) {
                    TimingState.Ready(TimingCheck.Invalid(TimingProblem.WRONG_SOURCE, "read ${r.reciter.timingRead} now points at $folder"))
                } else {
                    val c = open(r.reciter.timingUrl(r.sura))
                    val body = try {
                        if (c.responseCode != 200) throw IOException("HTTP ${c.responseCode}")
                        c.inputStream.bufferedReader().use { it.readText() }
                    } finally {
                        c.disconnect()
                    }
                    if (body.length > 2_000_000) throw IOException("timing map too large")
                    val check = TimingParser.parse(body, r.sura, AYAH_COUNTS[r.sura - 1])
                    timingFile(r).parentFile?.mkdirs()
                    timingFile(r).writeText(body)
                    TimingState.Ready(check)
                }
            } catch (x: IOException) {
                TimingState.Failed(DownloadFailure.NETWORK, x.toString())
            }
            Log.i(TAG, "timing ${r.id}: ${describe(state)}")
            _timing.update { it + (r.id to state) }
        }
    }

    private fun readsFolder(read: Int): String? {
        readsFolders?.let { return it[read] }
        val c = open(Reciter.READS_URL)
        val body = try {
            if (c.responseCode != 200) throw IOException("HTTP ${c.responseCode}")
            c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
        val map = Reciter.entries.associate { it.timingRead to (TimingParser.folderOf(body, it.timingRead) ?: "") }
        readsFolders = map
        return map[read]
    }

    companion object {
        const val TAG = "SakinaRecitation"

        /** Debug builds may slow downloads down, so the emulator test can cancel one midway. */
        @Volatile
        var debugSlowMs: Long = 0
        private const val UA = "Sakina/0.1 (Android; personal Quran app)"
        private const val SPACE_MARGIN = 50L * 1024 * 1024
        val AYAH_COUNTS = intArrayOf(7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111, 43, 52, 99, 128, 111, 110, 98, 135, 112, 78, 118, 64, 77, 227, 93, 88, 69, 60, 34, 30, 73, 54, 45, 83, 182, 88, 75, 85, 54, 53, 89, 59, 37, 35, 38, 29, 18, 45, 60, 49, 62, 55, 78, 96, 29, 22, 24, 13, 14, 11, 11, 18, 12, 12, 30, 52, 52, 44, 28, 28, 20, 56, 40, 31, 50, 40, 46, 42, 29, 19, 36, 25, 22, 17, 19, 26, 30, 20, 15, 21, 11, 8, 8, 19, 5, 8, 8, 11, 11, 8, 3, 9, 5, 4, 7, 3, 6, 3, 5, 4, 5, 6)

        fun describe(s: TimingState): String = when (s) {
            is TimingState.Ready -> when (val c = s.check) {
                is TimingCheck.Valid -> "valid, ${c.map.ayahs.size} ayahs"
                is TimingCheck.Invalid -> "invalid ${c.problem}: ${c.detail}"
            }
            is TimingState.Failed -> "failed ${s.reason} ${s.detail}"
            TimingState.Loading -> "loading"
            TimingState.Missing -> "missing"
        }
    }
}
