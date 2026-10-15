package com.sakina.app.widgets

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.sakina.app.SakinaApp
import com.sakina.app.core.prayer.PrayerSchedule
import com.sakina.app.data.AppClock
import com.sakina.app.ui.Strings
import kotlinx.coroutines.launch

/** Quick Settings tiles that open a screen of the app (where the system and launcher support them). */
abstract class SakinaTile(private val target: String) : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        val app = applicationContext as SakinaApp
        app.appScope.launch {
            val s = app.settings.current()
            val str = Strings(s.language, s.arabicDigits)
            val (label, subtitle) = describe(app, s, str)
            val tile = qsTile ?: return@launch
            tile.state = Tile.STATE_INACTIVE
            tile.label = label
            if (Build.VERSION.SDK_INT >= 29) tile.subtitle = subtitle
            tile.updateTile()
        }
    }

    abstract fun describe(app: SakinaApp, s: com.sakina.app.data.AppSettings, str: Strings): Pair<String, String?>

    override fun onClick() {
        super.onClick()
        val intent = Shortcuts.intent(this, target).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        Log.i("SakinaTiles", "tile clicked: $target")
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, target.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}

class QiblaTileService : SakinaTile("qibla") {
    override fun describe(app: SakinaApp, s: com.sakina.app.data.AppSettings, str: Strings) = str.toolsQibla to null
}

/** Shows the next prayer as its subtitle when the panel opens (a snapshot, like the widgets). */
class TimesTileService : SakinaTile("times") {
    override fun describe(app: SakinaApp, s: com.sakina.app.data.AppSettings, str: Strings): Pair<String, String?> {
        val loc = s.activeLocation?.takeIf { it.isUsable }
        val zone = loc?.zoneId
        val next = if (loc != null && zone != null) PrayerSchedule(loc.point, zone, s.calcConfig).next(AppClock.now()) else null
        val sub = next?.let { str.tileNext(str.prayer(it.key), s.fmt.time(it.instant, zone!!)) }
        return str.tabTimes to sub
    }
}
