package com.sakina.app.widgets

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.sakina.app.MainActivity
import com.sakina.app.R
import com.sakina.app.data.AppSettings
import com.sakina.app.ui.Strings

/**
 * Long-press shortcuts on the app icon (Prayer times, Qibla, Quran, Ramadan) in the app's language, and the
 * optional pinned adhkar shortcut. Published by the app itself, so their labels follow the app's language.
 */
object Shortcuts {
    private const val TAG = "SakinaShortcuts"
    @Volatile private var published: String? = null

    /** Adhkar shortcut targets: the library, or one of the daily sets. */
    val ADHKAR_TARGETS = listOf("library", "morning", "evening", "sleep", "waking")

    fun intent(context: Context, target: String): Intent = Intent(context, MainActivity::class.java)
        .setAction(Intent.ACTION_VIEW)
        .setData(Uri.parse("sakina://open/" + Uri.encode(target)))
        .putExtra(MainActivity.EXTRA_OPEN, target)

    private fun info(context: Context, id: String, short: String, long: String, icon: Int, target: String, rank: Int) =
        ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(short)
            .setLongLabel(long)
            .setIcon(IconCompat.createWithResource(context, icon))
            .setIntent(intent(context, target))
            .setRank(rank)
            .build()

    fun publish(context: Context, s: AppSettings) {
        val str = Strings(s.language, s.arabicDigits)
        val tag = "${s.language}"
        if (published == tag) return
        try {
            val list = listOf(
                info(context, "times", str.tabTimes, str.scTimesLong, R.drawable.sc_times, "times", 0),
                info(context, "qibla", str.toolsQibla, str.toolsQibla, R.drawable.sc_qibla, "qibla", 1),
                info(context, "quran", str.tabQuran, str.tabQuran, R.drawable.sc_quran, "quran", 2),
                info(context, "ramadan", str.ramadanTitle, str.ramadanTitle, R.drawable.sc_ramadan, "ramadan", 3),
            )
            ShortcutManagerCompat.setDynamicShortcuts(context, list)
            // Pinned adhkar shortcuts keep their target; only their labels follow the language.
            val pinned = ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED)
                .filter { it.id.startsWith("adhkar_") }
                .map { adhkarInfo(context, str, it.id.removePrefix("adhkar_")) }
            if (pinned.isNotEmpty()) ShortcutManagerCompat.updateShortcuts(context, pinned)
            published = tag
            Log.i(TAG, "published ${list.size} shortcuts (${s.language}), pinned adhkar=${pinned.size}")
        } catch (e: Exception) {
            Log.w(TAG, "shortcuts not published", e)
        }
    }

    fun adhkarLabel(str: Strings, target: String): String = when (target) {
        "morning" -> str.scAdhkarMorning
        "evening" -> str.scAdhkarEvening
        "sleep" -> str.scAdhkarSleep
        "waking" -> str.scAdhkarWaking
        else -> str.adhkarTitle
    }

    private fun adhkarInfo(context: Context, str: Strings, target: String): ShortcutInfoCompat {
        val label = adhkarLabel(str, target)
        return info(context, "adhkar_$target", label, label, R.drawable.sc_adhkar, if (target == "library") "adhkar_lib" else "adhkar:$target", 0)
    }

    fun canPin(context: Context) = ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    /** Asks the launcher to pin an adhkar shortcut; false if the launcher cannot. */
    fun pinAdhkar(context: Context, s: AppSettings, target: String): Boolean {
        if (!canPin(context)) return false
        return ShortcutManagerCompat.requestPinShortcut(context, adhkarInfo(context, Strings(s.language, s.arabicDigits), target), null)
    }
}
