package com.sakina.app.ui.quran

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakina.app.core.quran.Revelation
import com.sakina.app.core.quran.Surah
import com.sakina.app.data.QuranData
import com.sakina.app.ui.LocalFmt
import com.sakina.app.ui.LocalStrings
import com.sakina.app.ui.MainViewModel
import com.sakina.app.ui.theme.Amiri
import com.sakina.app.ui.theme.AmiriQuran
import com.sakina.app.ui.theme.LocalExtras
import kotlin.math.cos
import kotlin.math.sin

/** Arabic-Indic digits for ayah numbers inside the Uthmani text (the ayah-end sign joins with them). */
fun arabicNumber(n: Int): String = n.toString().map { '٠' + (it - '0') }.joinToString("")

/** The ayah-end sign with its number, as the Quran font draws it. */
fun ayahEnd(n: Int): String = "۝" + arabicNumber(n)

/** Waits for the Quran files to load (they are parsed once per process), then shows [content]. */
@Composable
fun WithQuran(vm: MainViewModel, content: @Composable (QuranData) -> Unit) {
    val data by vm.quran.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.loadQuran() }
    val d = data
    if (d == null) {
        Column(Modifier.fillMaxWidth().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(LocalStrings.current.quranLoading, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 12.dp))
        }
    } else {
        content(d)
    }
}

/** An eight-pointed star with the surah number, used in the index. */
@Composable
fun NumberStar(n: Int, size: Dp = 40.dp) {
    val color = MaterialTheme.colorScheme.primary
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val c = Offset(this.size.width / 2, this.size.height / 2)
            val r = this.size.minDimension / 2 * 0.92f
            val path = Path()
            for (i in 0 until 16) {
                val a = Math.PI / 8 * i - Math.PI / 2
                val rr = if (i % 2 == 0) r else r * 0.80f
                val p = Offset(c.x + (rr * cos(a)).toFloat(), c.y + (rr * sin(a)).toFloat())
                if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            path.close()
            drawPath(path, color.copy(alpha = 0.10f))
            drawPath(path, color.copy(alpha = 0.55f), style = Stroke(width = 1.2.dp.toPx()))
        }
        Text(LocalFmt.current.num(n), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

/** The decorative heading above a surah: name, revelation and ayah count. */
@Composable
fun SurahBanner(s: Surah, modifier: Modifier = Modifier) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val ex = LocalExtras.current
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, ex.gold.copy(alpha = 0.6f)),
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Column(Modifier.padding(vertical = 10.dp, horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "سُورَةُ " + s.nameAr,
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = Amiri, fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center,
            )
            Text(
                (if (s.revelation == Revelation.MECCAN) str.meccan else str.medinan) + " · " + str.ayahCount(fmt.num(s.ayahCount)) +
                    if (!str.isAr) " · " + s.nameTranslit else "",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
fun BasmalaLine(text: String, fontSize: Int) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge.copy(fontFamily = AmiriQuran, fontSize = (fontSize + 2).sp, lineHeight = (fontSize * 2.0).sp),
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}
