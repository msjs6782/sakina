@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sakina.app.ui.adhkar

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakina.app.core.adhkar.Tasbih
import com.sakina.app.core.adhkar.TasbihState
import com.sakina.app.data.ToolsState
import com.sakina.app.ui.LocalFmt
import com.sakina.app.ui.LocalStrings
import com.sakina.app.ui.MainViewModel
import com.sakina.app.ui.components.NoteText
import com.sakina.app.ui.components.SakinaIcons
import com.sakina.app.ui.components.ScreenBar
import com.sakina.app.ui.components.SectionCard
import com.sakina.app.ui.components.SwitchRow
import com.sakina.app.ui.components.exposeTestTags
import com.sakina.app.ui.location.FullScreen
import com.sakina.app.ui.theme.Amiri

private fun ToolsState.tasbihOps() = Tasbih(tasbih, tasbihUndo)
private fun ToolsState.withTasbih(t: Tasbih) = copy(tasbih = t.state, tasbihUndo = t.undo)

private fun vibrate(context: Context, long: Boolean) {
    val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
    if (v == null || !v.hasVibrator()) return
    val effect = if (!long && Build.VERSION.SDK_INT >= 29) VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
    else VibrationEffect.createOneShot(if (long) 280L else 18L, VibrationEffect.DEFAULT_AMPLITUDE)
    v.vibrate(effect)
}

@Composable
fun TasbihScreen(vm: MainViewModel) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val context = LocalContext.current
    val tools by vm.tools.collectAsStateWithLifecycle()
    val t = tools.tasbih
    var customOpen by remember { mutableStateOf(false) }
    var labelOpen by remember { mutableStateOf(false) }
    FullScreen {
        Column(Modifier.fillMaxSize()) {
            ScreenBar(str.tasbihTitle, onBack = { vm.pop() })
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(onClick = { labelOpen = true }, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.testTag("tasbih_label")) {
                    Text(
                        t.label.ifBlank { str.tasbihLabel },
                        style = if (t.label.isBlank()) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleLarge.copy(fontFamily = Amiri),
                        color = if (t.label.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    )
                }
                TapDial(t, fmt.num(t.inRound), str.tasbihTap) {
                    val next = t.count + 1
                    vm.updateTools { s -> s.withTasbih(s.tasbihOps().tap()) }
                    if (t.vibrate) vibrate(context, long = t.target > 0 && next % t.target == 0)
                }
                if (t.target > 0) {
                    Text(
                        (if (t.rounds > 0 && t.inRound == 0) str.tasbihReached + "، " else "") + str.tasbihRounds(fmt.num(t.rounds)) + "، " + str.tasbihTotal(fmt.num(t.count)),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("tasbih_rounds"),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { vm.updateTools { s -> s.withTasbih(s.tasbihOps().undo()) } }, enabled = tools.tasbihUndo != null,
                        modifier = Modifier.testTag("tasbih_undo")) {
                        Icon(SakinaIcons.Undo, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(str.tasbihUndo)
                    }
                    FilledTonalButton(onClick = { vm.updateTools { s -> s.withTasbih(s.tasbihOps().reset()) } }, enabled = t.count > 0,
                        modifier = Modifier.testTag("tasbih_reset")) {
                        Icon(SakinaIcons.Reset, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(str.tasbihReset)
                    }
                }
                NoteText(str.tasbihUndoNote, Modifier.fillMaxWidth())
                SectionCard(title = str.tasbihTarget, icon = SakinaIcons.Check) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = t.target == 0, onClick = { vm.updateTools { s -> s.withTasbih(s.tasbihOps().setTarget(0)) } },
                            label = { Text(str.tasbihNoTarget) }, modifier = Modifier.testTag("tasbih_target_0"))
                        TasbihState.PRESET_TARGETS.forEach { n ->
                            FilterChip(selected = t.target == n, onClick = { vm.updateTools { s -> s.withTasbih(s.tasbihOps().setTarget(n)) } },
                                label = { Text(fmt.num(n)) }, modifier = Modifier.testTag("tasbih_target_$n"))
                        }
                        val custom = t.target > 0 && t.target !in TasbihState.PRESET_TARGETS
                        FilterChip(selected = custom, onClick = { customOpen = true },
                            label = { Text(if (custom) fmt.num(t.target) else str.tasbihCustom) }, modifier = Modifier.testTag("tasbih_target_custom"))
                    }
                    Spacer(Modifier.height(6.dp))
                    SwitchRow(t.vibrate, str.tasbihVibrate) { on -> vm.updateTools { s -> s.withTasbih(s.tasbihOps().setVibrate(on)) } }
                }
            }
        }
    }
    if (customOpen) {
        // Starts empty unless the current target is already a custom one, so typing does not append to "33".
        var text by remember { mutableStateOf(if (t.target > 0 && t.target !in TasbihState.PRESET_TARGETS) t.target.toString() else "") }
        val n = text.filter { it.isDigit() }.take(5).toIntOrNull()
        val valid = n != null && n in 1..TasbihState.MAX_TARGET
        AlertDialog(
            onDismissRequest = { customOpen = false },
            title = { Text(str.tasbihCustomTitle) },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it.filter { c -> c.isDigit() }.take(5) }, singleLine = true,
                    supportingText = { Text(str.tasbihCustomHint) }, isError = text.isNotEmpty() && !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.testTag("tasbih_target_input"))
            },
            confirmButton = {
                TextButton(enabled = valid, onClick = { vm.updateTools { s -> s.withTasbih(s.tasbihOps().setTarget(n!!)) }; customOpen = false },
                    modifier = Modifier.testTag("tasbih_target_save")) { Text(str.save) }
            },
            dismissButton = { TextButton(onClick = { customOpen = false }) { Text(str.cancel) } },
            modifier = Modifier.exposeTestTags(),
        )
    }
    if (labelOpen) {
        var text by remember { mutableStateOf(t.label) }
        AlertDialog(
            onDismissRequest = { labelOpen = false },
            title = { Text(str.tasbihLabel) },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it.take(80) }, singleLine = true, placeholder = { Text(str.tasbihLabelHint) },
                    modifier = Modifier.testTag("tasbih_label_input"))
            },
            confirmButton = {
                TextButton(onClick = { vm.updateTools { s -> s.withTasbih(s.tasbihOps().setLabel(text.trim())) }; labelOpen = false },
                    modifier = Modifier.testTag("tasbih_label_save")) { Text(str.save) }
            },
            dismissButton = { TextButton(onClick = { labelOpen = false }) { Text(str.cancel) } },
            modifier = Modifier.exposeTestTags(),
        )
    }
}

@Composable
private fun TapDial(t: TasbihState, number: String, description: String, onTap: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Surface(
        onClick = onTap,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.size(260.dp).testTag("tasbih_tap").semantics { contentDescription = description },
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (t.target > 0) {
                Canvas(Modifier.fillMaxSize().padding(10.dp)) {
                    val w = 12.dp.toPx()
                    val inset = w / 2
                    val sz = Size(size.width - w, size.height - w)
                    drawArc(track, -90f, 360f, false, Offset(inset, inset), sz, style = Stroke(w))
                    drawArc(primary, -90f, 360f * t.inRound / t.target, false, Offset(inset, inset), sz, style = Stroke(w, cap = StrokeCap.Round))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(number, style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center, modifier = Modifier.testTag("tasbih_count"))
                if (t.target > 0) Text("/ " + LocalFmt.current.num(t.target), style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
            }
        }
    }
}
