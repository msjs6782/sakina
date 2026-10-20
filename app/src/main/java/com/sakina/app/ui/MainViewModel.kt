package com.sakina.app.ui

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sakina.app.SakinaApp
import com.sakina.app.core.location.City
import com.sakina.app.data.AppClock
import com.sakina.app.data.AppSettings
import com.sakina.app.data.GpsResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

enum class Tab { HOME, QURAN, CALENDAR, MORE }

enum class PlacePurpose { ADD, TRAVEL }

sealed interface Route {
    /** The day, week and month tables of prayer times (no longer a tab). */
    data class Times(val view: com.sakina.app.ui.times.TimesView = com.sakina.app.ui.times.TimesView.DAY) : Route
    data object Locations : Route
    data object Calc : Route
    data object Appearance : Route
    data object HomeSetup : Route
    data object Calendar : Route
    data object About : Route
    data class CityPicker(val purpose: PlacePurpose) : Route
    data class Manual(val purpose: PlacePurpose) : Route
    data class Gps(val purpose: PlacePurpose) : Route
    data class License(val asset: String, val title: String) : Route
    data object Alerts : Route
    data class PrayerAlert(val key: com.sakina.app.core.prayer.PrayerKey) : Route
    data object Reminders : Route
    data object Recordings : Route
    data class QuranReader(val global: Int, val highlight: Boolean = false) : Route
    data class QuranSearch(val query: String = "") : Route
    data object Khatma : Route
    data object QuranSources : Route
    /** The printed Mushaf at [page]; [focus] is an ayah (global index) to point out, or -1. */
    data class Mushaf(val page: Int, val focus: Int = -1) : Route
    data object MushafDownloads : Route
    /** Listening; [sura] scrolls the list to that surah (0 for none). */
    data class Listen(val sura: Int = 0) : Route
    /** The adhkar library; [query] opens it with a search (debug screenshots of Arabic search). */
    data class Adhkar(val query: String = "") : Route
    data class AdhkarCategory(val id: String) : Route
    data class AdhkarItem(val id: String) : Route
    /** A daily set of adhkar; [session] picks the prayer for the after-prayer set ("" = the latest prayer). */
    data class Wird(val practice: String, val session: String = "") : Route
    data object Tasbih : Route
    data object Qibla : Route
    /** The month calendar; [focusEpochDay] opens a day's details (Long.MIN_VALUE for none). */
    data class HijriCalendar(val focusEpochDay: Long = Long.MIN_VALUE) : Route
    /** Adding ([id] empty) or editing a personal occasion; [epochDay] is the date for a new one. */
    data class OccasionEdit(val id: String = "", val epochDay: Long = Long.MIN_VALUE) : Route
    data object CalendarTools : Route
    data object Ramadan : Route
    data object Night : Route
    data object Friday : Route
    /** Home-screen widgets, icon shortcuts and Quick Settings tiles. */
    data object Widgets : Route
}

/** The occasions list before the last change, so the change can be undone from the calendar. */
data class OccasionUndo(val previous: List<com.sakina.app.core.calendar.PersonalOccasion>, val message: String, val stamp: Long)

sealed interface GpsUi {
    data object Idle : GpsUi
    data object Locating : GpsUi
    data class Found(val latitude: Double, val longitude: Double, val accuracy: Float?) : GpsUi
    data object Disabled : GpsUi
    data object TimedOut : GpsUi
    data object Denied : GpsUi
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val sakina = app as SakinaApp

    val settings: StateFlow<AppSettings?> = sakina.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _cities = MutableStateFlow<List<City>>(emptyList())
    val cities: StateFlow<List<City>> = _cities.asStateFlow()

    var tab by mutableStateOf(Tab.HOME)
    /** The day the calendar tab opens on (an epoch day; Long.MIN_VALUE for today). */
    var calendarFocus by mutableStateOf(Long.MIN_VALUE)

    /** Opens the calendar tab, on [day] if given. */
    fun openCalendar(day: java.time.LocalDate? = null) {
        clearStack()
        calendarFocus = day?.toEpochDay() ?: Long.MIN_VALUE
        tab = Tab.CALENDAR
    }
    var stack by mutableStateOf(listOf<Route>())
        private set

    private val _gps = MutableStateFlow<GpsUi>(GpsUi.Idle)
    val gps: StateFlow<GpsUi> = _gps.asStateFlow()
    private var gpsJob: Job? = null

    val quran: StateFlow<com.sakina.app.data.QuranData?> = sakina.quran.data
    val quranState: StateFlow<com.sakina.app.data.QuranState> = sakina.quranState.state
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.sakina.app.data.QuranState())

    init {
        viewModelScope.launch { _cities.value = sakina.cities.all() }
    }

    val tools: StateFlow<com.sakina.app.data.ToolsState> = sakina.tools.state
        .stateIn(viewModelScope, SharingStarted.Eagerly, com.sakina.app.data.ToolsState())
    val adhkar: StateFlow<com.sakina.app.core.adhkar.AdhkarLibrary?> get() = sakina.tools.library
    val toolsRepo get() = sakina.tools

    val refs: StateFlow<Map<String, com.sakina.app.core.adhkar.HadithRef>?> get() = sakina.tools.refs

    fun loadRefs() {
        viewModelScope.launch { sakina.tools.loadRefs() }
    }

    var occasionUndo by mutableStateOf<OccasionUndo?>(null)

    /** Changes the personal occasions and keeps the previous list for one undo. */
    fun changeOccasions(message: String, transform: (List<com.sakina.app.core.calendar.PersonalOccasion>) -> List<com.sakina.app.core.calendar.PersonalOccasion>) {
        val prev = settings.value?.calendar?.occasions ?: return
        update { it.copy(calendar = it.calendar.copy(occasions = transform(it.calendar.occasions))) }
        occasionUndo = OccasionUndo(prev, message, System.nanoTime())
    }

    fun undoOccasions() {
        val u = occasionUndo ?: return
        update { it.copy(calendar = it.calendar.copy(occasions = u.previous)) }
        occasionUndo = null
    }

    fun loadAdhkar() {
        viewModelScope.launch { sakina.tools.loadLibrary() }
    }

    fun updateTools(transform: (com.sakina.app.data.ToolsState) -> com.sakina.app.data.ToolsState) {
        viewModelScope.launch { sakina.tools.update(transform) }
    }

    val mushaf get() = sakina.mushaf
    val recitations get() = sakina.recitations

    fun deleteRecording(r: com.sakina.app.core.recitation.Recording) {
        viewModelScope.launch { sakina.recitations.delete(r) }
    }

    /** Opens an ayah in the user's chosen view: the printed page (default) or the text. */
    fun openQuran(global: Int, highlight: Boolean = false) {
        val d = quran.value
        if (quranState.value.reader.view == com.sakina.app.data.QuranView.PRINTED && d != null) {
            push(Route.Mushaf(d.meta.pageOf(global), if (highlight) global else -1))
        } else {
            push(Route.QuranReader(global, highlight))
        }
    }

    /** Opens an ayah from outside the app (a widget), on the Quran tab, once the text is loaded. */
    fun openAyah(sura: Int, aya: Int) {
        viewModelScope.launch {
            val d = sakina.quran.load()
            val ref = com.sakina.app.core.quran.AyahRef(sura, aya)
            if (sura !in 1..d.meta.suras.size || aya !in 1..d.meta.surah(sura).ayahCount) return@launch
            clearStack()
            tab = Tab.QURAN
            openQuran(d.meta.global(ref), highlight = true)
        }
    }

    /** Opens a page in the user's chosen view. */
    fun openPage(page: Int) {
        val d = quran.value ?: return
        if (quranState.value.reader.view == com.sakina.app.data.QuranView.PRINTED) push(Route.Mushaf(page))
        else push(Route.QuranReader(d.meta.pageRange(page).first))
    }

    /** Switches between the printed and text views in place, and remembers the choice. */
    fun switchView(to: com.sakina.app.data.QuranView, route: Route) {
        updateQuran { it.copy(reader = it.reader.copy(view = to)) }
        replaceTop(route)
    }

    fun replaceTop(route: Route) {
        stack = stack.dropLast(1) + route
    }

    fun loadQuran() {
        viewModelScope.launch { sakina.quran.load() }
    }

    fun updateQuran(transform: (com.sakina.app.data.QuranState) -> com.sakina.app.data.QuranState) {
        viewModelScope.launch { sakina.quranState.update(transform) }
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { sakina.settings.update(transform) }
    }

    fun push(route: Route) {
        stack = stack + route
    }

    fun pop(): Boolean {
        if (stack.isEmpty()) return false
        stack = stack.dropLast(1)
        return true
    }

    fun popTo(predicate: (Route) -> Boolean) {
        val i = stack.indexOfLast(predicate)
        stack = if (i >= 0) stack.take(i + 1) else emptyList()
    }

    fun clearStack() {
        stack = emptyList()
    }

    fun locate() {
        gpsJob?.cancel()
        gpsJob = viewModelScope.launch {
            _gps.value = GpsUi.Locating
            _gps.value = when (val r = sakina.gps.current()) {
                is GpsResult.Found -> GpsUi.Found(r.latitude, r.longitude, r.accuracyMeters)
                GpsResult.Disabled -> GpsUi.Disabled
                GpsResult.TimedOut -> GpsUi.TimedOut
                GpsResult.NoPermission -> GpsUi.Denied
            }
        }
    }

    fun gpsDenied() {
        _gps.value = GpsUi.Denied
    }

    fun resetGps() {
        gpsJob?.cancel()
        _gps.value = GpsUi.Idle
    }

    fun hasLocationPermission() = sakina.gps.hasPermission()
}

/** The current instant, refreshed on each whole second while the composable is shown. */
@Composable
fun rememberNow(periodMs: Long = 1000L): State<Instant> = produceState(initialValue = AppClock.now()) {
    while (true) {
        val n = AppClock.now()
        value = n
        delay(periodMs - (n.toEpochMilli() % periodMs) + 5)
    }
}
