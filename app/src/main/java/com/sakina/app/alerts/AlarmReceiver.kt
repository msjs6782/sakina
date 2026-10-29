package com.sakina.app.alerts

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sakina.app.core.alerts.AlertMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/** Runs [block] off the main thread while keeping the broadcast alive (up to about nine seconds). */
private fun BroadcastReceiver.async(block: suspend () -> Unit) {
    val result = goAsync()
    receiverScope.launch {
        try {
            withTimeoutOrNull(9_000) { block() }
        } catch (e: Exception) {
            Log.e("SakinaAlerts", "Receiver failed", e)
        } finally {
            result.finish()
        }
    }
}

/** Alarms set by Sakina: the alert chain, the next-prayer card refresh, test alerts and the stop action. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FIRE -> async { AlertScheduler.run(context, AlertScheduler.Run.ALARM, "alarm") }
            ACTION_CARD -> async { AlertScheduler.run(context, AlertScheduler.Run.REFRESH, "card") }
            ACTION_TEST -> async {
                val mode = intent.getStringExtra(EXTRA_MODE)?.let { runCatching { AlertMode.valueOf(it) }.getOrNull() } ?: AlertMode.NOTIFY
                val at = Instant.ofEpochMilli(intent.getLongExtra(EXTRA_AT, System.currentTimeMillis()))
                AlertScheduler.fireTest(context, mode, at)
            }
            ACTION_STOP_ADHAN -> AdhanService.stop(context)
            ACTION_UNSILENCE -> PrayerSilence.end(context, "user")
            ACTION_PRAYED -> async {
                // "I prayed" from a prayer notification: marked in the prayer log, and the notification goes.
                val key = intent.getStringExtra(EXTRA_PRAYER)?.let { runCatching { com.sakina.app.core.prayer.PrayerKey.valueOf(it) }.getOrNull() } ?: return@async
                val date = intent.getStringExtra(EXTRA_DATE)?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() } ?: return@async
                val app = context.applicationContext as com.sakina.app.SakinaApp
                app.tools.update { t -> t.copy(prayed = com.sakina.app.core.prayer.PrayerLog.set(t.prayed, date, key, true, java.time.LocalDate.now())) }
                androidx.core.app.NotificationManagerCompat.from(context).cancel(intent.getIntExtra(EXTRA_NOTE, 0))
                Log.i("SakinaAlerts", "prayed $key $date (from the notification)")
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.sakina.app.ALERT_FIRE"
        const val ACTION_CARD = "com.sakina.app.CARD_REFRESH"
        const val ACTION_TEST = "com.sakina.app.ALERT_TEST"
        const val ACTION_STOP_ADHAN = "com.sakina.app.ADHAN_STOP"
        const val ACTION_UNSILENCE = "com.sakina.app.UNSILENCE"
        const val ACTION_PRAYED = "com.sakina.app.PRAYED"
        const val EXTRA_PRAYER = "prayer"
        const val EXTRA_DATE = "date"
        const val EXTRA_NOTE = "note"
        const val EXTRA_MODE = "mode"
        const val EXTRA_AT = "at"
    }
}

/** Re-creates the alarm chain after a reboot, an app update, a clock or time-zone change, or new exact-alarm access. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val mode = when (intent.action) {
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> AlertScheduler.Run.REFRESH
            else -> AlertScheduler.Run.CATCH_UP
        }
        Log.i("SakinaAlerts", "System event ${intent.action}")
        async {
            AlertScheduler.run(context, mode, intent.action ?: "system")
            // Widgets show times and dates too: redraw them all after a reboot, an update or a clock change.
            com.sakina.app.widgets.WidgetUpdater.update(context, intent.action ?: "system", force = true)
        }
    }
}
