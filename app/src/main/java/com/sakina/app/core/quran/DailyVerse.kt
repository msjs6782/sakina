package com.sakina.app.core.quran

import java.time.LocalDate

/**
 * "Verse of the day": a fixed list of short, self-contained verses chosen for Sakina. The text itself always
 * comes from the Tanzil Uthmani file; only the references are listed here.
 */
object DailyVerse {
    val refs: List<AyahRef> = listOf(
        "2:152", "2:153", "2:186", "2:201", "2:255", "2:286", "3:8", "3:139", "3:173", "3:200",
        "6:162", "7:23", "7:56", "9:51", "10:62", "13:28", "14:7", "14:40", "15:99", "16:97",
        "16:128", "17:80", "18:10", "20:114", "21:87", "23:118", "25:74", "28:24", "29:69", "33:41",
        "39:53", "40:60", "42:19", "47:7", "50:16", "51:56", "64:11", "93:5", "94:5", "2:45",
    ).map { s -> s.split(':').let { AyahRef(it[0].toInt(), it[1].toInt()) } }

    fun forDate(date: LocalDate): AyahRef = refs[Math.floorMod(date.toEpochDay(), refs.size.toLong()).toInt()]
}
