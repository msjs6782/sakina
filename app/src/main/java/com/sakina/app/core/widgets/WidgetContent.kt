package com.sakina.app.core.widgets

import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * One item of the content widget: a short dhikr from the bundled adhkar library or a selected verse from
 * the bundled Tanzil text, copied verbatim with its reference (checked against the sources by a unit test).
 */
@Serializable
data class ContentItem(
    /** "dhikr" or "ayah". */
    val type: String,
    /** Adhkar id, or "sura:aya". */
    val ref: String,
    val text: String,
    /** "Sahih al-Bukhari 6406" style source, or the surah name; numbers in Latin digits. */
    val sourceAr: String,
    val sourceEn: String,
    /** For a dhikr: the grade; for a verse: empty. */
    val gradeAr: String = "",
    val gradeEn: String = "",
    /** How many times the hadith mentions (1 when it names none). */
    val count: Int = 1,
) {
    val isAyah: Boolean get() = type == TYPE_AYAH

    companion object {
        const val TYPE_DHIKR = "dhikr"
        const val TYPE_AYAH = "ayah"
    }
}

@Serializable
data class ContentPack(val version: Int = 1, val items: List<ContentItem> = emptyList())

enum class ContentMode { MIXED, ADHKAR, VERSES }

object ContentPicker {
    /** Items for the mode; in mixed mode adhkar and verses alternate so consecutive days differ in kind. */
    fun candidates(pack: ContentPack, mode: ContentMode): List<ContentItem> {
        val adhkar = pack.items.filter { !it.isAyah }
        val verses = pack.items.filter { it.isAyah }
        return when (mode) {
            ContentMode.ADHKAR -> adhkar
            ContentMode.VERSES -> verses
            ContentMode.MIXED -> buildList {
                val n = maxOf(adhkar.size, verses.size)
                for (i in 0 until n) {
                    verses.getOrNull(i)?.let { add(it) }
                    adhkar.getOrNull(i)?.let { add(it) }
                }
            }
        }
    }

    /**
     * The item for [date], moved on by [shift] (the widget's "next" button), skipping items that do not fit the
     * widget's size. Null when nothing fits.
     */
    fun pick(pack: ContentPack, mode: ContentMode, date: LocalDate, shift: Int, fits: (ContentItem) -> Boolean): ContentItem? {
        val list = candidates(pack, mode)
        if (list.isEmpty()) return null
        val start = Math.floorMod(date.toEpochDay() + shift, list.size.toLong()).toInt()
        for (i in list.indices) {
            val item = list[(start + i) % list.size]
            if (fits(item)) return item
        }
        return null
    }
}
