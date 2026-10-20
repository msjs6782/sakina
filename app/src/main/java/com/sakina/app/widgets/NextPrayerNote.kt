package com.sakina.app.widgets

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sakina.app.R
import com.sakina.app.core.prayer.PrayerSchedule
import com.sakina.app.data.AppSettings
import com.sakina.app.ui.Strings
import java.time.Duration
import java.time.Instant

/**
 * The next prayer on the lock screen and in the shade: an ongoing, silent notification whose countdown the system
 * runs itself (a chronometer counting down), so it needs no redraw each minute. It leaves at the prayer's time and
 * the widget tick puts up the next one. Off unless the user turns it on.
 */
object NextPrayerNote {
    const val ID = 4200
    private const val CHANNEL = "next_prayer"

    /** Posts, updates or removes the note; returns when it next needs updating (null when off). */
    fun refresh(context: Context, s: AppSettings, now: Instant): Instant? {
        val nm = NotificationManagerCompat.from(context)
        val loc = s.activeLocation?.takeIf { it.isUsable }
        if (!s.lockNote || loc == null) { nm.cancel(ID); return null }
        val zone = loc.zoneId!!
        val next = PrayerSchedule(loc.point, zone, s.calcConfig).next(now) ?: run { nm.cancel(ID); return null }
        val str = Strings(s.language, s.arabicDigits)
        val fmt = s.fmt
        if (Build.VERSION.SDK_INT >= 26) {
            val m = context.getSystemService(NotificationManager::class.java)
            if (m.getNotificationChannel(CHANNEL) == null || m.getNotificationChannel(CHANNEL).name != str.lockNoteChannel) {
                m.createNotificationChannel(NotificationChannel(CHANNEL, str.lockNoteChannel, NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                })
            }
        }
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return next.instant.plusSeconds(1)
        }
        // Measured on the app's clock (debug builds can pin it), shown on the real one.
        val left = Duration.between(now, next.instant).toMillis().coerceAtLeast(0)
        val at = System.currentTimeMillis() + left
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_sakina)
            .setContentTitle(str.lockNoteTitle(str.prayer(next.key), fmt.time(next.instant, zone)))
            .setContentText(loc.name(s.language))
            .setWhen(at)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setTimeoutAfter(left + 1_000)
            .setContentIntent(WidgetIntents.open(context, "home"))
            .build()
        try { nm.notify(ID, n) } catch (e: SecurityException) { return null }
        return next.instant.plusSeconds(1)
    }
}
