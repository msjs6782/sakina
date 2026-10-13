package com.sakina.app

import android.app.Application
import com.sakina.app.data.CityRepository
import com.sakina.app.data.LocationFetcher
import com.sakina.app.data.SettingsRepository

class SakinaApp : Application() {
    lateinit var settings: SettingsRepository
        private set
    lateinit var cities: CityRepository
        private set
    lateinit var gps: LocationFetcher
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
        cities = CityRepository(this)
        gps = LocationFetcher(this)
    }
}
