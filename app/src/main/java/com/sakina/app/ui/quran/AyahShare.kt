package com.sakina.app.ui.quran

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.sakina.app.R
import com.sakina.app.core.quran.AyahRef
import com.sakina.app.data.QuranData
import com.sakina.app.ui.Strings
import java.io.File

/**
 * Sharing an ayah through Android's share sheet, as text or as a PNG image. Nothing is sent by itself: the user
 * picks the app and the recipient. The original text and its source are always included.
 */
object AyahShare {
    private fun reference(d: QuranData, ref: AyahRef) = "[" + d.meta.surah(ref.sura).nameAr + ": " + arabicNumber(ref.aya) + "]"

    fun text(d: QuranData, ref: AyahRef, str: Strings, withTranslation: Boolean): String {
        val g = d.meta.global(ref)
        return buildString {
            append("﴿").append(d.text.ayahs[g]).append("﴾\n")
            append(reference(d, ref))
            if (withTranslation) {
                append("\n\n").append(d.translation.lines[g])
                append("\n— Talal Itani (ClearQuran), via Tanzil")
            }
            append("\n\n").append(str.textSource)
        }
    }

    fun shareText(context: Context, d: QuranData, ref: AyahRef, str: Strings, withTranslation: Boolean) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text(d, ref, str, withTranslation))
        context.startActivity(Intent.createChooser(send, str.shareText).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Draws the ayah card (1080 px wide) with the Quran font, the reference and the source line. */
    fun render(context: Context, d: QuranData, ref: AyahRef, str: Strings, withTranslation: Boolean): Bitmap {
        val g = d.meta.global(ref)
        val width = 1080
        val pad = 80
        val inner = width - pad * 2
        val quranFont = ResourcesCompat.getFont(context, R.font.amiri_quran) ?: Typeface.SERIF
        val uiFont = ResourcesCompat.getFont(context, R.font.plex_regular) ?: Typeface.SANS_SERIF
        val ink = 0xFF1C1B17.toInt()
        val green = 0xFF0E6B5E.toInt()
        val gold = 0xFF9A7428.toInt()

        fun layout(text: String, font: Typeface, size: Float, color: Int, align: Layout.Alignment, rtl: Boolean, spacing: Float): StaticLayout {
            val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font; textSize = size; this.color = color }
            return StaticLayout.Builder.obtain(text, 0, text.length, p, inner)
                .setAlignment(align)
                .setTextDirection(if (rtl) TextDirectionHeuristics.RTL else TextDirectionHeuristics.LTR)
                .setLineSpacing(0f, spacing)
                .setIncludePad(true)
                .build()
        }

        val body = layout("﴿" + d.text.ayahs[g] + " " + ayahEnd(ref.aya) + "﴾", quranFont, 58f, ink, Layout.Alignment.ALIGN_CENTER, true, 1.25f)
        val refLine = layout("سورة " + d.meta.surah(ref.sura).nameAr + " · الآية " + arabicNumber(ref.aya), uiFont, 34f, green, Layout.Alignment.ALIGN_CENTER, true, 1.1f)
        val trans = if (withTranslation) layout(d.translation.lines[g] + "\n— Talal Itani (ClearQuran), via Tanzil", uiFont, 32f, 0xFF4A4840.toInt(), Layout.Alignment.ALIGN_CENTER, false, 1.2f) else null
        val source = layout(str.textSource + " · سكينة", uiFont, 26f, 0xFF7B786E.toInt(), Layout.Alignment.ALIGN_CENTER, true, 1.1f)

        val gap = 36
        val height = pad + body.height + gap + refLine.height + (trans?.let { gap + it.height } ?: 0) + gap * 2 + source.height + pad
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(0xFFF6F3EC.toInt())
        val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f; color = gold }
        c.drawRoundRect(RectF(24f, 24f, width - 24f, height - 24f), 36f, 36f, frame)
        frame.strokeWidth = 1.5f
        c.drawRoundRect(RectF(38f, 38f, width - 38f, height - 38f), 28f, 28f, frame)
        var y = pad.toFloat()
        fun draw(l: StaticLayout) {
            c.save(); c.translate(pad.toFloat(), y); l.draw(c); c.restore(); y += l.height
        }
        draw(body); y += gap
        draw(refLine)
        trans?.let { y += gap; draw(it) }
        y += gap * 2
        draw(source)
        return bmp
    }

    fun shareImage(context: Context, d: QuranData, ref: AyahRef, str: Strings, withTranslation: Boolean) {
        val bmp = render(context, d, ref, str, withTranslation)
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "ayah_${ref.sura}_${ref.aya}.png")
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        val uri = FileProvider.getUriForFile(context, context.packageName + ".share", file)
        val send = Intent(Intent.ACTION_SEND).setType("image/png")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(file.name, uri)
        context.startActivity(Intent.createChooser(send, str.shareImage).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
}
