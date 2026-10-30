package com.sakina.app.core.format

import java.time.Duration
import java.time.Instant

/**
 * How far a time is from now, in whole minutes, for the "in 2 h 5 min" / "3 h ago" chips. A coming time never
 * shows as zero (under a minute left reads as one minute, like the countdown's last minute); a time that has just
 * passed reads as "now" for its first minute.
 */
data class Relative(val future: Boolean, val minutes: Long) {
    val isNow: Boolean get() = !future && minutes == 0L
    val duration: Duration get() = Duration.ofMinutes(minutes)

    companion object {
        fun of(now: Instant, at: Instant): Relative {
            val d = Duration.between(now, at)
            return if (d.isNegative || d.isZero) Relative(false, d.negated().toMinutes())
            else Relative(true, maxOf(1L, d.toMinutes()))
        }
    }
}
