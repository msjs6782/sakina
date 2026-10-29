package com.sakina.app.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sakina.app.SakinaApp
import com.sakina.app.data.AppClock
import com.sakina.app.data.Backup
import com.sakina.app.ui.LocalFmt
import com.sakina.app.ui.LocalStrings
import com.sakina.app.ui.MainViewModel
import com.sakina.app.ui.components.NoteText
import com.sakina.app.ui.components.SakinaIcons
import com.sakina.app.ui.components.ScreenBar
import com.sakina.app.ui.components.SectionCard
import com.sakina.app.ui.location.FullScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId

/** Saves everything to a file the user chooses, and restores it from one (after showing what it holds). */
@Composable
fun BackupScreen(vm: MainViewModel) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val context = LocalContext.current
    val app = context.applicationContext as SakinaApp
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Backup.Contents?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val text = Backup.encode(app.settings.current(), app.quranState.current(), app.tools.current(), AppClock.now())
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.encodeToByteArray()) }
                }.isSuccess
            }
            Toast.makeText(context, if (ok) str.backupSaved else str.backupFailed, Toast.LENGTH_LONG).show()
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)!!.use { input ->
                        val bytes = input.readNBytesCompat(Backup.MAX_BYTES + 1)
                        Backup.decode(bytes.decodeToString())
                    }
                }.getOrElse { Backup.Read.Bad(Backup.Reason.NOT_JSON) }
            }
            when (r) {
                is Backup.Read.Ok -> pending = r.contents
                is Backup.Read.Bad -> Toast.makeText(context, str.backupBad(r.reason), Toast.LENGTH_LONG).show()
            }
        }
    }
    FullScreen {
        Column(Modifier.fillMaxSize()) {
            ScreenBar(str.backupTitle, onBack = { vm.pop() })
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionCard(title = str.backupSaveTitle, icon = SakinaIcons.Download) {
                    NoteText(str.backupSaveNote)
                    Button(onClick = {
                        val day = AppClock.now().atZone(ZoneId.systemDefault()).toLocalDate()
                        save.launch("sakina-backup-$day.json")
                    }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("backup_save")) { Text(str.backupSave) }
                }
                SectionCard(title = str.backupRestoreTitle, icon = SakinaIcons.Reset) {
                    NoteText(str.backupRestoreNote)
                    OutlinedButton(onClick = { open.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("backup_restore")) { Text(str.backupRestore) }
                }
                NoteText(str.backupNotIncluded)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
    pending?.let { c ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(str.backupConfirmTitle) },
            text = {
                Text(str.backupConfirmBody(
                    c.exportedAt.take(10).let { fmt.digits(it) },
                    fmt.num(c.settings.locations.size), fmt.num(c.quran.bookmarks.size), fmt.num(c.quran.hifz.size), fmt.num(c.tools.prayed.size),
                ), modifier = Modifier.testTag("backup_summary"))
            },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    scope.launch {
                        app.settings.update { c.settings }
                        app.quranState.update { c.quran }
                        app.tools.update { c.tools }
                        Toast.makeText(context, str.backupRestored, Toast.LENGTH_LONG).show()
                    }
                }, modifier = Modifier.testTag("backup_confirm")) { Text(str.backupRestore) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text(str.cancel) } },
        )
    }
}

private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(16 * 1024)
    while (out.size() < limit) {
        val n = read(buf, 0, minOf(buf.size, limit - out.size()))
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}
