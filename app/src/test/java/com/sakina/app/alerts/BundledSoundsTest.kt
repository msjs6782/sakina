package com.sakina.app.alerts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class BundledSoundsTest {
    @Test
    fun bundledFilesMatchTheirRecordedChecksums() {
        assertEquals(2, BundledSounds.list.size)
        for (b in BundledSounds.list) {
            val f = File("src/main/res/raw/adhan_${b.id}.ogg")
            assertTrue("${f.path} missing", f.isFile)
            val sha = MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }
            assertEquals(b.id, b.sha256, sha)
            assertTrue(b.sourceUrl.startsWith("https://commons.wikimedia.org/wiki/File:"))
            assertTrue(b.license.isNotBlank() && b.author.isNotBlank())
        }
    }
}
