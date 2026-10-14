package com.sakina.app.core.mushaf

import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.SequenceInputStream
import java.security.MessageDigest
import java.util.zip.CRC32
import java.util.zip.Inflater
import java.util.zip.InflaterInputStream

/** The answer to a byte-range request. */
class RangeReply(
    val code: Int,
    /** The Content-Range header, e.g. "bytes 476546-1188696/466430736". */
    val contentRange: String?,
    val finalUrl: String,
    val body: InputStream,
) : Closeable {
    override fun close() = body.close()
}

/** Network access, kept behind an interface so the fetch and checks can be tested without a network. */
fun interface RangeSource {
    @Throws(IOException::class)
    fun open(url: String, from: Long, to: Long): RangeReply
}

enum class FetchFailure {
    /** No connection, a timeout or a dropped transfer. */
    NETWORK,
    /** The server ignored the byte range (it would have sent the whole zip). */
    NO_RANGE,
    /** An HTTP error or a range other than the one asked for. */
    BAD_REPLY,
    SIZE,
    CRC,
    SHA256,
    /** Writing the file failed (for example, a full disk). */
    STORAGE,
    CANCELLED,
}

sealed interface FetchOutcome {
    data class Done(val bytesIn: Long, val mirror: String, val millis: Long) : FetchOutcome
    data class Failed(val failure: FetchFailure, val detail: String) : FetchOutcome
}

private class StorageException(cause: IOException) : IOException(cause)
private class CancelledException : IOException("cancelled")
private class VerifyException(val failure: FetchFailure, msg: String) : IOException(msg)

fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

/**
 * Fetches one page from the zip with a byte-range request, inflates it, and checks its size,
 * CRC-32 and SHA-256 before moving it into place. Nothing unverified is ever left at [target]:
 * the bytes go to a ".part" file that is deleted on any failure.
 */
class PageFetcher(private val source: RangeSource) {

    fun fetch(
        entry: MushafEntry,
        mirrors: List<String>,
        target: File,
        cancelled: () -> Boolean = { false },
        progress: (inBytes: Long) -> Unit = {},
    ): FetchOutcome {
        var last: FetchOutcome.Failed = FetchOutcome.Failed(FetchFailure.NETWORK, "no mirror")
        for (mirror in mirrors) {
            val r = fetchFrom(mirror, entry, target, cancelled, progress)
            if (r is FetchOutcome.Done) return r
            last = r as FetchOutcome.Failed
            // Another mirror cannot help with a full disk or a cancel.
            if (r.failure == FetchFailure.STORAGE || r.failure == FetchFailure.CANCELLED) return r
        }
        return last
    }

    private fun fetchFrom(mirror: String, e: MushafEntry, target: File, cancelled: () -> Boolean, progress: (Long) -> Unit): FetchOutcome {
        val part = File(target.path + ".part")
        val t0 = System.nanoTime()
        try {
            if (cancelled()) throw CancelledException()
            target.parentFile?.mkdirs()
            source.open(mirror, e.rangeFrom, e.rangeTo).use { reply ->
                if (reply.code == 200) return FetchOutcome.Failed(FetchFailure.NO_RANGE, "HTTP 200 without a range from ${reply.finalUrl}")
                if (reply.code != 206) return FetchOutcome.Failed(FetchFailure.BAD_REPLY, "HTTP ${reply.code} from ${reply.finalUrl}")
                val expected = "bytes ${e.rangeFrom}-${e.rangeTo}/"
                if (reply.contentRange?.startsWith(expected) != true) {
                    return FetchOutcome.Failed(FetchFailure.BAD_REPLY, "Content-Range '${reply.contentRange}' instead of '$expected…'")
                }
                val counted = CountingInputStream(reply.body, e.compressed, cancelled, progress)
                val data: InputStream = when (e.method) {
                    // "nowrap" inflation needs one extra byte at the end of the input.
                    8 -> InflaterInputStream(SequenceInputStream(counted, ByteArrayInputStream(byteArrayOf(0))), Inflater(true), 64 * 1024)
                    0 -> counted
                    else -> return FetchOutcome.Failed(FetchFailure.BAD_REPLY, "unsupported zip method ${e.method}")
                }
                val sha = MessageDigest.getInstance("SHA-256")
                val crc = CRC32()
                var size = 0L
                val out: OutputStream = try {
                    FileOutputStream(part)
                } catch (io: IOException) {
                    throw StorageException(io)
                }
                out.use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = data.read(buf)
                        if (n < 0) break
                        if (cancelled()) throw CancelledException()
                        size += n
                        if (size > e.size) throw VerifyException(FetchFailure.SIZE, "more than ${e.size} bytes")
                        sha.update(buf, 0, n)
                        crc.update(buf, 0, n)
                        try {
                            o.write(buf, 0, n)
                        } catch (io: IOException) {
                            throw StorageException(io)
                        }
                    }
                    try {
                        o.flush()
                        (o as FileOutputStream).fd.sync()
                    } catch (io: IOException) {
                        throw StorageException(io)
                    }
                }
                if (size != e.size) throw VerifyException(FetchFailure.SIZE, "got $size bytes, expected ${e.size}")
                if (crc.value != e.crc32) throw VerifyException(FetchFailure.CRC, "CRC %08x, expected %08x".format(crc.value, e.crc32))
                val digest = sha.digest().hex()
                if (digest != e.sha256) throw VerifyException(FetchFailure.SHA256, "SHA-256 $digest, expected ${e.sha256}")
                if (target.exists() && !target.delete()) throw StorageException(IOException("cannot replace $target"))
                if (!part.renameTo(target)) throw StorageException(IOException("cannot move ${part.name} into place"))
                return FetchOutcome.Done(counted.count, reply.finalUrl, (System.nanoTime() - t0) / 1_000_000)
            }
        } catch (x: CancelledException) {
            return FetchOutcome.Failed(FetchFailure.CANCELLED, "cancelled")
        } catch (x: VerifyException) {
            return FetchOutcome.Failed(x.failure, x.message ?: "")
        } catch (x: StorageException) {
            return FetchOutcome.Failed(FetchFailure.STORAGE, x.cause?.message ?: "storage")
        } catch (x: java.util.zip.ZipException) {
            return FetchOutcome.Failed(FetchFailure.CRC, "corrupt data: ${x.message}")
        } catch (x: IOException) {
            return FetchOutcome.Failed(FetchFailure.NETWORK, x.toString())
        } finally {
            if (part.exists()) part.delete()
        }
    }
}

/** Counts bytes read from the network, stops at the expected length, and checks for cancellation. */
private class CountingInputStream(
    private val inner: InputStream,
    private val limit: Long,
    private val cancelled: () -> Boolean,
    private val progress: (Long) -> Unit,
) : InputStream() {
    var count = 0L
        private set

    override fun read(): Int {
        val b = ByteArray(1)
        return if (read(b, 0, 1) < 0) -1 else b[0].toInt() and 0xff
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (cancelled()) throw CancelledException()
        val left = limit - count
        if (left <= 0) return -1
        val n = inner.read(b, off, minOf(len.toLong(), left).toInt())
        if (n > 0) {
            count += n
            progress(count)
        }
        return n
    }

    override fun close() = inner.close()
}

enum class PageCheck { OK, MISSING, SIZE, SHA256 }

object PageVerifier {
    /** Full check: size and SHA-256. */
    fun check(file: File, e: MushafEntry): PageCheck {
        if (!file.isFile) return PageCheck.MISSING
        if (file.length() != e.size) return PageCheck.SIZE
        val sha = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                sha.update(buf, 0, n)
            }
        }
        return if (sha.digest().hex() == e.sha256) PageCheck.OK else PageCheck.SHA256
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).hex()
}
