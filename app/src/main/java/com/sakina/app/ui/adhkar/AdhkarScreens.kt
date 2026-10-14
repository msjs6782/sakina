@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sakina.app.ui.adhkar

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakina.app.core.adhkar.AdhkarLibrary
import com.sakina.app.core.adhkar.Dhikr
import com.sakina.app.core.adhkar.DhikrCounter
import com.sakina.app.core.adhkar.DhikrPart
import com.sakina.app.core.adhkar.Practice
import com.sakina.app.core.adhkar.Wird
import com.sakina.app.core.alerts.ReminderKind
import com.sakina.app.core.prayer.PrayerKey
import com.sakina.app.core.prayer.PrayerSchedule
import com.sakina.app.core.quran.AyahRef
import com.sakina.app.data.AppClock
import com.sakina.app.data.AppSettings
import com.sakina.app.data.QuranData
import com.sakina.app.ui.LocalFmt
import com.sakina.app.ui.LocalStrings
import com.sakina.app.ui.MainViewModel
import com.sakina.app.ui.Route
import com.sakina.app.ui.Strings
import com.sakina.app.ui.components.Banner
import com.sakina.app.ui.components.DirectionalIcon
import com.sakina.app.ui.components.Divider
import com.sakina.app.ui.components.NoteText
import com.sakina.app.ui.components.SakinaIcons
import com.sakina.app.ui.components.ScreenBar
import com.sakina.app.ui.components.SectionCard
import com.sakina.app.ui.components.exposeTestTags
import com.sakina.app.ui.location.FullScreen
import com.sakina.app.ui.quran.ayahEnd
import com.sakina.app.ui.theme.Amiri
import com.sakina.app.ui.theme.AmiriQuran
import java.time.LocalDate
import java.time.ZoneId

private fun zoneOf(s: AppSettings): ZoneId = s.activeLocation?.zoneId ?: ZoneId.systemDefault()
internal fun todayOf(s: AppSettings): LocalDate = AppClock.now().atZone(zoneOf(s)).toLocalDate()

private fun Strings.title(d: Dhikr) = if (isAr) d.titleAr else d.titleEn

@Composable
private fun rememberLibrary(vm: MainViewModel): AdhkarLibrary? {
    LaunchedEffect(Unit) { vm.loadAdhkar() }
    val lib by vm.adhkar.collectAsStateWithLifecycle()
    return lib
}

@Composable
private fun AdhkarFrame(title: String, vm: MainViewModel, actions: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    FullScreen {
        Column(Modifier.fillMaxSize()) {
            ScreenBar(title, onBack = { vm.pop() }, actions = actions)
            content()
        }
    }
}

/** Arabic dhikr text, set in Amiri for clear vowel marks. */
@Composable
fun DhikrText(text: String, modifier: Modifier = Modifier, size: Int = 22) {
    Text(
        text,
        // Always a right-to-left paragraph, also inside the English layout.
        style = MaterialTheme.typography.bodyLarge.copy(fontFamily = Amiri, fontSize = size.sp, lineHeight = (size * 1.85).sp,
            textAlign = TextAlign.Start, textDirection = TextDirection.Rtl),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun QuranPartText(part: DhikrPart, quran: QuranData?, size: Int = 22) {
    val q = part.quran ?: return
    if (quran == null) {
        NoteText("…")
        return
    }
    val whole = q.from == 1 && q.sura != 2
    if (whole) quran.text.basmala[q.sura]?.let {
        Text(it, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = AmiriQuran, fontSize = size.sp, lineHeight = (size * 2).sp),
            color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
    val text = (q.from..q.to).joinToString(" ") { a ->
        val g = quran.meta.global(AyahRef(q.sura, a))
        quran.text.ayahs[g] + " " + ayahEnd(a)
    }
    Text(text, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = AmiriQuran, fontSize = size.sp, lineHeight = (size * 2.05).sp,
        textAlign = TextAlign.Start, textDirection = TextDirection.Rtl),
        color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.fillMaxWidth().testTag("quran_part_${q.sura}_${q.from}"))
}

@Composable
private fun partLabel(part: DhikrPart, quran: QuranData?): String? {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val q = part.quran
    if (q != null) {
        val sura = quran?.meta?.surah(q.sura)
        val name = if (str.isAr) sura?.nameAr ?: "" else sura?.nameTranslit ?: ""
        return str.quranPassage(name, fmt.num(q.from), fmt.num(q.to), q.from == 1 && q.sura != 2, q.from == q.to)
    }
    return if (str.isAr) part.labelAr else part.labelEn ?: part.labelAr
}

@Composable
private fun CountChip(count: Int) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 10.dp, vertical = 3.dp)) {
        Text(str.times(fmt.num(count), count), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

// ---------------------------------------------------------------- Home

@Composable
fun AdhkarHomeScreen(vm: MainViewModel, initialQuery: String = "") {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val lib = rememberLibrary(vm)
    val tools by vm.tools.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    AdhkarFrame(str.adhkarTitle, vm) {
        if (lib == null) return@AdhkarFrame
        val s = settings ?: return@AdhkarFrame
        val today = todayOf(s).toString()
        val results = remember(lib, query) { lib.search(query) }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(str.adhkarSearchHint) },
                    leadingIcon = { Icon(SakinaIcons.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(SakinaIcons.Close, str.cancel) }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().testTag("adhkar_search"),
                )
            }
            if (query.isNotBlank()) {
                if (results.isEmpty()) item { NoteText(str.adhkarNoResults, Modifier.padding(8.dp)) }
                items(results, key = { "r_" + it.id }) { d -> DhikrRow(d, lib, "adhkar_result_${d.id}") { vm.push(Route.AdhkarItem(d.id)) } }
                return@LazyColumn
            }
            item { SectionTitle(str.adhkarPractices) }
            items(lib.practices, key = { "p_" + it.id }) { p ->
                val items = lib.itemsOf(p)
                val session = if (p.id == Practice.AFTER_PRAYER) latestPrayer(s)?.first ?: Practice.PRAYER_SESSIONS.first() else Practice.MAIN_SESSION
                val date = if (p.id == Practice.AFTER_PRAYER) latestPrayer(s)?.second?.toString() ?: today else today
                val (done, total) = Wird.summary(tools.progress, Wird.key(date, p.id, session), items)
                val sessionName = if (p.id == Practice.AFTER_PRAYER) str.prayer(PrayerKey.valueOf(session.uppercase())) else null
                PracticeCard(p, done, total, str, fmt.num(done), fmt.num(total), sessionName) { vm.push(Route.Wird(p.id)) }
            }
            val favs = tools.favorites.mapNotNull { lib.item(it) }
            item { SectionTitle(str.adhkarFavorites) }
            if (favs.isEmpty()) item { NoteText(str.adhkarNoFavorites, Modifier.padding(horizontal = 4.dp)) }
            items(favs, key = { "f_" + it.id }) { d -> DhikrRow(d, lib, "adhkar_fav_${d.id}") { vm.push(Route.AdhkarItem(d.id)) } }
            item { SectionTitle(str.adhkarCategories) }
            item {
                SectionCard(padding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)) {
                    lib.categories.forEachIndexed { i, c ->
                        if (i > 0) Divider()
                        val n = lib.inCategory(c.id).size
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(role = Role.Button) { vm.push(Route.AdhkarCategory(c.id)) }
                                .padding(horizontal = 12.dp, vertical = 12.dp).testTag("cat_${c.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(if (str.isAr) c.titleAr else c.titleEn, style = MaterialTheme.typography.bodyLarge)
                                Text(str.adhkarCount(fmt.num(n), n), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            DirectionalIcon(SakinaIcons.ChevronEnd, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item { NoteText(str.adhkarLibraryNote, Modifier.padding(horizontal = 4.dp, vertical = 8.dp)) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
}

@Composable
private fun PracticeCard(p: Practice, done: Int, total: Int, str: Strings, doneText: String, totalText: String, session: String?, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth().testTag("practice_${p.id}"),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(practiceIcon(p.id), null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(if (str.isAr) p.titleAr else p.titleEn, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (done == total && total > 0) Icon(SakinaIcons.Check, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(4.dp))
            Text(if (str.isAr) p.ctxAr else p.ctxEn, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(progress = { if (total == 0) 0f else done.toFloat() / total }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape))
            Spacer(Modifier.height(4.dp))
            Text(str.progressOf(doneText, totalText) + (session?.let { "، " + str.wirdSession + " " + it } ?: ""),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun practiceIcon(id: String) = when (id) {
    Practice.MORNING -> SakinaIcons.Sunrise
    Practice.EVENING -> SakinaIcons.Maghrib
    Practice.SLEEP -> SakinaIcons.Moon
    Practice.WAKING -> SakinaIcons.Fajr
    else -> SakinaIcons.Beads
}

@Composable
private fun DhikrRow(d: Dhikr, lib: AdhkarLibrary, tag: String, onClick: () -> Unit) {
    val str = LocalStrings.current
    val cat = lib.category(d.cat)
    val preview = d.parts.firstNotNullOfOrNull { it.text } ?: ""
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().testTag(tag),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(str.title(d), style = MaterialTheme.typography.titleSmall)
            if (preview.isNotEmpty()) {
                Text(preview, style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Amiri, lineHeight = 26.sp, textDirection = TextDirection.Rtl),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 2.dp))
            }
            if (cat != null) Text(if (str.isAr) cat.titleAr else cat.titleEn, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp))
        }
    }
}

// ---------------------------------------------------------------- Category

@Composable
fun AdhkarCategoryScreen(vm: MainViewModel, id: String) {
    val str = LocalStrings.current
    val lib = rememberLibrary(vm)
    val cat = lib?.category(id)
    AdhkarFrame(cat?.let { if (str.isAr) it.titleAr else it.titleEn } ?: str.adhkarTitle, vm) {
        if (lib == null) return@AdhkarFrame
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(lib.inCategory(id), key = { it.id }) { d -> DhikrRow(d, lib, "dhikr_${d.id}") { vm.push(Route.AdhkarItem(d.id)) } }
        }
    }
}

// ---------------------------------------------------------------- Item

@Composable
fun AdhkarItemScreen(vm: MainViewModel, s: AppSettings, id: String) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val context = LocalContext.current
    val lib = rememberLibrary(vm)
    LaunchedEffect(Unit) { vm.loadQuran() }
    val quran by vm.quran.collectAsStateWithLifecycle()
    val tools by vm.tools.collectAsStateWithLifecycle()
    val d = lib?.item(id)
    val fav = id in tools.favorites
    AdhkarFrame(d?.let { str.title(it) } ?: str.adhkarTitle, vm, actions = {
        if (d != null) {
            IconButton(onClick = { vm.updateTools { t -> t.copy(favorites = if (fav) t.favorites - id else t.favorites + id) } }, modifier = Modifier.testTag("adhkar_fav")) {
                Icon(if (fav) SakinaIcons.StarFilled else SakinaIcons.Star, if (fav) str.favoriteRemove else str.favoriteAdd,
                    tint = if (fav) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = { shareDhikr(context, d, quran, str) }, modifier = Modifier.testTag("adhkar_share")) { Icon(SakinaIcons.Share, str.shareDhikr) }
        }
    }) {
        if (d == null || lib == null) return@AdhkarFrame
        val key = Wird.itemKey(todayOf(s).toString())
        val taps = tools.progress[key]?.get(id) ?: 0
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                SectionCard {
                    d.parts.forEachIndexed { i, p ->
                        if (i > 0) Divider()
                        PartBlock(p, quran, null, showVariants = true)
                    }
                    if (d.rounds > 1) {
                        Spacer(Modifier.height(6.dp))
                        Text(str.wholeSetTimes(fmt.num(d.rounds)), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    d.countNoteAr?.let { if (str.isAr) NoteText(it, Modifier.padding(top = 6.dp)) }
                }
            }
            item { ItemCounter(d, taps, quran, onTap = { vm.updateTools { t -> t.copy(progress = Wird.tap(t.progress, key, d, free = d.unnumbered)) } },
                onReset = { vm.updateTools { t -> t.copy(progress = Wird.resetItem(t.progress, key, id)) } }) }
            val ctx = if (str.isAr) d.ctxAr else d.ctxEn
            if (ctx.isNotBlank()) item {
                SectionCard(title = str.contextTitle, icon = SakinaIcons.Info) { Text(ctx, style = MaterialTheme.typography.bodyMedium) }
            }
            val note = if (str.isAr) d.noteAr else d.noteEn ?: d.noteAr
            if (note != null) item { SectionCard(title = str.noteTitle, icon = SakinaIcons.Edit) { Text(note, style = MaterialTheme.typography.bodyMedium) } }
            // Arabic citations give the number alone; sunnah.com's letter for a sub-narration stays in the English form.
            item { SourceCard(d, str, if (str.isAr) fmt.num(d.source.number.filter { it.isDigit() }.toIntOrNull() ?: 0) else d.source.number) }
            item {
                SectionCard(title = str.linksTitle, icon = SakinaIcons.Globe) {
                    d.links.forEach { l ->
                        TextButton(onClick = { openLink(context, l.url, str) }, modifier = Modifier.testTag("link_" + l.url.substringAfter("://").substringBefore('/'))) {
                            Text(if (str.isAr) l.titleAr else l.titleEn)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PartBlock(p: DhikrPart, quran: QuranData?, practice: String?, showVariants: Boolean, highlight: Boolean = false, tag: String? = null) {
    val str = LocalStrings.current
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        val label = partLabel(p, quran)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (label != null) Text(label, style = MaterialTheme.typography.labelLarge, color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
            CountChip(p.count)
        }
        Spacer(Modifier.height(4.dp))
        if (p.quran != null) {
            QuranPartText(p, quran)
        } else if (showVariants && p.morning != null && p.evening != null && p.morning != p.evening) {
            Text(str.morningWording, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            DhikrText(p.morning)
            Spacer(Modifier.height(8.dp))
            Text(str.eveningWording, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            DhikrText(p.evening)
        } else if (showVariants && p.evening != null && p.text != null && p.evening != p.text) {
            Text(str.morningWording, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            DhikrText(p.text)
            Spacer(Modifier.height(8.dp))
            Text(str.eveningWording, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            DhikrText(p.evening)
        } else {
            p.textFor(practice)?.let { DhikrText(it, if (tag != null) Modifier.testTag(tag) else Modifier) }
        }
    }
}

@Composable
private fun ItemCounter(d: Dhikr, taps: Int, quran: QuranData?, onTap: () -> Unit, onReset: () -> Unit) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val haptic = LocalHapticFeedback.current
    val pos = DhikrCounter.position(d, taps)
    SectionCard(title = str.itemCounterTitle, icon = SakinaIcons.Beads) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                onClick = {
                    val before = DhikrCounter.position(d, taps).done
                    onTap()
                    if (!before && DhikrCounter.position(d, taps + 1).done) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                shape = CircleShape,
                color = if (pos.done && !d.unnumbered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(96.dp).testTag("item_counter"),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val text = if (d.unnumbered) fmt.num(taps) else if (pos.done) str.wirdDone else fmt.num(pos.inPart)
                    Text(text, style = MaterialTheme.typography.headlineMedium,
                        color = if (pos.done && !d.unnumbered) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                if (!d.unnumbered) {
                    val part = d.parts[pos.part]
                    partLabel(part, quran)?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
                    if (!pos.done) Text(fmt.num(pos.inPart) + " / " + fmt.num(part.taps), style = MaterialTheme.typography.bodyMedium)
                    if (d.rounds > 1) Text(str.roundOf(fmt.num(pos.round), fmt.num(d.rounds)), style = MaterialTheme.typography.bodySmall)
                }
                NoteText(str.itemCounterNote)
                TextButton(onClick = onReset, enabled = taps > 0, modifier = Modifier.testTag("item_counter_reset")) { Text(str.resetItem) }
            }
        }
    }
}

@Composable
private fun SourceCard(d: Dhikr, str: Strings, number: String) {
    var showAll by remember { mutableStateOf(false) }
    SectionCard(title = str.sourceTitle, icon = SakinaIcons.Book) {
        val book = when (d.source.book) {
            "bukhari" -> if (str.isAr) "صحيح البخاري" else "Sahih al-Bukhari"
            "muslim" -> if (str.isAr) "صحيح مسلم" else "Sahih Muslim"
            "abudawud" -> if (str.isAr) "سنن أبي داود" else "Sunan Abi Dawud"
            "tirmidhi" -> if (str.isAr) "جامع الترمذي" else "Jami at-Tirmidhi"
            "ibnmajah" -> if (str.isAr) "سنن ابن ماجه" else "Sunan Ibn Majah"
            else -> d.source.book
        }
        Text("$book $number", style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("dhikr_source"))
        val g = d.source.grade
        val sahihayn = d.source.book == "bukhari" || d.source.book == "muslim"
        Text(str.gradeLine(if (str.isAr) g.ar else g.en, if (sahihayn) str.sahihayn else if (str.isAr) g.byAr else g.byEn),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        if (d.source.graders.isNotEmpty()) {
            TextButton(onClick = { showAll = !showAll }) { Text(str.gradersTitle) }
            if (showAll) d.source.graders.forEach { gr ->
                Text("${gr.by}: ${gr.grade}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun openLink(context: Context, url: String, str: Strings) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, str.noBrowser, Toast.LENGTH_SHORT).show()
    }
}

private fun shareDhikr(context: Context, d: Dhikr, quran: QuranData?, str: Strings) {
    val body = buildString {
        append(if (str.isAr) d.titleAr else d.titleEn).append("\n\n")
        d.parts.forEach { p ->
            val t = p.text ?: p.quran?.let { q ->
                if (quran == null) null else (q.from..q.to).joinToString(" ") { a -> quran.text.ayahs[quran.meta.global(AyahRef(q.sura, a))] + " " + ayahEnd(a) }
            }
            if (t != null) {
                append(t)
                if (p.count > 1) append(" (").append(str.times(str.digits(p.count.toString()), p.count)).append(")")
                append("\n")
            }
        }
        append("\n").append(d.source.ar.ifBlank { d.source.en }).append("\n")
        d.links.firstOrNull()?.let { append(it.url) }
    }
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, body)
    context.startActivity(Intent.createChooser(send, str.shareDhikr).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

// ---------------------------------------------------------------- Wird

/** The latest prayer at or before now (its session name and date), for the after-prayer set. */
internal fun latestPrayer(s: AppSettings): Pair<String, LocalDate>? {
    val loc = s.activeLocation ?: return null
    val zone = loc.zoneId ?: return null
    if (!loc.isUsable) return null
    val ev = PrayerSchedule(loc.point, zone, s.calcConfig).previous(AppClock.now(), PrayerKey.prayers) ?: return null
    return ev.key.name.lowercase() to ev.date
}

@Composable
fun WirdScreen(vm: MainViewModel, s: AppSettings, practiceId: String, sessionArg: String) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val lib = rememberLibrary(vm)
    LaunchedEffect(Unit) { vm.loadQuran() }
    val quran by vm.quran.collectAsStateWithLifecycle()
    val tools by vm.tools.collectAsStateWithLifecycle()
    val p = lib?.practice(practiceId)
    var confirmReset by remember { mutableStateOf(false) }
    val latest = remember(practiceId) { if (practiceId == Practice.AFTER_PRAYER) latestPrayer(s) else null }
    var session by rememberSaveable {
        mutableStateOf(sessionArg.ifBlank { if (practiceId == Practice.AFTER_PRAYER) latest?.first ?: Practice.PRAYER_SESSIONS.first() else Practice.MAIN_SESSION })
    }
    val date = if (practiceId == Practice.AFTER_PRAYER && latest != null && session == latest.first) latest.second else todayOf(s)
    val key = Wird.key(date.toString(), practiceId, session)
    AdhkarFrame(p?.let { if (str.isAr) it.titleAr else it.titleEn } ?: str.adhkarTitle, vm, actions = {
        IconButton(onClick = { confirmReset = true }, modifier = Modifier.testTag("wird_reset")) { Icon(SakinaIcons.Reset, str.wirdReset) }
    }) {
        if (p == null || lib == null) return@AdhkarFrame
        val items = lib.itemsOf(p)
        val progress = tools.progress[key].orEmpty()
        val (done, total) = Wird.summary(tools.progress, key, items)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column {
                    Text(if (str.isAr) p.ctxAr else p.ctxEn, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text(fmt.gregorian(date, withYear = false, withWeekday = true), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            p.reminder?.let { r -> item { ReminderLink(vm, s, runCatching { ReminderKind.valueOf(r) }.getOrNull()) } }
            if (p.id == Practice.AFTER_PRAYER) item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Practice.PRAYER_SESSIONS.forEach { name ->
                        val k = PrayerKey.valueOf(name.uppercase())
                        FilterChip(selected = session == name, onClick = { session = name }, label = { Text(str.prayer(k)) },
                            modifier = Modifier.testTag("session_$name"))
                    }
                }
            }
            item {
                Column {
                    LinearProgressIndicator(progress = { if (total == 0) 0f else done.toFloat() / total }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape))
                    Spacer(Modifier.height(4.dp))
                    Row {
                        Text(str.progressOf(fmt.num(done), fmt.num(total)), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f).testTag("wird_progress"))
                    }
                    NoteText(str.wirdTapHint)
                }
            }
            if (done == total && total > 0) item { Banner(str.wirdAllDone, icon = SakinaIcons.Check) }
            items(items, key = { it.id }) { d ->
                WirdCard(d, progress[d.id] ?: 0, p.id, quran,
                    onTap = { vm.updateTools { t -> t.copy(progress = Wird.tap(t.progress, key, d)) } },
                    onUntap = { vm.updateTools { t -> t.copy(progress = Wird.untap(t.progress, key, d)) } },
                    onOpen = { vm.push(Route.AdhkarItem(d.id)) })
            }
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            text = { Text(str.wirdResetConfirm) },
            confirmButton = {
                TextButton(onClick = { vm.updateTools { t -> t.copy(progress = Wird.resetSession(t.progress, key)) }; confirmReset = false },
                    modifier = Modifier.testTag("wird_reset_confirm")) { Text(str.wirdReset) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(str.cancel) } },
            modifier = Modifier.exposeTestTags(),
        )
    }
}

@Composable
private fun ReminderLink(vm: MainViewModel, s: AppSettings, kind: ReminderKind?) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    kind ?: return
    val r = s.alerts.allReminders.firstOrNull { it.kind == kind } ?: return
    Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (r.enabled) SakinaIcons.Bell else SakinaIcons.BellOff, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(str.reminderLink + " · " + str.reminderName(kind), style = MaterialTheme.typography.labelLarge)
                Text(if (r.enabled) str.reminderOn(fmt.clock(r.minuteOfDay / 60, r.minuteOfDay % 60)) else str.reminderOffNote,
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("wird_reminder_state"))
            }
            TextButton(onClick = { vm.push(Route.Reminders) }, modifier = Modifier.testTag("wird_reminder")) { Text(str.reminderSetup) }
        }
    }
}

@Composable
private fun WirdCard(d: Dhikr, taps: Int, practice: String, quran: QuranData?, onTap: () -> Unit, onUntap: () -> Unit, onOpen: () -> Unit) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val haptic = LocalHapticFeedback.current
    val pos = DhikrCounter.position(d, taps)
    val doneColor = MaterialTheme.colorScheme.primary
    Surface(
        onClick = {
            if (!pos.done) {
                onTap()
                if (DhikrCounter.position(d, taps + 1).done) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        },
        shape = RoundedCornerShape(22.dp),
        color = if (pos.done) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, if (pos.done) doneColor.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth().testTag("wird_item_${d.id}"),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(str.title(d), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (pos.done) {
                    Icon(SakinaIcons.Check, str.wirdDone, Modifier.size(22.dp).testTag("wird_done_${d.id}"), tint = doneColor)
                } else {
                    val part = d.parts[pos.part]
                    Text(fmt.num(pos.inPart) + "/" + fmt.num(part.taps), style = MaterialTheme.typography.titleMedium, color = doneColor,
                        modifier = Modifier.testTag("wird_count_${d.id}"))
                }
            }
            if (d.rounds > 1 && !pos.done) Text(str.roundOf(fmt.num(pos.round), fmt.num(d.rounds)), style = MaterialTheme.typography.labelMedium, color = doneColor)
            Spacer(Modifier.height(6.dp))
            d.parts.forEachIndexed { i, p ->
                if (i > 0) Spacer(Modifier.height(6.dp))
                PartBlock(p, quran, practice, showVariants = false, highlight = !pos.done && i == pos.part && d.parts.size > 1, tag = "wird_text_${d.id}_$i")
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text(d.source.ar.ifBlank { d.source.en }.let { if (str.isAr) str.digits(it) else d.source.en }, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                if (taps > 0) OutlinedButton(onClick = onUntap, contentPadding = PaddingValues(horizontal = 10.dp), modifier = Modifier.height(32.dp).testTag("wird_minus_${d.id}")) {
                    Text("−1", style = MaterialTheme.typography.labelLarge)
                }
                // The whole card counts; the source and context open from this button.
                IconButton(onClick = onOpen, modifier = Modifier.size(36.dp).testTag("wird_open_${d.id}")) {
                    Icon(SakinaIcons.Info, str.sourceTitle, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
