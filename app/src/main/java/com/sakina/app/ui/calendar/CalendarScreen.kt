@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sakina.app.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sakina.app.core.calendar.CalendarKind
import com.sakina.app.core.calendar.FastingDays
import com.sakina.app.core.calendar.FastingKind
import com.sakina.app.core.calendar.GridDay
import com.sakina.app.core.calendar.MonthGrid
import com.sakina.app.core.calendar.MonthRef
import com.sakina.app.core.calendar.Occasion
import com.sakina.app.core.calendar.Occasions
import com.sakina.app.core.calendar.PersonalOccasion
import com.sakina.app.core.calendar.PersonalOccasionOps
import com.sakina.app.core.calendar.UmmAlQura
import com.sakina.app.core.format.Lang
import com.sakina.app.core.prayer.PrayerKey
import com.sakina.app.core.prayer.PrayerSchedule
import com.sakina.app.data.AppClock
import com.sakina.app.data.AppSettings
import com.sakina.app.ui.LocalFmt
import com.sakina.app.ui.LocalStrings
import com.sakina.app.ui.MainViewModel
import com.sakina.app.ui.Route
import com.sakina.app.ui.components.DirectionalIcon
import com.sakina.app.ui.components.NoteText
import com.sakina.app.ui.components.SakinaIcons
import com.sakina.app.ui.components.ScreenBar
import com.sakina.app.ui.components.SectionCard
import com.sakina.app.ui.components.exposeTestTags
import com.sakina.app.ui.location.FullScreen
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/** Months either side of the current month that the pager can reach (the calendars' own limits apply inside). */
private const val SPAN = 300 * 12
private const val PAGES = 2 * SPAN + 1

/** The month on a pager page: pages count months from the current month in the chosen calendar. */
private fun monthAt(page: Int, base: MonthRef) = base.plus(page - SPAN)

internal fun weekStartFor(lang: Lang) = if (lang == Lang.AR) DayOfWeek.SATURDAY else DayOfWeek.MONDAY

/** Markers of a day: computed occasions, fasting kinds and the user's occasions. */
private class DayMarks(val occasions: Boolean, val fasting: Boolean, val forbidden: Boolean, val personal: Boolean)

private fun marks(d: LocalDate, s: AppSettings): DayMarks {
    val f = FastingDays.on(d, s.hijriOffset)
    return DayMarks(
        occasions = Occasions.on(d, s.hijriOffset).isNotEmpty(),
        // Monday/Thursday fasts and Ramadan's days would mark half the month; the day's details still list them.
        fasting = f.any { it != FastingKind.MONDAY && it != FastingKind.THURSDAY && it != FastingKind.RAMADAN } && !FastingDays.isForbidden(f),
        forbidden = FastingDays.isForbidden(f),
        personal = PersonalOccasionOps.on(s.calendar.occasions, d, s.hijriOffset).isNotEmpty(),
    )
}

@Composable
fun CalendarScreen(vm: MainViewModel, s: AppSettings, focusEpochDay: Long, tabPadding: PaddingValues? = null) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val zone = s.activeLocation?.zoneId ?: ZoneId.systemDefault()
    val today = AppClock.now().atZone(zone).toLocalDate()
    val kind = s.calendar.kind
    val focus = if (focusEpochDay == Long.MIN_VALUE) null else LocalDate.ofEpochDay(focusEpochDay)
    var selected by rememberSaveable { mutableStateOf(focus?.toEpochDay() ?: Long.MIN_VALUE) }
    // A date inside the month on screen, so switching calendars stays on the same time of year.
    var anchor by rememberSaveable { mutableStateOf((focus ?: today).toEpochDay()) }
    // One pager state for both calendars: page SPAN is the current month; switching calendars moves to the
    // month that contains the date on screen (a fresh state per calendar would not be the one attached to the layout).
    val base = MonthRef.of(kind, today, s.hijriOffset) ?: MonthRef(kind, if (kind == CalendarKind.HIJRI) 1448 else today.year, 1)
    val pager = rememberPagerState(initialPage = (SPAN + base.distanceTo(MonthRef.of(kind, LocalDate.ofEpochDay(anchor), s.hijriOffset) ?: base)).coerceIn(0, PAGES - 1)) { PAGES }
    var shownKind by rememberSaveable { mutableStateOf(kind) }
    LaunchedEffect(kind) {
        if (shownKind != kind) {
            val target = MonthRef.of(kind, LocalDate.ofEpochDay(anchor), s.hijriOffset) ?: base
            pager.scrollToPage((SPAN + base.distanceTo(target)).coerceIn(0, PAGES - 1))
            shownKind = kind
        }
    }
    LaunchedEffect(kind, pager.currentPage) {
        if (shownKind == kind) MonthGrid.range(monthAt(pager.currentPage, base), s.hijriOffset)?.let { r -> anchor = r.start.plusDays(14).toEpochDay() }
    }
    val scope = rememberCoroutineScope()
    var picker by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    // Offers to undo the last change to the occasions (made here or on the edit screen).
    val undo = vm.occasionUndo
    LaunchedEffect(undo?.stamp) {
        val u = undo ?: return@LaunchedEffect
        val r = snackbar.showSnackbar(u.message, actionLabel = str.undo, duration = SnackbarDuration.Long)
        if (r == SnackbarResult.ActionPerformed) vm.undoOccasions() else if (vm.occasionUndo?.stamp == u.stamp) vm.occasionUndo = null
    }
    val current = monthAt(pager.currentPage, base)
    val coming = remember(today, s.hijriOffset, s.calendar.occasions, str.lang) { upcoming(str, s, today) }
    val body: @Composable () -> Unit = {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar, Modifier.exposeTestTags()) },
            topBar = {
                ScreenBar(str.calendarTitle, onBack = if (tabPadding == null) ({ vm.pop() }) else null) {
                    IconButton(onClick = { vm.update { it.copy(calendar = it.calendar.copy(listMode = !it.calendar.listMode)) } }, modifier = Modifier.testTag("cal_view_toggle")) {
                        Icon(if (s.calendar.listMode) SakinaIcons.Grid else SakinaIcons.Lines, if (s.calendar.listMode) str.gridView else str.listView)
                    }
                    IconButton(onClick = { vm.push(Route.CalendarTools) }, modifier = Modifier.testTag("cal_tools")) { Icon(SakinaIcons.Table, str.calendarTools) }
                }
            },
        ) { pad ->
            Column(Modifier.fillMaxSize().padding(pad)) {
                MonthHeader(
                    str = str, fmt = fmt, kind = kind, current = current, offset = s.hijriOffset,
                    drift = { pager.currentPageOffsetFraction },
                    onPrev = { scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) } },
                    onNext = { scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(PAGES - 1)) } },
                    onPick = { picker = true },
                )
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    val kinds = listOf(CalendarKind.HIJRI, CalendarKind.GREGORIAN)
                    RaisedSwitch(
                        labels = listOf(str.hijriLabel, str.gregorianLabel),
                        tags = listOf("cal_kind_hijri", "cal_kind_gregorian"),
                        selected = kinds.indexOf(kind),
                        onSelect = { i -> vm.update { it.copy(calendar = it.calendar.copy(kind = kinds[i])) } },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    GoldPill(str.todayButton, "cal_today") { scope.launch { pager.animateScrollToPage(SPAN) } }
                }
                val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
                HorizontalPager(pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 0, verticalAlignment = Alignment.Top) { page ->
                    val month = monthAt(page, base)
                    Box(Modifier.fillMaxSize().graphicsLayer {
                        // How far this page is from the settled one: 0 on screen, ±1 beside it.
                        val off = ((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceIn(-1f, 1f)
                        val away = if (rtl) -off else off
                        transformOrigin = TransformOrigin(if (away > 0) 1f else 0f, 0.4f)
                        rotationY = -away * 38f
                        cameraDistance = 14f * density
                        alpha = 1f - 0.35f * kotlin.math.abs(off)
                    }) {
                        if (s.calendar.listMode) MonthList(month, s, today) { selected = it.toEpochDay() }
                        else MonthPage(month, s, today, selected, if (month == base) coming else emptyList()) { selected = it.toEpochDay() }
                    }
                }
            }
        }
    }
    // As a tab the calendar sits above the navigation bar; opened from elsewhere it fills the screen.
    if (tabPadding == null) FullScreen(body) else Box(Modifier.fillMaxSize().padding(tabPadding).testTag("calendar_tab")) { body() }
    if (picker) MonthPicker(kind, current, onDismiss = { picker = false }) { m ->
        picker = false
        if (m.isSupported) scope.launch { pager.scrollToPage((SPAN + base.distanceTo(m)).coerceIn(0, PAGES - 1)) }
    }
    if (selected != Long.MIN_VALUE) {
        DaySheet(vm, s, LocalDate.ofEpochDay(selected), onDismiss = { selected = Long.MIN_VALUE })
    }
}

/**
 * The month on screen: its name large (tap for the month picker), the other calendar's months beneath it, and the
 * arrows either side. With the paintings on it sits on the night sky, raised, with a gold edge; the painting drifts
 * a little as the months are swiped.
 */
@Composable
private fun MonthHeader(str: com.sakina.app.ui.Strings, fmt: com.sakina.app.core.format.Fmt, kind: CalendarKind, current: MonthRef, offset: Int, drift: () -> Float, onPrev: () -> Unit, onNext: () -> Unit, onPick: () -> Unit) {
    val range = remember(current, offset) { MonthGrid.range(current, offset) }
    val other = range?.let { r ->
        if (kind == CalendarKind.HIJRI) {
            val a = r.start; val b = r.endInclusive
            if (a.monthValue == b.monthValue) monthName(str, CalendarKind.GREGORIAN, a.monthValue) + " " + fmt.num(a.year)
            else monthName(str, CalendarKind.GREGORIAN, a.monthValue) + (if (a.year != b.year) " " + fmt.num(a.year) else "") + " – " +
                monthName(str, CalendarKind.GREGORIAN, b.monthValue) + " " + fmt.num(b.year)
        } else {
            val a = UmmAlQura.displayed(r.start, offset); val b = UmmAlQura.displayed(r.endInclusive, offset)
            if (a == null || b == null) null
            else if (a.month == b.month) monthName(str, CalendarKind.HIJRI, a.month) + " " + fmt.num(a.year)
            else monthName(str, CalendarKind.HIJRI, a.month) + (if (a.year != b.year) " " + fmt.num(a.year) else "") + " – " +
                monthName(str, CalendarKind.HIJRI, b.month) + " " + fmt.num(b.year) + (if (str.isAr) " هـ" else " AH")
        }
    }
    val days = range?.let { java.time.temporal.ChronoUnit.DAYS.between(it.start, it.endInclusive).toInt() + 1 }
    val ex = com.sakina.app.ui.theme.LocalExtras.current
    val art = ex.art
    val cs = MaterialTheme.colorScheme
    val onHeader = if (art) Color.White else cs.onPrimaryContainer
    val shape = RoundedCornerShape(28.dp)
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            .shadow(14.dp, shape, ambientColor = Color(0xFF1C2E5C), spotColor = if (art) Color(0xFF0B1A3A) else cs.primary)
            .clip(shape)
            .background(if (art) Brush.verticalGradient(listOf(Color(0xFF1C2E5C), Color(0xFF101A38))) else lit(cs.primaryContainer, ex.dark, 1.4f))
            .border(1.2.dp, goldEdge(if (art) 0.85f else 0.6f), shape)
            .testTag(if (art) "cal_header_art" else "cal_header"),
    ) {
        if (art) {
            androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(com.sakina.app.R.drawable.art_calendar), null,
                Modifier.matchParentSize().graphicsLayer {
                    // A slow parallax while swiping: the sky moves less than the page.
                    val f = drift()
                    scaleX = 1.12f; scaleY = 1.12f
                    translationX = -f * size.width * 0.08f
                },
                contentScale = androidx.compose.ui.layout.ContentScale.Crop, alignment = Alignment.Center,
            )
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.05f), Color.Black.copy(alpha = 0.35f)))))
        } else {
            androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(com.sakina.app.R.drawable.tex_geometric), null,
                Modifier.matchParentSize().alpha(0.07f), contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
        }
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides onHeader) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = if (art) 20.dp else 14.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassButton(!art, "cal_prev", onPrev) { DirectionalIcon(SakinaIcons.ChevronStart, str.prevMonth, tint = onHeader) }
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable(onClick = onPick).padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val title = monthName(str, kind, current.month) + " " + fmt.num(current.year) + if (kind == CalendarKind.HIJRI && str.isAr) " هـ" else ""
                androidx.compose.animation.AnimatedContent(
                    targetState = title,
                    transitionSpec = {
                        (androidx.compose.animation.fadeIn(tween(260)) + androidx.compose.animation.scaleIn(tween(260), initialScale = 0.9f))
                            .togetherWith(androidx.compose.animation.fadeOut(tween(160)))
                    },
                    label = "month",
                ) { t ->
                    // The year in gold; the name embossed (a soft shadow beneath it).
                    val yearStart = t.indexOf(fmt.num(current.year)).takeIf { it >= 0 } ?: t.length
                    Text(
                        androidx.compose.ui.text.buildAnnotatedString {
                            append(t)
                            addStyle(androidx.compose.ui.text.SpanStyle(color = if (art) ex.gold else cs.secondary), yearStart, t.length)
                        },
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = com.sakina.app.ui.theme.Amiri, fontWeight = FontWeight.Bold,
                            shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = if (art) 0.55f else 0.12f), Offset(0f, 4f), 10f),
                        ),
                        color = onHeader, textAlign = TextAlign.Center, maxLines = 1,
                        modifier = Modifier.testTag("cal_title"),
                    )
                }
                Ornament(if (art) ex.gold else cs.secondary, Modifier.padding(vertical = 4.dp))
                if (other != null) Text(
                    other + (days?.let { " · " + str.daysCount(fmt.num(it), it.toLong()) } ?: ""),
                    style = MaterialTheme.typography.labelLarge, color = onHeader.copy(alpha = 0.85f), textAlign = TextAlign.Center,
                )
            }
            GlassButton(!art, "cal_next", onNext) { DirectionalIcon(SakinaIcons.ChevronEnd, str.nextMonth, tint = onHeader) }
        }
        }
    }
}

@Composable
private fun MonthPage(month: MonthRef, s: AppSettings, today: LocalDate, selected: Long, coming: List<Pair<LocalDate, String>>, onDay: (LocalDate) -> Unit) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val cs = MaterialTheme.colorScheme
    val weekStart = weekStartFor(str.lang)
    val grid = remember(month, s.hijriOffset, weekStart) { MonthGrid.build(month, s.hijriOffset, weekStart) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp)) {
        if (grid == null) {
            NoteText(str.outOfRange, Modifier.padding(16.dp))
            return@Column
        }
        val shape = RoundedCornerShape(28.dp)
        Box(Modifier.fillMaxWidth().raisedCard(shape, 10.dp, edge = goldEdge(0.45f))) {
            // A faint geometric pattern under the days.
            androidx.compose.foundation.Image(
                androidx.compose.ui.res.painterResource(com.sakina.app.R.drawable.tex_geometric), null,
                Modifier.matchParentSize().alpha(if (com.sakina.app.ui.theme.LocalExtras.current.dark) 0.05f else 0.045f),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            )
            Column(Modifier.padding(horizontal = 6.dp, vertical = 10.dp)) {
                // The weekdays on a band; Friday stands out, as it does in printed calendars here.
                Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp).clip(RoundedCornerShape(14.dp)).background(cs.primary.copy(alpha = 0.07f))) {
                    MonthGrid.weekdays(weekStart).forEach { d ->
                        val fri = d == DayOfWeek.FRIDAY
                        Text(fmt.weekday(d, short = true), style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (fri) FontWeight.Bold else FontWeight.Medium,
                            color = if (fri) cs.primary else cs.onSurfaceVariant,
                            textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
                    }
                }
                Spacer(Modifier.height(4.dp))
                grid.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { day -> DayCell(day, month.kind, s, today, day.date.toEpochDay() == selected, Modifier.weight(1f)) { onDay(day.date) } }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Legend()
        if (coming.isNotEmpty()) Upcoming(coming, today, onDay)
        Spacer(Modifier.height(8.dp))
        NoteText(str.occasionNote)
        NoteText(str.fastingNote, Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(16.dp))
    }
}

/**
 * A day as a raised tile: lit from above with a soft shadow. Today glows in the accent colour with a gold ring;
 * the selected day lifts with a gold edge; the white days are tinted gold and the days when fasting is not allowed
 * rose. Tiles of the next and last months stay flat and faint. The tile sinks a little while pressed.
 */
@Composable
private fun DayCell(day: GridDay, kind: CalendarKind, s: AppSettings, today: LocalDate, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val fmt = LocalFmt.current
    val ex = com.sakina.app.ui.theme.LocalExtras.current
    val m = remember(day.date, s.hijriOffset, s.calendar.occasions) { marks(day.date, s) }
    val isToday = day.date == today
    val primary = if (kind == CalendarKind.HIJRI) day.hijri?.day else day.date.dayOfMonth
    val secondary = if (kind == CalendarKind.HIJRI) day.date.dayOfMonth else day.hijri?.day
    val cs = MaterialTheme.colorScheme
    val white = day.inMonth && day.hijri?.day in 13..15 && !m.forbidden
    val friday = day.date.dayOfWeek == DayOfWeek.FRIDAY
    val base = when {
        isToday -> cs.primary
        m.forbidden -> lerp(cs.surfaceContainerLowest, cs.errorContainer, 0.75f)
        white -> lerp(cs.surfaceContainerLowest, cs.secondaryContainer, if (ex.dark) 0.55f else 0.85f)
        friday -> lerp(if (ex.dark) cs.surfaceContainerHigh else cs.surfaceContainerLowest, cs.primaryContainer, if (ex.dark) 0.35f else 0.45f)
        else -> if (ex.dark) cs.surfaceContainerHigh else cs.surfaceContainerLowest
    }
    val ink = if (isToday) cs.onPrimary else cs.onSurface
    val sub = if (isToday) cs.onPrimary.copy(alpha = 0.85f) else cs.onSurfaceVariant
    val shape = RoundedCornerShape(15.dp)
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val press = pressScale(source, 0.9f)
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val raised = day.inMonth
    val lift = when { isToday -> 9.dp; selected -> 7.dp; raised -> 2.5.dp; else -> 0.dp }
    Box(
        modifier
            .aspectRatio(0.84f)
            .padding(3.dp)
            .graphicsLayer { scaleX = press; scaleY = press }
            .then(if (lift > 0.dp) Modifier.shadow(lift, shape, ambientColor = if (isToday) cs.primary else Color.Black, spotColor = if (isToday) cs.primary else Color.Black.copy(alpha = 0.45f)) else Modifier)
            .clip(shape)
            .then(if (raised || isToday) Modifier.background(lit(base, ex.dark, if (isToday) 1.3f else 1f)) else Modifier)
            .then(
                when {
                    isToday -> Modifier.border(1.5.dp, goldEdge(), shape)
                    selected -> Modifier.border(2.dp, goldEdge(), shape)
                    raised -> Modifier.border(0.8.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = if (ex.dark) 0.10f else 0.9f), cs.outlineVariant.copy(alpha = 0.35f))), shape)
                    else -> Modifier
                },
            )
            .clickable(interactionSource = source, indication = androidx.compose.material3.ripple()) {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .alpha(if (day.inMonth) 1f else 0.38f)
            .testTag("cal_day_${day.date}"),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(primary?.let { fmt.num(it) } ?: "–",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 24.sp,
                    shadow = if (isToday) androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.25f), Offset(0f, 2f), 4f) else null),
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.SemiBold, color = ink, maxLines = 1)
            Text(secondary?.let { fmt.num(it) } ?: "", style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 13.sp), color = sub, maxLines = 1)
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(top = 3.dp).height(5.dp)) {
                if (m.occasions) Dot(if (isToday) cs.onPrimary else cs.primary)
                if (m.fasting) Dot(if (isToday) cs.onPrimary else cs.tertiary, hollow = true)
                if (m.personal) Dot(if (isToday) ex.gold else cs.secondary)
            }
        }
    }
}

/** The next occasions and personal dates from today (up to about thirteen months ahead), at most five. */
private fun upcoming(str: com.sakina.app.ui.Strings, s: AppSettings, today: LocalDate): List<Pair<LocalDate, String>> {
    val out = mutableListOf<Pair<LocalDate, String>>()
    var d = today.plusDays(1)
    var last: Occasion? = null
    repeat(400) {
        if (out.size >= 5) return out
        // A multi-day occasion is listed on its first day only.
        val o = Occasions.on(d, s.hijriOffset).firstOrNull()
        if (o != null && o != last) out += d to str.occasion(o)
        last = o
        PersonalOccasionOps.on(s.calendar.occasions, d, s.hijriOffset).forEach { out += d to it.title }
        d = d.plusDays(1)
    }
    return out.take(5)
}

@Composable
private fun Upcoming(items: List<Pair<LocalDate, String>>, today: LocalDate, onDay: (LocalDate) -> Unit) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val cs = MaterialTheme.colorScheme
    val ex = com.sakina.app.ui.theme.LocalExtras.current
    Column(Modifier.padding(top = 16.dp).testTag("cal_upcoming")) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)) {
            Text(str.upcomingTitle, style = MaterialTheme.typography.titleMedium.copy(fontFamily = com.sakina.app.ui.theme.Amiri, fontWeight = FontWeight.Bold), color = cs.primary)
            Spacer(Modifier.width(10.dp))
            Ornament(ex.gold.copy(alpha = 0.8f), Modifier.weight(1f))
        }
        Column(Modifier.fillMaxWidth().raisedCard(RoundedCornerShape(24.dp), 6.dp).padding(vertical = 6.dp)) {
            items.forEachIndexed { i, (d, title) ->
                val days = java.time.temporal.ChronoUnit.DAYS.between(today, d).toInt()
                if (i > 0) androidx.compose.material3.HorizontalDivider(Modifier.padding(start = 74.dp, end = 16.dp), color = cs.outlineVariant.copy(alpha = 0.5f))
                Row(
                    Modifier.fillMaxWidth().clickable { onDay(d) }.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The days left on a raised medallion.
                    Box(
                        Modifier.size(46.dp).shadow(6.dp, CircleShape, ambientColor = cs.primary, spotColor = cs.primary)
                            .clip(CircleShape).background(lit(cs.primary, ex.dark, 1.3f)).border(1.2.dp, goldEdge(), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(fmt.num(days), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = cs.onPrimary, maxLines = 1)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            (if (days == 1) str.tomorrow else str.inDays(str.daysCount(fmt.num(days), days.toLong()))) + " · " +
                                fmt.weekday(d.dayOfWeek) + " " + fmt.gregorian(d, withYear = false),
                            style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Dot(color: Color, hollow: Boolean = false) =
    // A ring for fasting days, so the markers differ in shape as well as colour.
    Box(Modifier.size(6.dp).clip(CircleShape).then(if (hollow) Modifier.border(1.5.dp, color, CircleShape) else Modifier.background(color)))

@Composable
private fun Legend() {
    val str = LocalStrings.current
    val cs = MaterialTheme.colorScheme
    val chip = Modifier.clip(RoundedCornerShape(50)).background(cs.surfaceContainerHigh.copy(alpha = 0.8f)).padding(horizontal = 10.dp, vertical = 5.dp)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
        listOf(
            Triple(cs.primary, str.legendOccasion, false),
            Triple(cs.tertiary, str.legendFasting, true),
            Triple(cs.secondary, str.legendPersonal, false),
        ).forEach { (c, t, hollow) ->
            Row(chip, verticalAlignment = Alignment.CenterVertically) { Dot(c, hollow); Spacer(Modifier.width(6.dp)); Text(t, style = MaterialTheme.typography.labelMedium) }
        }
        Row(chip, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(11.dp).clip(RoundedCornerShape(3.dp)).background(cs.secondaryContainer).border(1.dp, goldEdge(), RoundedCornerShape(3.dp)))
            Spacer(Modifier.width(6.dp)); Text(str.legendWhite, style = MaterialTheme.typography.labelMedium)
        }
        Row(chip, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(11.dp).clip(RoundedCornerShape(3.dp)).background(cs.errorContainer))
            Spacer(Modifier.width(6.dp)); Text(str.legendForbidden, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun MonthList(month: MonthRef, s: AppSettings, today: LocalDate, onDay: (LocalDate) -> Unit) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val range = remember(month, s.hijriOffset) { MonthGrid.range(month, s.hijriOffset) }
    if (range == null) {
        NoteText(str.outOfRange, Modifier.padding(16.dp))
        return
    }
    val days = remember(range) { generateSequence(range.start) { it.plusDays(1) }.takeWhile { it <= range.endInclusive }.toList() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(days, key = { it.toEpochDay() }) { d ->
            val h = UmmAlQura.displayed(d, s.hijriOffset)
            val labels = buildList {
                Occasions.on(d, s.hijriOffset).forEach { add(str.occasion(it)) }
                FastingDays.on(d, s.hijriOffset).forEach { add(str.fastingKind(it)) }
                PersonalOccasionOps.on(s.calendar.occasions, d, s.hijriOffset).forEach { add(it.title) }
            }
            Surface(
                onClick = { onDay(d) },
                shape = RoundedCornerShape(14.dp),
                color = if (d == today) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
                shadowElevation = if (d == today) 6.dp else 2.dp,
                border = if (d == today) BorderStroke(1.2.dp, goldEdge()) else null,
                modifier = Modifier.fillMaxWidth().testTag("cal_row_$d"),
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.width(110.dp)) {
                        Text(fmt.weekday(d.dayOfWeek), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val main = if (month.kind == CalendarKind.HIJRI) h?.let { fmt.hijri(it, withYear = false) } ?: "–" else fmt.gregorian(d, withYear = false)
                        Text(main, style = MaterialTheme.typography.titleMedium)
                        val other = if (month.kind == CalendarKind.HIJRI) fmt.gregorian(d, withYear = false) else h?.let { fmt.hijri(it, withYear = false) } ?: ""
                        Text(other, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(labels.joinToString("، "), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                }
            }
        }
        item { NoteText(str.occasionNote, Modifier.padding(8.dp)) }
    }
}

@Composable
private fun MonthPicker(kind: CalendarKind, current: MonthRef, onDismiss: () -> Unit, onPick: (MonthRef) -> Unit) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    var year by remember { mutableIntStateOf(current.year) }
    val range = if (kind == CalendarKind.HIJRI) UmmAlQura.MIN_YEAR..UmmAlQura.MAX_YEAR else 1883..2173
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str.pickMonth) },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { if (year > range.first) year-- }, modifier = Modifier.testTag("picker_year_minus")) { Text("−", style = MaterialTheme.typography.titleLarge) }
                    Text(fmt.num(year), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.weight(1f).testTag("picker_year"))
                    IconButton(onClick = { if (year < range.last) year++ }, modifier = Modifier.testTag("picker_year_plus")) { Text("+", style = MaterialTheme.typography.titleLarge) }
                }
                Spacer(Modifier.height(8.dp))
                (1..12).chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 3.dp)) {
                        row.forEach { m ->
                            val sel = year == current.year && m == current.month
                            Surface(
                                onClick = { onPick(MonthRef(kind, year, m)) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f).testTag("picker_month_$m"),
                            ) {
                                Text(monthName(str, kind, m), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 1,
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 2.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str.cancel) } },
        modifier = Modifier.exposeTestTags(),
    )
}

@Composable
private fun DaySheet(vm: MainViewModel, s: AppSettings, date: LocalDate, onDismiss: () -> Unit) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val h = UmmAlQura.displayed(date, s.hijriOffset)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp).exposeTestTags(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // The day's dates beside its moon.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(fmt.weekday(date.dayOfWeek), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (h != null) Text(fmt.hijri(h),
                        style = MaterialTheme.typography.headlineSmall.copy(fontFamily = com.sakina.app.ui.theme.Amiri, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("day_hijri"))
                    Text(fmt.gregorian(date, withYear = true), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("day_gregorian"))
                }
                if (h != null) Moon(h.day, Modifier.testTag("day_moon"), size = 58.dp)
            }
            val occ = Occasions.on(date, s.hijriOffset)
            if (occ.isNotEmpty()) {
                SectionCard(title = str.legendOccasion, icon = SakinaIcons.Calendar) {
                    occ.forEach { Text(str.occasion(it), style = MaterialTheme.typography.bodyLarge) }
                    NoteText(str.occasionNote)
                }
            }
            val fasting = FastingDays.on(date, s.hijriOffset)
            if (fasting.isNotEmpty()) {
                SectionCard(title = str.fastingDayTitle, icon = SakinaIcons.Moon) {
                    fasting.forEach { Text(str.fastingKind(it), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("day_fasting_${it.name.lowercase()}")) }
                    NoteText(str.fastingNote)
                }
                FastingRefs(vm, fasting)
            }
            val loc = s.activeLocation
            val zone = loc?.zoneId
            if (loc != null && zone != null && loc.isUsable) {
                val day = remember(date, loc, s.calcConfig) { PrayerSchedule(loc.point, zone, s.calcConfig).day(date) }
                SectionCard(title = str.dayTimesTitle, icon = SakinaIcons.Clock) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(PrayerKey.FAJR, PrayerKey.MAGHRIB).forEach { k ->
                            Column(Modifier.weight(1f)) {
                                Text(str.prayer(k), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(day[k].instant?.let { fmt.time(it, zone) } ?: str.unavailable, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
            val mine = PersonalOccasionOps.on(s.calendar.occasions, date, s.hijriOffset)
            SectionCard(title = str.myOccasions, icon = SakinaIcons.Bookmark) {
                if (mine.isEmpty()) NoteText(str.noMyOccasions)
                mine.forEach { o -> OccasionRow(vm, o) }
                // The calendar's undo bar sits behind this sheet, so the sheet offers the undo itself.
                vm.occasionUndo?.let { u ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(u.message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { vm.undoOccasions() }, modifier = Modifier.testTag("day_undo")) { Text(str.undo) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                FilledTonalButton(onClick = { onDismiss(); vm.push(Route.OccasionEdit("", date.toEpochDay())) }, modifier = Modifier.testTag("day_add_occasion")) {
                    Icon(SakinaIcons.Plus, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(str.addOccasion)
                }
            }
        }
    }
}

@Composable
private fun OccasionRow(vm: MainViewModel, o: PersonalOccasion) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).clickable { vm.push(Route.OccasionEdit(o.id)) }.testTag("occasion_${o.id}")) {
            Text(o.title, style = MaterialTheme.typography.bodyLarge)
            val bits = listOfNotNull(
                o.minuteOfDay?.let { fmt.clock(it / 60, it % 60) },
                when (o.repeat) {
                    PersonalOccasion.Repeat.NONE -> null
                    PersonalOccasion.Repeat.YEARLY_HIJRI -> str.repeatHijri
                    PersonalOccasion.Repeat.YEARLY_GREGORIAN -> str.repeatGregorian
                },
                if (o.notify) str.occasionNotify else null,
            )
            if (bits.isNotEmpty()) Text(bits.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (o.note.isNotBlank()) Text(o.note, style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = { vm.changeOccasions(str.occasionDeleted) { PersonalOccasionOps.remove(it, o.id) } }, modifier = Modifier.testTag("occasion_delete_${o.id}")) {
            Icon(SakinaIcons.Trash, str.delete)
        }
    }
}

/** References for the fasting kinds of a day. */
@Composable
private fun FastingRefs(vm: MainViewModel, kinds: List<FastingKind>) {
    val ids = kinds.flatMap {
        when (it) {
            FastingKind.MONDAY -> listOf("mon_thu_tahari", "monday_born")
            FastingKind.THURSDAY -> listOf("mon_thu_tahari", "mon_thu_amal")
            FastingKind.WHITE_DAY -> listOf("white_days")
            FastingKind.ARAFAH -> listOf("arafah")
            FastingKind.TASUA -> listOf("tasua")
            FastingKind.ASHURA -> listOf("ashura")
            FastingKind.SHAWWAL -> listOf("shawwal_six")
            FastingKind.FORBIDDEN_EID -> listOf("eid_forbidden")
            FastingKind.FORBIDDEN_TASHREEQ -> listOf("tashreeq")
            FastingKind.RAMADAN -> emptyList()
        }
    }.distinct()
    if (ids.isNotEmpty()) RefsCard(vm, ids)
}
