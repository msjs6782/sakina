@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.sakina.app.ui

import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakina.app.core.format.Fmt
import com.sakina.app.core.format.Lang
import com.sakina.app.core.prayer.PrayerEngine
import com.sakina.app.core.prayer.PrayerKey
import com.sakina.app.data.AppSettings
import com.sakina.app.data.ThemeMode
import com.sakina.app.ui.alerts.AlertsScreen
import com.sakina.app.ui.alerts.PrayerAlertScreen
import com.sakina.app.ui.alerts.RecordingsScreen
import com.sakina.app.ui.alerts.RemindersScreen
import com.sakina.app.ui.components.SakinaIcons
import com.sakina.app.ui.quran.KhatmaScreen
import com.sakina.app.ui.quran.QuranHome
import com.sakina.app.ui.quran.QuranReaderScreen
import com.sakina.app.ui.quran.QuranSearchScreen
import com.sakina.app.ui.quran.QuranSourcesScreen
import com.sakina.app.ui.home.HomeScreen
import com.sakina.app.ui.location.CityPickerScreen
import com.sakina.app.ui.location.GpsPlaceScreen
import com.sakina.app.ui.location.LocationsScreen
import com.sakina.app.ui.location.ManualPlaceScreen
import com.sakina.app.ui.more.PlannedScreen
import com.sakina.app.ui.onboarding.OnboardingScreen
import com.sakina.app.ui.settings.AboutScreen
import com.sakina.app.ui.settings.AppearanceScreen
import com.sakina.app.ui.settings.CalcScreen
import com.sakina.app.ui.settings.CalendarSettingsScreen
import com.sakina.app.ui.settings.HomeSetupScreen
import com.sakina.app.ui.settings.LicenseScreen
import com.sakina.app.ui.settings.SettingsScreen
import com.sakina.app.ui.theme.SakinaTheme
import com.sakina.app.ui.times.TimesScreen
import com.sakina.app.ui.times.TimesView
import java.time.Instant

val LocalFmt = staticCompositionLocalOf { Fmt(Lang.AR, clock24 = false, arabicDigits = true) }

/** The app's view model, for small shared pieces (such as home cards) that need navigation or Quran data. */
val LocalMainVm = staticCompositionLocalOf<MainViewModel?> { null }

/** Light between sunrise and Maghrib at the chosen place; null when that cannot be calculated. */
fun sunIsUp(s: AppSettings, now: Instant): Boolean? {
    val loc = s.activeLocation ?: return null
    val zone = loc.zoneId ?: return null
    if (!loc.isUsable) return null
    val day = PrayerEngine.compute(now.atZone(zone).toLocalDate(), loc.point, zone, s.calcConfig)
    val sunrise = day[PrayerKey.SUNRISE].instant ?: return null
    val maghrib = day[PrayerKey.MAGHRIB].instant ?: return null
    return !now.isBefore(sunrise) && now.isBefore(maghrib)
}

@Composable
fun SakinaRoot(vm: MainViewModel, startTimesView: TimesView = TimesView.DAY) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings ?: return
    val systemDark = isSystemInDarkTheme()
    val minute by rememberNow(60_000)
    val dark = when (s.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.SUN -> sunIsUp(s, minute)?.not() ?: systemDark
    }
    val activity = LocalContext.current as? ComponentActivity
    LaunchedEffect(dark) {
        activity?.enableEdgeToEdge(
            statusBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
    }
    val strings = Strings(s.language, s.arabicDigits)
    CompositionLocalProvider(
        LocalLayoutDirection provides if (s.language == Lang.AR) LayoutDirection.Rtl else LayoutDirection.Ltr,
        LocalStrings provides strings,
        LocalFmt provides s.fmt,
        LocalMainVm provides vm,
    ) {
        SakinaTheme(style = s.style, dark = dark, dynamic = s.dynamicColor) {
            Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                BackHandler(enabled = vm.stack.isNotEmpty() || (s.onboardingDone && vm.tab != Tab.HOME)) {
                    if (!vm.pop()) vm.tab = Tab.HOME
                }
                when (val top = vm.stack.lastOrNull()) {
                    null -> if (!s.onboardingDone) OnboardingScreen(vm, s) else MainTabs(vm, s, startTimesView)
                    else -> RouteScreen(top, vm, s)
                }
            }
        }
    }
}

@Composable
private fun RouteScreen(route: Route, vm: MainViewModel, s: AppSettings) {
    when (route) {
        Route.Locations -> LocationsScreen(vm, s)
        Route.Calc -> CalcScreen(vm, s)
        Route.Appearance -> AppearanceScreen(vm, s)
        Route.HomeSetup -> HomeSetupScreen(vm, s)
        Route.Calendar -> CalendarSettingsScreen(vm, s)
        Route.About -> AboutScreen(vm)
        is Route.CityPicker -> CityPickerScreen(vm, route.purpose)
        is Route.Manual -> ManualPlaceScreen(vm, route.purpose)
        is Route.Gps -> GpsPlaceScreen(vm, route.purpose)
        is Route.License -> LicenseScreen(vm, route.asset, route.title)
        Route.Alerts -> AlertsScreen(vm, s)
        is Route.PrayerAlert -> PrayerAlertScreen(vm, s, route.key)
        Route.Reminders -> RemindersScreen(vm, s)
        Route.Recordings -> RecordingsScreen(vm)
        is Route.QuranReader -> QuranReaderScreen(vm, route.global, route.highlight)
        is Route.QuranSearch -> QuranSearchScreen(vm, route.query)
        Route.Khatma -> KhatmaScreen(vm)
        Route.QuranSources -> QuranSourcesScreen(vm)
    }
}

@Composable
private fun MainTabs(vm: MainViewModel, s: AppSettings, startTimesView: TimesView) {
    val str = LocalStrings.current
    val tabs = listOf(
        Tab.HOME to (SakinaIcons.Home to str.tabHome),
        Tab.TIMES to (SakinaIcons.Clock to str.tabTimes),
        Tab.QURAN to (SakinaIcons.Book to str.tabQuran),
        Tab.DISCOVER to (SakinaIcons.Compass to str.tabDiscover),
        Tab.SETTINGS to (SakinaIcons.Sliders to str.tabSettings),
    )
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                tabs.forEach { (tab, item) ->
                    NavigationBarItem(
                        selected = vm.tab == tab,
                        onClick = { vm.tab = tab },
                        icon = { Icon(item.first, null) },
                        label = { Text(item.second, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        modifier = Modifier.testTag("tab_" + tab.name.lowercase()),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            when (vm.tab) {
                Tab.HOME -> HomeScreen(vm, s, padding)
                Tab.TIMES -> TimesScreen(vm, s, padding, startTimesView)
                Tab.QURAN -> QuranHome(vm, padding)
                Tab.DISCOVER -> PlannedScreen(str.discoverSoon, str.discoverSoonBody, SakinaIcons.Compass, padding)
                Tab.SETTINGS -> SettingsScreen(vm, s, padding)
            }
        }
    }
}
