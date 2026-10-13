package com.sakina.app.data

import android.os.SystemClock
import java.time.Instant

/**
 * The app's notion of "now". Debug builds can pin it to a chosen moment (it keeps ticking from there)
 * so emulator screenshots are reproducible; release builds always use the device clock.
 */
object AppClock {
    @Volatile private var pinned: Instant? = null
    @Volatile private var pinnedAt: Long = 0L
    @Volatile private var frozen: Boolean = false

    fun now(): Instant {
        val p = pinned ?: return Instant.now()
        return if (frozen) p else p.plusMillis(SystemClock.elapsedRealtime() - pinnedAt)
    }

    /** [freeze] stops the clock entirely (used so UI-automation dumps see an idle screen). */
    fun pin(instant: Instant, freeze: Boolean = false) {
        pinned = instant
        pinnedAt = SystemClock.elapsedRealtime()
        frozen = freeze
    }
}
