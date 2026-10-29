package com.sakina.app.data

import com.sakina.app.core.quran.Hifz
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class BackupTest {
    @Test fun roundTrip() {
        val s = AppSettings(heroArt = false, lockNote = true)
        val q = QuranState(hifz = Hifz.add(emptyList(), 112, 1, 4, 100), bookmarks = listOf(Bookmark(2, 255, 1L)))
        val t = ToolsState(prayed = mapOf("2026-10-29" to 31), tasbihDays = mapOf("2026-10-29" to 33))
        val text = Backup.encode(s, q, t, Instant.parse("2026-10-29T20:00:00Z"))
        val r = Backup.decode(text)
        assertTrue(r is Backup.Read.Ok)
        val c = (r as Backup.Read.Ok).contents
        assertEquals(s, c.settings)
        assertEquals(q, c.quran)
        assertEquals(t, c.tools)
        assertEquals("2026-10-29T20:00:00Z", c.exportedAt)
    }

    @Test fun refusesOtherFiles() {
        assertEquals(Backup.Read.Bad(Backup.Reason.NOT_JSON), Backup.decode("not json"))
        assertEquals(Backup.Read.Bad(Backup.Reason.NOT_SAKINA), Backup.decode("""{"format":"other"}"""))
        assertEquals(Backup.Read.Bad(Backup.Reason.NEWER_VERSION), Backup.decode("""{"format":"sakina-backup","version":99}"""))
    }
}
