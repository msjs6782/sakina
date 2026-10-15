package com.sakina.app.alerts

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sakina.app.MainActivity
import com.sakina.app.R
import com.sakina.app.core.alerts.AlertEvent
import com.sakina.app.core.alerts.AlertMode
import com.sakina.app.core.alerts.EventKind
import com.sakina.app.core.alerts.NotifyStyle
import com.sakina.app.core.format.Fmt
import com.sakina.app.core.prayer.PrayerEvent
import com.sakina.app.data.AppSettings
import com.sakina.app.ui.Strings
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Builds and posts every Sakina notification, in the app's language. */
class Notifier(private val context: Context, private val s: AppSettings) {
    private val str = Strings(s.language, s.arabicDigits)
    private val fmt: Fmt = s.fmt
    private val nm = NotificationManagerCompat.from(context)
    private val place = s.activeLocation?.name(s.language)
    private val zone: ZoneId = s.activeLocation?.zoneId ?: ZoneId.systemDefault()

    init {
        Channels.ensure(context, str)
    }

    fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return nm.areNotificationsEnabled()
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Adhkar reminders open their set of adhkar directly. */
    private fun openPractice(kind: com.sakina.app.core.alerts.ReminderKind): PendingIntent? {
        val practice = when (kind) {
            com.sakina.app.core.alerts.ReminderKind.ADHKAR_MORNING -> "morning"
            com.sakina.app.core.alerts.ReminderKind.ADHKAR_EVENING -> "evening"
            com.sakina.app.core.alerts.ReminderKind.ADHKAR_SLEEP -> "sleep"
            com.sakina.app.core.alerts.ReminderKind.ADHKAR_WAKING -> "waking"
            else -> return null
        }
        return PendingIntent.getActivity(
            context, 100 + kind.ordinal,
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_ADHKAR + practice)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Calendar notifications open the calendar on their day. */
    private fun openCalendar(day: java.time.LocalDate): PendingIntent? = PendingIntent.getActivity(
        context, 200 + Math.floorMod(day.toEpochDay(), 50L).toInt(),
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_CALENDAR + day)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun base(channel: String) = NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_stat_sakina)
        .setColor(0xFF0E6B5E.toInt())
        .setContentIntent(openApp())
        .setAutoCancel(true)

    private fun withPlace(text: String) = if (place != null) "$text · $place" else text

    /** Posts the notification for an event. [late] adds a note that Android delivered it late. */
    @SuppressLint("MissingPermission")
    fun showEvent(e: AlertEvent, late: Boolean, now: Instant, overrideMode: AlertMode? = null): Boolean {
        if (!canPost()) return false
        val lateNote = if (late) " · " + str.lateNote(fmt.num(Duration.between(e.at, now).toMinutes().toInt())) else ""
        val prayerName = e.prayer?.let { str.prayer(it) } ?: ""
        val prayerTime = e.prayerAt?.let { fmt.time(it, zone) } ?: ""
        val b: NotificationCompat.Builder
        val id: Int
        when (e.kind) {
            EventKind.ADHAN -> {
                val mode = if (late) AlertMode.SILENT else (overrideMode ?: e.mode ?: AlertMode.NOTIFY)
                b = base(Channels.forPrayer(if (mode == AlertMode.ADHAN || mode == AlertMode.EXCERPT) AlertMode.NOTIFY else mode))
                    .setContentTitle(str.prayerTimeTitle(prayerName))
                    .setContentText(withPlace(prayerTime) + lateNote)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setTimeoutAfter(Duration.ofMinutes(90).toMillis())
                id = ID_PRAYER + (e.prayer?.ordinal ?: 0)
            }
            EventKind.PRE -> {
                b = base(Channels.forReminder(if (late) NotifyStyle.SILENT else e.style ?: NotifyStyle.NORMAL))
                    .setContentTitle(str.preTitle(prayerName, fmt.num(e.minutes)))
                    .setContentText(withPlace(prayerTime) + lateNote)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setTimeoutAfter(Duration.ofMinutes(e.minutes.toLong() + 5).toMillis())
                id = ID_PRE + (e.prayer?.ordinal ?: 0)
            }
            EventKind.IQAMA -> {
                b = base(Channels.forReminder(if (late) NotifyStyle.SILENT else e.style ?: NotifyStyle.NORMAL))
                    .setContentTitle(str.iqamaTitle(prayerName))
                    .setContentText(str.iqamaText(fmt.num(e.minutes)) + lateNote)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setTimeoutAfter(Duration.ofMinutes(30).toMillis())
                id = ID_IQAMA + (e.prayer?.ordinal ?: 0)
            }
            EventKind.SUHOOR -> {
                b = base(Channels.forReminder(if (late) NotifyStyle.SILENT else e.style ?: NotifyStyle.NORMAL))
                    .setContentTitle(str.suhoorTitle)
                    .setContentText(withPlace(str.suhoorText(prayerTime, fmt.num(e.minutes))) + lateNote)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setTimeoutAfter(Duration.ofMinutes(e.minutes.toLong()).toMillis())
                id = ID_SUHOOR
            }
            EventKind.REMINDER -> {
                val kind = e.reminder ?: return false
                b = base(Channels.forReminder(if (late) NotifyStyle.SILENT else e.style ?: NotifyStyle.NORMAL))
                    .setContentTitle(str.reminderTitle(kind))
                    .setContentText(str.reminderText(fmt.time(e.at, zone)) + lateNote)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setTimeoutAfter(Duration.ofHours(3).toMillis())
                openPractice(kind)?.let { b.setContentIntent(it) }
                id = ID_REMINDER + kind.ordinal
            }
            EventKind.OCCASION -> {
                val o = s.calendar.occasions.firstOrNull { it.id == e.subject } ?: return false
                val time = o.minuteOfDay?.let { fmt.clock(it / 60, it % 60) }
                b = base(Channels.forReminder(if (late) NotifyStyle.SILENT else e.style ?: NotifyStyle.NORMAL))
                    .setContentTitle(o.title)
                    .setContentText(listOfNotNull(time, o.note.takeIf { it.isNotBlank() }).joinToString(" · ").ifBlank { str.occasionNotifText } + lateNote)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(o.note.ifBlank { str.occasionNotifText }))
                    .setCategory(NotificationCompat.CATEGORY_EVENT)
                    .setTimeoutAfter(Duration.ofHours(12).toMillis())
                openCalendar(e.date)?.let { b.setContentIntent(it) }
                id = ID_OCCASION + Math.floorMod(o.id.hashCode(), 90)
            }
            EventKind.FASTING -> {
                val (dayText, kindsText) = (e.subject ?: return false).split('|').let { it[0] to it.getOrElse(1) { "" } }
                val day = runCatching { java.time.LocalDate.parse(dayText) }.getOrNull() ?: return false
                val kinds = kindsText.split(',').mapNotNull { k -> runCatching { com.sakina.app.core.calendar.FastingKind.valueOf(k) }.getOrNull() }
                val tomorrow = day != e.date
                b = base(Channels.forReminder(if (late) NotifyStyle.SILENT else e.style ?: NotifyStyle.NORMAL))
                    .setContentTitle(str.fastingNotifTitle(tomorrow))
                    .setContentText(kinds.joinToString("، ") { str.fastingKind(it) } + " · " + fmt.gregorian(day, withYear = false, withWeekday = true) + lateNote)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .setTimeoutAfter(Duration.ofHours(12).toMillis())
                openCalendar(day)?.let { b.setContentIntent(it) }
                id = ID_FASTING
            }
            EventKind.TEST -> {
                val mode = overrideMode ?: e.mode ?: AlertMode.NOTIFY
                b = base(Channels.forPrayer(if (mode == AlertMode.ADHAN || mode == AlertMode.EXCERPT) AlertMode.NOTIFY else mode))
                    .setContentTitle(str.testTitle)
                    .setContentText(str.testText(fmt.timeSeconds(e.at, ZoneId.systemDefault()), fmt.timeSeconds(now, ZoneId.systemDefault())))
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                id = ID_TEST
            }
        }
        nm.notify(id, b.build())
        return true
    }

    /** The foreground-service notification shown while an adhan plays, with a stop action. */
    fun adhanPlaying(title: String, text: String): Notification {
        val stop = PendingIntent.getBroadcast(
            context, 7,
            Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_STOP_ADHAN),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return base(Channels.ADHAN_PLAYBACK)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setAutoCancel(false)
            .setDeleteIntent(stop)
            .addAction(0, str.stop, stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /** A quiet record after the adhan ended or could not play (for example during a call). */
    @SuppressLint("MissingPermission")
    fun prayerRecord(prayerName: String, prayerAt: Instant?, note: String?, prayerOrdinal: Int, vibrate: Boolean = false) {
        if (!canPost()) return
        val text = withPlace(prayerAt?.let { fmt.time(it, zone) } ?: "") + (note?.let { " · $it" } ?: "")
        nm.notify(
            ID_PRAYER + prayerOrdinal,
            base(if (vibrate) Channels.PRAYER_VIBRATE else Channels.PRAYER_SILENT)
                .setContentTitle(str.prayerTimeTitle(prayerName))
                .setContentText(text)
                .setTimeoutAfter(Duration.ofMinutes(90).toMillis())
                .build(),
        )
    }

    /**
     * The optional next-prayer card. The remaining time is a snapshot taken at [now]; Android may refresh it
     * late, so the card always shows the absolute time and when it was last updated.
     */
    @SuppressLint("MissingPermission")
    fun showCard(next: PrayerEvent?, now: Instant, quietUntil: Instant?) {
        if (!canPost()) return
        val title: String
        val text: String
        if (next == null) {
            title = str.cardNoTimes
            text = str.cardUpdated(fmt.time(now, zone))
        } else {
            val today = now.atZone(zone).toLocalDate()
            val dayLabel = when (ChronoUnit.DAYS.between(today, next.date)) {
                0L -> str.today
                1L -> str.tomorrow
                else -> fmt.gregorian(next.date, withYear = false, withWeekday = true)
            }
            val remaining = Duration.between(now, next.instant).coerceAtLeast(Duration.ZERO)
            title = str.prayer(next.key) + " · " + fmt.time(next.instant, zone)
            text = dayLabel + " · " + str.cardRemaining(fmt.durationShort(remaining))
        }
        val lines = buildList {
            add(text)
            if (quietUntil != null) add(str.quietUntil(fmt.time(quietUntil, zone)))
            add(str.cardUpdated(fmt.time(now, zone)))
        }
        val n = base(Channels.NEXT_PRAYER_CARD)
            .setContentTitle(title)
            .setContentText(lines.joinToString(" · "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        nm.notify(ID_CARD, n)
    }

    fun cancelCard() = nm.cancel(ID_CARD)

    companion object {
        const val ID_CARD = 1
        const val ID_ADHAN_SERVICE = 2
        const val ID_PRAYER = 100
        const val ID_PRE = 200
        const val ID_IQAMA = 300
        const val ID_SUHOOR = 400
        const val ID_REMINDER = 500
        const val ID_OCCASION = 600
        const val ID_FASTING = 700
        const val ID_TEST = 900
    }
}
