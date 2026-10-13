package com.sakina.app.core.location

import java.text.Normalizer

data class City(
    val id: String,
    val nameAr: String,
    val nameEn: String,
    val country: String,
    val countryAr: String,
    val countryEn: String,
    val latitude: Double,
    val longitude: Double,
    val zone: String,
) {
    internal val keys: List<String> by lazy {
        listOf(nameAr, nameEn, countryAr, countryEn, id).map { SearchText.normalize(it) }
    }
}

object CityParser {
    fun parse(tsv: String): List<City> = tsv.lineSequence()
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .map { line ->
            val f = line.split('\t')
            require(f.size >= 9) { "Bad city line: $line" }
            City(f[0], f[1], f[2], f[3], f[4], f[5], f[6].toDouble(), f[7].toDouble(), f[8])
        }.toList()
}

/** Arabic- and Latin-aware text folding for search. */
object SearchText {
    private val tashkeel = Regex("[ؐ-ًؚ-ٰٟۖ-ۭـ]")
    private val latinMarks = Regex("\\p{Mn}+")
    private val spaces = Regex("[\\s\\-_'’‘.,()]+")

    fun normalize(text: String): String {
        var s = text.replace(tashkeel, "")
        s = s.map {
            when (it) {
                'أ', 'إ', 'آ', 'ٱ' -> 'ا'
                'ة' -> 'ه'
                'ى' -> 'ي'
                'ؤ' -> 'و'
                'ئ' -> 'ي'
                else -> it
            }
        }.joinToString("")
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replace(latinMarks, "")
        return s.lowercase().replace(spaces, " ").trim()
    }

    /** Also drops a leading Arabic or Latin article from each word ("ال", "al ", "el "). */
    fun loose(text: String): String = normalize(text).split(' ').joinToString(" ") { w ->
        when {
            w.length > 3 && w.startsWith("ال") -> w.substring(2)
            else -> w
        }
    }.replace(Regex("\\b(al|el|ad|ar|as|an|at|az) "), "").replace(" ", "")
}

object CitySearch {
    fun search(cities: List<City>, query: String, limit: Int = 60): List<City> {
        val q = SearchText.normalize(query)
        if (q.isEmpty()) return cities.take(limit)
        val ql = SearchText.loose(query)
        fun score(c: City): Int {
            var best = Int.MAX_VALUE
            c.keys.forEachIndexed { i, k ->
                val loose = SearchText.loose(k)
                val s = when {
                    k == q -> 0
                    k.startsWith(q) -> 1
                    loose.startsWith(ql) -> 2
                    k.split(' ').any { it.startsWith(q) } -> 3
                    k.contains(q) || loose.contains(ql) -> 5
                    else -> Int.MAX_VALUE
                }
                // Country matches rank below city-name matches.
                val weighted = if (s == Int.MAX_VALUE) s else s + (if (i >= 2) 10 else 0)
                best = minOf(best, weighted)
            }
            return best
        }
        return cities.asSequence()
            .map { it to score(it) }
            .filter { it.second != Int.MAX_VALUE }
            .sortedBy { it.second }
            .map { it.first }
            .take(limit)
            .toList()
    }

    /** The nearest listed city within [maxKm], used only to suggest a name after GPS. */
    fun nearest(cities: List<City>, lat: Double, lon: Double, maxKm: Double = 40.0): City? =
        cities.map { it to Geo.distanceKm(lat, lon, it.latitude, it.longitude) }
            .filter { it.second <= maxKm }
            .minByOrNull { it.second }?.first
}

object Geo {
    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p = Math.PI / 180
        val h = Math.sin((lat2 - lat1) * p / 2).let { it * it } +
            Math.cos(lat1 * p) * Math.cos(lat2 * p) * Math.sin((lon2 - lon1) * p / 2).let { it * it }
        return 12742.0 * Math.asin(Math.sqrt(h))
    }
}
