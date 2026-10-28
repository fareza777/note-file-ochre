package app.ochre.notes.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.exifinterface.media.ExifInterface
import app.ochre.notes.data.local.AttachmentEntity
import app.ochre.notes.data.local.AttachmentKind
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AttachmentTooLargeException(val limitBytes: Long) :
    IOException("Attachment exceeds $limitBytes bytes")

class AttachmentStore(private val context: Context) {

    private val attachmentsDir = File(context.filesDir, "attachments").apply { mkdirs() }
    private val thumbnailsDir = File(context.filesDir, "thumbnails").apply { mkdirs() }

    fun fileFor(entity: AttachmentEntity): File = File(attachmentsDir, entity.fileName)

    fun fileFor(uid: String, fileName: String): File = File(attachmentsDir, fileName)

    fun thumbnailFor(uid: String): File? =
        File(thumbnailsDir, "$uid.webp").takeIf { it.exists() }

    fun newFile(fileName: String): File = File(attachmentsDir, fileName)

    suspend fun importUri(
        uri: Uri,
        memoUid: String,
        kind: AttachmentKind,
        createdAt: Long = System.currentTimeMillis(),
    ): AttachmentEntity = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri) ?: guessMimeFromName(queryDisplayName(uri))
        val extension = extensionForMime(mime, kind)
        val uid = UUID.randomUUID().toString()
        val fileName = "$uid.$extension"
        val destination = File(attachmentsDir, fileName)
        try {
            resolver.openInputStream(uri).use { input ->
                if (input == null) throw IOException("Could not open attachment")
                copyWithLimit(input, destination, MAX_ATTACHMENT_BYTES)
            }
            val originalName = queryDisplayName(uri)
            buildEntity(
                uid = uid,
                memoUid = memoUid,
                kind = kindFromMime(mime, kind),
                fileName = fileName,
                originalName = originalName,
                mime = mime,
                file = destination,
                createdAt = createdAt,
            )
        } catch (t: Throwable) {
            destination.delete()
            File(thumbnailsDir, "$uid.webp").delete()
            throw t
        }
    }

    suspend fun importLocalFile(
        source: File,
        memoUid: String,
        kind: AttachmentKind,
        mimeType: String,
        originalName: String?,
        durationMs: Long? = null,
        waveform: String? = null,
        createdAt: Long = System.currentTimeMillis(),
    ): AttachmentEntity = withContext(Dispatchers.IO) {
        val extension = extensionForMime(mimeType, kind)
        val uid = UUID.randomUUID().toString()
        val fileName = "$uid.$extension"
        val destination = File(attachmentsDir, fileName)
        try {
            source.copyTo(destination, overwrite = true)
            source.delete()
            buildEntity(
                uid = uid,
                memoUid = memoUid,
                kind = kind,
                fileName = fileName,
                originalName = originalName,
                mime = mimeType,
                file = destination,
                createdAt = createdAt,
                durationMs = durationMs,
                waveform = waveform,
            )
        } catch (t: Throwable) {
            destination.delete()
            File(thumbnailsDir, "$uid.webp").delete()
            throw t
        }
    }

    suspend fun importFromStream(
        input: InputStream,
        uid: String,
        memoUid: String,
        kind: AttachmentKind,
        fileName: String,
        originalName: String?,
        mimeType: String,
        createdAt: Long,
        sizeBytes: Long? = null,
        width: Int? = null,
        height: Int? = null,
        durationMs: Long? = null,
        waveform: String? = null,
    ): AttachmentEntity = withContext(Dispatchers.IO) {
        val destination = File(attachmentsDir, fileName)
        destination.parentFile?.mkdirs()
        copyWithLimit(input, destination, MAX_ATTACHMENT_BYTES)
        var finalWidth = width
        var finalHeight = height
        if (kind == AttachmentKind.IMAGE && (finalWidth == null || finalHeight == null)) {
            val dimensions = readDimensions(destination)
            finalWidth = dimensions.first
            finalHeight = dimensions.second
        }
        if (kind == AttachmentKind.IMAGE) {
            createThumbnail(destination, uid)
        }
        val finalDuration = durationMs ?: if (kind == AttachmentKind.AUDIO) readAudioDuration(destination) else null
        AttachmentEntity(
            uid = uid,
            memoUid = memoUid,
            kind = kind,
            fileName = fileName,
            originalName = originalName,
            mimeType = mimeType,
            sizeBytes = sizeBytes ?: destination.length(),
            width = finalWidth,
            height = finalHeight,
            durationMs = finalDuration,
            waveform = waveform,
            createdAt = createdAt,
            deletedAt = null,
        )
    }

    suspend fun deleteFiles(entity: AttachmentEntity) = withContext(Dispatchers.IO) {
        fileFor(entity).delete()
        File(thumbnailsDir, "${entity.uid}.webp").delete()
        Unit
    }

    suspend fun ensureThumbnail(entity: AttachmentEntity) = withContext(Dispatchers.IO) {
        if (entity.kind == AttachmentKind.IMAGE && !File(thumbnailsDir, "${entity.uid}.webp").exists()) {
            val file = fileFor(entity)
            if (file.exists()) createThumbnail(file, entity.uid)
        }
    }

    fun tempRecordingFile(): File =
        File(context.cacheDir, "recording-${UUID.randomUUID()}.m4a")

    private fun buildEntity(
        uid: String,
        memoUid: String,
        kind: AttachmentKind,
        fileName: String,
        originalName: String?,
        mime: String,
        file: File,
        createdAt: Long,
        durationMs: Long? = null,
        waveform: String? = null,
    ): AttachmentEntity {
        var width: Int? = null
        var height: Int? = null
        if (kind == AttachmentKind.IMAGE) {
            val dimensions = readDimensions(file)
            width = dimensions.first
            height = dimensions.second
            createThumbnail(file, uid)
        }
        val resolvedDuration = durationMs ?: if (kind == AttachmentKind.AUDIO) readAudioDuration(file) else null
        return AttachmentEntity(
            uid = uid,
            memoUid = memoUid,
            kind = kind,
            fileName = fileName,
            originalName = originalName,
            mimeType = mime,
            sizeBytes = file.length(),
            width = width,
            height = height,
            durationMs = resolvedDuration,
            waveform = waveform,
            createdAt = createdAt,
            deletedAt = null,
        )
    }

    private fun kindFromMime(mime: String, fallback: AttachmentKind): AttachmentKind = when {
        mime.startsWith("image/") -> AttachmentKind.IMAGE
        mime.startsWith("audio/") -> AttachmentKind.AUDIO
        else -> fallback
    }

    private fun readDimensions(file: File): Pair<Int?, Int?> {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            if (options.outWidth <= 0 || options.outHeight <= 0) return null to null
            val orientation = runCatching {
                ExifInterface(file.absolutePath).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
            if (orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
                orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
                orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
                orientation == ExifInterface.ORIENTATION_TRANSVERSE
            ) {
                options.outHeight to options.outWidth
            } else {
                options.outWidth to options.outHeight
            }
        } catch (_: Throwable) {
            null to null
        }
    }

    private fun createThumbnail(source: File, uid: String) {
        if (source.length() > MAX_THUMBNAIL_SOURCE_BYTES) return
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(source.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return
            val sample = calculateSampleSize(bounds.outWidth, bounds.outHeight, THUMBNAIL_MAX_PX * 2)
            val bitmap = BitmapFactory.decodeFile(
                source.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = sample },
            ) ?: return
            val oriented = applyExifOrientation(source, bitmap)
            val scale = minOf(1f, THUMBNAIL_MAX_PX.toFloat() / maxOf(oriented.width, oriented.height))
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    oriented,
                    (oriented.width * scale).toInt().coerceAtLeast(1),
                    (oriented.height * scale).toInt().coerceAtLeast(1),
                    true,
                )
            } else {
                oriented
            }
            FileOutputStream(File(thumbnailsDir, "$uid.webp")).use { out ->
                scaled.compress(Bitmap.CompressFormat.WEBP, 82, out)
            }
            if (scaled !== oriented) scaled.recycle()
            if (oriented !== bitmap) oriented.recycle()
            bitmap.recycle()
        } catch (_: Throwable) {
            File(thumbnailsDir, "$uid.webp").delete()
        }
    }

    private fun applyExifOrientation(source: File, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            ExifInterface(source.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (_: Throwable) {
            bitmap
        }
    }

    private fun calculateSampleSize(width: Int, height: Int, target: Int): Int {
        var sample = 1
        while (width / (sample * 2) >= target && height / (sample * 2) >= target) {
            sample *= 2
        }
        return sample
    }

    private fun readAudioDuration(file: File): Long? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        } catch (_: Throwable) {
            null
        }
    }

    private fun guessMimeFromName(name: String?): String {
        val extension = name?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }

    private fun extensionForMime(mime: String, kind: AttachmentKind): String {
        val fromMime = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)
        if (!fromMime.isNullOrBlank()) return fromMime.lowercase()
        return when (kind) {
            AttachmentKind.IMAGE -> "jpg"
            AttachmentKind.AUDIO -> "m4a"
            AttachmentKind.FILE -> "bin"
        }
    }

    private fun copyWithLimit(input: InputStream, destination: File, limit: Long) {
        destination.parentFile?.mkdirs()
        var total = 0L
        destination.outputStream().buffered().use { output ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > limit) throw AttachmentTooLargeException(limit)
                output.write(buffer, 0, read)
            }
        }
    }

    companion object {
        const val MAX_ATTACHMENT_BYTES = 50L * 1024 * 1024
        private const val MAX_THUMBNAIL_SOURCE_BYTES = 60L * 1024 * 1024
        private const val THUMBNAIL_MAX_PX = 1200
    }
}
