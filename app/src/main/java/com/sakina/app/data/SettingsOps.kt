package com.sakina.app.data

import com.sakina.app.core.location.City
import java.util.UUID

/** Pure edits of [AppSettings], kept here so they can be unit-tested without Android. */
object SettingsOps {

    fun cityLocation(city: City) = SavedLocation(
        id = "city:" + city.id,
        nameAr = city.nameAr,
        nameEn = city.nameEn,
        latitude = city.latitude,
        longitude = city.longitude,
        zone = city.zone,
        source = LocationSource.CITY,
        cityId = city.id,
        countryAr = city.countryAr,
        countryEn = city.countryEn,
    )

    fun newId(prefix: String) = prefix + ":" + UUID.randomUUID().toString().take(8)

    /** Adds (or refreshes) a saved place and makes it active. Ends travel mode, since the user chose a place. */
    fun addAndActivate(s: AppSettings, loc: SavedLocation): AppSettings {
        val existing = s.locations.indexOfFirst { it.id == loc.id }
        val list = if (existing >= 0) {
            s.locations.toMutableList().also { it[existing] = loc }
        } else {
            (s.locations + loc).takeLast(AppSettings.MAX_LOCATIONS)
        }
        return s.copy(locations = list, activeLocationId = loc.id, travel = null)
    }

    fun activate(s: AppSettings, id: String): AppSettings =
        if (s.locations.any { it.id == id }) s.copy(activeLocationId = id) else s

    fun remove(s: AppSettings, id: String): AppSettings {
        val list = s.locations.filterNot { it.id == id }
        val active = if (s.activeLocationId == id) list.firstOrNull()?.id else s.activeLocationId
        val travel = s.travel?.takeIf { it.homeId != id }
        return s.copy(locations = list, activeLocationId = active, travel = travel)
    }

    fun rename(s: AppSettings, id: String, nameAr: String, nameEn: String): AppSettings =
        s.copy(locations = s.locations.map { if (it.id == id) it.copy(nameAr = nameAr, nameEn = nameEn) else it })

    /** Travel keeps the current saved place as home; the destination is temporary and not added to the list. */
    fun startTravel(s: AppSettings, destination: SavedLocation, now: Long): AppSettings {
        val home = s.locations.firstOrNull { it.id == s.activeLocationId } ?: s.locations.firstOrNull() ?: return s
        val homeId = s.travel?.homeId ?: home.id
        return s.copy(travel = TravelState(homeId, destination, now))
    }

    fun returnHome(s: AppSettings): AppSettings {
        val t = s.travel ?: return s
        val homeExists = s.locations.any { it.id == t.homeId }
        return s.copy(travel = null, activeLocationId = if (homeExists) t.homeId else s.activeLocationId)
    }

    /** Saves the travel destination as a regular place (and keeps travelling). */
    fun keepDestination(s: AppSettings): AppSettings {
        val t = s.travel ?: return s
        if (s.locations.any { it.id == t.destination.id }) return s
        return s.copy(locations = (s.locations + t.destination).takeLast(AppSettings.MAX_LOCATIONS))
    }

    fun moveCard(s: AppSettings, card: HomeCard, delta: Int): AppSettings {
        val list = s.orderedCards.toMutableList()
        val i = list.indexOfFirst { it.card == card }
        val j = i + delta
        if (i < 0 || j !in list.indices) return s
        val item = list.removeAt(i)
        list.add(j, item)
        return s.copy(homeCards = list)
    }

    fun setCardVisible(s: AppSettings, card: HomeCard, visible: Boolean): AppSettings =
        s.copy(homeCards = s.orderedCards.map { if (it.card == card) it.copy(visible = visible) else it })
}
