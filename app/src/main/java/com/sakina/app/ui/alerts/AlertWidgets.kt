package com.sakina.app.ui.alerts

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakina.app.alerts.AdhanPreview
import com.sakina.app.alerts.SoundLibrary
import com.sakina.app.core.alerts.NotifyStyle
import com.sakina.app.core.alerts.SoundPref
import com.sakina.app.core.alerts.SoundRef
import com.sakina.app.ui.LocalFmt
import com.sakina.app.ui.LocalStrings
import com.sakina.app.ui.components.SakinaIcons
import com.sakina.app.ui.settings.Segmented
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** A 0…max minutes control: slider for speed, − and + for exact values. [min] = 0 shows "off". */
@Composable
fun MinutesControl(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit, valueText: String? = null) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    var drag by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                valueText ?: if (drag.roundToInt() == 0 && min == 0) str.minutesOff else str.minutesValue(fmt.num(drag.roundToInt())),
                style = MaterialTheme.typography.titleMedium,
                color = if (drag.roundToInt() == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = { onChange((value - 1).coerceAtLeast(min)) }, enabled = value > min, modifier = Modifier.size(36.dp)) {
                Text("−", style = MaterialTheme.typography.titleMedium)
            }
            Slider(
                value = drag,
                onValueChange = { drag = it },
                onValueChangeFinished = { onChange(drag.roundToInt().coerceIn(min, max)) },
                valueRange = min.toFloat()..max.toFloat(),
                modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
            )
            FilledTonalIconButton(onClick = { onChange((value + 1).coerceAtMost(max)) }, enabled = value < max, modifier = Modifier.size(36.dp)) {
                Text("+", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
fun StyleSelector(value: NotifyStyle, onChange: (NotifyStyle) -> Unit) {
    val str = LocalStrings.current
    val all = NotifyStyle.entries
    Segmented(all.map { str.styleNameN(it) }, all.indexOf(value)) { onChange(all[it]) }
}

/** Shows a sound with its volume, a preview button and a picker (bundled candidates or a device file). */
@Composable
fun SoundEditor(title: String, pref: SoundPref, onChange: (SoundPref) -> Unit) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playing by AdhanPreview.playing.collectAsStateWithLifecycle()
    val isPlaying = playing == AdhanPreview.key(pref.sound)
    var picking by remember { mutableStateOf(false) }
    var volume by remember(pref.volume) { mutableFloatStateOf(pref.volume.toFloat()) }
    DisposableEffect(Unit) { onDispose { AdhanPreview.stop() } }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            when (val r = SoundLibrary.import(context, uri)) {
                is SoundLibrary.ImportResult.Ok -> {
                    onChange(pref.copy(sound = r.ref))
                    Toast.makeText(context, str.fileCopied, Toast.LENGTH_SHORT).show()
                }
                SoundLibrary.ImportResult.TooLarge -> Toast.makeText(context, str.fileTooLarge, Toast.LENGTH_LONG).show()
                SoundLibrary.ImportResult.NotAudio -> Toast.makeText(context, str.fileNotAudio, Toast.LENGTH_LONG).show()
                SoundLibrary.ImportResult.Failed -> Toast.makeText(context, str.fileFailed, Toast.LENGTH_LONG).show()
            }
        }
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(SakinaIcons.Music, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(SoundLibrary.displayName(pref.sound, str.isAr), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            FilledTonalIconButton(onClick = { AdhanPreview.toggle(context, pref.sound, volume / 100f) }) {
                Icon(if (isPlaying) SakinaIcons.StopSq else SakinaIcons.Play, if (isPlaying) str.stopPreview else str.preview, Modifier.size(20.dp))
            }
            TextButton(onClick = { picking = true }) { Text(str.chooseSound) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(SakinaIcons.Volume, str.volume, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Slider(
                value = volume,
                onValueChange = { volume = it; if (isPlaying) AdhanPreview.setVolume(it / 100f) },
                onValueChangeFinished = { onChange(pref.copy(volume = volume.roundToInt())) },
                valueRange = 0f..100f,
                steps = 19,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text(str.volumeValue(fmt.num(volume.roundToInt())), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 44.dp))
        }
    }
    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(str.chooseSound) },
            text = {
                Column {
                    SoundLibrary.bundled.forEach { b ->
                        val ref = SoundRef(bundled = b.id)
                        SoundOption(selected = !pref.sound.isFile && pref.sound.bundled == b.id, title = if (str.isAr) b.titleAr else b.titleEn, detail = str.candidateBadge) {
                            onChange(pref.copy(sound = ref)); picking = false
                        }
                    }
                    if (pref.sound.isFile) {
                        SoundOption(selected = true, title = pref.sound.displayName ?: "", detail = null) { picking = false }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { picking = false; launcher.launch(arrayOf("audio/*")) }, modifier = Modifier.fillMaxWidth()) {
                        Text(str.pickFile)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = false }) { Text(str.close) } },
        )
    }
}

@Composable
private fun SoundOption(selected: Boolean, title: String, detail: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (detail != null) Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
        }
    }
}

/** A status line with an action, used for permissions. */
@Composable
fun PermissionRow(title: String, body: String, ok: Boolean, okText: String, actionText: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = CircleShape,
            color = if (ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.size(28.dp),
        ) {
            Icon(
                if (ok) SakinaIcons.Check else SakinaIcons.Warning, null, Modifier.padding(5.dp),
                tint = if (ok) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        if (ok) Text(okText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        else TextButton(onClick = onAction) { Text(actionText) }
    }
}

@Composable
fun ChipRow(content: @Composable () -> Unit) = Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
