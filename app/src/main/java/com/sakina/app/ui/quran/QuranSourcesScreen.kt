package com.sakina.app.ui.quran

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.sakina.app.ui.LocalStrings
import com.sakina.app.ui.MainViewModel
import com.sakina.app.ui.Route
import com.sakina.app.ui.components.SakinaIcons
import com.sakina.app.ui.components.ScreenBar
import com.sakina.app.ui.components.SectionCard
import com.sakina.app.ui.location.FullScreen

@Composable
fun QuranSourcesScreen(vm: MainViewModel) {
    val str = LocalStrings.current
    val context = LocalContext.current
    FullScreen {
        Column(Modifier.fillMaxSize()) {
            ScreenBar(str.sourcesTitle, onBack = { vm.pop() })
            WithQuran(vm) { d ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SectionCard(title = str.quranTitle, icon = SakinaIcons.Book) {
                        Text(str.quranSourcesBody, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://tanzil.net/")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }) { Text(str.openTanzil) }
                    }
                    Block(str.copyrightBlock, d.textHeader)
                    Block(str.translationTitle, d.translation.header)
                    Block(str.tafsirTitle, d.tafsir.header)
                    SectionCard(title = "Amiri Quran — OFL 1.1", icon = SakinaIcons.Info) {
                        TextButton(onClick = { vm.push(Route.License("licenses/OFL-AmiriQuran.txt", "Amiri Quran — OFL 1.1")) }) { Text(str.showLicense) }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun Block(title: String, lines: List<String>) {
    SectionCard(title = title, icon = SakinaIcons.Info) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(Modifier.fillMaxWidth()) {
                lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
