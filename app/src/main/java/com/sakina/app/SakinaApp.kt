package com.sakina.app

import android.app.Application
import com.sakina.app.alerts.AlertScheduler
import com.sakina.app.alerts.AlertState
import com.sakina.app.alerts.Channels
import com.sakina.app.alerts.SoundLibrary
import com.sakina.app.data.CityRepository
import com.sakina.app.data.LocationFetcher
import com.sakina.app.data.MushafRepository
import com.sakina.app.data.RecitationRepository
import com.sakina.app.data.QuranRepository
import com.sakina.app.data.QuranStateRepository
import com.sakina.app.data.SettingsRepository
import com.sakina.app.ui.Strings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class SakinaApp : Application() {
    lateinit var settings: SettingsRepository
        private set
    lateinit var cities: CityRepository
        private set
    lateinit var gps: LocationFetcher
        private set
    lateinit var alertState: AlertState
        private set
    lateinit var quran: QuranRepository
        private set
    lateinit var quranState: QuranStateRepository
        private set
    lateinit var mushaf: MushafRepository
        private set
    lateinit var recitations: RecitationRepository
        private set

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
        cities = CityRepository(this)
        gps = LocationFetcher(this)
        alertState = AlertState(this)
        quran = QuranRepository(this)
        quranState = QuranStateRepository(this)
        mushaf = MushafRepository(this, appScope)
        recitations = RecitationRepository(this, appScope)
        // Whenever anything that affects alerts changes (and once at start), rebuild the alarm chain.
        appScope.launch {
            settings.settings
                .map { s -> listOf(s.alerts, s.calcConfig, s.activeLocation, s.language, s.arabicDigits, s.clock24) }
                .distinctUntilChanged()
                .collect {
                    val s = settings.current()
                    Channels.ensure(this@SakinaApp, Strings(s.language, s.arabicDigits))
                    SoundLibrary.cleanup(this@SakinaApp, s)
                    AlertScheduler.run(this@SakinaApp, AlertScheduler.Run.REFRESH, "settings")
                }
        }
    }
}
