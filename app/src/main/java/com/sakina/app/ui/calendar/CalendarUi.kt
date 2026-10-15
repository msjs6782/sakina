@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sakina.app.ui.calendar

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakina.app.core.adhkar.HadithRef
import com.sakina.app.core.calendar.CalendarKind
import com.sakina.app.core.calendar.DateConvert
import com.sakina.app.core.calendar.HijriDate
import com.sakina.app.core.format.Names
import com.sakina.app.ui.LocalFmt
import com.sakina.app.ui.LocalStrings
import com.sakina.app.ui.MainViewModel
import com.sakina.app.ui.Strings
import com.sakina.app.ui.components.NoteText
import com.sakina.app.ui.components.SakinaIcons
import com.sakina.app.ui.components.SectionCard
import com.sakina.app.ui.theme.Amiri
import java.time.LocalDate

fun monthName(str: Strings, kind: CalendarKind, month: Int): String =
    if (kind == CalendarKind.HIJRI) Names.hijriMonth(month, str.lang) else Names.gregorianMonth(month, str.lang)

internal fun openUrl(context: Context, url: String, str: Strings) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, str.noBrowser, Toast.LENGTH_SHORT).show()
    }
}

/**
 * A date typed as day, month and year in either calendar. Reports the civil date, or null while the
 * fields do not make a valid date in range. [tag] prefixes the fields' test tags.
 */
@Composable
fun DateFields(
    kind: CalendarKind,
    value: LocalDate?,
    hijriOffset: Int,
    tag: String,
    resetKey: Int = 0,
    onKind: (CalendarKind) -> Unit,
    onChange: (LocalDate?) -> Unit,
) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    var day by remember(kind, resetKey) { mutableStateOf("") }
    var month by remember(kind, resetKey) { mutableStateOf("") }
    var year by remember(kind, resetKey) { mutableStateOf("") }
    // Fill the fields from the value whenever the calendar changes (or the caller sets a new date).
    LaunchedEffect(kind, resetKey) {
        if (value != null) {
            if (kind == CalendarKind.GREGORIAN) {
                day = value.dayOfMonth.toString(); month = value.monthValue.toString(); year = value.year.toString()
            } else DateConvert.gregorianToHijri(value, hijriOffset)?.let {
                day = it.day.toString(); month = it.month.toString(); year = it.year.toString()
            }
        }
    }
    fun emit(d: String, m: String, y: String) {
        val dd = d.toIntOrNull(); val mm = m.toIntOrNull(); val yy = y.toIntOrNull()
        val date = if (dd == null || mm == null || yy == null || mm !in 1..12) null
        else if (kind == CalendarKind.GREGORIAN) runCatching { LocalDate.of(yy, mm, dd) }.getOrNull()?.takeIf { it.year in 1883..2173 }
        else DateConvert.hijriToGregorian(HijriDate(yy, mm, dd), hijriOffset)
        onChange(date)
    }
    Column {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = kind == CalendarKind.HIJRI, onClick = { onKind(CalendarKind.HIJRI) }, label = { Text(str.hijriLabel) },
                modifier = Modifier.testTag("${tag}_hijri"))
            FilterChip(selected = kind == CalendarKind.GREGORIAN, onClick = { onKind(CalendarKind.GREGORIAN) }, label = { Text(str.gregorianLabel) },
                modifier = Modifier.testTag("${tag}_gregorian"))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val number = KeyboardOptions(keyboardType = KeyboardType.Number)
            OutlinedTextField(day, { day = it.filter(Char::isDigit).take(2); emit(day, month, year) }, label = { Text(str.dayLabel) }, singleLine = true,
                keyboardOptions = number, modifier = Modifier.weight(1f).testTag("${tag}_day"))
            OutlinedTextField(month, { month = it.filter(Char::isDigit).take(2); emit(day, month, year) }, label = { Text(str.monthLabel) }, singleLine = true,
                keyboardOptions = number, modifier = Modifier.weight(1f).testTag("${tag}_month"),
                supportingText = { month.toIntOrNull()?.takeIf { it in 1..12 }?.let { Text(monthName(str, kind, it)) } })
            OutlinedTextField(year, { year = it.filter(Char::isDigit).take(4); emit(day, month, year) }, label = { Text(str.yearLabel) }, singleLine = true,
                keyboardOptions = number, modifier = Modifier.weight(1.3f).testTag("${tag}_year"))
        }
        if (value == null && (day.isNotEmpty() || month.isNotEmpty() || year.isNotEmpty())) {
            Text(str.invalidDate, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        } else if (value != null) {
            val h = DateConvert.gregorianToHijri(value, hijriOffset)
            Text(
                (h?.let { fmt.hijri(it) + " · " } ?: "") + fmt.gregorian(value, withYear = true, withWeekday = true),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("${tag}_result"),
            )
        }
    }
}

/** A verified hadith quotation with its source, grade and a link to check it. */
@Composable
fun HadithRefBlock(ref: HadithRef?, modifier: Modifier = Modifier) {
    ref ?: return
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val context = LocalContext.current
    Column(modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            "«" + ref.text + "»",
            style = MaterialTheme.typography.bodyLarge.copy(fontFamily = Amiri, fontSize = 19.sp, lineHeight = 34.sp, textDirection = TextDirection.Rtl),
        )
        val sahihayn = ref.book == "bukhari" || ref.book == "muslim"
        val number = if (str.isAr) fmt.num(ref.number.filter { it.isDigit() }.toIntOrNull() ?: 0) else ref.number
        val source = (if (str.isAr) ref.ar.substringBeforeLast(' ') else ref.en.substringBeforeLast(' ')) + " " + number
        Text(
            source + " · " + str.gradeLine(if (str.isAr) ref.grade.ar else ref.grade.en, if (sahihayn) str.sahihayn else if (str.isAr) ref.grade.byAr else ref.grade.byEn),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
        )
        if (ref.disputed) {
            NoteText(str.gradersLine(ref.graders.joinToString("، ") { "${it.by}: ${it.grade}" }))
        }
        TextButton(onClick = { openUrl(context, ref.url, str) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text("sunnah.com", style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** A card of references from the verified list, by id. */
@Composable
fun RefsCard(vm: MainViewModel, ids: List<String>, title: String? = null) {
    val str = LocalStrings.current
    LaunchedEffect(Unit) { vm.loadRefs() }
    val refs by vm.refs.collectAsStateWithLifecycle()
    val map = refs ?: return
    SectionCard(title = title ?: str.referencesTitle, icon = SakinaIcons.Book) {
        ids.forEach { id -> HadithRefBlock(map[id]) }
        Spacer(Modifier.height(2.dp))
    }
}
