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

enum class Tab { HOME, TIMES, QURAN, DISCOVER, SETTINGS }

enum class PlacePurpose { ADD, TRAVEL }

sealed interface Route {
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
}

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
    var stack by mutableStateOf(listOf<Route>())
        private set

    private val _gps = MutableStateFlow<GpsUi>(GpsUi.Idle)
    val gps: StateFlow<GpsUi> = _gps.asStateFlow()
    private var gpsJob: Job? = null

    init {
        viewModelScope.launch { _cities.value = sakina.cities.all() }
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
