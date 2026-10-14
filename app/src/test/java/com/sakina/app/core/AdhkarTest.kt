package com.sakina.app.core

import com.sakina.app.core.adhkar.AdhkarCategory
import com.sakina.app.core.adhkar.AdhkarLibrary
import com.sakina.app.core.adhkar.Dhikr
import com.sakina.app.core.adhkar.DhikrCounter
import com.sakina.app.core.adhkar.DhikrPart
import com.sakina.app.core.adhkar.DhikrSource
import com.sakina.app.core.adhkar.Grade
import com.sakina.app.core.adhkar.Practice
import com.sakina.app.core.adhkar.QuranSpan
import com.sakina.app.core.adhkar.Tasbih
import com.sakina.app.core.adhkar.TasbihState
import com.sakina.app.core.adhkar.Wird
import com.sakina.app.core.qibla.GeoRings
import com.sakina.app.core.qibla.MapGeometry
import com.sakina.app.core.qibla.Projection
import com.sakina.app.core.qibla.Qibla
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.atan2

class AdhkarTest {
    private val src = DhikrSource("bukhari", "1", grade = Grade("صحيح", "Sahih", "", ""))
    private fun dhikr(id: String, vararg parts: DhikrPart, rounds: Int = 1, cat: String = "dhikr", title: String = id) =
        Dhikr(id, cat, title, title, rounds, parts.toList(), source = src)

    private val tasbih33 = dhikr("t33", DhikrPart("سُبْحَانَ اللَّهِ", count = 33), DhikrPart("الْحَمْدُ لِلَّهِ", count = 33),
        DhikrPart("اللَّهُ أَكْبَرُ", count = 33), DhikrPart("لاَ إِلَهَ إِلاَّ اللَّهُ", count = 1))
    private val muawwidhat = dhikr("m3", DhikrPart(quran = QuranSpan(112, 1, 4)), DhikrPart(quran = QuranSpan(113, 1, 5)),
        DhikrPart(quran = QuranSpan(114, 1, 6)), rounds = 3)
    private val single = dhikr("one", DhikrPart("غُفْرَانَكَ"))
    private val free = dhikr("free", DhikrPart("أَسْتَغْفِرُ اللَّهَ", count = 0))

    @Test
    fun counterWalksPartsAndRounds() {
        assertEquals(100, tasbih33.totalTaps)
        assertEquals(DhikrCounter.position(tasbih33, 0).let { Triple(it.part, it.inPart, it.done) }, Triple(0, 0, false))
        DhikrCounter.position(tasbih33, 33).let { assertEquals(1, it.part); assertEquals(0, it.inPart) }
        DhikrCounter.position(tasbih33, 98).let { assertEquals(2, it.part); assertEquals(32, it.inPart) }
        DhikrCounter.position(tasbih33, 99).let { assertEquals(3, it.part); assertFalse(it.done) }
        assertTrue(DhikrCounter.position(tasbih33, 100).done)
        assertEquals(9, muawwidhat.totalTaps)
        DhikrCounter.position(muawwidhat, 4).let { assertEquals(2, it.round); assertEquals(1, it.part) }
        assertEquals(1, free.totalTaps)
        assertTrue(free.unnumbered)
    }

    @Test
    fun wirdCountersNeverMix() {
        val morning = Wird.key("2026-10-14", Practice.MORNING, Practice.MAIN_SESSION)
        val sleep = Wird.key("2026-10-14", Practice.SLEEP, Practice.MAIN_SESSION)
        val fajr = Wird.key("2026-10-14", Practice.AFTER_PRAYER, "fajr")
        val dhuhr = Wird.key("2026-10-14", Practice.AFTER_PRAYER, "dhuhr")
        val tomorrow = Wird.key("2026-10-15", Practice.MORNING, Practice.MAIN_SESSION)
        var p = emptyMap<String, Map<String, Int>>()
        repeat(5) { p = Wird.tap(p, morning, tasbih33) }
        repeat(2) { p = Wird.tap(p, fajr, tasbih33) }
        assertEquals(5, p[morning]!!["t33"])
        assertNull(p[sleep])
        assertNull(p[dhuhr])
        assertNull(p[tomorrow])
        assertEquals(2, p[fajr]!!["t33"])
        // A dhikr stops at its total in a set; a free count goes on.
        repeat(3) { p = Wird.tap(p, morning, single) }
        assertEquals(1, p[morning]!!["one"])
        repeat(3) { p = Wird.tap(p, morning, free, free = true) }
        assertEquals(3, p[morning]!!["free"])
        p = Wird.untap(p, morning, tasbih33)
        assertEquals(4, p[morning]!!["t33"])
        assertEquals(1 to 3, Wird.summary(p, morning, listOf(tasbih33, single, muawwidhat)))
        p = Wird.resetItem(p, morning, "t33")
        assertNull(p[morning]!!["t33"])
        p = Wird.resetSession(p, fajr)
        assertNull(p[fajr])
        val old = Wird.key("2026-08-01", Practice.MORNING, Practice.MAIN_SESSION)
        p = p + (old to mapOf("one" to 1))
        val kept = Wird.prune(p, "2026-08-16")
        assertFalse(old in kept)
        assertTrue(morning in kept)
        // Untapping at zero changes nothing.
        assertSame(p, Wird.untap(p, sleep, single))
    }

    @Test
    fun tasbihUndoTakesBackTheLastRealChange() {
        var t = Tasbih()
        repeat(10) { t = t.tap() }
        t = t.setTarget(33)
        assertEquals(33, t.state.target)
        t = t.reset()
        assertEquals(0, t.state.count)
        // A reset at zero is not a change: the slot still holds the count before the real reset.
        t = t.reset()
        t = t.undo()
        assertEquals(10, t.state.count)
        assertEquals(33, t.state.target)
        assertNull(t.undo)
        assertEquals(t, t.undo())
        // Toggling vibration is a preference, not a change to undo.
        t = t.tap().setVibrate(false)
        assertEquals(10, t.undo!!.count)
        t = t.undo()
        assertEquals(10, t.state.count)
        assertFalse(t.state.vibrate)
        // Targets and rounds.
        var r = Tasbih(TasbihState(count = 32, target = 33))
        r = r.tap()
        assertTrue(r.reachedTarget())
        assertEquals(1, r.state.rounds)
        assertEquals(0, r.state.inRound)
        assertEquals(TasbihState.MAX_TARGET, Tasbih().setTarget(1_000_000).state.target)
        assertEquals(80, Tasbih().setLabel("x".repeat(200)).state.label.length)
    }

    @Test
    fun searchCoversArabicTextTitlesAndCategories() {
        val lib = AdhkarLibrary(
            categories = listOf(AdhkarCategory("sleep", "أذكار النوم", "Before sleep"), AdhkarCategory("home", "المنزل", "Home")),
            items = listOf(
                Dhikr("a", "sleep", "باسمك اللهم أموت وأحيا", "In Your name I die and live", parts = listOf(DhikrPart("بِاسْمِكَ اللَّهُمَّ أَمُوتُ وَأَحْيَا")), source = src),
                Dhikr("b", "home", "دعاء الخروج من المنزل", "Leaving home", parts = listOf(DhikrPart("بِسْمِ اللَّهِ تَوَكَّلْتُ عَلَى اللَّهِ")), source = src),
                Dhikr("c", "home", "غفرانك", "Leaving the restroom", parts = listOf(DhikrPart("غُفْرَانَكَ")), source = src),
            ),
        )
        assertEquals(listOf("a"), lib.search("أموت").map { it.id })   // vowelled text, plain query
        assertEquals(listOf("b"), lib.search("توكلت").map { it.id })
        assertEquals(listOf("a"), lib.search("SLEEP").map { it.id })  // English category
        assertEquals(listOf("a"), lib.search("النوم").map { it.id })  // Arabic category
        assertEquals(listOf("b", "c"), lib.search("leaving").map { it.id })
        // Every word must match somewhere; a title match ranks first ("c" matches "home" through its category).
        assertEquals(listOf("b", "c"), lib.search("home leaving").map { it.id })
        assertEquals(listOf("c"), lib.search("restroom home").map { it.id })
        assertTrue(lib.search("   ").isEmpty())
        assertTrue(lib.search("زكاة").isEmpty())
        val p = DhikrPart(text = "t", morning = "m", evening = "e")
        assertEquals("m", p.textFor(Practice.MORNING))
        assertEquals("e", p.textFor(Practice.EVENING))
        assertEquals("t", p.textFor(Practice.SLEEP))
        assertEquals(5, Practice("after_prayer", "", "").sessions.size)
        assertEquals(listOf("main"), Practice("morning", "", "").sessions)
    }

    private fun geo(name: String) = GeoRings.parse(File("src/main/assets/qibla/$name.bin").readBytes())

    @Test
    fun naturalEarthLayersParse() {
        val l110 = geo("land110")
        assertEquals(128, l110.rings.size)
        assertEquals(5132, l110.pointCount)
        val l50 = geo("land50")
        assertEquals(60667, l50.pointCount)
        assertEquals(19750, geo("borders50").pointCount)
        assertEquals(451, geo("lakes110").pointCount)
        for (r in l50.rings) for (i in r.indices step 2) {
            assertTrue(r[i] in -180f..180f)
            assertTrue(r[i + 1] in -90f..90f)
        }
    }

    @Test
    fun centredMapKeepsTheQiblaStraightAndFillsOnlySafeRings() {
        val lat = 24.7136; val lon = 46.6753
        val shapes = MapGeometry.centered(lat, lon, geo("land110"), polygons = true)
        assertTrue(shapes.count { it.closed } > 50)
        // Every filled polygon stays well away from the antipode.
        for (s in shapes.filter { it.closed }) for (i in s.points.indices step 2) {
            assertTrue(Math.hypot(s.points[i].toDouble(), s.points[i + 1].toDouble()) < MapGeometry.FILL_LIMIT + 1e-3)
        }
        val path = MapGeometry.centeredPath(lat, lon)
        val b = Qibla.bearing(lat, lon)
        for (i in 2 until path.size step 2) {
            assertEquals(b, Qibla.norm360(Math.toDegrees(atan2(path[i].toDouble(), path[i + 1].toDouble()))), 1e-3)
        }
        val m = Projection.azimuthal(lat, lon, Qibla.KAABA_LAT, Qibla.KAABA_LON)!!
        assertEquals(m.first, path[path.size - 2].toDouble(), 1e-6)
        // From New Zealand (near Spain's antipode) the far side breaks into outlines instead of wrong fills.
        val nz = MapGeometry.centered(-41.29, 174.78, geo("land110"), polygons = true)
        assertTrue(nz.any { !it.closed })
    }

    @Test
    fun worldPathSplitsAtTheDateLine() {
        val pieces = MapGeometry.worldPath(21.3069, -157.8583)
        assertTrue(pieces.size >= 2)
        for (p in pieces) for (i in 2 until p.size step 2) assertTrue(abs(p[i] - p[i - 2]) < 180f)
        assertEquals(1, MapGeometry.worldPath(24.7136, 46.6753).size)
    }
}
