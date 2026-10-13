package com.sakina.app.data

import android.content.Context
import com.sakina.app.core.location.City
import com.sakina.app.core.location.CityParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** The bundled city list (assets/cities.tsv), loaded once on demand. */
class CityRepository(private val context: Context) {
    private val lock = Mutex()
    @Volatile private var cache: List<City>? = null

    suspend fun all(): List<City> = cache ?: lock.withLock {
        cache ?: withContext(Dispatchers.IO) {
            val text = context.assets.open("cities.tsv").bufferedReader(Charsets.UTF_8).use { it.readText() }
            CityParser.parse(text)
        }.also { cache = it }
    }

    suspend fun byId(id: String): City? = all().firstOrNull { it.id == id }
}
