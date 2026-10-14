package com.sakina.app.ui.mushaf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakina.app.core.mushaf.MushafManifest
import com.sakina.app.data.FullDownload
import com.sakina.app.data.NetState
import com.sakina.app.data.QuranData
import com.sakina.app.data.StopReason
import com.sakina.app.ui.LocalFmt
import com.sakina.app.ui.LocalStrings
import com.sakina.app.ui.MainViewModel
import com.sakina.app.ui.components.Banner
import com.sakina.app.ui.components.Divider
import com.sakina.app.ui.components.NoteText
import com.sakina.app.ui.components.SakinaIcons
import com.sakina.app.ui.components.ScreenBar
import com.sakina.app.ui.components.SectionCard
import com.sakina.app.ui.components.SwitchRow
import com.sakina.app.ui.components.exposeTestTags
import com.sakina.app.ui.location.FullScreen
import com.sakina.app.ui.quran.WithQuran
import kotlinx.coroutines.launch

@Composable
fun MushafDownloadsScreen(vm: MainViewModel) {
    val str = LocalStrings.current
    val repo = vm.mushaf
    // The whole-Mushaf download only runs while this screen is open and the app is in front.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) repo.stopFull(StopReason.BACKGROUND) }
        owner.lifecycle.addObserver(obs)
        onDispose {
            owner.lifecycle.removeObserver(obs)
            repo.stopFull(StopReason.LEFT_SCREEN)
        }
    }
    FullScreen {
        Column(Modifier.fillMaxSize()) {
            ScreenBar(str.dlTitle, onBack = { vm.pop() })
            WithQuran(vm) { d -> DownloadsBody(vm, d) }
        }
    }
}

@Composable
private fun DownloadsBody(vm: MainViewModel, d: QuranData) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val repo = vm.mushaf
    val m = repo.manifest
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { repo.init() }
    val available by repo.available.collectAsStateWithLifecycle()
    val full by repo.full.collectAsStateWithLifecycle()
    val qs by vm.quranState.collectAsStateWithLifecycle()
    var meteredAsk by remember { mutableStateOf(false) }
    var offline by remember { mutableStateOf(false) }
    var deleteAsk by remember { mutableStateOf<Pair<String, List<Int>>?>(null) }
    var verifyText by remember { mutableStateOf<String?>(null) }
    var verifying by remember { mutableStateOf(false) }
    val running = full is FullDownload.Running
    val missing = (1..MushafManifest.PAGES).filter { it !in available }

    fun start() {
        offline = false
        when (repo.network()) {
            NetState.OFFLINE -> offline = true
            NetState.METERED -> meteredAsk = true
            NetState.UNMETERED -> repo.startFull()
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionCard(title = str.mushafTitle, icon = SakinaIcons.Storage, modifier = Modifier.testTag("dl_summary")) {
            Text(str.dlCount(fmt.num(available.size), fmt.num(MushafManifest.PAGES)), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("dl_count"))
            LinearProgressIndicator(progress = { available.size / MushafManifest.PAGES.toFloat() }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            Text(str.dlStored(fmt.bytes(m.sizeOf(available))), style = MaterialTheme.typography.bodyMedium)
            Text(str.dlTotal(String.format(java.util.Locale.US, "%.2f MiB", m.totalSize / 1048576.0)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (missing.isNotEmpty()) {
                Text(str.dlRemaining(fmt.num(missing.size), missing.size, fmt.bytes(m.sizeOf(missing)), fmt.bytes(m.compressedOf(missing))), style = MaterialTheme.typography.bodyMedium)
            }
            Text(str.dlFree(fmt.bytes(repo.freeBytes())), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            when (val f = full) {
                is FullDownload.Running -> {
                    LinearProgressIndicator(progress = { if (f.bytesTotal > 0) (f.bytesIn.toFloat() / f.bytesTotal).coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth().testTag("dl_progress"))
                    Text(str.dlProgress(fmt.num(f.done), fmt.num(f.total)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp).testTag("dl_progress_text"))
                    Text(str.dlBytes(fmt.bytes(f.bytesIn), fmt.bytes(f.bytesTotal)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = { repo.stopFull(StopReason.USER) }, modifier = Modifier.fillMaxWidth().testTag("dl_stop")) { Text(str.dlStop) }
                }
                else -> {
                    when (f) {
                        is FullDownload.Stopped -> Banner(
                            str.dlStopped(f.reason, fmt.bytes(m.sizeOf(missing))) + if (f.done > 0) " " + str.dlFetchedNow(fmt.num(f.done), f.done) else "",
                            if (f.reason in setOf(StopReason.USER, StopReason.LEFT_SCREEN, StopReason.BACKGROUND)) SakinaIcons.Info else SakinaIcons.Warning,
                            warning = f.reason !in setOf(StopReason.USER, StopReason.LEFT_SCREEN, StopReason.BACKGROUND),
                            modifier = Modifier.testTag("dl_stopped"),
                        )
                        is FullDownload.Completed -> if (missing.isEmpty()) Banner(str.dlComplete, SakinaIcons.Check)
                        else -> {}
                    }
                    if (offline) Banner(str.offline, SakinaIcons.Warning, warning = true)
                    if (missing.isNotEmpty()) {
                        Button(onClick = { start() }, modifier = Modifier.fillMaxWidth().testTag("dl_start")) {
                            Icon(SakinaIcons.Download, null, Modifier.size(18.dp)); Spacer(Modifier.size(8.dp))
                            Text(if (available.size > MushafManifest.BUNDLED.size) str.dlResume else str.dlStart)
                        }
                    } else if (f !is FullDownload.Completed) {
                        Banner(str.dlComplete, SakinaIcons.Check)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            NoteText(str.dlStaysNote)
        }

        SectionCard(title = str.printedView, icon = SakinaIcons.Page) {
            Box(Modifier.testTag("dl_auto_switch")) {
                SwitchRow(qs.reader.autoDownload, str.autoDownload, str.autoDownloadNote) { v -> vm.updateQuran { it.copy(reader = it.reader.copy(autoDownload = v)) } }
            }
        }

        SectionCard(title = str.dlManage, icon = SakinaIcons.Grid) {
            OutlinedButton(
                onClick = {
                    if (running) return@OutlinedButton
                    verifying = true
                    scope.launch {
                        val bad = repo.verifyAll { i, n -> verifyText = str.dlVerifying(fmt.num(i), fmt.num(n)) }
                        val n = repo.available.value.size + bad.size
                        verifyText = if (bad.isEmpty()) str.dlVerified(fmt.num(n), n) else str.dlVerifyRemoved(fmt.num(bad.size), bad.size)
                        verifying = false
                    }
                },
                enabled = !verifying && !running,
                modifier = Modifier.fillMaxWidth().testTag("dl_verify"),
            ) { Text(str.dlVerify) }
            verifyText?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 6.dp).testTag("dl_verify_result")) }
            if (running) NoteText(str.dlBusy)
            Spacer(Modifier.height(6.dp))
            for (j in 1..30) {
                val from = d.meta.pageOf(d.meta.juzStartGlobal(j))
                val to = if (j < 30) d.meta.pageOf(d.meta.juzStartGlobal(j + 1)) - 1 else MushafManifest.PAGES
                val pages = (from..to).toList()
                val have = pages.count { it in available }
                val deletable = pages.filter { it in available && it !in MushafManifest.BUNDLED }
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).testTag("dl_juz_$j"), verticalAlignment = Alignment.CenterVertically) {
                    Text(str.juzN(fmt.num(j)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(str.dlJuzLine(fmt.num(have), fmt.num(pages.size)), style = MaterialTheme.typography.bodySmall,
                        color = if (have == pages.size) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { deleteAsk = str.dlDeleteJuzQ(fmt.num(j)) to deletable }, enabled = deletable.isNotEmpty() && !running,
                        modifier = Modifier.testTag("dl_delete_juz_$j")) { Text(str.delete) }
                }
            }
            Divider()
            val all = available.filter { it !in MushafManifest.BUNDLED }
            TextButton(
                onClick = { deleteAsk = str.dlDeleteAllQ(fmt.num(all.size), all.size, fmt.bytes(m.sizeOf(all))) to all },
                enabled = all.isNotEmpty() && !running,
                modifier = Modifier.testTag("dl_delete_all"),
            ) { Text(str.dlDeleteAll, color = MaterialTheme.colorScheme.error) }
        }

        NoteText(str.dlSource)
        Spacer(Modifier.height(16.dp))
    }

    if (meteredAsk) {
        AlertDialog(
            onDismissRequest = { meteredAsk = false },
            text = { Text(str.dlMetered(fmt.bytes(m.compressedOf(missing)))) },
            confirmButton = { Button(onClick = { meteredAsk = false; repo.startFull() }, modifier = Modifier.exposeTestTags().testTag("dl_metered_ok")) { Text(str.dlContinue) } },
            dismissButton = { TextButton(onClick = { meteredAsk = false }) { Text(str.cancel) } },
        )
    }
    deleteAsk?.let { (question, pages) ->
        AlertDialog(
            onDismissRequest = { deleteAsk = null },
            text = { Text(question) },
            confirmButton = {
                TextButton(onClick = { deleteAsk = null; scope.launch { repo.delete(pages) } }, modifier = Modifier.exposeTestTags().testTag("dl_delete_ok")) {
                    Text(str.delete, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteAsk = null }) { Text(str.cancel) } },
        )
    }
}
