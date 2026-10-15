package com.sakina.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.sakina.app.ui.MainViewModel
import com.sakina.app.ui.SakinaRoot
import com.sakina.app.ui.times.TimesView

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val demo = if (BuildConfig.DEBUG && savedInstanceState == null) Demo.apply(application as SakinaApp, intent) else null
        demo?.tab?.let { vm.tab = it }
        demo?.routes?.forEach { vm.push(it) }
        if (savedInstanceState == null) handleOpen(intent)
        setContent { SakinaRoot(vm, demo?.view ?: TimesView.DAY) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (BuildConfig.DEBUG) Demo.applyOnNew(application as SakinaApp, intent)
        handleOpen(intent)
    }

    /** The recitation notification opens the listening screen; an adhkar reminder opens its set. */
    private fun handleOpen(intent: Intent?) {
        val open = intent?.getStringExtra(EXTRA_OPEN) ?: return
        if (open == OPEN_LISTEN && vm.stack.lastOrNull() !is com.sakina.app.ui.Route.Listen) {
            vm.push(com.sakina.app.ui.Route.Listen())
        }
        if (open.startsWith(OPEN_CALENDAR)) {
            val day = runCatching { java.time.LocalDate.parse(open.removePrefix(OPEN_CALENDAR)) }.getOrNull()
            vm.push(com.sakina.app.ui.Route.HijriCalendar(day?.toEpochDay() ?: Long.MIN_VALUE))
        }
        if (open.startsWith(OPEN_ADHKAR)) {
            val route = com.sakina.app.ui.Route.Wird(open.removePrefix(OPEN_ADHKAR))
            if (vm.stack.lastOrNull() != route) vm.push(route)
        }
    }

    companion object {
        const val EXTRA_OPEN = "sakina_open"
        const val OPEN_LISTEN = "listen"
        const val OPEN_ADHKAR = "adhkar:"
        const val OPEN_CALENDAR = "calendar:"
    }
}
