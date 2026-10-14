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
        }
    }

    companion object {
        const val ACTION_FIRE = "com.sakina.app.ALERT_FIRE"
        const val ACTION_CARD = "com.sakina.app.CARD_REFRESH"
        const val ACTION_TEST = "com.sakina.app.ALERT_TEST"
        const val ACTION_STOP_ADHAN = "com.sakina.app.ADHAN_STOP"
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
        async { AlertScheduler.run(context, mode, intent.action ?: "system") }
    }
}
