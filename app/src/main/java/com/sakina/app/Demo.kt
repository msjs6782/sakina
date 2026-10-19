package com.sakina.app

import android.content.Intent
import android.util.Log
import com.sakina.app.alerts.AlertsCenter
import com.sakina.app.core.alerts.AlertMode
import com.sakina.app.core.alerts.AudioRoute
import com.sakina.app.core.alerts.ReminderKind
import com.sakina.app.core.calendar.UmmAlQura
import com.sakina.app.core.format.Lang
import com.sakina.app.core.prayer.CalcMethod
import com.sakina.app.core.prayer.HighLatitudeRule
import com.sakina.app.core.prayer.PrayerKey
import com.sakina.app.core.prayer.UmmAlQuraIsha
import com.sakina.app.data.AppClock
import com.sakina.app.data.AppSettings
import com.sakina.app.data.HomeLayout
import com.sakina.app.data.LocationSource
import com.sakina.app.data.OffsetPrefs
import com.sakina.app.data.SavedLocation
import com.sakina.app.data.SettingsOps
import com.sakina.app.data.ThemeMode
import com.sakina.app.data.VisualStyle
import com.sakina.app.ui.PlacePurpose
import com.sakina.app.ui.Route
import com.sakina.app.ui.Tab
import com.sakina.app.ui.times.TimesView
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

data class DemoStart(val tab: Tab?, val routes: List<Route>, val view: TimesView?)

/**
 * Debug builds only: prepares app state from intent extras so the emulator check in CI can open
 * reproducible screens (fixed clock, chosen places and settings). Release builds never call this.
 */
object Demo {
    private const val TAG = "SakinaDemo"

    fun apply(app: SakinaApp, intent: Intent?): DemoStart? {
        if (!BuildConfig.DEBUG) return null
        val e = intent?.extras ?: return null
        if (e.keySet().none { it.startsWith("demo_") }) return null
        if (e.getBoolean("demo_reset")) app.alertState.reset()
        e.getString("demo_now")?.let { AppClock.pin(Instant.parse(it), freeze = e.getBoolean("demo_freeze")) }
        runBlocking {
            val cities = app.cities.all()
            app.settings.update { start ->
                var s = if (e.getBoolean("demo_reset")) AppSettings() else start
                if (e.containsKey("demo_onboarded")) s = s.copy(onboardingDone = e.getBoolean("demo_onboarded"))
                e.getString("demo_cities")?.split(',')?.filter { it.isNotBlank() }?.forEach { id ->
                    cities.firstOrNull { it.id == id }?.let { s = SettingsOps.addAndActivate(s, SettingsOps.cityLocation(it)) }
                }
                e.getString("demo_active")?.let { s = SettingsOps.activate(s, "city:$it") }
                e.getString("demo_manual")?.split(',')?.takeIf { it.size == 4 }?.let { f ->
                    // "_" stands for a space: adb shell would split the argument at a real one.
                    val name = f[0].replace('_', ' ')
                    s = SettingsOps.addAndActivate(s, SavedLocation(SettingsOps.newId("manual"), name, name, f[1].toDouble(), f[2].toDouble(), f[3], LocationSource.MANUAL))
                }
                e.getString("demo_travel")?.let { id ->
                    cities.firstOrNull { it.id == id }?.let { s = SettingsOps.startTravel(s, SettingsOps.cityLocation(it).copy(id = "trip:demo"), AppClock.now().toEpochMilli()) }
                }
                e.getString("demo_lang")?.let { s = s.copy(language = if (it == "en") Lang.EN else Lang.AR, arabicDigits = it != "en") }
                if (e.containsKey("demo_latin")) s = s.copy(arabicDigits = !e.getBoolean("demo_latin"))
                if (e.containsKey("demo_clock24")) s = s.copy(clock24 = e.getBoolean("demo_clock24"))
                e.getString("demo_style")?.let { s = s.copy(style = VisualStyle.valueOf(it)) }
                e.getString("demo_theme")?.let { s = s.copy(themeMode = ThemeMode.valueOf(it)) }
                e.getString("demo_layout")?.let { s = s.copy(homeLayout = HomeLayout.valueOf(it)) }
                e.getString("demo_method")?.let { s = s.copy(method = CalcMethod.valueOf(it)) }
                e.getString("demo_highlat")?.let { s = s.copy(highLatitude = HighLatitudeRule.valueOf(it)) }
                e.getString("demo_isha")?.let { s = s.copy(ummAlQuraIsha = UmmAlQuraIsha.valueOf(it)) }
                if (e.containsKey("demo_dynamic")) s = s.copy(dynamicColor = e.getBoolean("demo_dynamic"))
                if (e.containsKey("demo_hero_art")) s = s.copy(heroArt = e.getBoolean("demo_hero_art"))
                if (e.containsKey("demo_hijri")) s = s.copy(hijriOffset = e.getInt("demo_hijri"))
                e.getString("demo_alerts")?.let { spec ->
                    var a = s.alerts
                    spec.split(',').mapNotNull { p -> p.split('=').takeIf { it.size == 2 } }.forEach { (k, v) ->
                        a = a.withPrayer(PrayerKey.valueOf(k.uppercase())) { it.copy(mode = AlertMode.valueOf(v.uppercase())) }
                    }
                    s = s.copy(alerts = a)
                }
                if (e.containsKey("demo_pre")) s = s.copy(alerts = s.alerts.copy(preMinutes = e.getInt("demo_pre")))
                if (e.containsKey("demo_iqama")) s = s.copy(alerts = s.alerts.copy(iqamaMinutes = e.getInt("demo_iqama")))
                if (e.containsKey("demo_card")) s = s.copy(alerts = s.alerts.copy(ongoingCard = e.getBoolean("demo_card")))
                if (e.containsKey("demo_route_alarm")) s = s.copy(alerts = s.alerts.copy(audioRoute = if (e.getBoolean("demo_route_alarm")) AudioRoute.ALARM else AudioRoute.NOTIFICATION))
                if (e.containsKey("demo_suhoor")) s = s.copy(alerts = s.alerts.copy(suhoor = s.alerts.suhoor.copy(enabled = true, ramadanOnly = false, minutesBeforeFajr = e.getInt("demo_suhoor"))))
                // Phase 7: calendar view, personal occasions ("date:title[:minute[:repeat]]" with _ for spaces, ";"-free), fasting reminders.
                e.getString("demo_cal_kind")?.let { k -> s = s.copy(calendar = s.calendar.copy(kind = com.sakina.app.core.calendar.CalendarKind.valueOf(k.uppercase()))) }
                if (e.containsKey("demo_cal_list")) s = s.copy(calendar = s.calendar.copy(listMode = e.getBoolean("demo_cal_list")))
                e.getString("demo_occasions")?.split(',')?.filter { it.isNotBlank() }?.forEachIndexed { i, spec ->
                    val f = spec.split(':')
                    val d = LocalDate.parse(f[0])
                    val h = com.sakina.app.core.calendar.UmmAlQura.displayed(d, s.hijriOffset)
                    val o = com.sakina.app.core.calendar.PersonalOccasion(
                        id = "demo$i", title = f[1].replace('_', ' '), epochDay = d.toEpochDay(),
                        minuteOfDay = f.getOrNull(2)?.toIntOrNull(),
                        repeat = f.getOrNull(3)?.let { r -> com.sakina.app.core.calendar.PersonalOccasion.Repeat.valueOf(r.uppercase()) } ?: com.sakina.app.core.calendar.PersonalOccasion.Repeat.NONE,
                        hijriMonth = h?.month ?: 0, hijriDay = h?.day ?: 0, notify = true,
                    )
                    s = s.copy(calendar = s.calendar.copy(occasions = s.calendar.occasions + o))
                }
                e.getString("demo_fasting")?.let { spec ->
                    // monthu|white|both[:minute[:same]]
                    val f = spec.split(':')
                    s = s.copy(calendar = s.calendar.copy(fasting = com.sakina.app.core.calendar.FastingReminders(
                        mondayThursday = f[0] == "monthu" || f[0] == "both", whiteDays = f[0] == "white" || f[0] == "both",
                        minuteOfDay = f.getOrNull(1)?.toIntOrNull() ?: 21 * 60, eveningBefore = f.getOrNull(2) != "same",
                    )))
                }
                // KIND or KIND:minuteOfDay, comma-separated.
                e.getString("demo_reminders")?.split(',')?.filter { it.isNotBlank() }?.forEach { spec ->
                    val f = spec.split(':')
                    s = s.copy(alerts = s.alerts.withReminder(ReminderKind.valueOf(f[0].uppercase())) {
                        it.copy(enabled = true, minuteOfDay = f.getOrNull(1)?.toIntOrNull() ?: it.minuteOfDay)
                    })
                }
                e.getString("demo_offsets")?.let { spec ->
                    var o = OffsetPrefs().toCore()
                    spec.split(',').mapNotNull { p -> p.split('=').takeIf { it.size == 2 } }.forEach { (k, v) ->
                        o = o.with(PrayerKey.valueOf(k.uppercase()), v.toInt())
                    }
                    s = s.copy(offsets = OffsetPrefs.from(o))
                }
                s
            }
        }
        if (e.getBoolean("demo_hijri_check")) hijriCheck()
        if (e.containsKey("demo_quiet")) {
            val m = e.getInt("demo_quiet")
            if (m > 0) AlertsCenter.startQuiet(app, java.time.Duration.ofMinutes(m.toLong())) else AlertsCenter.endQuiet(app)
        }
        val quranState = app.quranState
        if (e.getBoolean("demo_reset")) runBlocking { quranState.update { com.sakina.app.data.QuranState() } }
        e.getString("demo_quran")?.let { spec ->
            runBlocking {
                quranState.update { st ->
                    var r = st.reader
                    spec.split(',').mapNotNull { p -> p.split('=').takeIf { it.size == 2 } }.forEach { (k, v) ->
                        r = when (k) {
                            "mode" -> r.copy(mode = com.sakina.app.data.ReaderMode.valueOf(v.uppercase()))
                            "translation" -> r.copy(showTranslation = v == "1")
                            "tafsir" -> r.copy(showTafsir = v == "1")
                            "font" -> r.copy(fontSize = v.toInt())
                            "view" -> r.copy(view = com.sakina.app.data.QuranView.valueOf(v.uppercase()))
                            "night" -> r.copy(mushafNight = com.sakina.app.data.MushafNight.valueOf(v.uppercase()))
                            "auto" -> r.copy(autoDownload = v == "1")
                            else -> r
                        }
                    }
                    st.copy(reader = r)
                }
            }
        }
        e.getString("demo_reciter")?.let { key ->
            runBlocking { quranState.update { it.copy(listen = it.listen.copy(reciter = key)) } }
        }
        if (e.getBoolean("demo_recitations_clear")) {
            java.io.File(app.filesDir, "recitations").deleteRecursively()
        }
        if (e.containsKey("demo_diagnostics")) com.sakina.app.audio.PlaybackDiagnostics.setEnabled(app, e.getBoolean("demo_diagnostics"))
        e.getString("demo_bookmarks")?.let { spec ->
            runBlocking {
                quranState.update { st ->
                    st.copy(bookmarks = spec.split(',').mapIndexed { i, x -> x.split(':').let { com.sakina.app.data.Bookmark(it[0].toInt(), it[1].toInt(), 1_700_000_000_000L + i) } })
                }
            }
        }
        e.getString("demo_lastread")?.let { spec ->
            val (su, ay, pg) = spec.split(':').map { it.toInt() }
            runBlocking { quranState.update { it.copy(lastRead = com.sakina.app.data.LastRead(su, ay, pg, 1_700_000_000_000L)) } }
        }
        e.getString("demo_khatma")?.let { spec ->
            // days:progress:startedDaysAgo
            val f = spec.split(':').map { it.toInt() }
            val plan = com.sakina.app.core.quran.Khatma.create(java.time.LocalDate.now().minusDays(f.getOrElse(2) { 0 }.toLong()), f[0])
                .copy(progressPage = f.getOrElse(1) { 0 })
            runBlocking { quranState.update { it.copy(khatma = plan) } }
        }
        // Phase 6: adhkar, tasbih and qibla state.
        if (e.getBoolean("demo_reset") || e.getBoolean("demo_tools_reset")) runBlocking { app.tools.update { com.sakina.app.data.ToolsState() } }
        e.getString("demo_favorites")?.let { spec -> runBlocking { app.tools.update { it.copy(favorites = spec.split(',').filter { x -> x.isNotBlank() }) } } }
        e.getString("demo_tasbih")?.let { spec ->
            // count:target[:label]
            val f = spec.split(':')
            runBlocking { app.tools.update { it.copy(tasbih = it.tasbih.copy(count = f[0].toInt(), target = f.getOrElse(1) { "0" }.toInt(), label = f.getOrElse(2) { "" }.replace('_', ' ')), tasbihUndo = null) } }
        }
        e.getString("demo_wird")?.let { spec ->
            // practice:session:id=taps,id=taps — today's date in the active place.
            val f = spec.split(':', limit = 3)
            val zone = runBlocking { app.settings.current() }.activeLocation?.zoneId ?: java.time.ZoneId.systemDefault()
            val date = AppClock.now().atZone(zone).toLocalDate().toString()
            val key = com.sakina.app.core.adhkar.Wird.key(date, f[0], f[1])
            val taps = f.getOrElse(2) { "" }.split(',').mapNotNull { kv -> kv.split('=').takeIf { it.size == 2 }?.let { it[0] to it[1].toInt() } }.toMap()
            runBlocking { app.tools.update { it.copy(progress = it.progress + (key to taps)) } }
        }
        e.getString("demo_qibla_map")?.let { m -> runBlocking { app.tools.update { it.copy(qibla = it.qibla.copy(mapMode = com.sakina.app.data.QiblaMapMode.valueOf(m.uppercase()))) } } }
        com.sakina.app.data.CompassEngine.forcedSource = when (e.getString("demo_compass")) {
            "rv" -> com.sakina.app.core.qibla.CompassSource.ROTATION_VECTOR
            "geomag" -> com.sakina.app.core.qibla.CompassSource.GEOMAGNETIC_ROTATION_VECTOR
            "accelmag" -> com.sakina.app.core.qibla.CompassSource.ACCEL_MAGNETOMETER
            "none" -> com.sakina.app.core.qibla.CompassSource.NONE
            else -> null
        }
        com.sakina.app.ui.mushaf.MushafDebug.initialZoom = e.getFloat("demo_zoom", 1f)
        com.sakina.app.ui.widgets.WidgetsDebug.sizes = e.getBoolean("demo_widget_sizes")
        com.sakina.app.ui.widgets.WidgetsDebug.gallerySky = e.getBoolean("demo_gallery_sky")
        if (e.containsKey("demo_idle_ms")) com.sakina.app.audio.RecitationService.idleEndMs = e.getLong("demo_idle_ms")
        com.sakina.app.data.RecitationRepository.debugSlowMs = e.getLong("demo_dl_slow_ms", 0)
        if (e.getBoolean("demo_mushaf_clear")) runBlocking {
            app.mushaf.init()
            app.mushaf.delete((1..com.sakina.app.core.mushaf.MushafManifest.PAGES).toList())
        }
        e.getInt("demo_mushaf_corrupt", 0).takeIf { it > 0 }?.let { p ->
            // Flips one byte of a stored page (same size), to test that the integrity check finds it.
            val f = app.mushaf.file(p)
            if (f.isFile) java.io.RandomAccessFile(f, "rw").use { raf ->
                raf.seek(5000); val b = raf.read(); raf.seek(5000); raf.write(b xor 0x5a)
            }
            Log.i(TAG, "corrupted stored page $p: ${f.isFile}")
        }
        e.getString("demo_test")?.split(':')?.let { f ->
            AlertsCenter.test(app, AlertMode.valueOf(f[0].uppercase()), f.getOrNull(1)?.toLongOrNull() ?: 5)
        }
        if (e.getBoolean("demo_log_alarm")) {
            val sc = app.alertState.scheduled()
            Log.i(TAG, "AlarmState scheduled=${sc?.ids} at=${sc?.at} epochMs=${sc?.at?.toEpochMilli()} exact=${sc?.exact}")
        }
        val tab = e.getString("demo_tab")?.let { Tab.valueOf(it.uppercase()) }
        val routes = e.getString("demo_route")?.split(',')?.mapNotNull { r ->
            when {
                r.startsWith("quran_reader:") -> {
                    val (su, ay) = r.removePrefix("quran_reader:").split(':').map { it.toInt() }
                    val d = runBlocking { app.quran.load() }
                    Route.QuranReader(d.meta.global(com.sakina.app.core.quran.AyahRef(su, ay)), highlight = e.getBoolean("demo_highlight"))
                }
                r.startsWith("quran_search:") -> Route.QuranSearch(r.removePrefix("quran_search:"))
                r.startsWith("listen:") -> Route.Listen(r.removePrefix("listen:").toInt())
                r.startsWith("adhkar_cat:") -> Route.AdhkarCategory(r.removePrefix("adhkar_cat:"))
                r.startsWith("calendar_day:") -> Route.HijriCalendar(LocalDate.parse(r.removePrefix("calendar_day:")).toEpochDay())
                r.startsWith("occasion_new:") -> Route.OccasionEdit("", LocalDate.parse(r.removePrefix("occasion_new:")).toEpochDay())
                r.startsWith("adhkar_search:") -> Route.Adhkar(r.removePrefix("adhkar_search:"))
                r.startsWith("adhkar_item:") -> Route.AdhkarItem(r.removePrefix("adhkar_item:"))
                r.startsWith("wird:") -> r.removePrefix("wird:").split(':').let { Route.Wird(it[0], it.getOrElse(1) { "" }) }
                r.startsWith("mushaf:") -> {
                    val f = r.removePrefix("mushaf:").split(':').map { it.toInt() }
                    val focus = if (f.size >= 3) runBlocking { app.quran.load() }.meta.global(com.sakina.app.core.quran.AyahRef(f[1], f[2])) else -1
                    Route.Mushaf(f[0], focus)
                }
                else -> route(r)
            }
        } ?: emptyList()
        val view = e.getString("demo_view")?.let { TimesView.valueOf(it.uppercase()) }
        Log.i(TAG, "applied tab=$tab routes=$routes view=$view")
        return DemoStart(tab, routes, view)
    }

    /**
     * Extras applied to the running app without restarting it (CI only): a test alert or quiet mode
     * while a recitation plays.
     */
    fun applyOnNew(app: SakinaApp, intent: Intent?) {
        if (!BuildConfig.DEBUG) return
        val e = intent?.extras ?: return
        if (e.containsKey("demo_quiet")) {
            val m = e.getInt("demo_quiet")
            if (m > 0) AlertsCenter.startQuiet(app, java.time.Duration.ofMinutes(m.toLong())) else AlertsCenter.endQuiet(app)
        }
        e.getString("demo_test")?.split(':')?.let { f ->
            AlertsCenter.test(app, AlertMode.valueOf(f[0].uppercase()), f.getOrNull(1)?.toLongOrNull() ?: 5)
        }
        if (e.getBoolean("demo_adhan_stop")) com.sakina.app.alerts.AdhanService.stop(app)
        Log.i(TAG, "applied on new intent: ${e.keySet().filter { it.startsWith("demo_") }}")
    }

    private fun route(name: String): Route? = when (name) {
        "locations" -> Route.Locations
        "calc" -> Route.Calc
        "appearance" -> Route.Appearance
        "home" -> Route.HomeSetup
        "calendar" -> Route.Calendar
        "about" -> Route.About
        "city" -> Route.CityPicker(PlacePurpose.ADD)
        "city_travel" -> Route.CityPicker(PlacePurpose.TRAVEL)
        "manual" -> Route.Manual(PlacePurpose.ADD)
        "gps" -> Route.Gps(PlacePurpose.ADD)
        "alerts" -> Route.Alerts
        "reminders" -> Route.Reminders
        "recordings" -> Route.Recordings
        "prayer_asr" -> Route.PrayerAlert(PrayerKey.ASR)
        "prayer_fajr" -> Route.PrayerAlert(PrayerKey.FAJR)
        "quran_search" -> Route.QuranSearch()
        "khatma" -> Route.Khatma
        "quran_sources" -> Route.QuranSources
        "mushaf_downloads" -> Route.MushafDownloads
        "listen" -> Route.Listen()
        "adhkar" -> Route.Adhkar()
        "tasbih" -> Route.Tasbih
        "qibla" -> Route.Qibla
        "month" -> Route.HijriCalendar()
        "calendar_tools" -> Route.CalendarTools
        "ramadan" -> Route.Ramadan
        "night" -> Route.Night
        "friday" -> Route.Friday
        "widgets" -> Route.Widgets
        else -> null
    }

    /** Compares the Umm al-Qura calendar used by the app (java.time) with Android's ICU implementation. */
    private fun hijriCheck() {
        var mismatches = 0
        var compared = 0
        val cal = android.icu.util.IslamicCalendar(android.icu.util.TimeZone.GMT_ZONE)
        cal.calculationType = android.icu.util.IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
        var d = LocalDate.of(2024, 1, 1)
        while (d.year < 2031) {
            cal.timeInMillis = d.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
            val icu = Triple(cal.get(android.icu.util.Calendar.YEAR), cal.get(android.icu.util.Calendar.MONTH) + 1, cal.get(android.icu.util.Calendar.DAY_OF_MONTH))
            val ours = UmmAlQura.fromGregorian(d)
            compared++
            if (ours == null || Triple(ours.year, ours.month, ours.day) != icu) {
                mismatches++
                if (mismatches <= 5) Log.w(TAG, "HijriCheck $d ours=$ours icu=$icu")
            }
            d = d.plusDays(1)
        }
        Log.i(TAG, "HijriCheck compared=$compared mismatches=$mismatches")
    }
}
