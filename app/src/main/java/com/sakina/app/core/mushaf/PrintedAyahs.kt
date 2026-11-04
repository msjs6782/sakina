package com.sakina.app.core.mushaf

import com.sakina.app.core.quran.AyahRef
import kotlin.math.abs

/**
 * Where each ayah is on the printed pages of the 1441H Madinah Mushaf (assets/mushaf/ayahs.tsv, written by
 * tools/mushaf_ayahs.py from the PDFs). Positions are PDF points on the page. The edition breaks a few pages
 * differently from Tanzil's page index, so these page starts are the ones the app uses.
 */
class PrintedPage(
    val page: Int,
    val first: AyahRef,
    /** Ayahs that end on the page (pages always start with an ayah). */
    val count: Int,
    /** The 15 lines: T text, H surah header, B basmala; null for the first two pages (their own ornate frame). */
    val kinds: String?,
    val lines: List<Line>,
    val markers: List<Marker>,
) {
    class Line(val x0: Float, val x1: Float, val cy: Float)
    class Marker(val line: Int, val x0: Float, val x1: Float)

    /** One line's part of an ayah: [ayah] counts from the page's first ayah. */
    data class Segment(val ayah: Int, val line: Int, val x0: Float, val x1: Float)

    val interactive: Boolean get() = kinds != null && kinds.length == 15 && lines.size == 15 && markers.size == count

    /** Distance between line centres. */
    val pitch: Float by lazy {
        if (lines.size < 2) 27f else (lines.last().cy - lines.first().cy) / (lines.size - 1)
    }

    fun lineTop(j: Int) = lines[j].cy - pitch / 2
    fun lineBottom(j: Int) = lines[j].cy + pitch / 2

    /**
     * The ayahs' parts, line by line, right to left: each marker ends an ayah, and the next one starts just past it
     * (headers and basmala lines hold none).
     */
    val segments: List<Segment> by lazy {
        if (!interactive) return@lazy emptyList()
        val out = ArrayList<Segment>()
        var k = 0
        val byLine = markers.groupBy { it.line }
        for (j in 0 until 15) {
            if (kinds!![j] != 'T') continue
            val ln = lines[j]
            var pos = ln.x1
            for (m in byLine[j].orEmpty().sortedByDescending { it.x1 }) {
                out += Segment(k, j, m.x0, pos)
                k++
                pos = m.x0
            }
            if (k < count && pos - ln.x0 > pitch * 0.3f) out += Segment(k, j, ln.x0, pos)
        }
        out
    }

    /** The ayah (counted from the page's first) at a point of the page, if there is text there. */
    fun ayahAt(x: Float, y: Float): Int? {
        if (!interactive) return null
        val j = (0 until 15).filter { kinds!![it] == 'T' }.minByOrNull { abs(lines[it].cy - y) } ?: return null
        if (abs(lines[j].cy - y) > pitch * 0.6f) return null
        val onLine = segments.filter { it.line == j }
        return (onLine.firstOrNull { x >= it.x0 && x <= it.x1 } ?: onLine.minByOrNull { minOf(abs(x - it.x0), abs(x - it.x1)) })?.ayah
    }

    fun segmentsOf(ayah: Int): List<Segment> = segments.filter { it.ayah == ayah }
}

class PrintedLayout(val pages: List<PrintedPage>) {
    fun page(p: Int): PrintedPage = pages[p - 1]

    /** First ayah of each page, for the app's page index. */
    val pageStarts: List<AyahRef> get() = pages.map { it.first }

    companion object {
        fun parse(text: String): PrintedLayout {
            val pages = ArrayList<PrintedPage>()
            for (raw in text.lineSequence()) {
                val line = raw.trimEnd('\r')
                if (line.isBlank() || line.startsWith("#")) continue
                val f = line.split('\t')
                val page = f[0].toInt()
                val (s, a) = f[1].split(':').map { it.toInt() }
                val count = f[2].toInt()
                if (f.size < 7 || f[3] == "-") {
                    pages += PrintedPage(page, AyahRef(s, a), count, null, emptyList(), emptyList())
                    continue
                }
                val lines = f[5].split(';').map { l -> l.split(',').map { it.toFloat() }.let { PrintedPage.Line(it[0], it[1], it[2]) } }
                val markers = f[6].split(' ').filter { it.isNotBlank() }.map { m ->
                    m.split(':').let { PrintedPage.Marker(it[0].toInt(), it[1].toFloat(), it[2].toFloat()) }
                }
                pages += PrintedPage(page, AyahRef(s, a), count, f[3], lines, markers)
            }
            require(pages.size == MushafManifest.PAGES && pages.withIndex().all { (i, p) -> p.page == i + 1 }) { "expected ${MushafManifest.PAGES} pages in order" }
            return PrintedLayout(pages)
        }
    }
}
