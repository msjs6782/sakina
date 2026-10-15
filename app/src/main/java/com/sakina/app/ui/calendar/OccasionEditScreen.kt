@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sakina.app.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sakina.app.core.calendar.CalendarKind
import com.sakina.app.core.calendar.DateConvert
import com.sakina.app.core.calendar.PersonalOccasion
import com.sakina.app.core.calendar.PersonalOccasionOps
import com.sakina.app.data.AppClock
import com.sakina.app.data.AppSettings
import com.sakina.app.ui.LocalFmt
import com.sakina.app.ui.LocalStrings
import com.sakina.app.ui.MainViewModel
import com.sakina.app.ui.components.NoteText
import com.sakina.app.ui.components.SakinaIcons
import com.sakina.app.ui.components.ScreenBar
import com.sakina.app.ui.components.SectionCard
import com.sakina.app.ui.components.SwitchRow
import com.sakina.app.ui.components.exposeTestTags
import com.sakina.app.ui.location.FullScreen
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@Composable
fun OccasionEditScreen(vm: MainViewModel, s: AppSettings, id: String, epochDay: Long) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val existing = s.calendar.occasions.firstOrNull { it.id == id }
    val today = AppClock.now().atZone(s.activeLocation?.zoneId ?: ZoneId.systemDefault()).toLocalDate()
    val initialDate = existing?.date ?: if (epochDay != Long.MIN_VALUE) LocalDate.ofEpochDay(epochDay) else today
    var title by rememberSaveable { mutableStateOf(existing?.title ?: "") }
    var note by rememberSaveable { mutableStateOf(existing?.note ?: "") }
    var dateDay by rememberSaveable { mutableStateOf<Long?>(initialDate.toEpochDay()) }
    var kind by rememberSaveable { mutableStateOf(if (existing?.repeat == PersonalOccasion.Repeat.YEARLY_GREGORIAN) CalendarKind.GREGORIAN else s.calendar.kind) }
    var repeat by rememberSaveable { mutableStateOf(existing?.repeat ?: PersonalOccasion.Repeat.NONE) }
    var minute by rememberSaveable { mutableStateOf(existing?.minuteOfDay) }
    var notify by rememberSaveable { mutableStateOf(existing?.notify ?: false) }
    var timeOpen by remember { mutableStateOf(false) }
    var tried by remember { mutableStateOf(false) }
    FullScreen {
        Column(Modifier.fillMaxSize()) {
            ScreenBar(if (existing != null) str.editOccasion else str.addOccasion, onBack = { vm.pop() })
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OutlinedTextField(
                    title, { title = it.take(PersonalOccasion.MAX_TITLE) }, label = { Text(str.occasionTitle) }, singleLine = true,
                    isError = tried && title.isBlank(), supportingText = { if (tried && title.isBlank()) Text(str.occasionTitleRequired) },
                    modifier = Modifier.fillMaxWidth().testTag("occ_title"),
                )
                SectionCard(title = str.dayLabel, icon = SakinaIcons.Calendar) {
                    DateFields(kind, dateDay?.let { LocalDate.ofEpochDay(it) }, s.hijriOffset, "occ_date", onKind = { kind = it }) { d -> dateDay = d?.toEpochDay() }
                }
                SectionCard(title = str.occasionRepeat, icon = SakinaIcons.Repeat) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            PersonalOccasion.Repeat.NONE to str.repeatNone,
                            PersonalOccasion.Repeat.YEARLY_HIJRI to str.repeatHijri,
                            PersonalOccasion.Repeat.YEARLY_GREGORIAN to str.repeatGregorian,
                        ).forEach { (r, label) ->
                            FilterChip(selected = repeat == r, onClick = { repeat = r }, label = { Text(label) }, modifier = Modifier.testTag("occ_repeat_${r.name.lowercase()}"))
                        }
                    }
                }
                SectionCard(title = str.occasionTimeLabel, icon = SakinaIcons.Clock) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { timeOpen = true }, modifier = Modifier.testTag("occ_time")) {
                            Text(minute?.let { fmt.clock(it / 60, it % 60) } ?: str.occasionNoTime)
                        }
                        Spacer(Modifier.width(8.dp))
                        if (minute != null) TextButton(onClick = { minute = null }, modifier = Modifier.testTag("occ_time_clear")) { Text(str.occasionNoTime) }
                    }
                    SwitchRow(notify, str.occasionNotify, str.occasionNotifyNote) { notify = it }
                }
                OutlinedTextField(
                    note, { note = it.take(PersonalOccasion.MAX_NOTE) }, label = { Text(str.occasionNoteLabel) }, minLines = 2,
                    modifier = Modifier.fillMaxWidth().testTag("occ_note"),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = {
                            tried = true
                            val day = dateDay
                            if (title.isBlank() || day == null) return@Button
                            val d = LocalDate.ofEpochDay(day)
                            val h = DateConvert.gregorianToHijri(d, s.hijriOffset)
                            val o = PersonalOccasion(
                                id = existing?.id ?: UUID.randomUUID().toString(),
                                title = title.trim(), note = note.trim(), epochDay = day, repeat = repeat,
                                hijriMonth = h?.month ?: 0, hijriDay = h?.day ?: 0, minuteOfDay = minute, notify = notify,
                            )
                            vm.changeOccasions(str.occasionSaved) { PersonalOccasionOps.upsert(it, o) }
                            vm.pop()
                        },
                        modifier = Modifier.testTag("occ_save"),
                    ) { Text(str.save) }
                    if (existing != null) {
                        OutlinedButton(onClick = {
                            vm.changeOccasions(str.occasionDeleted) { PersonalOccasionOps.remove(it, existing.id) }
                            vm.pop()
                        }, modifier = Modifier.testTag("occ_delete")) { Text(str.delete) }
                    }
                }
                if (dateDay == null) NoteText(str.invalidDate)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    if (timeOpen) {
        val st = rememberTimePickerState(initialHour = (minute ?: 9 * 60) / 60, initialMinute = (minute ?: 0) % 60, is24Hour = s.clock24)
        AlertDialog(
            onDismissRequest = { timeOpen = false },
            title = { Text(str.pickTime) },
            text = { TimePicker(state = st) },
            confirmButton = { TextButton(onClick = { minute = st.hour * 60 + st.minute; timeOpen = false }, modifier = Modifier.testTag("occ_time_ok")) { Text(str.save) } },
            dismissButton = { TextButton(onClick = { timeOpen = false }) { Text(str.cancel) } },
            modifier = Modifier.exposeTestTags(),
        )
    }
}
