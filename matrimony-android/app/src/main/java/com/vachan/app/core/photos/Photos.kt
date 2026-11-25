package com.vachan.app.core.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.runtime.staticCompositionLocalOf
import com.google.crypto.tink.Aead
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.GeneralSecurityException
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Turns a picked image into a safe, upload-ready JPEG:
 *  - decodes with a bounded sample size (a hostile 20,000 px image can't exhaust memory);
 *  - applies the EXIF orientation, then re-encodes, which **drops all metadata** — GPS location,
 *    device model, timestamps — before anything is stored or uploaded;
 *  - caps the long edge at [MAX_EDGE] px.
 */
object PhotoProcessor {
    const val MAX_EDGE = 1600
    const val MIN_EDGE = 320
    private const val QUALITY = 85

    class PhotoException(message: String) : Exception(message)

    /** Smallest power-of-two sample size that keeps the long edge at or above [target]. */
    fun sampleSizeFor(width: Int, height: Int, target: Int = MAX_EDGE): Int {
        var sample = 1
        val longEdge = max(width, height)
        while (longEdge / (sample * 2) >= target) sample *= 2
        return sample
    }

    /** Output dimensions after capping the long edge at [maxEdge], preserving aspect ratio. */
    fun scaledSize(width: Int, height: Int, maxEdge: Int = MAX_EDGE): Pair<Int, Int> {
        val longEdge = max(width, height)
        if (longEdge <= maxEdge) return width to height
        val f = maxEdge.toFloat() / longEdge
        return (width * f).roundToInt().coerceAtLeast(1) to (height * f).roundToInt().coerceAtLeast(1)
    }

    suspend fun process(context: Context, uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: throw PhotoException("We couldn't open that photo.")
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw PhotoException("That file isn't a photo we can use.")
        if (max(bounds.outWidth, bounds.outHeight) < MIN_EDGE) throw PhotoException("Please choose a larger, clearer photo.")

        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight) }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: throw PhotoException("That file isn't a photo we can use.")
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        encode(decoded, orientation)
    }

    /** Rotates per EXIF, scales and re-encodes. The output JPEG carries no EXIF block at all. */
    fun encode(source: Bitmap, exifOrientation: Int = ExifInterface.ORIENTATION_NORMAL): ByteArray {
        val (w, h) = scaledSize(source.width, source.height)
        val m = Matrix().apply {
            postScale(w.toFloat() / source.width, h.toFloat() / source.height)
            when (exifOrientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            }
        }
        val out = Bitmap.createBitmap(source, 0, 0, source.width, source.height, m, true)
        return ByteArrayOutputStream().use { bytes ->
            out.compress(Bitmap.CompressFormat.JPEG, QUALITY, bytes)
            if (out !== source) out.recycle()
            bytes.toByteArray()
        }
    }
}

/**
 * The member's *own* photos, kept on the device encrypted with the app's Keystore-backed AEAD
 * (each file bound to its photo id). Other members' photos are never written to disk: they're
 * fetched through short-lived signed URLs and cached in memory only.
 */
interface PhotoStore {
    suspend fun read(id: String): ByteArray?
    suspend fun write(id: String, jpeg: ByteArray)
    suspend fun delete(id: String)
    /** Called on sign-out so the next person on a shared phone sees nothing. */
    suspend fun clear()
}

class EncryptedPhotoStore(context: Context, private val aead: () -> Aead) : PhotoStore {
    private val dir = File(context.applicationContext.filesDir, "photos")
    private val idPattern = Regex("[A-Za-z0-9_-]{1,64}")

    private fun file(id: String): File {
        require(idPattern.matches(id)) { "Invalid photo id" }
        return File(dir, "$id.bin")
    }

    override suspend fun read(id: String): ByteArray? = withContext(Dispatchers.IO) {
        val f = runCatching { file(id) }.getOrNull() ?: return@withContext null
        if (!f.exists()) return@withContext null
        try { aead().decrypt(f.readBytes(), id.encodeToByteArray()) } catch (_: GeneralSecurityException) { null }
    }

    override suspend fun write(id: String, jpeg: ByteArray) = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val target = file(id)
        val tmp = File(dir, "$id.tmp")
        tmp.writeBytes(aead().encrypt(jpeg, id.encodeToByteArray()))
        if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
        Unit
    }

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) { runCatching { file(id).delete() }; Unit }

    override suspend fun clear() = withContext(Dispatchers.IO) { dir.deleteRecursively(); Unit }
}

/** Provided at the root so photo composables can show the member's own encrypted photos. */
val LocalPhotoStore = staticCompositionLocalOf<PhotoStore?> { null }
