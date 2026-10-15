package com.sakina.app.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sakina.app.core.calendar.CalendarKind
import com.sakina.app.core.calendar.Intervals
import com.sakina.app.data.AppClock
import com.sakina.app.data.AppSettings
import com.sakina.app.ui.LocalFmt
import com.sakina.app.ui.LocalStrings
import com.sakina.app.ui.MainViewModel
import com.sakina.app.ui.Route
import com.sakina.app.ui.components.NoteText
import com.sakina.app.ui.components.SakinaIcons
import com.sakina.app.ui.components.ScreenBar
import com.sakina.app.ui.components.SectionCard
import com.sakina.app.ui.location.FullScreen
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun CalendarToolsScreen(vm: MainViewModel, s: AppSettings) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val today = AppClock.now().atZone(s.activeLocation?.zoneId ?: ZoneId.systemDefault()).toLocalDate()
    var convKind by rememberSaveable { mutableStateOf(CalendarKind.HIJRI) }
    var conv by rememberSaveable { mutableStateOf<Long?>(today.toEpochDay()) }
    var convReset by rememberSaveable { mutableStateOf(0) }
    var aKind by rememberSaveable { mutableStateOf(CalendarKind.GREGORIAN) }
    var a by rememberSaveable { mutableStateOf<Long?>(today.toEpochDay()) }
    var bKind by rememberSaveable { mutableStateOf(CalendarKind.HIJRI) }
    var b by rememberSaveable { mutableStateOf<Long?>(today.plusDays(30).toEpochDay()) }
    FullScreen {
        Column(Modifier.fillMaxSize()) {
            ScreenBar(str.calendarTools, onBack = { vm.pop() })
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SectionCard(title = str.converterTitle, icon = SakinaIcons.Repeat) {
                    Text(str.converterFrom, style = MaterialTheme.typography.labelLarge)
                    DateFields(convKind, conv?.let { LocalDate.ofEpochDay(it) }, s.hijriOffset, "conv", convReset, onKind = { convKind = it }) { conv = it?.toEpochDay() }
                    TextButton(onClick = { conv = today.toEpochDay(); convReset++ }, modifier = Modifier.testTag("conv_today")) { Text(str.useToday) }
                }
                SectionCard(title = str.intervalTitle, icon = SakinaIcons.Calendar) {
                    Text(str.intervalStart, style = MaterialTheme.typography.labelLarge)
                    DateFields(aKind, a?.let { LocalDate.ofEpochDay(it) }, s.hijriOffset, "int_a", onKind = { aKind = it }) { a = it?.toEpochDay() }
                    Spacer(Modifier.height(8.dp))
                    Text(str.intervalEnd, style = MaterialTheme.typography.labelLarge)
                    DateFields(bKind, b?.let { LocalDate.ofEpochDay(it) }, s.hijriOffset, "int_b", onKind = { bKind = it }) { b = it?.toEpochDay() }
                    val x = a; val y = b
                    if (x != null && y != null) {
                        val i = Intervals.between(LocalDate.ofEpochDay(x), LocalDate.ofEpochDay(y), s.hijriOffset)
                        Spacer(Modifier.height(10.dp))
                        Text(str.intervalDays(fmt.num(i.days.toInt())), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("int_days"))
                        Text(str.intervalWeeks(fmt.num(i.weeks.toInt()), fmt.num(i.weekDays.toInt())), style = MaterialTheme.typography.bodyMedium)
                        Text(str.gregorianLabel + ": " + str.intervalYmd(fmt.num(i.gregorian.years), fmt.num(i.gregorian.months), fmt.num(i.gregorian.days)),
                            style = MaterialTheme.typography.bodyMedium)
                        i.hijri?.let { (yy, mm, dd) ->
                            Text(str.hijriLabel + ": " + str.intervalYmd(fmt.num(yy), fmt.num(mm), fmt.num(dd)), style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.testTag("int_hijri"))
                        }
                        NoteText(str.intervalNote)
                    }
                }
                SectionCard(title = str.offsetLink, icon = SakinaIcons.Sliders) {
                    NoteText(str.hijriOffsetNote)
                    TextButton(onClick = { vm.push(Route.Calendar) }, modifier = Modifier.testTag("tools_offset")) { Text(str.settingsCalendar) }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
