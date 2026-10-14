package com.sakina.app.alerts

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.sakina.app.MainActivity
import com.sakina.app.SakinaApp
import com.sakina.app.core.alerts.AlertEvent
import com.sakina.app.core.alerts.AlertMode
import com.sakina.app.core.alerts.AlertPlanner
import com.sakina.app.core.alerts.Decision
import com.sakina.app.core.alerts.EventKind
import com.sakina.app.core.prayer.PrayerKey
import com.sakina.app.core.prayer.PrayerSchedule
import com.sakina.app.data.AppSettings
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Keeps exactly one alarm set: the next alert event. When it fires, the due events are handled and the next
 * alarm is set. Every change (settings, boot, time change, permission) recomputes the chain, so there are
 * no duplicates and nothing depends on alarms set long ago.
 */
object AlertScheduler {
    private const val TAG = "SakinaAlerts"
    private const val REQ_FIRE = 1001
    private const val REQ_CARD = 1002
    private const val REQ_TEST = 1003
    private const val REQ_FIRE_BACKUP = 1004

    /** Without exact-alarm access Android stretches windows to at least ten minutes. */
    private val INEXACT_WINDOW: Duration = Duration.ofMinutes(10)
    private val mutex = Mutex()

    enum class Run {
        /** The chain's alarm fired. */
        ALARM,

        /** After a reboot, a time change or an update: catch up on events of the last 30 minutes. */
        CATCH_UP,

        /** Settings changed or the app opened: past events are treated as seen, except a pending one that was missed. */
        REFRESH,
    }

    data class Context2(val s: AppSettings, val schedule: PrayerSchedule?, val zone: ZoneId)

    fun canExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    private fun build(s: AppSettings): Context2 {
        val loc = s.activeLocation?.takeIf { it.isUsable }
        val zone = loc?.zoneId ?: ZoneId.systemDefault()
        return Context2(s, loc?.let { PrayerSchedule(it.point, zone, s.calcConfig) }, zone)
    }

    suspend fun run(context: Context, mode: Run, reason: String) = mutex.withLock {
        val app = context.applicationContext as SakinaApp
        val s = app.settings.current()
        val state = app.alertState
        val now = Instant.now()
        val c = build(s)
        val plan = AlertPlanner.plan(s.alerts, c.schedule, c.zone, now)
        val notifier = Notifier(app, s)
        var fired = state.fired()
        val window = when (mode) {
            Run.ALARM, Run.CATCH_UP -> AlertPlanner.due(plan, now, catchUp = true)
            Run.REFRESH -> AlertPlanner.due(plan, now, catchUp = true)
        }
        val pending = state.scheduled()?.ids?.toSet() ?: emptySet()
        val handled = ArrayList<Pair<String, Instant>>()
        for (e in window) {
            if (e.id in fired) continue
            // On a refresh, only the alarm that was pending (and Android delivered late) is still handled.
            if (mode == Run.REFRESH && e.id !in pending && !e.at.isAfter(now)) {
                handled += e.id to e.at
                continue
            }
            val d = AlertPlanner.decide(e, now, fired, state.quietUntil)
            if (d == Decision.NOT_DUE) continue
            handled += e.id to e.at
            execute(app, notifier, s, e, d, now)
            state.log(e.id, d.name.lowercase(), now)
            Log.i(TAG, "Event ${e.id} at ${e.at}: $d")
        }
        state.markHandled(handled)
        fired = fired + handled.map { it.first }
        setNextAlarm(app, s, plan, now, fired)
        updateCard(app, s, c, now)
        Log.i(TAG, "Run $mode ($reason) handled=${handled.size}")
    }

    private fun execute(app: SakinaApp, notifier: Notifier, s: AppSettings, e: AlertEvent, d: Decision, now: Instant) {
        if (d != Decision.FIRE && d != Decision.FIRE_LATE_SILENT) return
        val late = d == Decision.FIRE_LATE_SILENT
        if (e.kind == EventKind.ADHAN && !late && (e.mode == AlertMode.ADHAN || e.mode == AlertMode.EXCERPT)) {
            val key = e.prayer ?: PrayerKey.DHUHR
            val sound = s.alerts.soundFor(key)
            val ok = AdhanService.start(
                app,
                AdhanService.Request(key, e.prayerAt, e.mode == AlertMode.EXCERPT, sound.sound, sound.volumeFraction, s.alerts.audioRoute, test = false),
            )
            // Android may refuse to start the service (for example without exact-alarm access): notify instead.
            if (!ok) notifier.showEvent(e, late = false, now = now, overrideMode = AlertMode.NOTIFY)
            return
        }
        if (!notifier.showEvent(e, late, now)) app.alertState.log(e.id, "notifications_blocked", now)
    }

    private fun pending(context: Context, req: Int, action: String, extras: Intent.() -> Unit = {}): PendingIntent =
        PendingIntent.getBroadcast(
            context, req,
            Intent(context, AlarmReceiver::class.java).setAction(action).apply(extras),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun setNextAlarm(context: Context, s: AppSettings, plan: List<AlertEvent>, now: Instant, fired: Set<String>) {
        val am = context.getSystemService(AlarmManager::class.java)
        val next = AlertPlanner.next(plan, now, fired)
        val pi = pending(context, REQ_FIRE, AlarmReceiver.ACTION_FIRE)
        val backup = pending(context, REQ_FIRE_BACKUP, AlarmReceiver.ACTION_FIRE)
        val state = (context.applicationContext as SakinaApp).alertState
        if (next == null) {
            am.cancel(pi)
            am.cancel(backup)
            state.saveScheduled(null)
            return
        }
        val ids = plan.filter { it.at == next.at && it.id !in fired }.map { it.id }
        val at = next.at.toEpochMilli()
        val exact = canExact(context)
        val alarmClock = exact && s.alerts.useAlarmClock
        try {
            when {
                alarmClock -> {
                    val show = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
                    am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
                    am.cancel(backup)
                }
                exact -> {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                    am.cancel(backup)
                }
                else -> setInexact(am, at, pi, backup)
            }
            state.saveScheduled(AlertState.Scheduled(ids, next.at, exact, alarmClock, now))
            Log.i(TAG, "Scheduled ${ids.joinToString()} at ${next.at} exact=$exact alarmClock=$alarmClock epochMs=$at")
        } catch (e: SecurityException) {
            setInexact(am, at, pi, backup)
            state.saveScheduled(AlertState.Scheduled(ids, next.at, false, false, now))
            Log.w(TAG, "Exact alarm refused; using inexact alarms", e)
        }
    }

    /**
     * Without exact-alarm access: a ten-minute window that is honoured while the device is awake, plus an
     * allow-while-idle alarm that still fires in Doze (Android may delay it up to about an hour). Whichever comes
     * first handles the events; the other finds them already handled.
     */
    private fun setInexact(am: AlarmManager, at: Long, pi: PendingIntent, backup: PendingIntent) {
        am.setWindow(AlarmManager.RTC_WAKEUP, at, INEXACT_WINDOW.toMillis(), pi)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, backup)
    }

    /** Refreshes the optional next-prayer card and sets a non-waking alarm for its next update. */
    private fun updateCard(context: Context, s: AppSettings, c: Context2, now: Instant) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pending(context, REQ_CARD, AlarmReceiver.ACTION_CARD)
        val notifier = Notifier(context, s)
        if (!s.alerts.ongoingCard || c.schedule == null) {
            am.cancel(pi)
            notifier.cancelCard()
            return
        }
        val quiet = (context.applicationContext as SakinaApp).alertState.quietUntil
        val next = c.schedule.next(now)
        notifier.showCard(next, now, quiet)
        // Every five minutes, at the next prayer and at the end of quiet mode; delivered when the device is awake.
        var at = now.plus(Duration.ofMinutes(5))
        next?.instant?.let { if (it.isBefore(at)) at = it.plusSeconds(1) }
        quiet?.let { if (it.isBefore(at)) at = it.plusSeconds(1) }
        am.set(AlarmManager.RTC, at.toEpochMilli(), pi)
    }

    /** Schedules a test alert [delaySeconds] from now through the same alarm path (not part of the plan). */
    fun scheduleTest(context: Context, mode: AlertMode, delaySeconds: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        val at = Instant.now().plusSeconds(delaySeconds)
        val pi = pending(context, REQ_TEST, AlarmReceiver.ACTION_TEST) {
            putExtra(AlarmReceiver.EXTRA_MODE, mode.name)
            putExtra(AlarmReceiver.EXTRA_AT, at.toEpochMilli())
        }
        try {
            if (canExact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pi)
        }
        Log.i(TAG, "Test alert $mode scheduled at $at")
    }

    suspend fun fireTest(context: Context, mode: AlertMode, scheduledAt: Instant) = mutex.withLock {
        val app = context.applicationContext as SakinaApp
        val s = app.settings.current()
        val now = Instant.now()
        val e = AlertEvent("TEST:${scheduledAt.toEpochMilli()}", EventKind.TEST, scheduledAt, now.atZone(ZoneId.systemDefault()).toLocalDate(), mode = mode)
        val d = AlertPlanner.decide(e, now, emptySet(), app.alertState.quietUntil)
        app.alertState.log("TEST:${mode.name}", d.name.lowercase(), now)
        Log.i(TAG, "Test alert $mode delivered ${Duration.between(scheduledAt, now).toMillis()} ms after its time: $d")
        if (d != Decision.FIRE && d != Decision.FIRE_LATE_SILENT) return@withLock
        if ((mode == AlertMode.ADHAN || mode == AlertMode.EXCERPT) && d == Decision.FIRE) {
            val sound = s.alerts.generalSound
            val ok = AdhanService.start(app, AdhanService.Request(null, scheduledAt, mode == AlertMode.EXCERPT, sound.sound, sound.volumeFraction, s.alerts.audioRoute, test = true))
            if (ok) return@withLock
        }
        Notifier(app, s).showEvent(e, d == Decision.FIRE_LATE_SILENT, now, overrideMode = mode)
    }
}
