package com.sakina.app.core

import com.sakina.app.core.prayer.AsrMethod
import com.sakina.app.core.prayer.CalcConfig
import com.sakina.app.core.prayer.CalcMethod
import com.sakina.app.core.prayer.GeoPoint
import com.sakina.app.core.prayer.HighLatitudeRule
import com.sakina.app.core.prayer.PrayerEngine
import com.sakina.app.core.prayer.PrayerKey
import com.sakina.app.core.prayer.UmmAlQuraIsha
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.zip.GZIPInputStream
import kotlin.math.abs

/**
 * Compares the engine against an independent implementation (Adhan, Java branch) for 12 cities, the five
 * methods, both Asr options and the three high-latitude rules through a whole year.
 */
class PrayerReferenceTest {
    private val order = listOf(PrayerKey.FAJR, PrayerKey.SUNRISE, PrayerKey.DHUHR, PrayerKey.ASR, PrayerKey.MAGHRIB, PrayerKey.ISHA)

    @Test
    fun matchesAdhanReference() {
        val stream = javaClass.classLoader!!.getResourceAsStream("adhan_reference.tsv.gz")
        val lines = GZIPInputStream(stream).bufferedReader(Charsets.UTF_8).readLines().filter { !it.startsWith("#") && it.isNotBlank() }
        var compared = 0
        var exact = 0
        var worst = 0L
        val failures = mutableListOf<String>()
        for (line in lines) {
            val f = line.split('\t')
            val date = LocalDate.parse(f[7])
            val config = CalcConfig(
                method = CalcMethod.valueOf(f[4]),
                asr = AsrMethod.valueOf(f[5]),
                highLatitude = HighLatitudeRule.valueOf(f[6]),
                ummAlQuraIsha = UmmAlQuraIsha.FIXED_90, // the reference has no Ramadan rule
            )
            val day = PrayerEngine.compute(date, GeoPoint(f[1].toDouble(), f[2].toDouble()), ZoneId.of(f[3]), config)
            val base = date.atStartOfDay(ZoneOffset.UTC).toEpochSecond()
            order.forEachIndexed { i, key ->
                val ref = f[8 + i]
                if (ref == "-") return@forEachIndexed
                val expected = base + ref.toLong() * 60
                val actual = day[key].instant?.epochSecond
                if (actual == null) {
                    failures += "${f[0]} ${f[4]} ${f[5]} ${f[6]} $date $key missing"
                    return@forEachIndexed
                }
                val diff = abs(actual - expected) / 60
                compared++
                if (diff == 0L) exact++
                worst = maxOf(worst, diff)
                if (diff > 1) failures += "${f[0]} ${f[4]} ${f[5]} ${f[6]} $date $key off by $diff min"
            }
        }
        println("Reference comparison: $compared times, $exact exact, worst difference $worst min")
        assertTrue(failures.take(20).joinToString("\n"), failures.isEmpty())
        assertTrue("Too few exact matches: $exact of $compared", exact >= compared * 0.995)
        assertTrue(compared > 20000)
    }

    /** Official Umm al-Qura times for Makkah (ummulqura.org.sa, as compiled in the Adhan test data). */
    @Test
    fun matchesOfficialUmmAlQuraMakkah() {
        val json = javaClass.classLoader!!.getResourceAsStream("adhan_makkah_ummalqura.json")!!.bufferedReader().readText()
        val entries = Regex("\\{\\s*\"date\":\\s*\"([0-9-]+)\",([^}]*)\\}").findAll(json).toList()
        assertEquals(12, entries.size)
        val zone = ZoneId.of("Asia/Riyadh")
        val names = mapOf(
            PrayerKey.FAJR to "fajr", PrayerKey.SUNRISE to "sunrise", PrayerKey.DHUHR to "dhuhr",
            PrayerKey.ASR to "asr", PrayerKey.MAGHRIB to "maghrib", PrayerKey.ISHA to "isha",
        )
        for (m in entries) {
            val date = LocalDate.parse(m.groupValues[1])
            val day = PrayerEngine.compute(date, GeoPoint(21.427009, 39.828685), zone, CalcConfig())
            for ((key, name) in names) {
                val text = Regex("\"$name\":\\s*\"(\\d+):(\\d+) (AM|PM)\"").find(m.groupValues[2])!!.groupValues
                var h = text[1].toInt() % 12
                if (text[3] == "PM") h += 12
                val expectedMin = h * 60 + text[2].toInt()
                val local = day[key].instant!!.atZone(zone)
                val actualMin = local.hour * 60 + local.minute
                assertTrue("$date $key expected $expectedMin got $actualMin", abs(actualMin - expectedMin) <= 1)
            }
        }
    }
}
