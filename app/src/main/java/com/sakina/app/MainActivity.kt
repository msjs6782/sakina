package com.sakina.app

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
        setContent { SakinaRoot(vm, demo?.view ?: TimesView.DAY) }
    }
}
