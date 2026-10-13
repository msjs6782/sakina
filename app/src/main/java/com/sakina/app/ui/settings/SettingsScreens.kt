@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sakina.app.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sakina.app.BuildConfig
import com.sakina.app.core.format.Lang
import com.sakina.app.core.prayer.AsrMethod
import com.sakina.app.core.prayer.CalcMethod
import com.sakina.app.core.prayer.HighLatitudeRule
import com.sakina.app.core.prayer.PrayerEngine
import com.sakina.app.core.prayer.PrayerKey
import com.sakina.app.core.prayer.UmmAlQuraIsha
import com.sakina.app.data.AppClock
import com.sakina.app.data.AppSettings
import com.sakina.app.data.HomeLayout
import com.sakina.app.data.OffsetPrefs
import com.sakina.app.data.SettingsOps
import com.sakina.app.data.ThemeMode
import com.sakina.app.data.VisualStyle
import com.sakina.app.ui.LocalFmt
import com.sakina.app.ui.LocalStrings
import com.sakina.app.ui.MainViewModel
import com.sakina.app.ui.Route
import com.sakina.app.ui.components.ChoiceRow
import com.sakina.app.ui.components.Divider
import com.sakina.app.ui.components.NavRow
import com.sakina.app.ui.components.NoteText
import com.sakina.app.ui.components.SakinaIcons
import com.sakina.app.ui.components.ScreenBar
import com.sakina.app.ui.components.SectionCard
import com.sakina.app.ui.components.Stepper
import com.sakina.app.ui.components.SwitchRow
import com.sakina.app.ui.location.FullScreen
import com.sakina.app.ui.theme.dynamicColorSupported
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(vm: MainViewModel, s: AppSettings, contentPadding: PaddingValues) {
    val str = LocalStrings.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(str.tabSettings, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp))
        SectionCard(padding = PaddingValues(6.dp)) {
            NavRow(SakinaIcons.Pin, str.locations, s.activeLocation?.name(str.lang) ?: str.chooseLocationTitle, onClick = { vm.push(Route.Locations) })
            NavRow(SakinaIcons.Clock, str.settingsCalc, str.methodName(s.method), onClick = { vm.push(Route.Calc) })
        }
        SectionCard(padding = PaddingValues(6.dp)) {
            NavRow(SakinaIcons.Palette, str.settingsAppearance, str.styleName(s.style) + " · " + str.themeName(s.themeMode) + " · " + (if (s.language == Lang.AR) "العربية" else "English"), onClick = { vm.push(Route.Appearance) })
            NavRow(SakinaIcons.Grid, str.settingsHome, str.layoutName(s.homeLayout), onClick = { vm.push(Route.HomeSetup) })
            NavRow(SakinaIcons.Calendar, str.settingsCalendar, str.hijriOffsetTitle + ": " + LocalFmt.current.signed(s.hijriOffset), onClick = { vm.push(Route.Calendar) })
        }
        SectionCard(padding = PaddingValues(6.dp)) {
            NavRow(SakinaIcons.Info, str.settingsAbout, str.aboutVersion + " " + BuildConfig.VERSION_NAME, onClick = { vm.push(Route.About) })
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SubScreen(title: String, vm: MainViewModel, content: @Composable () -> Unit) {
    FullScreen {
        Column(Modifier.fillMaxSize()) {
            ScreenBar(title, onBack = { vm.pop() })
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                content()
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun CalcScreen(vm: MainViewModel, s: AppSettings) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    SubScreen(str.settingsCalc, vm) {
        // Live preview of today's times with the current choices.
        SectionCard(title = str.preview, icon = SakinaIcons.Clock) {
            val loc = s.activeLocation
            val zone = loc?.zoneId
            if (loc == null || zone == null) {
                NoteText(str.noLocationPreview)
            } else {
                val today = AppClock.now().atZone(zone).toLocalDate()
                val day = PrayerEngine.compute(today, loc.point, zone, s.calcConfig)
                Text(loc.name(str.lang), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    day.times.forEach { t ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(str.prayer(t.key), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            Text(t.instant?.let { fmt.time(it, zone, suffix = false) } ?: "—", style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
            }
        }
        SectionCard(title = str.method, icon = SakinaIcons.Compass) {
            CalcMethod.entries.forEach { m ->
                ChoiceRow(s.method == m, str.methodName(m), str.methodDetail(m), onClick = { vm.update { it.copy(method = m) } })
            }
        }
        if (s.method == CalcMethod.UMM_AL_QURA) {
            SectionCard(title = str.uqIshaTitle, icon = SakinaIcons.Isha) {
                UmmAlQuraIsha.entries.forEach { p ->
                    ChoiceRow(s.ummAlQuraIsha == p, str.uqIshaName(p), onClick = { vm.update { it.copy(ummAlQuraIsha = p) } })
                }
                Spacer(Modifier.height(6.dp))
                NoteText(str.uqIshaNote)
            }
        }
        SectionCard(title = str.asrTitle, icon = SakinaIcons.Asr) {
            AsrMethod.entries.forEach { a ->
                ChoiceRow(s.asr == a, str.asrName(a), onClick = { vm.update { it.copy(asr = a) } })
            }
        }
        SectionCard(title = str.highLatTitle, icon = SakinaIcons.Globe) {
            NoteText(str.highLatNote)
            Spacer(Modifier.height(6.dp))
            HighLatitudeRule.entries.forEach { r ->
                ChoiceRow(s.highLatitude == r, str.highLatName(r), onClick = { vm.update { it.copy(highLatitude = r) } })
            }
        }
        SectionCard(title = str.offsetsTitle, icon = SakinaIcons.Sliders) {
            NoteText(str.offsetsNote)
            Spacer(Modifier.height(6.dp))
            val offsets = s.offsets.toCore()
            PrayerKey.entries.forEach { k ->
                Stepper(offsets[k], str.prayer(k), -60, 60, fmt.signed(offsets[k])) { v ->
                    vm.update { it.copy(offsets = OffsetPrefs.from(it.offsets.toCore().with(k, v))) }
                }
            }
            if (!offsets.isZero) {
                TextButton(onClick = { vm.update { it.copy(offsets = OffsetPrefs()) } }) { Text(str.resetOffsets) }
            }
        }
    }
}

@Composable
fun AppearanceScreen(vm: MainViewModel, s: AppSettings) {
    val str = LocalStrings.current
    SubScreen(str.settingsAppearance, vm) {
        SectionCard(title = str.language, icon = SakinaIcons.Globe) {
            Segmented(
                listOf("العربية", "English"),
                if (s.language == Lang.AR) 0 else 1,
            ) { i ->
                val lang = if (i == 0) Lang.AR else Lang.EN
                vm.update { it.copy(language = lang, arabicDigits = if (it.language != lang) lang == Lang.AR else it.arabicDigits) }
            }
        }
        SectionCard(title = str.styleTitle, icon = SakinaIcons.Palette) {
            VisualStyle.entries.forEach { st ->
                StyleOption(st, s.style == st, str.styleName(st), str.styleDetail(st)) { vm.update { it.copy(style = st) } }
                Spacer(Modifier.height(8.dp))
            }
        }
        SectionCard(title = str.themeTitle, icon = SakinaIcons.Moon) {
            val modes = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SUN)
            Segmented(modes.map { str.themeName(it) }, modes.indexOf(s.themeMode)) { i -> vm.update { it.copy(themeMode = modes[i]) } }
            Spacer(Modifier.height(8.dp))
            NoteText(str.sunThemeNote)
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                checked = s.dynamicColor && dynamicColorSupported,
                title = str.dynamicColor,
                detail = if (dynamicColorSupported) str.dynamicColorNote else str.dynamicUnsupported,
                enabled = dynamicColorSupported,
            ) { v -> vm.update { it.copy(dynamicColor = v) } }
        }
        SectionCard(title = str.timeFormat, icon = SakinaIcons.Clock) {
            Segmented(listOf(str.h12, str.h24), if (s.clock24) 1 else 0) { i -> vm.update { it.copy(clock24 = i == 1) } }
            Spacer(Modifier.height(12.dp))
            Text(str.digits, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Segmented(listOf(str.digitsArabic, str.digitsLatin), if (s.arabicDigits) 0 else 1) { i -> vm.update { it.copy(arabicDigits = i == 0) } }
        }
        SectionCard(title = str.mushafNight, icon = SakinaIcons.Book) {
            SwitchRow(s.mushafNight, str.mushafNight, str.mushafNightNote) { v -> vm.update { it.copy(mushafNight = v) } }
        }
    }
}

@Composable
fun Segmented(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        labels.forEachIndexed { i, label ->
            SegmentedButton(
                selected = i == selected,
                onClick = { onSelect(i) },
                shape = SegmentedButtonDefaults.itemShape(i, labels.size),
                icon = {},
            ) { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun StyleOption(style: VisualStyle, selected: Boolean, name: String, detail: String, onClick: () -> Unit) {
    val swatch: Brush = when (style) {
        VisualStyle.OASIS -> Brush.linearGradient(listOf(Color(0xFF0E6B5E), Color(0xFF5FB2CF)))
        VisualStyle.SAHAR -> Brush.linearGradient(listOf(Color(0xFF2E2F7A), Color(0xFF8E4A7A)))
        VisualStyle.RAML -> Brush.linearGradient(listOf(Color(0xFFF2EBE2), Color(0xFFD9B88F)))
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(swatch))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) Icon(SakinaIcons.Check, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun HomeSetupScreen(vm: MainViewModel, s: AppSettings) {
    val str = LocalStrings.current
    SubScreen(str.settingsHome, vm) {
        SectionCard(title = str.layoutTitle, icon = SakinaIcons.Grid) {
            HomeLayout.entries.forEach { l ->
                ChoiceRow(s.homeLayout == l, str.layoutName(l), str.layoutDetail(l), onClick = { vm.update { it.copy(homeLayout = l) } })
            }
        }
        SectionCard(title = str.cardsTitle, icon = SakinaIcons.Sliders) {
            NoteText(str.cardsNote)
            Spacer(Modifier.height(6.dp))
            val cards = s.orderedCards
            cards.forEachIndexed { i, pref ->
                if (i > 0) Divider()
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(str.cardName(pref.card), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { vm.update { SettingsOps.moveCard(it, pref.card, -1) } }, enabled = i > 0) { Icon(SakinaIcons.ArrowUp, str.moveUp) }
                    IconButton(onClick = { vm.update { SettingsOps.moveCard(it, pref.card, 1) } }, enabled = i < cards.size - 1) { Icon(SakinaIcons.ArrowDown, str.moveDown) }
                    Switch(checked = pref.visible, onCheckedChange = { v -> vm.update { SettingsOps.setCardVisible(it, pref.card, v) } })
                }
            }
            Spacer(Modifier.height(8.dp))
            NoteText(str.laterCards)
        }
    }
}

@Composable
fun CalendarSettingsScreen(vm: MainViewModel, s: AppSettings) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    SubScreen(str.settingsCalendar, vm) {
        SectionCard(title = str.hijriOffsetTitle, icon = SakinaIcons.Calendar) {
            Stepper(s.hijriOffset, str.hijriOffsetTitle, -2, 2, fmt.signed(s.hijriOffset)) { v -> vm.update { it.copy(hijriOffset = v) } }
            val date = AppClock.now().atZone(s.activeLocation?.zoneId ?: java.time.ZoneId.systemDefault()).toLocalDate()
            com.sakina.app.core.calendar.UmmAlQura.displayed(date, s.hijriOffset)?.let {
                Text(fmt.hijri(it), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(6.dp))
            NoteText(str.hijriOffsetNote)
        }
    }
}

@Composable
fun AboutScreen(vm: MainViewModel) {
    val str = LocalStrings.current
    SubScreen(str.settingsAbout, vm) {
        SectionCard {
            Text(str.appName, style = MaterialTheme.typography.headlineSmall)
            Text(str.aboutVersion + " " + BuildConfig.VERSION_NAME, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionCard(title = str.aboutPrivacy, icon = SakinaIcons.Info) {
            Text(str.aboutPrivacyBody, style = MaterialTheme.typography.bodyMedium)
        }
        SectionCard(title = str.aboutSources, icon = SakinaIcons.Book) {
            listOf(str.srcAlgorithm, str.srcCalendar, str.srcCities, str.srcFonts).forEach {
                Row(Modifier.padding(vertical = 4.dp)) {
                    Box(Modifier.padding(top = 8.dp).size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                    Spacer(Modifier.width(10.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(6.dp))
            listOf(
                "licenses/OFL-IBMPlexSansArabic.txt" to "IBM Plex Sans Arabic — OFL 1.1",
                "licenses/OFL-Amiri.txt" to "Amiri — OFL 1.1",
                "licenses/MIT-Adhan.txt" to "Adhan — MIT",
            ).forEach { (asset, title) ->
                TextButton(onClick = { vm.push(Route.License(asset, title)) }) { Text(title) }
            }
        }
    }
}

@Composable
fun LicenseScreen(vm: MainViewModel, asset: String, title: String) {
    val context = LocalContext.current
    val text by produceState("", asset) {
        value = withContext(Dispatchers.IO) { runCatching { context.assets.open(asset).bufferedReader().use { it.readText() } }.getOrDefault("") }
    }
    SubScreen(title, vm) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) {
            Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth())
        }
    }
}
