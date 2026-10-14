package com.sakina.app.core.quran

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Serializable
data class KhatmaEntry(val epochDay: Long, val page: Int)

/**
 * A khatma plan over the 604 pages of the Madani mushaf layout. Progress is recorded by hand (the last page
 * completed); opening the app or a page never records anything by itself.
 */
@Serializable
data class KhatmaPlan(
    val startEpochDay: Long,
    val days: Int,
    val progressPage: Int = 0,
    val log: List<KhatmaEntry> = emptyList(),
) {
    val start: LocalDate get() = LocalDate.ofEpochDay(startEpochDay)
    val end: LocalDate get() = start.plusDays(days - 1L)
}

data class KhatmaStatus(
    /** 0-based day of the plan for "today"; negative before the start, ≥ days after the end. */
    val dayIndex: Int,
    val today: IntRange?,
    /** Pages that should have been finished before today. */
    val expectedBeforeToday: Int,
    val behind: Int,
    val remainingToday: Int,
    val ahead: Int,
    val completed: Boolean,
    val overdue: Boolean,
    val percent: Int,
)

object Khatma {
    const val PAGES = 604
    const val MIN_DAYS = 1
    const val MAX_DAYS = 365

    fun create(start: LocalDate, days: Int) = KhatmaPlan(start.toEpochDay(), days.coerceIn(MIN_DAYS, MAX_DAYS))

    /** Pages of day [i] (0-based). With more days than pages some days are empty. */
    fun portion(plan: KhatmaPlan, i: Int): IntRange {
        val d = plan.days
        val from = (i.toLong() * PAGES / d).toInt() + 1
        val to = ((i + 1L) * PAGES / d).toInt()
        return from..to
    }

    fun dateOf(plan: KhatmaPlan, i: Int): LocalDate = plan.start.plusDays(i.toLong())

    fun status(plan: KhatmaPlan, today: LocalDate): KhatmaStatus {
        val d = ChronoUnit.DAYS.between(plan.start, today).toInt()
        val p = plan.progressPage.coerceIn(0, PAGES)
        val expected = when {
            d <= 0 -> 0
            d >= plan.days -> PAGES
            else -> portion(plan, d - 1).last
        }
        val todayRange = if (d in 0 until plan.days) portion(plan, d) else null
        val remainingToday = todayRange?.let { r -> if (r.isEmpty()) 0 else (r.last - maxOf(p, r.first - 1)).coerceAtLeast(0) } ?: 0
        val endOfToday = todayRange?.last ?: expected
        val completed = p >= PAGES
        return KhatmaStatus(
            dayIndex = d,
            today = todayRange,
            expectedBeforeToday = expected,
            behind = (expected - p).coerceAtLeast(0),
            remainingToday = remainingToday,
            ahead = if (d >= 0) (p - endOfToday).coerceAtLeast(0) else 0,
            completed = completed,
            overdue = d >= plan.days && !completed,
            percent = p * 100 / PAGES,
        )
    }

    /** Records that the reader finished up to [page] (kept within 0…604). */
    fun record(plan: KhatmaPlan, page: Int, today: LocalDate): KhatmaPlan {
        val pg = page.coerceIn(0, PAGES)
        val log = (plan.log.filter { it.epochDay != today.toEpochDay() } + KhatmaEntry(today.toEpochDay(), pg)).takeLast(400)
        return plan.copy(progressPage = pg, log = log)
    }

    /** Marks today's portion as done. */
    fun finishToday(plan: KhatmaPlan, today: LocalDate): KhatmaPlan {
        val s = status(plan, today)
        val target = s.today?.last ?: return plan
        return if (target > plan.progressPage) record(plan, target, today) else plan
    }
}
