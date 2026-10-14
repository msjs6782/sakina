package com.sakina.app.core.alerts

import com.sakina.app.core.prayer.PrayerKey
import kotlinx.serialization.Serializable
import java.time.DayOfWeek

/** What happens at a prayer's adhan time. Everything starts OFF. */
enum class AlertMode { OFF, NOTIFY, SILENT, VIBRATE, ADHAN, EXCERPT }

/** How a reminder-type notification (pre-alert, iqama, personal reminders, suhoor) is delivered. */
enum class NotifyStyle { NORMAL, SILENT, VIBRATE }

/** NOTIFICATION follows the ringer (no sound when the phone is on silent or vibrate); ALARM plays on the alarm stream. */
enum class AudioRoute { NOTIFICATION, ALARM }

enum class ReminderKind { DUHA, WITR, QIYAM, ADHKAR_MORNING, ADHKAR_EVENING, ADHKAR_SLEEP, ADHKAR_WAKING, FRIDAY }

/** A sound: one of the bundled candidate recordings, or a file the user copied into the app. */
@Serializable
data class SoundRef(
    val bundled: String? = BUNDLED_DEFAULT,
    val fileName: String? = null,
    val displayName: String? = null,
) {
    val isFile: Boolean get() = fileName != null

    companion object {
        const val BUNDLED_DEFAULT = "candidate_1"
    }
}

@Serializable
data class SoundPref(val sound: SoundRef = SoundRef(), val volume: Int = 80) {
    val volumeFraction: Float get() = volume.coerceIn(0, 100) / 100f
}

@Serializable
data class PrayerAlert(
    val mode: AlertMode = AlertMode.OFF,
    /** null: use the general sound (or the Fajr sound for Fajr). */
    val sound: SoundPref? = null,
    /** null: use the general value. 0 turns it off for this prayer. */
    val preMinutes: Int? = null,
    val iqamaMinutes: Int? = null,
)

@Serializable
data class Reminder(
    val kind: ReminderKind,
    val enabled: Boolean = false,
    /** Civil time in the active place's time zone, minutes after midnight. */
    val minuteOfDay: Int,
    /** ISO day numbers, 1 = Monday … 7 = Sunday. */
    val days: Set<Int> = ALL_DAYS,
    val style: NotifyStyle = NotifyStyle.NORMAL,
) {
    fun appliesOn(day: DayOfWeek) = day.value in days

    companion object {
        val ALL_DAYS = (1..7).toSet()
    }
}

@Serializable
data class SuhoorPref(
    val enabled: Boolean = false,
    val minutesBeforeFajr: Int = 45,
    val ramadanOnly: Boolean = true,
    val style: NotifyStyle = NotifyStyle.NORMAL,
)

@Serializable
data class AlertSettings(
    val prayers: Map<PrayerKey, PrayerAlert> = emptyMap(),
    val generalSound: SoundPref = SoundPref(),
    val fajrSound: SoundPref = SoundPref(),
    val preMinutes: Int = 0,
    val preStyle: NotifyStyle = NotifyStyle.NORMAL,
    val iqamaMinutes: Int = 0,
    val iqamaStyle: NotifyStyle = NotifyStyle.NORMAL,
    val audioRoute: AudioRoute = AudioRoute.NOTIFICATION,
    /** Uses Android's alarm-clock alarms (most reliable; shows the alarm icon in the status bar). */
    val useAlarmClock: Boolean = false,
    val ongoingCard: Boolean = false,
    val suhoor: SuhoorPref = SuhoorPref(),
    val reminders: List<Reminder> = defaultReminders(),
) {
    fun prayer(key: PrayerKey): PrayerAlert = prayers[key] ?: PrayerAlert()

    fun soundFor(key: PrayerKey): SoundPref = prayer(key).sound ?: if (key == PrayerKey.FAJR) fajrSound else generalSound

    fun preFor(key: PrayerKey): Int = (prayer(key).preMinutes ?: preMinutes).coerceIn(0, MAX_OFFSET)

    fun iqamaFor(key: PrayerKey): Int = (prayer(key).iqamaMinutes ?: iqamaMinutes).coerceIn(0, MAX_OFFSET)

    /** Reminders in the fixed order, adding any kind missing from older saved settings. */
    val allReminders: List<Reminder>
        get() = ReminderKind.entries.map { k -> reminders.firstOrNull { it.kind == k } ?: defaultReminder(k) }

    val anyEnabled: Boolean
        get() = PrayerKey.prayers.any { prayer(it).mode != AlertMode.OFF || preFor(it) > 0 || iqamaFor(it) > 0 } ||
            suhoor.enabled || allReminders.any { it.enabled }

    val anySound: Boolean get() = PrayerKey.prayers.any { prayer(it).mode == AlertMode.ADHAN || prayer(it).mode == AlertMode.EXCERPT }

    fun withPrayer(key: PrayerKey, transform: (PrayerAlert) -> PrayerAlert) = copy(prayers = prayers + (key to transform(prayer(key))))

    fun withReminder(kind: ReminderKind, transform: (Reminder) -> Reminder) =
        copy(reminders = allReminders.map { if (it.kind == kind) transform(it) else it })

    companion object {
        const val MAX_OFFSET = 60
        const val SUHOOR_MIN = 1
        const val SUHOOR_MAX = 120

        fun defaultReminder(kind: ReminderKind): Reminder = when (kind) {
            ReminderKind.DUHA -> Reminder(kind, minuteOfDay = 9 * 60)
            ReminderKind.WITR -> Reminder(kind, minuteOfDay = 22 * 60)
            ReminderKind.QIYAM -> Reminder(kind, minuteOfDay = 3 * 60 + 30)
            ReminderKind.ADHKAR_MORNING -> Reminder(kind, minuteOfDay = 6 * 60 + 30)
            ReminderKind.ADHKAR_EVENING -> Reminder(kind, minuteOfDay = 16 * 60 + 45)
            ReminderKind.ADHKAR_SLEEP -> Reminder(kind, minuteOfDay = 22 * 60 + 30)
            ReminderKind.ADHKAR_WAKING -> Reminder(kind, minuteOfDay = 5 * 60)
            ReminderKind.FRIDAY -> Reminder(kind, minuteOfDay = 10 * 60, days = setOf(DayOfWeek.FRIDAY.value))
        }

        fun defaultReminders() = ReminderKind.entries.map { defaultReminder(it) }
    }
}
