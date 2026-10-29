@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.sakina.app.ui.quran

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sakina.app.core.quran.Khatma
import com.sakina.app.data.QuranData
import com.sakina.app.data.QuranOps
import com.sakina.app.data.QuranState
import com.sakina.app.ui.LocalFmt
import com.sakina.app.ui.LocalStrings
import com.sakina.app.ui.MainViewModel
import com.sakina.app.ui.components.SakinaIcons
import com.sakina.app.ui.components.exposeTestTags
import com.sakina.app.ui.theme.AmiriQuran
import java.time.LocalDate

/** Actions for one ayah: bookmark, copy, share, khatma progress, translation and tafsir. */
@Composable
fun AyahSheet(vm: MainViewModel, d: QuranData, qs: QuranState, global: Int, onDismiss: () -> Unit) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val context = LocalContext.current
    val ref = d.meta.ref(global)
    val surah = d.meta.surah(ref.sura)
    val page = d.meta.pageOf(global)
    val marked = QuranOps.isBookmarked(qs, ref.sura, ref.aya)
    var showTranslation by remember { mutableStateOf(qs.reader.showTranslation) }
    var showTafsir by remember { mutableStateOf(qs.reader.showTafsir) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().exposeTestTags().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding()) {
            Text(
                str.surahAyah(surah.nameAr, fmt.num(ref.aya)),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                str.pageN(fmt.num(page)) + " · " + str.juzN(fmt.num(d.meta.juzOf(global))) + " · " + str.quarterName(d.meta.quarterOf(global)) { fmt.num(it) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                d.text.ayahs[global] + " " + ayahEnd(ref.aya),
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = AmiriQuran, fontSize = 26.sp, lineHeight = 52.sp),
                textAlign = TextAlign.Justify,
                modifier = Modifier.fillMaxWidth(),
            )
            d.meta.sajdaAt(global)?.let {
                Text("۩ " + str.sajdaRecommended, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AssistChip(
                    onClick = { vm.updateQuran { QuranOps.toggleBookmark(it, ref.sura, ref.aya, System.currentTimeMillis()) } },
                    label = { Text(if (marked) str.removeBookmark else str.bookmark) },
                    leadingIcon = { Icon(SakinaIcons.Book, null, Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("ayah_bookmark"),
                )
                AssistChip(onClick = { copy(context, AyahShare.text(d, ref, str, showTranslation)); Toast.makeText(context, str.copied, Toast.LENGTH_SHORT).show() },
                    label = { Text(str.copy) })
                AssistChip(onClick = { AyahShare.shareText(context, d, ref, str, showTranslation) }, label = { Text(str.shareText) },
                    modifier = Modifier.testTag("ayah_share_text"))
                AssistChip(onClick = { AyahShare.shareImage(context, d, ref, str, showTranslation) }, label = { Text(str.shareImage) },
                    modifier = Modifier.testTag("ayah_share_image"))
                AssistChip(
                    onClick = {
                        onDismiss()
                        vm.push(com.sakina.app.ui.Route.HifzPractice(ref.sura, ref.aya, minOf(ref.aya + 4, surah.ayahCount)))
                    },
                    label = { Text(str.hifzFromHere) },
                    leadingIcon = { Icon(SakinaIcons.Repeat, null, Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("ayah_hifz"),
                )
                if (qs.khatma != null) {
                    AssistChip(onClick = {
                        vm.updateQuran { s -> s.copy(khatma = s.khatma?.let { Khatma.record(it, page, LocalDate.now()) }) }
                        Toast.makeText(context, str.khatmaRecorded, Toast.LENGTH_SHORT).show()
                    }, label = { Text(str.khatmaRecordHere(fmt.num(page))) })
                }
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            TextButton(onClick = { showTranslation = !showTranslation }) { Text(str.translationTitle) }
            if (showTranslation) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Column(Modifier.fillMaxWidth()) {
                        Text(d.translation.lines[global], style = MaterialTheme.typography.bodyLarge)
                        Text("— " + str.translationSource, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            TextButton(onClick = { showTafsir = !showTafsir }) { Text(str.tafsirTitle) }
            if (showTafsir) {
                Text(d.tafsir.lines[global], style = MaterialTheme.typography.bodyLarge)
                Text("— " + str.tafsirSource, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Text(str.textSource, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun copy(context: Context, text: String) {
    val cm = context.getSystemService(ClipboardManager::class.java)
    cm.setPrimaryClip(ClipData.newPlainText("ayah", text))
}
