package com.sakina.app.widgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import com.sakina.app.MainActivity
import com.sakina.app.SakinaApp
import com.sakina.app.alerts.AlertScheduler
import com.sakina.app.core.widgets.ContentPack
import com.sakina.app.core.widgets.WidgetKind
import com.sakina.app.data.AppClock
import com.sakina.app.data.SettingsJson
import com.sakina.app.data.WidgetConfig
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant

/** One provider per widget type; they share all their behaviour. */
abstract class SakinaWidgetProvider(val kind: WidgetKind) : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) =
        async(context) { WidgetUpdater.update(context, "update:${kind.id}", only = ids.toSet()) }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        async(context) { WidgetUpdater.update(context, "resize:${kind.id}", only = setOf(id)) }

    override fun onDeleted(context: Context, ids: IntArray) =
        async(context) { (context.applicationContext as SakinaApp).widgets.remove(ids) }

    override fun onDisabled(context: Context) = async(context) { WidgetUpdater.update(context, "disabled:${kind.id}") }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        when (intent.action) {
            ACTION_REFRESH -> async(context) { WidgetUpdater.update(context, "manual:${kind.id}", only = setOf(id), force = true) }
            ACTION_NEXT -> async(context) {
                val app = context.applicationContext as SakinaApp
                app.widgets.update(id, kind.id) { it.copy(contentShift = it.contentShift + 1) }
                WidgetUpdater.update(context, "next:${kind.id}", only = setOf(id), force = true)
            }
            else -> super.onReceive(context, intent)
        }
    }

    private fun async(context: Context, block: suspend () -> Unit) {
        val pending = goAsync()
        (context.applicationContext as SakinaApp).appScope.launch {
            try {
                block()
            } catch (e: Exception) {
                Log.e(WidgetUpdater.TAG, "widget work failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.sakina.app.widget.REFRESH"
        const val ACTION_NEXT = "com.sakina.app.widget.NEXT"
    }
}

class NextPrayerWidget : SakinaWidgetProvider(WidgetKind.NEXT)
class CountdownWidget : SakinaWidgetProvider(WidgetKind.COUNTDOWN)
class DayWidget : SakinaWidgetProvider(WidgetKind.DAY)
class GlassWidget : SakinaWidgetProvider(WidgetKind.GLASS)
class MiniWidget : SakinaWidgetProvider(WidgetKind.MINI)
class StripWidget : SakinaWidgetProvider(WidgetKind.STRIP)
class DigitalClockWidget : SakinaWidgetProvider(WidgetKind.DIGITAL)
class AnalogClockWidget : SakinaWidgetProvider(WidgetKind.ANALOG)
class ContentWidget : SakinaWidgetProvider(WidgetKind.CONTENT)
class FajrMaghribWidget : SakinaWidgetProvider(WidgetKind.FAJR_MAGHRIB)

/** The non-waking alarm that redraws widgets when their content changes (every minute for remaining times). */
class WidgetTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pinned = intent.action == ACTION_PINNED
        if (pinned) Log.i(WidgetUpdater.TAG, "pinned ${intent.getStringExtra(EXTRA_KIND)} id=${intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)}")
        val pending = goAsync()
        (context.applicationContext as SakinaApp).appScope.launch {
            try { WidgetUpdater.update(context, if (pinned) "pinned" else "tick", force = pinned) } finally { pending.finish() }
        }
    }

    companion object {
        const val ACTION_TICK = "com.sakina.app.widget.TICK"
        const val ACTION_PINNED = "com.sakina.app.widget.PINNED"
        const val EXTRA_KIND = "kind"
    }
}

object WidgetIntents {
    fun open(context: Context, target: String): PendingIntent = PendingIntent.getActivity(
        context, target.hashCode(),
        Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(Uri.parse("sakina://open/" + Uri.encode(target)))
            .putExtra(MainActivity.EXTRA_OPEN, target)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun broadcast(context: Context, kind: WidgetKind, id: Int, action: String) = PendingIntent.getBroadcast(
        context, id,
        Intent(context, WidgetUpdater.providerClass(kind))
            .setAction(action)
            .setData(Uri.parse("sakina://widget/$id/$action"))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun refresh(context: Context, kind: WidgetKind, id: Int) = broadcast(context, kind, id, SakinaWidgetProvider.ACTION_REFRESH)
    fun next(context: Context, kind: WidgetKind, id: Int) = broadcast(context, kind, id, SakinaWidgetProvider.ACTION_NEXT)
}

/** A widget on the home screen, as listed in the app. */
data class PlacedWidget(val id: Int, val kind: WidgetKind, val config: WidgetConfig)

object WidgetUpdater {
    const val TAG = "SakinaWidgets"
    private const val REQ_TICK = 2001
    private val mutex = Mutex()
    private val lastKeys = HashMap<Int, String>()
    @Volatile private var pack: ContentPack? = null

    val providers: Map<WidgetKind, Class<out SakinaWidgetProvider>> = linkedMapOf(
        WidgetKind.NEXT to NextPrayerWidget::class.java,
        WidgetKind.COUNTDOWN to CountdownWidget::class.java,
        WidgetKind.DAY to DayWidget::class.java,
        WidgetKind.GLASS to GlassWidget::class.java,
        WidgetKind.MINI to MiniWidget::class.java,
        WidgetKind.STRIP to StripWidget::class.java,
        WidgetKind.DIGITAL to DigitalClockWidget::class.java,
        WidgetKind.ANALOG to AnalogClockWidget::class.java,
        WidgetKind.CONTENT to ContentWidget::class.java,
        WidgetKind.FAJR_MAGHRIB to FajrMaghribWidget::class.java,
    )

    fun providerClass(kind: WidgetKind): Class<out SakinaWidgetProvider> = providers.getValue(kind)

    /** Called back by the launcher once a widget asked for from the gallery is on the home screen (it adds the id). */
    fun pinnedCallback(context: Context, kind: WidgetKind): PendingIntent = PendingIntent.getBroadcast(
        context, 3000 + kind.ordinal,
        Intent(context, WidgetTickReceiver::class.java).setAction(WidgetTickReceiver.ACTION_PINNED).putExtra(WidgetTickReceiver.EXTRA_KIND, kind.id),
        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun kindOf(context: Context, id: Int): WidgetKind? {
        val info = AppWidgetManager.getInstance(context).getAppWidgetInfo(id) ?: return null
        return providers.entries.firstOrNull { it.value.name == info.provider.className }?.key
    }

    fun loadPack(context: Context): ContentPack = pack ?: runCatching {
        SettingsJson.decodeFromString(ContentPack.serializer(), context.assets.open("widgets/content.json").bufferedReader().use { it.readText() })
    }.getOrElse { Log.e(TAG, "content pack unreadable", it); ContentPack() }.also { pack = it }

    /** Every widget currently on the home screen, with its settings. */
    suspend fun placed(context: Context): List<PlacedWidget> {
        val app = context.applicationContext as SakinaApp
        val mgr = AppWidgetManager.getInstance(context)
        val configs = app.widgets.all()
        return providers.flatMap { (kind, cls) ->
            mgr.getAppWidgetIds(ComponentName(context, cls)).map { id -> PlacedWidget(id, kind, configs[id.toString()] ?: WidgetConfig.defaultsFor(kind.id)) }
        }
    }

    /** The sizes (dp) a widget may be shown at: one per orientation, or the launcher's list on Android 12+. */
    fun sizesOf(context: Context, id: Int, kind: WidgetKind): List<SizeF> {
        val o = AppWidgetManager.getInstance(context).getAppWidgetOptions(id)
        if (Build.VERSION.SDK_INT >= 31) {
            @Suppress("DEPRECATION")
            val list = o.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            if (!list.isNullOrEmpty()) return list.filter { it.width > 0 && it.height > 0 }.distinct().take(4)
        }
        val minW = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val maxW = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
        val minH = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        val maxH = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        if (minW > 0 && maxH > 0) return listOf(SizeF(maxW.toFloat(), minH.toFloat()), SizeF(minW.toFloat(), maxH.toFloat()))
        return listOf(WidgetRenderer.defaultSize(kind))
    }

    /** Asks for a redraw without waiting (settings changed, the app started, a time change). */
    fun request(context: Context, reason: String, force: Boolean = false) {
        val app = context.applicationContext as SakinaApp
        app.appScope.launch {
            try { update(context, reason, force = force) } catch (e: Exception) { Log.e(TAG, "update failed", e) }
        }
    }

    /**
     * Redraws every widget whose content changed (or [only] / all with [force]) and sets one non-waking alarm for
     * the earliest next change. Nothing is scheduled when no widget is placed.
     */
    suspend fun update(context: Context, reason: String, only: Set<Int>? = null, force: Boolean = false) = mutex.withLock {
        val app = context.applicationContext as SakinaApp
        val mgr = AppWidgetManager.getInstance(context)
        val s = app.settings.current()
        val configs = app.widgets.all()
        val now = AppClock.now()
        val env = WidgetEnv(app, s, now, loadPack(app))
        var next: Instant? = null
        val alive = HashSet<Int>()
        var pushed = 0
        for ((kind, cls) in providers) {
            for (id in mgr.getAppWidgetIds(ComponentName(context, cls))) {
                alive += id
                val cfg = configs[id.toString()] ?: WidgetConfig.defaultsFor(kind.id)
                val r = try {
                    WidgetRenderer.render(env, kind, cfg, id, sizesOf(context, id, kind))
                } catch (e: Exception) {
                    Log.e(TAG, "render failed for ${kind.id} #$id", e)
                    continue
                }
                if (force || (only != null && id in only) || lastKeys[id] != r.key) {
                    mgr.updateAppWidget(id, r.views)
                    lastKeys[id] = r.key
                    pushed++
                    Log.i(TAG, "drawn ${kind.id} #$id lang=${cfg.lang} place=${cfg.placeMode}")
                }
                next = if (next == null || r.nextChange.isBefore(next)) r.nextChange else next
            }
        }
        lastKeys.keys.retainAll(alive)
        if (configs.keys.any { k -> k.toIntOrNull() !in alive }) app.widgets.retain(alive)
        schedule(context, now, next)
        Log.i(TAG, "update ($reason) widgets=${alive.size} pushed=$pushed next=$next")
    }

    /**
     * Sets the next redraw. The alarm does not wake the device: while the screen is off nothing is drawn, and the
     * pending redraw runs as soon as the device wakes. Exact when the app may use exact alarms.
     */
    private fun schedule(context: Context, now: Instant, at: Instant?) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            context, REQ_TICK, Intent(context, WidgetTickReceiver::class.java).setAction(WidgetTickReceiver.ACTION_TICK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (at == null) {
            am.cancel(pi)
            return
        }
        // Measured from the app's clock (debug builds can pin it), applied to the real one.
        val delay = Duration.between(now, at).toMillis().coerceIn(5_000L, Duration.ofDays(1).toMillis())
        val trigger = System.currentTimeMillis() + delay
        try {
            if (AlertScheduler.canExact(context)) am.setExact(AlarmManager.RTC, trigger, pi) else am.set(AlarmManager.RTC, trigger, pi)
        } catch (e: SecurityException) {
            am.set(AlarmManager.RTC, trigger, pi)
        }
    }
}
