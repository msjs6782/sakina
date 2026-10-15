@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sakina.app.ui.more

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sakina.app.ui.LocalStrings
import com.sakina.app.ui.MainViewModel
import com.sakina.app.ui.Route
import com.sakina.app.ui.components.Badge
import com.sakina.app.ui.components.DirectionalIcon
import com.sakina.app.ui.components.Divider
import com.sakina.app.ui.components.SakinaIcons
import com.sakina.app.ui.components.SectionCard

/** The Discover tab: the adhkar, tasbih and qibla tools, then what later phases add. */
@Composable
fun DiscoverScreen(vm: MainViewModel, contentPadding: PaddingValues) {
    val str = LocalStrings.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(str.discoverTitle, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
        ToolCard(SakinaIcons.Sparkle, str.toolsAdhkar, str.toolsAdhkarBody, "tool_adhkar") { vm.push(Route.Adhkar()) }
        ToolCard(SakinaIcons.Beads, str.toolsTasbih, str.toolsTasbihBody, "tool_tasbih") { vm.push(Route.Tasbih) }
        ToolCard(SakinaIcons.Kaaba, str.toolsQibla, str.toolsQiblaBody, "tool_qibla") { vm.push(Route.Qibla) }
        ToolCard(SakinaIcons.Calendar, str.calendarTitle, str.toolsCalendarBody, "tool_calendar") { vm.push(Route.HijriCalendar()) }
        ToolCard(SakinaIcons.Moon, str.ramadanTitle, str.toolsRamadanBody, "tool_ramadan") { vm.push(Route.Ramadan) }
        ToolCard(SakinaIcons.Isha, str.nightTitle, str.toolsNightBody, "tool_night") { vm.push(Route.Night) }
        ToolCard(SakinaIcons.Book, str.fridayTitle, str.toolsFridayBody, "tool_friday") { vm.push(Route.Friday) }
        SectionCard(title = str.plannedTitle, icon = SakinaIcons.Calendar) {
            str.plannedLater.forEachIndexed { i, (name, phase) ->
                if (i > 0) Divider()
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Badge(phase)
                }
            }
        }
    }
}

@Composable
private fun ToolCard(icon: ImageVector, title: String, body: String, tag: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().testTag(tag),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DirectionalIcon(SakinaIcons.ChevronEnd, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
