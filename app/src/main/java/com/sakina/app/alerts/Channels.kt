package com.sakina.app.alerts

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import com.sakina.app.core.alerts.AlertMode
import com.sakina.app.core.alerts.NotifyStyle
import com.sakina.app.ui.Strings

/**
 * Notification channels. Sound and vibration are fixed per channel by Android, so each delivery style has its
 * own channel; the user can still change any of them in Android's settings. Names follow the app language.
 */
object Channels {
    const val PRAYER_SOUND = "prayer_sound"
    const val PRAYER_SILENT = "prayer_silent"
    const val PRAYER_VIBRATE = "prayer_vibrate"
    const val ADHAN_PLAYBACK = "adhan_playback"
    const val REMINDER_SOUND = "reminder_sound"
    const val REMINDER_SILENT = "reminder_silent"
    const val REMINDER_VIBRATE = "reminder_vibrate"
    const val NEXT_PRAYER_CARD = "next_prayer_card"

    private const val GROUP_PRAYER = "group_prayer"
    private const val GROUP_REMINDER = "group_reminder"
    private val VIBRATION = longArrayOf(0, 450, 250, 450, 250, 700)

    val all = listOf(PRAYER_SOUND, PRAYER_SILENT, PRAYER_VIBRATE, ADHAN_PLAYBACK, REMINDER_SOUND, REMINDER_SILENT, REMINDER_VIBRATE, NEXT_PRAYER_CARD)

    fun forPrayer(mode: AlertMode): String = when (mode) {
        AlertMode.NOTIFY -> PRAYER_SOUND
        AlertMode.VIBRATE -> PRAYER_VIBRATE
        AlertMode.ADHAN, AlertMode.EXCERPT -> ADHAN_PLAYBACK
        else -> PRAYER_SILENT
    }

    fun forReminder(style: NotifyStyle): String = when (style) {
        NotifyStyle.NORMAL -> REMINDER_SOUND
        NotifyStyle.SILENT -> REMINDER_SILENT
        NotifyStyle.VIBRATE -> REMINDER_VIBRATE
    }

    /** Creates or renames the channels (safe to call often; Android keeps the user's changes). */
    fun ensure(context: Context, str: Strings) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannelGroups(
            listOf(
                NotificationChannelGroup(GROUP_PRAYER, str.chGroupPrayer),
                NotificationChannelGroup(GROUP_REMINDER, str.chGroupReminder),
            ),
        )
        val soundAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        fun channel(id: String, name: String, desc: String, importance: Int, group: String?, sound: Boolean, vibrate: Boolean) =
            NotificationChannel(id, name, importance).apply {
                description = desc
                if (group != null) this.group = group
                if (sound) setSound(defaultSound, soundAttrs) else setSound(null, null)
                enableVibration(vibrate)
                if (vibrate) vibrationPattern = VIBRATION
                setShowBadge(false)
            }

        nm.createNotificationChannels(
            listOf(
                channel(PRAYER_SOUND, str.chPrayerSound, str.chPrayerSoundDesc, NotificationManager.IMPORTANCE_HIGH, GROUP_PRAYER, sound = true, vibrate = false),
                channel(PRAYER_SILENT, str.chPrayerSilent, str.chPrayerSilentDesc, NotificationManager.IMPORTANCE_DEFAULT, GROUP_PRAYER, sound = false, vibrate = false),
                channel(PRAYER_VIBRATE, str.chPrayerVibrate, str.chPrayerVibrateDesc, NotificationManager.IMPORTANCE_HIGH, GROUP_PRAYER, sound = false, vibrate = true),
                channel(ADHAN_PLAYBACK, str.chAdhan, str.chAdhanDesc, NotificationManager.IMPORTANCE_HIGH, GROUP_PRAYER, sound = false, vibrate = false),
                channel(REMINDER_SOUND, str.chReminderSound, str.chReminderDesc, NotificationManager.IMPORTANCE_HIGH, GROUP_REMINDER, sound = true, vibrate = false),
                channel(REMINDER_SILENT, str.chReminderSilent, str.chReminderDesc, NotificationManager.IMPORTANCE_DEFAULT, GROUP_REMINDER, sound = false, vibrate = false),
                channel(REMINDER_VIBRATE, str.chReminderVibrate, str.chReminderDesc, NotificationManager.IMPORTANCE_HIGH, GROUP_REMINDER, sound = false, vibrate = true),
                channel(NEXT_PRAYER_CARD, str.chCard, str.chCardDesc, NotificationManager.IMPORTANCE_LOW, null, sound = false, vibrate = false),
            ),
        )
    }

    /** True when the user turned a channel off in Android's settings. */
    fun isBlocked(context: Context, id: String): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java)
        return nm.getNotificationChannel(id)?.importance == NotificationManager.IMPORTANCE_NONE
    }
}
