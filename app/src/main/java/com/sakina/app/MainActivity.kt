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

    /** The recitation notification opens the listening screen. */
    private fun handleOpen(intent: Intent?) {
        if (intent?.getStringExtra(EXTRA_OPEN) == OPEN_LISTEN && vm.stack.lastOrNull() !is com.sakina.app.ui.Route.Listen) {
            vm.push(com.sakina.app.ui.Route.Listen())
        }
    }

    companion object {
        const val EXTRA_OPEN = "sakina_open"
        const val OPEN_LISTEN = "listen"
    }
}
