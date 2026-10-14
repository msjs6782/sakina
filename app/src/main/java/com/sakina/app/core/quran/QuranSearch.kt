package com.sakina.app.core.quran

/** Arabic text folding for search: drops diacritics and Quranic marks and unifies letter variants. */
object ArabicText {
    private fun isMark(c: Char): Boolean {
        val u = c.code
        return u in 0x0610..0x061A || u in 0x064B..0x065F || u == 0x0670 || u in 0x06D6..0x06ED || u == 0x0640
    }

    private fun fold(c: Char): Char? = when (c) {
        'أ', 'إ', 'آ', 'ٱ', 'ٲ', 'ٳ' -> 'ا'
        'ى' -> 'ي'
        'ة' -> 'ه'
        'ؤ' -> 'و'
        'ئ' -> 'ي'
        'ء' -> null
        in '٠'..'٩' -> '0' + (c - '٠')
        in '۰'..'۹' -> '0' + (c - '۰')
        else -> c.lowercaseChar()
    }

    /** Folded text and, for each folded character, its index in the original. */
    fun foldWithMap(s: String): Pair<String, IntArray> {
        val sb = StringBuilder(s.length)
        val map = IntArray(s.length)
        var lastSpace = true
        for ((i, c) in s.withIndex()) {
            if (isMark(c)) continue
            val f = fold(c) ?: continue
            if (f.isWhitespace()) {
                if (lastSpace) continue
                map[sb.length] = i; sb.append(' '); lastSpace = true
            } else {
                map[sb.length] = i; sb.append(f); lastSpace = false
            }
        }
        while (sb.isNotEmpty() && sb.last() == ' ') sb.setLength(sb.length - 1)
        return sb.toString() to map.copyOf(sb.length)
    }

    fun fold(s: String): String = foldWithMap(s).first

    fun hasArabic(s: String) = s.any { it.code in 0x0600..0x06FF }
}

sealed interface RefTarget {
    data class Ayah(val ref: AyahRef) : RefTarget
    data class SurahStart(val sura: Int) : RefTarget
    data class Page(val page: Int) : RefTarget
    data class Juz(val juz: Int) : RefTarget
}

/** Understands "2:255", "٢:٢٥٥", "البقرة 255", "Baqara 255", "صفحة 50", "page 50", "جزء 3", "juz 3" and surah names. */
class ReferenceParser(private val meta: QuranMeta) {
    private val keysAr: List<Pair<String, Int>> = meta.suras.flatMap { s ->
        val n = ArabicText.fold(s.nameAr)
        listOfNotNull(n to s.index, n.removePrefix("ال").takeIf { n.startsWith("ال") && it.length >= 2 }?.let { it to s.index })
    }
    private val keysLatin: List<Pair<String, Int>> = meta.suras.flatMap { s -> listOf(latin(s.nameTranslit) to s.index, latin(s.nameEn) to s.index) }

    private fun latin(s: String): String {
        var t = s.lowercase().filter { it.isLetter() }
        for (p in listOf("al", "an", "ash", "at", "az", "ad", "ar", "as", "the")) {
            if (t.startsWith(p) && t.length > p.length + 2) { t = t.removePrefix(p); break }
        }
        t = t.replace("aa", "a").replace("ee", "i").replace("oo", "u").replace("ou", "u")
        if (t.length > 3 && t.endsWith("h")) t = t.dropLast(1)
        return t
    }

    private fun surahByName(name: String): Int? {
        val n = name.trim()
        if (n.isEmpty()) return null
        if (ArabicText.hasArabic(n)) {
            var f = ArabicText.fold(n).removePrefix("سوره").trim()
            if (f.isEmpty()) return null
            keysAr.firstOrNull { it.first == f }?.let { return it.second }
            f = f.removePrefix("ال")
            return keysAr.firstOrNull { it.first == f || it.first.removePrefix("ال") == f }?.second
        }
        val l = latin(n.removePrefix("surah").removePrefix("sura"))
        if (l.length < 2) return null
        return keysLatin.firstOrNull { it.first == l }?.second ?: keysLatin.firstOrNull { it.first.startsWith(l) && l.length >= 4 }?.second
    }

    fun parse(input: String): RefTarget? {
        val q = ArabicText.fold(input).replace('،', ' ').replace(',', ' ').trim()
        if (q.isEmpty()) return null
        Regex("""^(\d{1,3})\s*[:.\-/ ]\s*(\d{1,3})$""").find(q)?.let { m ->
            val r = AyahRef(m.groupValues[1].toInt(), m.groupValues[2].toInt())
            return if (meta.isValid(r)) RefTarget.Ayah(r) else null
        }
        Regex("""^(?:page|p|صفحه|ص)\s*(\d{1,3})$""").find(q)?.let { m ->
            val p = m.groupValues[1].toInt()
            return if (p in 1..meta.pageCount) RefTarget.Page(p) else null
        }
        Regex("""^(?:juz|jozz|part|جز|الجز)\s*(\d{1,2})$""").find(q)?.let { m ->
            val j = m.groupValues[1].toInt()
            return if (j in 1..30) RefTarget.Juz(j) else null
        }
        Regex("""^(\d{1,3})$""").find(q)?.let { m ->
            val s = m.groupValues[1].toInt()
            return if (s in 1..meta.suras.size) RefTarget.SurahStart(s) else null
        }
        // Name with an optional ayah number: "البقره 255", "سوره البقره ايه 255", "baqara:255".
        Regex("""^(.*?)[\s:]*(?:ايه|ayah|aya|verse)?\s*(\d{1,3})?$""").find(q)?.let { m ->
            val sura = surahByName(m.groupValues[1]) ?: return null
            val n = m.groupValues[2].toIntOrNull() ?: return RefTarget.SurahStart(sura)
            val r = AyahRef(sura, n)
            return if (meta.isValid(r)) RefTarget.Ayah(r) else null
        }
        return null
    }
}

data class SearchHit(
    val global: Int,
    val inTranslation: Boolean,
    val count: Int,
    /** Character range of the first match in the displayed snippet source (simple text or translation). */
    val first: IntRange,
)

data class SearchResult(val query: String, val reference: RefTarget?, val hits: List<SearchHit>, val total: Int, val arabic: Boolean)

/**
 * Searches the plain (imla'i) Tanzil text for Arabic queries and the English translation for Latin queries.
 * The basmala that Tanzil's plain text puts before ayah 1 of each surah is skipped, so it is not counted as a match.
 */
class QuranSearch(private val meta: QuranMeta, simple: AyahTexts, private val translation: AyahTexts?) {
    private val references = ReferenceParser(meta)

    /** The plain text without the leading basmala, as displayed in results. */
    val plain: List<String> = simple.lines.mapIndexed { g, t -> stripBasmala(g, t) }

    private val folded: List<Pair<String, IntArray>> = plain.map { ArabicText.foldWithMap(it) }
    private val english: List<String>? = translation?.lines?.map { it.lowercase() }

    private fun stripBasmala(global: Int, text: String): String {
        val ref = meta.ref(global)
        if (ref.aya != 1 || ref.sura == 1 || ref.sura == 9) return text
        val prefix = "بسم الله الرحمن الرحيم"
        return if (text.startsWith(prefix)) text.removePrefix(prefix).trimStart() else text
    }

    fun search(input: String, limit: Int = 300): SearchResult {
        val q = input.trim()
        val reference = references.parse(q)
        if (q.length < 2) return SearchResult(q, reference, emptyList(), 0, ArabicText.hasArabic(q))
        val hits = ArrayList<SearchHit>()
        var total = 0
        if (ArabicText.hasArabic(q)) {
            val f = ArabicText.fold(q)
            if (f.length >= 2) for ((g, pair) in folded.withIndex()) {
                val (text, map) = pair
                var i = text.indexOf(f)
                if (i < 0) continue
                val start = map[i]
                val end = map[i + f.length - 1]
                var n = 0
                while (i >= 0) { n++; i = text.indexOf(f, i + f.length) }
                total++
                if (hits.size < limit) hits += SearchHit(g, false, n, start..end)
            }
        } else if (english != null) {
            val f = q.lowercase()
            for ((g, text) in english.withIndex()) {
                var i = text.indexOf(f)
                if (i < 0) continue
                val first = i until i + f.length
                var n = 0
                while (i >= 0) { n++; i = text.indexOf(f, i + f.length) }
                total++
                if (hits.size < limit) hits += SearchHit(g, true, n, first)
            }
        }
        return SearchResult(q, reference, hits, total, ArabicText.hasArabic(q))
    }
}
