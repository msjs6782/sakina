@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.sakina.app.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakina.app.SakinaApp
import com.sakina.app.core.format.Lang
import com.sakina.app.data.ThemeMode
import com.sakina.app.data.WidgetConfig
import com.sakina.app.ui.LocalFmt
import com.sakina.app.ui.LocalStrings
import com.sakina.app.ui.Strings
import com.sakina.app.ui.sunIsUp
import com.sakina.app.ui.theme.SakinaTheme
import com.sakina.app.ui.widgets.WidgetConfigScreen
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.Instant

/**
 * A widget's own settings. The launcher opens it when a widget is added (Android 8–11; optional on 12+) or
 * reconfigured, and the app opens it from its widget list. Changes apply only when saved.
 */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Until saved, backing out of a first-time setup leaves the widget off the home screen (Android 8–11).
        setResult(Activity.RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        val kind = WidgetUpdater.kindOf(this, id)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID || kind == null) {
            Log.w(WidgetUpdater.TAG, "config opened for an unknown widget $id")
            finish()
            return
        }
        val app = application as SakinaApp
        val initial = runBlocking { app.widgets.get(id, kind.id) }
        enableEdgeToEdge()
        setContent { ConfigRoot(app, id, kind, initial) }
    }

    @androidx.compose.runtime.Composable
    private fun ConfigRoot(app: SakinaApp, id: Int, kind: com.sakina.app.core.widgets.WidgetKind, initial: WidgetConfig) {
        val settings by remember { app.settings.settings.map<com.sakina.app.data.AppSettings, com.sakina.app.data.AppSettings?> { it } }.collectAsStateWithLifecycle(initialValue = null)
        val s = settings ?: return
        val scope = rememberCoroutineScope()
        val systemDark = isSystemInDarkTheme()
        val dark = when (s.themeMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.SYSTEM -> systemDark
            ThemeMode.SUN -> sunIsUp(s, Instant.now())?.not() ?: systemDark
        }
        val str = Strings(s.language, s.arabicDigits)
        CompositionLocalProvider(
            LocalLayoutDirection provides if (s.language == Lang.AR) LayoutDirection.Rtl else LayoutDirection.Ltr,
            LocalStrings provides str,
            LocalFmt provides s.fmt,
        ) {
            SakinaTheme(style = s.style, dark = dark, dynamic = s.dynamicColor, heroArt = s.heroArt) {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                    WidgetConfigScreen(
                        app = app, s = s, id = id, kind = kind, initial = initial,
                        onSave = { cfg: WidgetConfig ->
                            scope.launch {
                                app.widgets.put(id, cfg.copy(kind = kind.id))
                                WidgetUpdater.update(this@WidgetConfigActivity, "config", only = setOf(id), force = true)
                                Log.i(WidgetUpdater.TAG, "saved ${kind.id} #$id lang=${cfg.lang} place=${cfg.placeMode}:${cfg.place?.id} theme=${cfg.theme} corner=${cfg.corner} border=${cfg.border} bg=${cfg.backgroundColor}")
                                Toast.makeText(this@WidgetConfigActivity, str.wSaved, Toast.LENGTH_SHORT).show()
                                setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                                finish()
                            }
                        },
                        onRefresh = {
                            scope.launch {
                                WidgetUpdater.update(this@WidgetConfigActivity, "manual:config", only = setOf(id), force = true)
                                Toast.makeText(this@WidgetConfigActivity, str.refreshed, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onBack = { finish() },
                    )
                }
            }
        }
    }
}
