package com.sakina.app.data

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import com.sakina.app.core.adhkar.AdhkarLibrary
import com.sakina.app.core.adhkar.TasbihState
import com.sakina.app.core.adhkar.Wird
import com.sakina.app.core.qibla.GeoRings
import com.sakina.app.core.qibla.Wmm
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
import java.time.LocalDate

/** How the offline qibla map is drawn. */
enum class QiblaMapMode { CENTERED, WORLD }

@Serializable
data class QiblaPrefs(
    val vibrateOnAlign: Boolean = false,
    val mapMode: QiblaMapMode = QiblaMapMode.CENTERED,
)

/** Adhkar favourites and wird progress, the tasbih, and qibla choices; stored on the device only. */
@Serializable
data class ToolsState(
    val favorites: List<String> = emptyList(),
    /** Wird.key(date, practice, session) → dhikr id → taps. */
    val progress: Map<String, Map<String, Int>> = emptyMap(),
    val tasbih: TasbihState = TasbihState(),
    val tasbihUndo: TasbihState? = null,
    val qibla: QiblaPrefs = QiblaPrefs(),
) {
    companion object {
        /** Days of wird progress kept (today included). */
        const val KEEP_DAYS = 60L
    }
}

private object ToolsStateSerializer : Serializer<ToolsState> {
    override val defaultValue = ToolsState()
    override suspend fun readFrom(input: InputStream): ToolsState = try {
        SettingsJson.decodeFromString(ToolsState.serializer(), input.readBytes().decodeToString())
    } catch (e: SerializationException) {
        throw CorruptionException("Unreadable tools state", e)
    } catch (e: IllegalArgumentException) {
        throw CorruptionException("Unreadable tools state", e)
    }

    override suspend fun writeTo(t: ToolsState, output: OutputStream) {
        output.write(SettingsJson.encodeToString(ToolsState.serializer(), t).encodeToByteArray())
    }
}

private val Context.toolsStore: DataStore<ToolsState> by dataStore(
    fileName = "tools.json",
    serializer = ToolsStateSerializer,
    corruptionHandler = ReplaceFileCorruptionHandler { ToolsState() },
)

class ToolsRepository(context: Context) {
    private val app = context.applicationContext
    private val store = app.toolsStore
    val state: Flow<ToolsState> = store.data

    suspend fun current(): ToolsState = store.data.first()

    suspend fun update(transform: (ToolsState) -> ToolsState) {
        store.updateData { transform(it) }
    }

    /** Drops wird progress older than [ToolsState.KEEP_DAYS]. */
    suspend fun prune(today: LocalDate) {
        val from = today.minusDays(ToolsState.KEEP_DAYS - 1).toString()
        store.updateData { s ->
            val kept = Wird.prune(s.progress, from)
            if (kept.size == s.progress.size) s else s.copy(progress = kept)
        }
    }

    private val lock = Mutex()
    private val _library = MutableStateFlow<AdhkarLibrary?>(null)
    val library: StateFlow<AdhkarLibrary?> = _library.asStateFlow()

    suspend fun loadLibrary(): AdhkarLibrary = _library.value ?: lock.withLock {
        _library.value ?: withContext(Dispatchers.Default) {
            val text = app.assets.open("adhkar/adhkar.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            SettingsJson.decodeFromString(AdhkarLibrary.serializer(), text)
        }.also { _library.value = it }
    }

    private val geoCache = java.util.concurrent.ConcurrentHashMap<String, GeoRings>()

    /** Natural Earth layers for the offline map (assets/qibla/<name>.bin), parsed once. */
    suspend fun geo(name: String): GeoRings = geoCache[name] ?: withContext(Dispatchers.IO) {
        GeoRings.parse(app.assets.open("qibla/$name.bin").use { it.readBytes() })
    }.also { geoCache[name] = it }

    private val _wmm = MutableStateFlow<Wmm?>(null)
    val wmm: StateFlow<Wmm?> = _wmm.asStateFlow()

    suspend fun loadWmm(): Wmm = _wmm.value ?: withContext(Dispatchers.Default) {
        Wmm.parse(app.assets.open("qibla/WMM.COF").bufferedReader().use { it.readText() })
    }.also { _wmm.value = it }
}
