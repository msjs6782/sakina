package com.sakina.app.core.mushaf

/**
 * One page of the printed Mushaf: a single-page PDF stored inside the source zip.
 * [dataOffset] and [compressed] locate its deflated bytes in the zip, so the page can be fetched
 * on its own with an HTTP Range request.
 */
data class MushafEntry(
    val page: Int,
    val size: Long,
    val sha256: String,
    val crc32: Long,
    val dataOffset: Long,
    val compressed: Long,
    /** Zip compression method: 8 = deflate, 0 = stored. */
    val method: Int,
    val entryName: String,
) {
    val rangeFrom: Long get() = dataOffset
    val rangeTo: Long get() = dataOffset + compressed - 1
    val rangeHeader: String get() = "bytes=$rangeFrom-$rangeTo"
}

/** The page list of the King Fahd Complex digital Mushaf (Madinah, Hafs, 1441H), with fingerprints. */
class MushafManifest(
    val zipSize: Long,
    val zipMd5: String,
    val zipSha256: String,
    val mirrors: List<String>,
    val entries: List<MushafEntry>,
) {
    init {
        require(entries.size == PAGES) { "expected $PAGES pages, got ${entries.size}" }
        entries.forEachIndexed { i, e -> require(e.page == i + 1) { "page order broken at ${i + 1}" } }
        require(mirrors.isNotEmpty()) { "no mirror" }
    }

    fun entry(page: Int): MushafEntry = entries[page - 1]

    /** Size of all pages once stored (the uncompressed PDFs). */
    val totalSize: Long get() = entries.sumOf { it.size }

    /** Bytes transferred to fetch all pages (deflated in the zip). */
    val totalCompressed: Long get() = entries.sumOf { it.compressed }

    fun sizeOf(pages: Collection<Int>): Long = pages.sumOf { entry(it).size }
    fun compressedOf(pages: Collection<Int>): Long = pages.sumOf { entry(it).compressed }

    companion object {
        const val PAGES = 604

        /** Pages shipped inside the app, readable without internet. */
        val BUNDLED: Set<Int> = setOf(1, 2)

        fun parse(text: String): MushafManifest {
            var zip: List<String>? = null
            val mirrors = mutableListOf<String>()
            val entries = mutableListOf<MushafEntry>()
            for (raw in text.lineSequence()) {
                val line = raw.trimEnd('\r')
                if (line.isBlank() || line.startsWith("#")) continue
                val f = line.split('\t')
                when (f[0]) {
                    "zip" -> zip = f
                    "mirror" -> mirrors += f[1]
                    else -> entries += MushafEntry(
                        page = f[0].toInt(),
                        size = f[1].toLong(),
                        sha256 = f[2].lowercase(),
                        crc32 = f[3].toLong(16),
                        dataOffset = f[4].toLong(),
                        compressed = f[5].toLong(),
                        method = f[6].toInt(),
                        entryName = f.getOrElse(7) { "" },
                    )
                }
            }
            val z = requireNotNull(zip) { "missing zip line" }
            return MushafManifest(z[1].toLong(), z[2], z[3], mirrors, entries)
        }
    }
}
