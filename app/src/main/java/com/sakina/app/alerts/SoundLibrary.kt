package com.sakina.app.alerts

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.RawRes
import com.sakina.app.core.alerts.SoundRef
import com.sakina.app.data.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * A recording shipped with the app. These are candidates: their content still needs review, so the app
 * shows their source and license and never presents them as approved recordings.
 */
data class BundledSound(
    val id: String,
    @RawRes val res: Int,
    val titleAr: String,
    val titleEn: String,
    val descriptionAr: String,
    val descriptionEn: String,
    val author: String,
    val sourceUrl: String,
    val license: String,
    val licenseUrl: String,
    val sha256: String,
    val durationSec: Int,
)

sealed interface ResolvedSound {
    data class Raw(val res: Int) : ResolvedSound
    data class FileSound(val file: File) : ResolvedSound
}

object SoundLibrary {
    const val MAX_IMPORT_BYTES = 20L * 1024 * 1024

    val bundled: List<BundledSound> = BundledSounds.list

    fun find(id: String?): BundledSound? = bundled.firstOrNull { it.id == id }

    private fun dir(context: Context) = File(context.filesDir, "sounds").apply { mkdirs() }

    /** The sound to play; a missing user file falls back to the first bundled candidate ([fellBack] = true). */
    fun resolve(context: Context, ref: SoundRef): Pair<ResolvedSound, Boolean> {
        if (ref.fileName != null) {
            val f = File(dir(context), ref.fileName)
            if (f.isFile && f.length() > 0) return ResolvedSound.FileSound(f) to false
            return ResolvedSound.Raw(bundled.first().res) to true
        }
        val b = find(ref.bundled) ?: bundled.first()
        return ResolvedSound.Raw(b.res) to false
    }

    fun openRaw(context: Context, res: Int): AssetFileDescriptor = context.resources.openRawResourceFd(res)

    fun displayName(ref: SoundRef, arabic: Boolean): String =
        ref.displayName?.takeIf { ref.isFile } ?: find(ref.bundled)?.let { if (arabic) it.titleAr else it.titleEn } ?: (ref.bundled ?: "")

    sealed interface ImportResult {
        data class Ok(val ref: SoundRef) : ImportResult
        data object TooLarge : ImportResult
        data object NotAudio : ImportResult
        data object Failed : ImportResult
    }

    /** Copies a user-chosen audio file into the app, so it keeps working if the original moves. */
    suspend fun import(context: Context, uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        var name = "audio"
        var size = -1L
        runCatching {
            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    name = c.getString(0) ?: name
                    size = if (c.isNull(1)) -1 else c.getLong(1)
                }
            }
        }
        if (size > MAX_IMPORT_BYTES) return@withContext ImportResult.TooLarge
        val ext = name.substringAfterLast('.', "").lowercase().takeIf { it.length in 2..5 } ?: "audio"
        val target = File(dir(context), "user_" + UUID.randomUUID().toString().take(12) + "." + ext)
        try {
            var copied = 0L
            cr.openInputStream(uri)?.use { input ->
                target.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        copied += n
                        if (copied > MAX_IMPORT_BYTES) throw IllegalStateException("too large")
                        out.write(buf, 0, n)
                    }
                }
            } ?: return@withContext ImportResult.Failed
        } catch (e: IllegalStateException) {
            target.delete(); return@withContext ImportResult.TooLarge
        } catch (e: Exception) {
            target.delete(); return@withContext ImportResult.Failed
        }
        val ms = runCatching {
            MediaMetadataRetriever().run {
                setDataSource(target.absolutePath)
                val d = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                val audio = extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
                release()
                if (audio == null || audio == "yes") d else null
            }
        }.getOrNull()
        if (ms == null || ms < 1000) {
            target.delete(); return@withContext ImportResult.NotAudio
        }
        ImportResult.Ok(SoundRef(bundled = null, fileName = target.name, displayName = name))
    }

    /** Deletes copied files that no setting refers to any more. */
    fun cleanup(context: Context, s: AppSettings) {
        val a = s.alerts
        val used = (listOf(a.generalSound, a.fajrSound) + a.prayers.values.mapNotNull { it.sound }).mapNotNull { it.sound.fileName }.toSet()
        dir(context).listFiles()?.filter { it.name.startsWith("user_") && it.name !in used }?.forEach { it.delete() }
    }
}
