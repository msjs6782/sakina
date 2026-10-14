package com.sakina.app.core.quran

/**
 * Reads Tanzil's files as published (they are shipped unchanged). Line-oriented parsing keeps this independent of
 * Android's XML classes so it can be unit-tested on the JVM.
 */
object TanzilParser {
    private val suraRe = Regex("""<sura\s+index="(\d+)"""")
    private val ayaRe = Regex("""<aya\s+index="(\d+)"\s+text="([^"]*)"(?:\s+bismillah="([^"]*)")?\s*/>""")
    private val attr = Regex("""(\w+)="([^"]*)"""")

    private fun unescape(s: String) =
        if ('&' !in s) s else s.replace("&quot;", "\"").replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")

    /** The copyright and terms block at the top of a Tanzil file ("#" lines), without the comment markers. */
    fun header(text: String): List<String> = text.lineSequence()
        .map { it.trim() }
        .dropWhile { !it.startsWith("#") }
        .takeWhile { it.startsWith("#") || it.isEmpty() }
        .filter { it.startsWith("#") }
        .map { it.removePrefix("#").trimEnd() }
        .filter { it.isNotBlank() && !it.trim().matches(Regex("[=\\-]+")) }
        .map { it.trim() }
        .toList()

    fun parseUthmaniXml(xml: String, meta: QuranMeta): QuranText {
        val ayahs = arrayOfNulls<String>(meta.totalAyahs)
        val basmala = HashMap<Int, String>()
        var sura = 0
        for (line in xml.lineSequence()) {
            suraRe.find(line)?.let { sura = it.groupValues[1].toInt() }
            val m = ayaRe.find(line) ?: continue
            val aya = m.groupValues[1].toInt()
            ayahs[meta.global(AyahRef(sura, aya))] = unescape(m.groupValues[2])
            val b = m.groupValues[3]
            if (b.isNotEmpty()) basmala[sura] = unescape(b)
        }
        val missing = ayahs.indexOfFirst { it == null }
        require(missing < 0) { "Missing ayah ${meta.ref(missing)}" }
        return QuranText(ayahs.map { it!! }, basmala)
    }

    /** Tanzil "txt-2" format: `sura|aya|text` lines after a `#` header. */
    fun parseTxt2(id: String, text: String, meta: QuranMeta): AyahTexts {
        val lines = arrayOfNulls<String>(meta.totalAyahs)
        for (line in text.lineSequence()) {
            if (line.isEmpty() || line[0] == '#') continue
            val a = line.indexOf('|')
            val b = line.indexOf('|', a + 1)
            if (a <= 0 || b <= a) continue
            val sura = line.substring(0, a).toIntOrNull() ?: continue
            val aya = line.substring(a + 1, b).toIntOrNull() ?: continue
            lines[meta.global(AyahRef(sura, aya))] = line.substring(b + 1)
        }
        val missing = lines.indexOfFirst { it == null }
        require(missing < 0) { "$id is missing ${meta.ref(missing)}" }
        return AyahTexts(id, header(text), lines.map { it!! })
    }

    fun parseMeta(xml: String): QuranMeta {
        val suras = ArrayList<Surah>()
        val juz = ArrayList<AyahRef>()
        val quarters = ArrayList<AyahRef>()
        val pages = ArrayList<AyahRef>()
        val sajdas = ArrayList<Sajda>()
        for (raw in xml.lineSequence()) {
            val line = raw.trim()
            if (!line.startsWith("<") || line.startsWith("</")) continue
            val tag = line.substring(1).takeWhile { it.isLetter() }
            val a = attr.findAll(line).associate { it.groupValues[1] to unescape(it.groupValues[2]) }
            fun ref() = AyahRef(a.getValue("sura").toInt(), a.getValue("aya").toInt())
            when (tag) {
                "sura" -> if ("ayas" in a) suras += Surah(
                    index = a.getValue("index").toInt(),
                    ayahCount = a.getValue("ayas").toInt(),
                    start = a.getValue("start").toInt(),
                    nameAr = a.getValue("name"),
                    nameTranslit = a.getValue("tname"),
                    nameEn = a.getValue("ename"),
                    revelation = if (a["type"] == "Medinan") Revelation.MEDINAN else Revelation.MECCAN,
                    order = a.getValue("order").toInt(),
                )
                "juz" -> if ("sura" in a) juz += ref()
                "quarter" -> if ("sura" in a) quarters += ref()
                "page" -> if ("sura" in a) pages += ref()
                "sajda" -> if ("sura" in a) sajdas += Sajda(
                    a.getValue("index").toInt(), ref(),
                    if (a["type"] == "obligatory") SajdaType.OBLIGATORY else SajdaType.RECOMMENDED,
                )
            }
        }
        return QuranMeta(suras, juz, quarters, pages, sajdas)
    }
}
