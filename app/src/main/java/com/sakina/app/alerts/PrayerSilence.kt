package com.sakina.app.alerts

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sakina.app.R
import com.sakina.app.core.alerts.AlertEvent
import com.sakina.app.data.AppSettings
import com.sakina.app.ui.Strings
import java.time.Instant
import java.time.ZoneId

/**
 * Silences the phone for the prayer and gives it back its sound afterwards. Vibrate needs no permission;
 * Do Not Disturb needs the user's Do Not Disturb access. Nothing is changed when the phone is already quiet
 * (the user's own choice), and it is only put back if it is still as Sakina left it.
 */
object PrayerSilence {
    private const val TAG = "SakinaSilence"
    private const val PREFS = "prayer_silence"
    private const val CHANNEL = "prayer_silence"
    const val NOTE_ID = 4300

    fun hasDndAccess(context: Context): Boolean = context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

    fun isActive(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("active", false)

    fun begin(context: Context, s: AppSettings, e: AlertEvent) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean("active", false)) return
        val am = context.getSystemService(AudioManager::class.java)
        val nm = context.getSystemService(NotificationManager::class.java)
        val useDnd = s.alerts.silence.dnd && hasDndAccess(context)
        val mode = try {
            if (useDnd) {
                if (nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) { Log.i(TAG, "already in Do Not Disturb; left as is"); return }
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                "dnd"
            } else {
                if (am.ringerMode != AudioManager.RINGER_MODE_NORMAL) { Log.i(TAG, "ringer already ${am.ringerMode}; left as is"); return }
                am.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                "vibrate"
            }
        } catch (x: SecurityException) {
            Log.w(TAG, "cannot silence: $x")
            return
        }
        val until = e.at.plusSeconds(e.minutes * 60L)
        prefs.edit().putBoolean("active", true).putString("mode", mode).putLong("until", until.toEpochMilli()).apply()
        Log.i(TAG, "silenced ($mode) for ${e.prayer} until $until")
        note(context, s, until, mode)
    }

    fun end(context: Context, reason: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("active", false)) return
        val am = context.getSystemService(AudioManager::class.java)
        val nm = context.getSystemService(NotificationManager::class.java)
        try {
            when (prefs.getString("mode", "")) {
                "dnd" -> if (hasDndAccess(context) && nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                }
                "vibrate" -> if (am.ringerMode == AudioManager.RINGER_MODE_VIBRATE) am.ringerMode = AudioManager.RINGER_MODE_NORMAL
            }
        } catch (x: SecurityException) {
            Log.w(TAG, "cannot restore: $x")
        }
        prefs.edit().clear().apply()
        NotificationManagerCompat.from(context).cancel(NOTE_ID)
        Log.i(TAG, "sound back ($reason)")
    }

    @SuppressLint("MissingPermission")
    private fun note(context: Context, s: AppSettings, until: Instant, mode: String) {
        val str = Strings(s.language, s.arabicDigits)
        if (Build.VERSION.SDK_INT >= 26) {
            val m = context.getSystemService(NotificationManager::class.java)
            m.createNotificationChannel(NotificationChannel(CHANNEL, str.silenceChannel, NotificationManager.IMPORTANCE_LOW).apply { setSound(null, null); enableVibration(false); setShowBadge(false) })
        }
        val back = PendingIntent.getBroadcast(
            context, 43, Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_UNSILENCE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val zone = s.activeLocation?.zoneId ?: ZoneId.systemDefault()
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_sakina)
            .setContentTitle(if (mode == "dnd") str.silenceOnDnd else str.silenceOnVibrate)
            .setContentText(str.silenceUntil(s.fmt.time(until, zone)))
            .setOngoing(true).setSilent(true).setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, str.silenceEndNow, back)
            .build()
        try { NotificationManagerCompat.from(context).notify(NOTE_ID, n) } catch (_: SecurityException) { }
    }
}
