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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sakina.app.data.AppSettings
import com.sakina.app.ui.LocalStrings
import com.sakina.app.ui.MainViewModel
import com.sakina.app.ui.Route
import com.sakina.app.ui.components.DirectionalIcon
import com.sakina.app.ui.components.SakinaIcons
import com.sakina.app.ui.settings.SettingsGroups

/** A tool in the More tab: a tile in a group. */
private class Tool(val icon: ImageVector, val title: String, val body: String, val tag: String, val onClick: () -> Unit)

/**
 * The More tab: the tools in groups of tiles (worship, seasons, the phone's home screen), then the settings.
 * The tools keep their old Discover tags (tool_*), so links and checks still find them.
 */
@Composable
fun MoreScreen(vm: MainViewModel, s: AppSettings, contentPadding: PaddingValues) {
    val str = LocalStrings.current
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding).padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("more_screen"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(str.tabMore, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
        GroupTitle(str.groupWorship)
        Tiles(listOf(
            Tool(SakinaIcons.Sparkle, str.toolsAdhkar, str.toolsAdhkarShort, "tool_adhkar") { vm.push(Route.Adhkar()) },
            Tool(SakinaIcons.Beads, str.toolsTasbih, str.toolsTasbihShort, "tool_tasbih") { vm.push(Route.Tasbih) },
            Tool(SakinaIcons.Kaaba, str.toolsQibla, str.toolsQiblaShort, "tool_qibla") { vm.push(Route.Qibla) },
        ))
        GroupTitle(str.groupSeasons)
        Tiles(listOf(
            Tool(SakinaIcons.Moon, str.ramadanTitle, str.toolsRamadanShort, "tool_ramadan") { vm.push(Route.Ramadan) },
            Tool(SakinaIcons.Isha, str.nightTitle, str.toolsNightShort, "tool_night") { vm.push(Route.Night) },
            Tool(SakinaIcons.Book, str.fridayTitle, str.toolsFridayShort, "tool_friday") { vm.push(Route.Friday) },
        ))
        GroupTitle(str.groupDevice)
        WideTool(Tool(SakinaIcons.Home, str.widgetsTitle, str.toolsWidgetsBody, "tool_widgets") { vm.push(Route.Widgets) })
        GroupTitle(str.tabSettings)
        SettingsGroups(vm, s)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

/** Three tiles in a row, each with its icon, name and a short line. */
@Composable
private fun Tiles(tools: List<Tool>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (t in tools) {
            Surface(
                onClick = t.onClick,
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.weight(1f).heightIn(min = 128.dp).testTag(t.tag),
            ) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) { Icon(t.icon, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                    Spacer(Modifier.height(10.dp))
                    Text(t.title, style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        t.body, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WideTool(t: Tool) {
    Surface(
        onClick = t.onClick,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().testTag(t.tag),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(t.icon, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(t.title, style = MaterialTheme.typography.titleMedium)
                Text(t.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DirectionalIcon(SakinaIcons.ChevronEnd, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
