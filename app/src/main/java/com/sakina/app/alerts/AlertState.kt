package com.sakina.app.alerts

import android.content.Context
import java.time.Duration
import java.time.Instant

/**
 * Small scheduler state kept in SharedPreferences so alarm receivers can read and write it synchronously:
 * which events already fired (to prevent repeats), the alarm that is set, quiet mode and a short log.
 * Writes use commit() because a receiver's process may end right after it returns.
 */
class AlertState(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("alert_state", Context.MODE_PRIVATE)

    data class Scheduled(val ids: List<String>, val at: Instant, val exact: Boolean, val alarmClock: Boolean, val setAt: Instant)

    data class LogEntry(val at: Instant, val eventId: String, val outcome: String)

    @Synchronized
    fun fired(): Set<String> = firedWithTimes().keys

    private fun firedWithTimes(): Map<String, Long> =
        (prefs.getStringSet(KEY_FIRED, emptySet()) ?: emptySet()).mapNotNull { e ->
            val i = e.lastIndexOf('|')
            if (i <= 0) null else e.substring(0, i) to (e.substring(i + 1).toLongOrNull() ?: 0L)
        }.toMap()

    /** Records events as handled (fired, skipped or seen). Entries older than three days are dropped. */
    @Synchronized
    fun markHandled(events: Collection<Pair<String, Instant>>) {
        if (events.isEmpty()) return
        val cutoff = Instant.now().minus(Duration.ofDays(3)).epochSecond
        val map = firedWithTimes().filterValues { it >= cutoff }.toMutableMap()
        events.forEach { (id, at) -> map[id] = at.epochSecond }
        prefs.edit().putStringSet(KEY_FIRED, map.map { "${it.key}|${it.value}" }.toSet()).commit()
    }

    var quietUntil: Instant?
        @Synchronized get() = prefs.getLong(KEY_QUIET, 0L).takeIf { it > 0 }?.let { Instant.ofEpochMilli(it) }?.takeIf { it.isAfter(Instant.now()) }
        @Synchronized set(value) {
            prefs.edit().putLong(KEY_QUIET, value?.toEpochMilli() ?: 0L).commit()
        }

    @Synchronized
    fun saveScheduled(s: Scheduled?) {
        val e = prefs.edit()
        if (s == null) e.remove(KEY_SCHED) else e.putString(KEY_SCHED, listOf(s.ids.joinToString(","), s.at.toEpochMilli(), s.exact, s.alarmClock, s.setAt.toEpochMilli()).joinToString(";"))
        e.commit()
    }

    @Synchronized
    fun scheduled(): Scheduled? {
        val f = prefs.getString(KEY_SCHED, null)?.split(';') ?: return null
        if (f.size < 5) return null
        return runCatching {
            Scheduled(f[0].split(',').filter { it.isNotBlank() }, Instant.ofEpochMilli(f[1].toLong()), f[2].toBoolean(), f[3].toBoolean(), Instant.ofEpochMilli(f[4].toLong()))
        }.getOrNull()
    }

    @Synchronized
    fun log(eventId: String, outcome: String, at: Instant = Instant.now()) {
        val lines = (prefs.getString(KEY_LOG, "") ?: "").lines().filter { it.isNotBlank() }
        val updated = (listOf("${at.toEpochMilli()}|$eventId|$outcome") + lines).take(MAX_LOG)
        prefs.edit().putString(KEY_LOG, updated.joinToString("\n")).commit()
    }

    @Synchronized
    fun recentLog(): List<LogEntry> = (prefs.getString(KEY_LOG, "") ?: "").lines().mapNotNull { l ->
        val f = l.split('|')
        if (f.size < 3) null else runCatching { LogEntry(Instant.ofEpochMilli(f[0].toLong()), f[1], f[2]) }.getOrNull()
    }

    /** Debug and tests only: forget everything. */
    @Synchronized
    fun reset() {
        prefs.edit().clear().commit()
    }

    companion object {
        private const val KEY_FIRED = "fired"
        private const val KEY_QUIET = "quiet_until"
        private const val KEY_SCHED = "scheduled"
        private const val KEY_LOG = "log"
        private const val MAX_LOG = 40
    }
}
