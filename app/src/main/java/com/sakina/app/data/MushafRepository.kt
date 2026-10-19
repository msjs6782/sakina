package com.sakina.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StatFs
import android.util.Log
import com.sakina.app.core.mushaf.FetchFailure
import com.sakina.app.core.mushaf.FetchOutcome
import com.sakina.app.core.mushaf.MushafManifest
import com.sakina.app.core.mushaf.PageCheck
import com.sakina.app.core.mushaf.PageFetcher
import com.sakina.app.core.mushaf.PageVerifier
import com.sakina.app.core.mushaf.RangeReply
import com.sakina.app.core.mushaf.RangeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** HTTPS byte-range requests. Redirects are followed by hand so the Range header goes with them, and only to HTTPS. */
object HttpRangeSource : RangeSource {
    private const val UA = "Sakina/0.1 (Android; personal Quran app)"

    override fun open(url: String, from: Long, to: Long): RangeReply {
        var u = URL(url)
        var redirects = 0
        while (true) {
            if (u.protocol != "https") throw IOException("refusing non-HTTPS address $u")
            val c = (u.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("Range", "bytes=$from-$to")
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("User-Agent", UA)
            }
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location")
                c.disconnect()
                if (loc == null || ++redirects > 5) throw IOException("bad redirect from $u")
                u = URL(u, loc)
                continue
            }
            val body = if (code >= 400) c.errorStream ?: ByteArrayInputStream(ByteArray(0)) else c.inputStream
            return RangeReply(code, c.getHeaderField("Content-Range"), u.toString(), object : FilterInputStream(body) {
                override fun close() {
                    try { super.close() } finally { c.disconnect() }
                }
            })
        }
    }
}

sealed interface PageDownload {
    data class Running(val bytes: Long, val total: Long) : PageDownload
    data class Failed(val failure: FetchFailure, val detail: String) : PageDownload
}

enum class StopReason { USER, LEFT_SCREEN, BACKGROUND, NETWORK, STORAGE, NO_SPACE, VERIFY }

sealed interface FullDownload {
    data object Idle : FullDownload
    data class Running(val done: Int, val total: Int, val bytesIn: Long, val bytesTotal: Long) : FullDownload
    data class Stopped(val reason: StopReason, val done: Int, val total: Int, val detail: String = "") : FullDownload
    data class Completed(val fetched: Int) : FullDownload
}

enum class NetState { OFFLINE, METERED, UNMETERED }

/**
 * The printed Mushaf pages on this device: pages 1 and 2 come with the app; the rest are fetched
 * on request, checked against the manifest's fingerprints, and kept in the app's private storage.
 */
class MushafRepository(private val context: Context, private val scope: CoroutineScope) {
    val manifest: MushafManifest by lazy {
        MushafManifest.parse(context.assets.open("mushaf/pages.tsv").bufferedReader(Charsets.UTF_8).use { it.readText() })
    }
    private val dir = File(context.filesDir, "mushaf/pages")
    fun file(page: Int) = File(dir, "%03d.pdf".format(java.util.Locale.ROOT, page))

    private val _available = MutableStateFlow<Set<Int>>(emptySet())
    val available: StateFlow<Set<Int>> = _available.asStateFlow()

    private val _pages = MutableStateFlow<Map<Int, PageDownload>>(emptyMap())
    /** Single-page downloads in progress, and the last failure of each page. */
    val pages: StateFlow<Map<Int, PageDownload>> = _pages.asStateFlow()

    private val _full = MutableStateFlow<FullDownload>(FullDownload.Idle)
    val full: StateFlow<FullDownload> = _full.asStateFlow()

    private val ready = MutableStateFlow(false)
    private val initLock = Mutex()
    private val busy: MutableSet<Int> = ConcurrentHashMap.newKeySet()
    private val fetcher = PageFetcher(HttpRangeSource)
    private var fullJob: Job? = null
    private val stopReason = AtomicReference<StopReason?>(null)

    /** Copies the bundled pages into place (checked) and lists the pages already stored. */
    suspend fun init() {
        if (ready.value) return
        initLock.withLock {
            if (ready.value) return
            withContext(Dispatchers.IO) {
                dir.mkdirs()
                dir.listFiles()?.filter { it.name.endsWith(".part") }?.forEach { it.delete() }
                // Older builds could name pages with Arabic-Indic digits on phones set to Arabic.
                dir.listFiles()?.forEach { f ->
                    val latin = f.name.map { c -> if (c in '٠'..'٩') '0' + (c - '٠') else c }.joinToString("")
                    if (latin != f.name) f.renameTo(File(dir, latin))
                }
                for (p in MushafManifest.BUNDLED) {
                    val f = file(p)
                    if (PageVerifier.check(f, manifest.entry(p)) != PageCheck.OK) {
                        context.assets.open("mushaf/%03d.pdf".format(java.util.Locale.ROOT, p)).use { input -> f.outputStream().use { input.copyTo(it) } }
                        Log.i(TAG, "bundled page $p copied: ${PageVerifier.check(f, manifest.entry(p))}")
                    }
                }
                // Stored pages were verified when written; a size check catches truncation since then.
                val found = (1..MushafManifest.PAGES).filter { p -> file(p).length() == manifest.entry(p).size }.toSet()
                _available.value = found
                Log.i(TAG, "pages on device: ${found.size}")
            }
            ready.value = true
        }
    }

    fun isAvailable(page: Int) = page in _available.value

    fun freeBytes(): Long = runCatching { StatFs(context.filesDir.path).availableBytes }.getOrDefault(0L)

    fun storedBytes(): Long = manifest.sizeOf(_available.value)

    fun missing(): List<Int> = (1..MushafManifest.PAGES).filter { it !in _available.value }

    fun network(): NetState {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return NetState.OFFLINE
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return NetState.OFFLINE) ?: return NetState.OFFLINE
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return NetState.OFFLINE
        return if (cm.isActiveNetworkMetered) NetState.METERED else NetState.UNMETERED
    }

    private fun fetchOne(page: Int, cancelled: () -> Boolean, progress: (Long) -> Unit): FetchOutcome? {
        if (!busy.add(page)) return null
        try {
            val e = manifest.entry(page)
            val r = fetcher.fetch(e, manifest.mirrors, file(page), cancelled, progress)
            when (r) {
                is FetchOutcome.Done -> {
                    _available.update { it + page }
                    Log.i(TAG, "page $page verified: ${e.size} bytes, crc32+sha256 ok, ${r.bytesIn} bytes in from ${URL(r.mirror).host} in ${r.millis} ms")
                }
                is FetchOutcome.Failed -> Log.w(TAG, "page $page failed: ${r.failure} ${r.detail}")
            }
            return r
        } finally {
            busy.remove(page)
        }
    }

    /** Fetches one page now (the reader's "download this page", or the automatic option). */
    fun requestPage(page: Int) {
        if (page !in 1..MushafManifest.PAGES || isAvailable(page) || page in busy) return
        val total = manifest.entry(page).compressed
        _pages.update { it + (page to PageDownload.Running(0, total)) }
        scope.launch(Dispatchers.IO) {
            init()
            var last = 0L
            val r = fetchOne(page, { false }) { b ->
                if (b - last > 32 * 1024 || b == total) {
                    last = b
                    _pages.update { it + (page to PageDownload.Running(b, total)) }
                }
            }
            _pages.update { m ->
                when (r) {
                    is FetchOutcome.Failed -> m + (page to PageDownload.Failed(r.failure, r.detail))
                    else -> m - page
                }
            }
        }
    }

    /**
     * Fetches every missing page, three at a time. Completed pages stay; a stop (leaving the screen,
     * the app going to the background, the stop button, or an error) keeps them and deletes partial files.
     */
    fun startFull() {
        if (fullJob?.isActive == true) return
        stopReason.set(null)
        fullJob = scope.launch(Dispatchers.IO) {
            init()
            val todo = missing()
            if (todo.isEmpty()) {
                _full.value = FullDownload.Completed(0)
                return@launch
            }
            val need = manifest.sizeOf(todo)
            if (freeBytes() < need + SPACE_MARGIN) {
                _full.value = FullDownload.Stopped(StopReason.NO_SPACE, 0, todo.size, "need $need, free ${freeBytes()}")
                Log.w(TAG, "full download: not enough space (need $need, free ${freeBytes()})")
                return@launch
            }
            val bytesTotal = manifest.compressedOf(todo)
            val done = AtomicInteger(0)
            val doneBytes = AtomicLong(0)
            val inFlight = ConcurrentHashMap<Int, Long>()
            val queue = Channel<Int>(Channel.UNLIMITED)
            todo.forEach { queue.trySend(it) }
            queue.close()
            val detail = AtomicReference("")
            Log.i(TAG, "full download started: ${todo.size} pages, $bytesTotal bytes to fetch")
            _full.value = FullDownload.Running(0, todo.size, 0, bytesTotal)
            val job = coroutineContext[Job]!!
            fun stopped() = stopReason.get() != null || !job.isActive
            coroutineScope {
                val ticker = launch {
                    while (isActive) {
                        if (stopReason.get() == null) {
                            _full.value = FullDownload.Running(done.get(), todo.size, doneBytes.get() + inFlight.values.sum(), bytesTotal)
                        }
                        delay(250)
                    }
                }
                List(WORKERS) {
                    launch {
                        for (p in queue) {
                            if (stopped()) break
                            var attempt = 0
                            while (true) {
                                val r = fetchOne(p, ::stopped) { b -> inFlight[p] = b }
                                inFlight.remove(p)
                                when {
                                    r == null || r is FetchOutcome.Done -> {
                                        done.incrementAndGet(); doneBytes.addAndGet(manifest.entry(p).compressed)
                                        break
                                    }
                                    r is FetchOutcome.Failed && r.failure == FetchFailure.CANCELLED -> break
                                    r is FetchOutcome.Failed && attempt == 0 && r.failure != FetchFailure.STORAGE -> {
                                        attempt++; delay(1500)
                                    }
                                    else -> {
                                        val f = (r as FetchOutcome.Failed).failure
                                        detail.set("page $p: ${r.detail}")
                                        stopReason.compareAndSet(null, when (f) {
                                            FetchFailure.STORAGE -> StopReason.STORAGE
                                            FetchFailure.NETWORK, FetchFailure.BAD_REPLY, FetchFailure.NO_RANGE -> StopReason.NETWORK
                                            else -> StopReason.VERIFY
                                        })
                                        break
                                    }
                                }
                            }
                        }
                    }
                }.forEach { it.join() }
                ticker.cancel()
            }
            val reason = stopReason.get()
            _full.value = if (reason == null) FullDownload.Completed(done.get())
            else FullDownload.Stopped(reason, done.get(), todo.size, detail.get())
            Log.i(TAG, "full download ${if (reason == null) "completed" else "stopped: $reason"}; fetched ${done.get()} of ${todo.size}; pages on device ${_available.value.size}")
        }
    }

    fun stopFull(reason: StopReason) {
        if (fullJob?.isActive != true) return
        if (stopReason.compareAndSet(null, reason)) Log.i(TAG, "full download stop requested: $reason")
    }

    val fullRunning: Boolean get() = fullJob?.isActive == true

    /** Deletes downloaded pages (the bundled pages stay). */
    suspend fun delete(pages: Collection<Int>) = withContext(Dispatchers.IO) {
        val targets = pages.filter { it !in MushafManifest.BUNDLED && it !in busy }
        targets.forEach { file(it).delete() }
        _available.update { it - targets.toSet() }
        _pages.update { it - targets.toSet() }
        Log.i(TAG, "deleted ${targets.size} pages; pages on device ${_available.value.size}")
    }

    /** Re-checks the SHA-256 of every stored page and removes any that changed. Returns the removed pages. */
    suspend fun verifyAll(progress: (Int, Int) -> Unit): List<Int> = withContext(Dispatchers.IO) {
        init()
        val list = _available.value.sorted()
        val bad = mutableListOf<Int>()
        list.forEachIndexed { i, p ->
            if (PageVerifier.check(file(p), manifest.entry(p)) != PageCheck.OK) {
                file(p).delete()
                bad += p
            }
            progress(i + 1, list.size)
        }
        _available.update { it - bad.toSet() }
        // A changed bundled page is restored from the app.
        if (bad.any { it in MushafManifest.BUNDLED }) {
            ready.value = false
            init()
        }
        Log.i(TAG, "verify: checked ${list.size}, removed ${bad.size} $bad")
        bad
    }

    companion object {
        const val TAG = "SakinaMushaf"
        private const val WORKERS = 3
        private const val SPACE_MARGIN = 50L * 1024 * 1024
    }
}
