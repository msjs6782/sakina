package com.sakina.app.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import com.sakina.app.core.quran.AyahTexts
import com.sakina.app.core.quran.KhatmaPlan
import com.sakina.app.core.quran.QuranMeta
import com.sakina.app.core.quran.QuranSearch
import com.sakina.app.core.quran.QuranText
import com.sakina.app.core.quran.TanzilParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import java.io.InputStream
import java.io.OutputStream

/** Everything parsed from the bundled Tanzil files. */
class QuranData(
    val meta: QuranMeta,
    val text: QuranText,
    val translation: AyahTexts,
    val tafsir: AyahTexts,
    val search: QuranSearch,
    /** The copyright block of the Uthmani text, shown in the sources screen. */
    val textHeader: List<String>,
)

/** Loads the bundled Quran files once, off the main thread, and keeps them in memory. */
class QuranRepository(private val context: Context) {
    private val lock = Mutex()
    private val _data = MutableStateFlow<QuranData?>(null)
    val data: StateFlow<QuranData?> = _data.asStateFlow()

    private fun read(name: String) = context.assets.open("quran/$name").bufferedReader(Charsets.UTF_8).use { it.readText() }

    suspend fun load(): QuranData = _data.value ?: lock.withLock {
        _data.value ?: withContext(Dispatchers.Default) {
            val t0 = System.nanoTime()
            val meta = TanzilParser.parseMeta(read("quran-data.xml"))
            val xml = read("quran-uthmani.xml")
            val text = TanzilParser.parseUthmaniXml(xml, meta)
            val simple = TanzilParser.parseTxt2("simple-clean", read("quran-simple-clean.txt"), meta)
            val translation = TanzilParser.parseTxt2("en.itani", read("en.itani.txt"), meta)
            val tafsir = TanzilParser.parseTxt2("ar.jalalayn", read("ar.jalalayn.txt"), meta)
            QuranData(meta, text, translation, tafsir, QuranSearch(meta, simple, translation), TanzilParser.header(xml.substringAfter("<!--")))
                .also { android.util.Log.i("SakinaQuran", "Loaded ${meta.suras.size} surahs, ${text.ayahs.size} ayahs in ${(System.nanoTime() - t0) / 1_000_000} ms") }
        }.also { _data.value = it }
    }
}

enum class ReaderMode { PAGES, AYAHS }

/** Which view opens from the index, search and bookmarks: the printed pages (the default) or the text. */
enum class QuranView { PRINTED, TEXT }

/** A display-only filter for the printed pages; the files themselves are never changed. */
enum class MushafNight { OFF, DARK, WARM }

@Serializable
data class ReaderPrefs(
    val fontSize: Int = 26,
    val mode: ReaderMode = ReaderMode.PAGES,
    val showTranslation: Boolean = false,
    val showTafsir: Boolean = false,
    val view: QuranView = QuranView.PRINTED,
    val mushafNight: MushafNight = MushafNight.OFF,
    /** Trim the white paper around the printed frame, so the page fills the screen. */
    val mushafTrim: Boolean = true,
    /** Fetch a printed page when it is opened, without asking each time. Off unless the user turns it on. */
    val autoDownload: Boolean = false,
) {
    companion object {
        const val MIN_FONT = 18
        const val MAX_FONT = 44
    }
}

@Serializable
data class LastRead(val sura: Int, val aya: Int, val page: Int, val atEpochMs: Long)

@Serializable
data class Bookmark(val sura: Int, val aya: Int, val createdEpochMs: Long, val note: String = "")

/** Listening choices, remembered between sessions (playback itself never restarts on its own). */
@Serializable
data class ListenPrefs(
    val reciter: String = "alafasy",
    val speed: Float = 1f,
    val repeatTimes: Int = 3,
)

@Serializable
data class QuranState(
    val reader: ReaderPrefs = ReaderPrefs(),
    val lastRead: LastRead? = null,
    val bookmarks: List<Bookmark> = emptyList(),
    val khatma: KhatmaPlan? = null,
    val listen: ListenPrefs = ListenPrefs(),
    /** Passages being memorised, with their next review. */
    val hifz: List<com.sakina.app.core.quran.HifzItem> = emptyList(),
    /** How the last memorisation session showed the text. */
    val hifzMask: com.sakina.app.core.quran.HifzMask = com.sakina.app.core.quran.HifzMask.FIRST_WORD,
)

private object QuranStateSerializer : Serializer<QuranState> {
    override val defaultValue = QuranState()
    override suspend fun readFrom(input: InputStream): QuranState = try {
        SettingsJson.decodeFromString(QuranState.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Unreadable Quran state", e)
    } catch (e: IllegalArgumentException) {
        throw CorruptionException("Unreadable Quran state", e)
    }

    override suspend fun writeTo(t: QuranState, output: OutputStream) {
        output.write(SettingsJson.encodeToString(QuranState.serializer(), t).encodeToByteArray())
    }
}

private val Context.quranStore: DataStore<QuranState> by dataStore(
    fileName = "quran.json",
    serializer = QuranStateSerializer,
    corruptionHandler = ReplaceFileCorruptionHandler { QuranState() },
)

/** Reading position, bookmarks, khatma plan and reader preferences, stored on the device. */
class QuranStateRepository(context: Context) {
    private val store = context.applicationContext.quranStore
    val state: Flow<QuranState> = store.data
    suspend fun current(): QuranState = store.data.first()
    suspend fun update(transform: (QuranState) -> QuranState) {
        store.updateData { transform(it) }
    }
}

object QuranOps {
    fun toggleBookmark(s: QuranState, sura: Int, aya: Int, now: Long): QuranState =
        if (s.bookmarks.any { it.sura == sura && it.aya == aya }) s.copy(bookmarks = s.bookmarks.filterNot { it.sura == sura && it.aya == aya })
        else s.copy(bookmarks = (s.bookmarks + Bookmark(sura, aya, now)).takeLast(500))

    fun isBookmarked(s: QuranState, sura: Int, aya: Int) = s.bookmarks.any { it.sura == sura && it.aya == aya }
}
