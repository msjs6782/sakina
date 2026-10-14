package com.sakina.app.core.qibla

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Polygon rings or polylines in degrees: each ring is lon0, lat0, lon1, lat1, … */
class GeoRings(val rings: List<FloatArray>) {
    val pointCount: Int get() = rings.sumOf { it.size / 2 }

    companion object {
        /** Reads the "SKG1" files written by tools/qibla/ne_pack.py. */
        fun parse(bytes: ByteArray): GeoRings {
            val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            require(bytes.size >= 8 && String(bytes, 0, 4, Charsets.US_ASCII) == "SKG1") { "Not a SKG1 file" }
            b.position(4)
            val count = b.int
            require(count in 0..1_000_000) { "Bad ring count $count" }
            val out = ArrayList<FloatArray>(count)
            repeat(count) {
                val n = b.int
                require(n in 0..10_000_000 && b.remaining() >= n * 4) { "Bad ring size $n" }
                val ring = FloatArray(n * 2)
                for (i in 0 until n) {
                    ring[2 * i] = b.short * (180f / 32767f)
                    ring[2 * i + 1] = b.short * (90f / 32767f)
                }
                out += ring
            }
            require(!b.hasRemaining()) { "Trailing bytes" }
            return GeoRings(out)
        }
    }
}
