package com.sakina.app.core.format

import com.sakina.app.core.calendar.HijriDate
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs

enum class Lang { AR, EN }

/** Number, time and date formatting that does not depend on the device's locale settings. */
class Fmt(val lang: Lang, val clock24: Boolean, val arabicDigits: Boolean) {
    private val ar get() = lang == Lang.AR

    fun digits(text: String): String {
        if (!arabicDigits) return text
        val sb = StringBuilder(text.length)
        for (c in text) sb.append(if (c in '0'..'9') ('٠' + (c - '0')) else c)
        return sb.toString()
    }

    fun num(n: Int): String = digits(n.toString())

    fun signed(n: Int): String = when {
        n > 0 -> "+" + num(n)
        n < 0 -> "−" + num(-n)
        else -> num(0)
    }

    private fun two(n: Int) = if (n < 10) "0$n" else n.toString()

    /** "4:35 م" / "4:35 PM" / "16:35". [suffix] false drops AM/PM (for tables). */
    fun time(instant: Instant, zone: ZoneId, suffix: Boolean = true): String {
        val t = instant.atZone(zone)
        return clock(t.hour, t.minute, suffix)
    }

    fun clock(hour: Int, minute: Int, suffix: Boolean = true): String {
        if (clock24) return digits("${two(hour)}:${two(minute)}")
        val h12 = if (hour % 12 == 0) 12 else hour % 12
        val base = digits("$h12:${two(minute)}")
        if (!suffix) return base
        val pm = hour >= 12
        return if (ar) "$base ${if (pm) "م" else "ص"}" else "$base ${if (pm) "PM" else "AM"}"
    }

    fun amPm(instant: Instant, zone: ZoneId): String? {
        if (clock24) return null
        val pm = instant.atZone(zone).hour >= 12
        return if (ar) (if (pm) "م" else "ص") else (if (pm) "PM" else "AM")
    }

    /** "01:23:45" or "23:45" when under an hour. Negative durations show as zero. */
    fun countdown(d: Duration, alwaysHours: Boolean = false): String {
        val s = maxOf(0L, d.seconds)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        val text = if (h > 0 || alwaysHours) "${two(h.toInt())}:${two(m.toInt())}:${two(sec.toInt())}" else "${two(m.toInt())}:${two(sec.toInt())}"
        return digits(text)
    }

    /** "2:05" hours and minutes, for elapsed times. */
    fun hoursMinutes(d: Duration): String {
        val m = maxOf(0L, d.toMinutes())
        return digits("${m / 60}:${two((m % 60).toInt())}")
    }

    /** Short human duration: "ساعتان و5 د" style kept numeric: "2 س 5 د" / "2 h 5 min". */
    fun durationShort(d: Duration): String {
        val m = maxOf(0L, d.toMinutes())
        val h = m / 60
        val r = m % 60
        return if (ar) {
            when {
                h == 0L -> "${num(r.toInt())} د"
                r == 0L -> "${num(h.toInt())} س"
                else -> "${num(h.toInt())} س ${num(r.toInt())} د"
            }
        } else {
            when {
                h == 0L -> "$r min"
                r == 0L -> "$h h"
                else -> "$h h $r min"
            }
        }
    }

    fun weekday(day: DayOfWeek, short: Boolean = false): String = Names.weekday(day, lang, short)

    fun gregorian(date: LocalDate, withYear: Boolean = true, withWeekday: Boolean = false): String {
        val parts = buildString {
            if (withWeekday) append(weekday(date.dayOfWeek)).append(if (ar) "، " else ", ")
            if (ar) {
                append(num(date.dayOfMonth)).append(' ').append(Names.gregorianMonth(date.monthValue, lang))
                if (withYear) append(' ').append(num(date.year))
            } else {
                append(date.dayOfMonth).append(' ').append(Names.gregorianMonth(date.monthValue, lang))
                if (withYear) append(' ').append(date.year)
            }
        }
        return parts
    }

    fun gregorianShort(date: LocalDate): String = digits("${date.dayOfMonth}/${date.monthValue}")

    fun hijri(h: HijriDate, withYear: Boolean = true): String {
        val month = Names.hijriMonth(h.month, lang)
        return if (ar) {
            buildString {
                append(num(h.day)).append(' ').append(month)
                if (withYear) append(' ').append(num(h.year)).append(" هـ")
            }
        } else {
            buildString {
                append(h.day).append(' ').append(month)
                if (withYear) append(' ').append(h.year).append(" AH")
            }
        }
    }

    fun coordinate(value: Double, latitude: Boolean): String {
        val dir = if (latitude) {
            if (value >= 0) (if (ar) "ش" else "N") else (if (ar) "ج" else "S")
        } else {
            if (value >= 0) (if (ar) "ق" else "E") else (if (ar) "غ" else "W")
        }
        var n = digits(String.format(Locale.US, "%.4f°", abs(value)))
        if (arabicDigits) n = n.replace('.', '\u066B')
        return ltr(n) + " " + dir
    }

    /** Wraps left-to-right text (offsets, coordinates) so it keeps its order inside Arabic sentences. */
    fun ltr(text: String) = "\u2066" + text + "\u2069"

    /** "UTC+3", "UTC+5:30" for a zone at an instant. */
    fun utcOffset(zone: ZoneId, at: Instant): String {
        val total = zone.rules.getOffset(at).totalSeconds
        val sign = if (total < 0) "−" else "+"
        val a = abs(total)
        val h = a / 3600
        val m = (a % 3600) / 60
        return ltr("UTC" + sign + digits(if (m == 0) "$h" else "$h:${two(m)}"))
    }
}

object Names {
    private val weekdaysAr = listOf("الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد")
    private val weekdaysEn = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    private val weekdaysShortAr = listOf("اثنين", "ثلاثاء", "أربعاء", "خميس", "جمعة", "سبت", "أحد")
    private val weekdaysShortEn = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val gregorianAr = listOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")
    private val gregorianEn = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    private val hijriAr = listOf("محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة")
    private val hijriEn = listOf("Muharram", "Safar", "Rabi al-Awwal", "Rabi al-Akhir", "Jumada al-Ula", "Jumada al-Akhirah", "Rajab", "Shaban", "Ramadan", "Shawwal", "Dhu al-Qadah", "Dhu al-Hijjah")

    fun weekday(day: DayOfWeek, lang: Lang, short: Boolean = false): String {
        val i = day.value - 1
        return when {
            lang == Lang.AR && short -> weekdaysShortAr[i]
            lang == Lang.AR -> weekdaysAr[i]
            short -> weekdaysShortEn[i]
            else -> weekdaysEn[i]
        }
    }

    fun gregorianMonth(month: Int, lang: Lang) = (if (lang == Lang.AR) gregorianAr else gregorianEn)[month - 1]
    fun hijriMonth(month: Int, lang: Lang) = (if (lang == Lang.AR) hijriAr else hijriEn)[month - 1]
}
