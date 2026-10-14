package com.sakina.app.core

import com.sakina.app.core.mushaf.FetchFailure
import com.sakina.app.core.mushaf.FetchOutcome
import com.sakina.app.core.mushaf.MushafEntry
import com.sakina.app.core.mushaf.MushafManifest
import com.sakina.app.core.mushaf.PageCheck
import com.sakina.app.core.mushaf.PageFetcher
import com.sakina.app.core.mushaf.PageVerifier
import com.sakina.app.core.mushaf.RangeReply
import com.sakina.app.core.mushaf.RangeSource
import com.sakina.app.core.quran.AyahRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random

class MushafTest {
    private val manifest by lazy { MushafManifest.parse(File("src/main/assets/mushaf/pages.tsv").readText()) }

    @Test
    fun manifestCoversAllPagesOfTheSourceZip() {
        assertEquals(604, manifest.entries.size)
        // The 604 page files of 1441-AI-hafs.zip add up to 733.36 MiB.
        assertEquals(768_981_578L, manifest.totalSize)
        assertEquals("733.36", "%.2f".format(java.util.Locale.US, manifest.totalSize / 1048576.0))
        assertEquals(466_430_736L, manifest.zipSize)
        assertEquals(2, manifest.mirrors.size)
        assertTrue(manifest.mirrors.all { it.startsWith("https://archive.org/download/") && it.endsWith("/1441-AI-hafs.zip") })
        // Entries lie inside the zip, in order, without overlapping.
        var end = 0L
        for (e in manifest.entries) {
            assertTrue("page ${e.page}", e.dataOffset > end)
            assertEquals(8, e.method)
            assertEquals("%03d___Hafs39__DM.ai".format(e.page), e.entryName)
            assertEquals(64, e.sha256.length)
            end = e.dataOffset + e.compressed
        }
        assertTrue(end <= manifest.zipSize)
        assertEquals("bytes=476546-1188696", manifest.entry(3).rangeHeader)
    }

    @Test
    fun bundledPagesAreTheExactSourceFiles() {
        for (p in MushafManifest.BUNDLED) {
            val f = File("src/main/assets/mushaf/%03d.pdf".format(p))
            assertEquals(PageCheck.OK, PageVerifier.check(f, manifest.entry(p)))
            assertEquals("%PDF-1.5", f.readBytes().copyOf(8).decodeToString())
        }
    }

    @Test
    fun editionPagesMatchTheTanzilPageIndex() {
        // First ayah of pages checked by eye against renders of the 1441H PDFs.
        val meta = QuranTest.meta
        val firsts = mapOf(1 to AyahRef(1, 1), 2 to AyahRef(2, 1), 3 to AyahRef(2, 6), 50 to AyahRef(3, 1), 187 to AyahRef(9, 1),
            300 to AyahRef(18, 54), 582 to AyahRef(78, 1), 603 to AyahRef(109, 1), 604 to AyahRef(112, 1))
        for ((page, ref) in firsts) assertEquals("page $page", ref, meta.ref(meta.pageRange(page).first))
        assertEquals(AyahRef(2, 16), meta.ref(meta.pageRange(3).last))
        assertEquals(AyahRef(18, 61), meta.ref(meta.pageRange(300).last))
        assertEquals(604, meta.pageCount)
    }

    @Test
    fun sizesAreShownInBinaryUnits() {
        val en = com.sakina.app.core.format.Fmt(com.sakina.app.core.format.Lang.EN, clock24 = false, arabicDigits = false)
        val ar = com.sakina.app.core.format.Fmt(com.sakina.app.core.format.Lang.AR, clock24 = false, arabicDigits = true)
        assertEquals("733.36 MB", en.bytes(manifest.totalSize, 2))
        assertEquals("\u0667\u0663\u0663\u066b\u0664 ميغابايت", ar.bytes(manifest.totalSize))
        assertEquals("385 KB", en.bytes(393_966))
        assertEquals("1.2 GB", en.bytes(1_288_490_189))
    }

    // ---- fetching from a zip with byte ranges ----

    private class TestZip(val bytes: ByteArray, val entries: List<MushafEntry>, val contents: List<ByteArray>)

    /** Builds a real zip and reads its local headers, as the manifest generator did for the source zip. */
    private fun testZip(): TestZip {
        val contents = listOf(Random(1).nextBytes(150_000), ("%PDF-1.5 " + "page two ".repeat(20_000)).toByteArray())
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            contents.forEachIndexed { i, c ->
                z.putNextEntry(ZipEntry("%03d___Hafs39__DM.ai".format(i + 1)))
                z.write(c)
                z.closeEntry()
            }
        }
        val bytes = bos.toByteArray()
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val entries = mutableListOf<MushafEntry>()
        var pos = 0
        contents.forEachIndexed { i, c ->
            assertEquals(0x04034b50, bb.getInt(pos))
            val nameLen = bb.getShort(pos + 26).toInt()
            val extraLen = bb.getShort(pos + 28).toInt()
            val dataOff = pos + 30 + nameLen + extraLen
            // With a data descriptor the local header has no sizes: find the next header instead.
            var next = dataOff
            while (next + 4 <= bytes.size) {
                val sig = bb.getInt(next)
                if (sig == 0x08074b50 || sig == 0x04034b50 || sig == 0x02014b50) break
                next++
            }
            val compressed = (next - dataOff).toLong()
            val crc = CRC32().also { it.update(c) }.value
            entries += MushafEntry(i + 1, c.size.toLong(), PageVerifier.sha256(c), crc, dataOff.toLong(), compressed, 8, "")
            pos = if (bb.getInt(next) == 0x08074b50) next + 16 else next
        }
        return TestZip(bytes, entries, contents)
    }

    private class FakeServer(val zip: ByteArray, var code: Int = 206, var corruptAt: Int = -1, var truncate: Int = 0, var failFirst: Int = 0) : RangeSource {
        val calls = mutableListOf<String>()
        override fun open(url: String, from: Long, to: Long): RangeReply {
            calls += url
            if (failFirst > 0) { failFirst--; throw IOException("connection reset") }
            val slice = zip.copyOfRange(from.toInt(), (to + 1).toInt() - truncate)
            if (corruptAt >= 0) slice[corruptAt] = (slice[corruptAt].toInt() xor 0x5a).toByte()
            val range = if (code == 206) "bytes $from-$to/${zip.size}" else null
            return RangeReply(code, range, url, ByteArrayInputStream(if (code == 200) zip else slice))
        }
    }

    private fun tmpDir(): File = Files.createTempDirectory("mushaf").toFile()

    @Test
    fun fetchedPageIsInflatedVerifiedAndMovedIntoPlace() {
        val z = testZip()
        val dir = tmpDir()
        for ((i, e) in z.entries.withIndex()) {
            val target = File(dir, "%03d.pdf".format(e.page))
            var seen = 0L
            val r = PageFetcher(FakeServer(z.bytes)).fetch(e, listOf("https://a/zip"), target, progress = { seen = it })
            assertTrue(r.toString(), r is FetchOutcome.Done)
            assertTrue(z.contents[i].contentEquals(target.readBytes()))
            assertEquals(e.compressed, seen)
            assertEquals(PageCheck.OK, PageVerifier.check(target, e))
            assertFalse(File(target.path + ".part").exists())
        }
    }

    @Test
    fun corruptOrWrongRepliesNeverLeaveAFile() {
        val z = testZip()
        val e = z.entries[0]
        val dir = tmpDir()
        val target = File(dir, "001.pdf")
        fun run(server: FakeServer, mirrors: List<String> = listOf("https://a/zip")) = PageFetcher(server).fetch(e, mirrors, target)

        val corrupt = run(FakeServer(z.bytes, corruptAt = 5000)) as FetchOutcome.Failed
        assertTrue(corrupt.failure in setOf(FetchFailure.CRC, FetchFailure.SHA256, FetchFailure.SIZE))
        assertEquals(FetchFailure.NO_RANGE, (run(FakeServer(z.bytes, code = 200)) as FetchOutcome.Failed).failure)
        assertEquals(FetchFailure.BAD_REPLY, (run(FakeServer(z.bytes, code = 503)) as FetchOutcome.Failed).failure)
        assertEquals(FetchFailure.NETWORK, (run(FakeServer(z.bytes, truncate = 1000)) as FetchOutcome.Failed).failure)
        assertEquals(FetchFailure.CANCELLED, (PageFetcher(FakeServer(z.bytes)).fetch(e, listOf("https://a/zip"), target, cancelled = { true }) as FetchOutcome.Failed).failure)
        assertFalse(target.exists())
        assertTrue(dir.listFiles()!!.isEmpty())
    }

    @Test
    fun aFailingMirrorFallsBackToTheNextOne() {
        val z = testZip()
        val server = FakeServer(z.bytes, failFirst = 1)
        val target = File(tmpDir(), "002.pdf")
        val r = PageFetcher(server).fetch(z.entries[1], listOf("https://one/zip", "https://two/zip"), target)
        assertTrue(r is FetchOutcome.Done)
        assertEquals(listOf("https://one/zip", "https://two/zip"), server.calls)
        assertEquals("https://two/zip", (r as FetchOutcome.Done).mirror)
    }

    @Test
    fun verifierRejectsChangedFiles() {
        val z = testZip()
        val f = File(tmpDir(), "001.pdf")
        assertEquals(PageCheck.MISSING, PageVerifier.check(f, z.entries[0]))
        f.writeBytes(z.contents[0])
        assertEquals(PageCheck.OK, PageVerifier.check(f, z.entries[0]))
        f.writeBytes(z.contents[0].copyOf().also { it[10] = (it[10] + 1).toByte() })
        assertEquals(PageCheck.SHA256, PageVerifier.check(f, z.entries[0]))
        f.writeBytes(z.contents[0].copyOf(100))
        assertEquals(PageCheck.SIZE, PageVerifier.check(f, z.entries[0]))
    }
}
