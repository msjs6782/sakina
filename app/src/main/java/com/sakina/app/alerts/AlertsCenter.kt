package com.sakina.app.alerts

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sakina.app.SakinaApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/** Permission checks, system-settings shortcuts and quiet mode, for the alerts screens. */
object AlertsCenter {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    data class Status(
        val notificationPermission: Boolean,
        val notificationsEnabled: Boolean,
        val exactAlarms: Boolean,
        val exactAlarmsApplicable: Boolean,
        val batteryUnrestricted: Boolean,
        val blockedChannels: List<String>,
    )

    fun status(context: Context): Status {
        val perm = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val pm = context.getSystemService(PowerManager::class.java)
        return Status(
            notificationPermission = perm,
            notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
            exactAlarms = AlertScheduler.canExact(context),
            exactAlarmsApplicable = Build.VERSION.SDK_INT >= 31,
            batteryUnrestricted = pm.isIgnoringBatteryOptimizations(context.packageName),
            blockedChannels = Channels.all.filter { Channels.isBlocked(context, it) },
        )
    }

    private fun start(context: Context, intent: Intent, fallback: Intent? = null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            fallback?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        }
    }

    private fun appDetails(context: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))

    fun openNotificationSettings(context: Context) = start(
        context,
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        appDetails(context),
    )

    fun openChannelSettings(context: Context, channel: String) = start(
        context,
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, channel),
        appDetails(context),
    )

    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= 31) {
            start(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName)), appDetails(context))
        }
    }

    @SuppressLint("BatteryLife")
    fun requestBatteryExemption(context: Context) = start(
        context,
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + context.packageName)),
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
    )

    fun openSoundSettings(context: Context) = start(context, Intent(Settings.ACTION_SOUND_SETTINGS), Intent(Settings.ACTION_SETTINGS))

    /** Recomputes the alarm chain in the background (after a settings change or when the app opens). */
    fun refresh(context: Context, reason: String) {
        val app = context.applicationContext
        scope.launch { AlertScheduler.run(app, AlertScheduler.Run.REFRESH, reason) }
    }

    fun test(context: Context, mode: com.sakina.app.core.alerts.AlertMode, delaySeconds: Long = 10) =
        AlertScheduler.scheduleTest(context.applicationContext, mode, delaySeconds)

    /**
     * Quiet mode: for [duration], Sakina's adhan, alerts and reminders are skipped (not postponed). Reading and
     * prayer times stay available; calls and other apps are not affected; nothing resumes when it ends.
     */
    fun startQuiet(context: Context, duration: Duration) {
        val app = context.applicationContext as SakinaApp
        app.alertState.quietUntil = Instant.now().plus(duration)
        app.alertState.log("QUIET", "start_${duration.toMinutes()}m")
        AdhanService.stop(app)
        refresh(app, "quiet")
    }

    fun endQuiet(context: Context) {
        val app = context.applicationContext as SakinaApp
        app.alertState.quietUntil = null
        app.alertState.log("QUIET", "end")
        refresh(app, "quiet_end")
    }
}
