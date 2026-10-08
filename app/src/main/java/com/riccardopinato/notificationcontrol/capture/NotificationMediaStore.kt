package com.riccardopinato.notificationcontrol.capture

import com.riccardopinato.notificationcontrol.BuildConfig

import android.Manifest
import android.app.Notification
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.math.max

data class MediaStoreSnapshot(
    val version: String?,
    val generation: Long?
)

class NotificationMediaStore(private val context: Context) {
    private val directory = File(context.filesDir, "notification_thumbnails").apply { mkdirs() }

    fun savePicture(
        notification: Notification,
        stableKey: String,
        messages: List<CapturedMessage> = emptyList()
    ): String? {
        val messageUri = messages
            .asReversed()
            .firstOrNull {
                it.mimeType?.startsWith("image/", ignoreCase = true) == true &&
                    !it.dataUri.isNullOrBlank()
            }
            ?.dataUri
            ?.let(Uri::parse)

        val fromMessage = messageUri
            ?.takeIf { it.scheme == "content" }
            ?.let { uri ->
                loadBitmapFromContentUri(uri)
                    ?.let { bitmap -> saveBitmap(bitmap, stableKey, recycleSource = true) }
            }
        if (fromMessage != null) return fromMessage

        val extras = notification.extras
        val rawPicture =
            extras?.get(Notification.EXTRA_PICTURE)
                ?: extras?.get(NotificationCompat.EXTRA_PICTURE_ICON)

        val bitmap = when (rawPicture) {
            is Bitmap -> rawPicture
            is Icon -> runCatching { rawPicture.loadDrawable(context)?.toBitmap() }.getOrNull()
            else -> null
        } ?: return null

        return saveBitmap(bitmap, stableKey, recycleSource = rawPicture is Icon)
    }

    fun canRecoverWhatsAppImages(): Boolean {
        if (!BuildConfig.MEDIASTORE_RECOVERY_ENABLED) return false
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun snapshot(): MediaStoreSnapshot {
        if (!canRecoverWhatsAppImages() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return MediaStoreSnapshot(null, null)
        }
        val volume = MediaStore.VOLUME_EXTERNAL_PRIMARY
        return runCatching {
            MediaStoreSnapshot(
                version = MediaStore.getVersion(context, volume),
                generation = MediaStore.getGeneration(context, volume)
            )
        }.getOrDefault(MediaStoreSnapshot(null, null))
    }

    fun queryWhatsAppCandidates(
        packageName: String,
        postedAt: Long,
        baselineGeneration: Long?,
        expectedVersion: String?,
        now: Long = System.currentTimeMillis()
    ): List<MediaRecoveryCandidate> {
        if (!WhatsAppMediaRecoveryPolicy.supportsPackage(packageName)) return emptyList()
        if (!canRecoverWhatsAppImages()) return emptyList()

        val resolver = context.contentResolver
        val useGeneration =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                baselineGeneration != null &&
                expectedVersion != null &&
                runCatching {
                    MediaStore.getVersion(
                        context,
                        MediaStore.VOLUME_EXTERNAL_PRIMARY
                    ) == expectedVersion
                }.getOrDefault(false)

        val projection = buildList {
            add(MediaStore.Images.Media._ID)
            add(MediaStore.Images.Media.DATE_ADDED)
            add(MediaStore.Images.Media.DATE_MODIFIED)
            add(MediaStore.Images.Media.MIME_TYPE)
            add(MediaStore.Images.Media.WIDTH)
            add(MediaStore.Images.Media.HEIGHT)
            add(MediaStore.Images.Media.SIZE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Images.Media.RELATIVE_PATH)
                add(MediaStore.Images.Media.OWNER_PACKAGE_NAME)
            } else {
                @Suppress("DEPRECATION")
                add(MediaStore.Images.Media.DATA)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add(MediaStore.MediaColumns.GENERATION_ADDED)
            }
        }.toTypedArray()

        val startSeconds =
            ((postedAt - WhatsAppMediaRecoveryPolicy.SEARCH_BEFORE_MS).coerceAtLeast(0L)) / 1000L
        val endMillis = minOf(
            now + WhatsAppMediaRecoveryPolicy.FUTURE_TOLERANCE_MS,
            postedAt + WhatsAppMediaRecoveryPolicy.SEARCH_AFTER_MS
        )
        val endSeconds = endMillis / 1000L

        val selections = mutableListOf(
            MediaStore.Images.Media.DATE_ADDED + " >= ?",
            MediaStore.Images.Media.DATE_ADDED + " <= ?"
        )
        val args = mutableListOf(startSeconds.toString(), endSeconds.toString())

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val candidates = mutableListOf<MediaRecoveryCandidate>()

        runCatching {
            resolver.query(
                collection,
                projection,
                selections.joinToString(" AND "),
                args.toTypedArray(),
                MediaStore.Images.Media.DATE_ADDED + " DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val addedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val modifiedColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                val mimeColumn = cursor.getColumnIndex(MediaStore.Images.Media.MIME_TYPE)
                val widthColumn = cursor.getColumnIndex(MediaStore.Images.Media.WIDTH)
                val heightColumn = cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT)
                val sizeColumn = cursor.getColumnIndex(MediaStore.Images.Media.SIZE)
                val pathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
                } else {
                    @Suppress("DEPRECATION")
                    cursor.getColumnIndex(MediaStore.Images.Media.DATA)
                }
                val ownerColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Images.Media.OWNER_PACKAGE_NAME)
                } else {
                    -1
                }
                val generationColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.GENERATION_ADDED)
                } else {
                    -1
                }

                while (cursor.moveToNext() && candidates.size < MAX_QUERY_CANDIDATES) {
                    val id = cursor.getLong(idColumn)
                    val added = cursor.getLong(addedColumn)
                    val modified = cursor.getLong(modifiedColumn)
                    val timestampSeconds = if (added > 0L) added else modified
                    val path = cursor.stringOrNull(pathColumn)
                    val ownerPackageName = cursor.stringOrNull(ownerColumn)

                    if (
                        !WhatsAppMediaRecoveryPolicy.isCompatibleImageCandidate(
                            packageName = packageName,
                            path = path,
                            ownerPackageName = ownerPackageName
                        )
                    ) {
                        continue
                    }

                    val uri = ContentUris.withAppendedId(collection, id)
                    val generation = if (useGeneration && generationColumn >= 0) {
                        cursor.getLong(generationColumn)
                    } else {
                        null
                    }

                    candidates += MediaRecoveryCandidate(
                        sourceKey = "mediastore:" + uri.toString(),
                        sourceKind = MediaCorrelationEngine.SOURCE_MEDIASTORE,
                        sourceUri = uri.toString(),
                        mediaStoreId = id,
                        timestampMillis = timestampSeconds * 1000L,
                        path = path,
                        ownerPackageName = ownerPackageName,
                        generationAdded = generation,
                        mimeType = cursor.stringOrNull(mimeColumn),
                        width = cursor.intOrZero(widthColumn),
                        height = cursor.intOrZero(heightColumn),
                        sizeBytes = cursor.longOrZero(sizeColumn)
                    )
                }
            }
        }.getOrElse { return emptyList() }

        return candidates
    }

    fun withPerceptualHash(candidate: MediaRecoveryCandidate): MediaRecoveryCandidate {
        if (candidate.perceptualHash != null) return candidate
        val bitmap = loadCandidateBitmap(candidate, HASH_WIDTH, HASH_HEIGHT) ?: return candidate
        val hash = runCatching { perceptualHash(bitmap) }.getOrNull()
        if (!bitmap.isRecycled) bitmap.recycle()
        return candidate.copy(perceptualHash = hash)
    }

    fun perceptualHashForPath(path: String?): String? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.isFile) return null
        val bitmap = runCatching {
            BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply { inSampleSize = 2 }
            )
        }.getOrNull() ?: return null
        return runCatching { perceptualHash(bitmap) }
            .also { if (!bitmap.isRecycled) bitmap.recycle() }
            .getOrNull()
    }

    fun copyCandidate(candidate: MediaRecoveryCandidate, stableKey: String): String? {
        val bitmap = loadCandidateBitmap(candidate, MAX_SIDE, MAX_SIDE) ?: return null
        return saveBitmap(bitmap, stableKey, recycleSource = true)
    }

    fun read(path: String?): ByteArray? {
        if (path.isNullOrBlank()) return null
        return runCatching {
            val file = File(path)
            if (file.parentFile?.canonicalFile != directory.canonicalFile) return@runCatching null
            if (!file.isFile || file.length() > MAX_STORED_MEDIA_BYTES) return@runCatching null
            file.readBytes()
        }.getOrNull()
    }

    fun restorePicture(stableKey: String, bytes: ByteArray): String? {
        if (bytes.isEmpty() || bytes.size > MAX_STORED_MEDIA_BYTES) return null
        val file = fileFor(stableKey)
        return runCatching {
            FileOutputStream(file).use { it.write(bytes) }
            file.absolutePath
        }.getOrNull()
    }

    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        runCatching {
            val file = File(path)
            if (file.parentFile?.canonicalFile == directory.canonicalFile) file.delete()
        }
    }

    fun cleanupOrphans(referencedPaths: Collection<String>) {
        val referenced = referencedPaths.mapNotNull {
            runCatching { File(it).canonicalPath }.getOrNull()
        }.toSet()
        directory.listFiles()?.forEach { file ->
            val canonical = runCatching { file.canonicalPath }.getOrNull() ?: return@forEach
            if (canonical !in referenced) runCatching { file.delete() }
        }
    }

    private fun loadCandidateBitmap(
        candidate: MediaRecoveryCandidate,
        width: Int,
        height: Int
    ): Bitmap? {
        val uri = runCatching { Uri.parse(candidate.sourceUri) }.getOrNull() ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                context.contentResolver.loadThumbnail(uri, Size(width, height), null)
            }.getOrNull() ?: loadBitmapFromContentUri(uri)
        } else {
            loadBitmapFromContentUri(uri)
        }
    }

    private fun loadBitmapFromContentUri(uri: Uri): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
        }.getOrNull()
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (
            bounds.outWidth / sample > DECODE_BOUND ||
            bounds.outHeight / sample > DECODE_BOUND
        ) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return runCatching {
            resolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }
        }.getOrNull()
    }

    private fun perceptualHash(source: Bitmap): String {
        val scaled = Bitmap.createScaledBitmap(source, HASH_WIDTH, HASH_HEIGHT, true)
        var hash = 0UL
        var bit = 0
        for (y in 0 until HASH_HEIGHT) {
            for (x in 0 until HASH_WIDTH - 1) {
                val left = scaled.getPixel(x, y)
                val right = scaled.getPixel(x + 1, y)
                if (luma(left) > luma(right)) {
                    hash = hash or (1UL shl bit)
                }
                bit++
            }
        }
        if (scaled !== source && !scaled.isRecycled) scaled.recycle()
        return hash.toString(16).padStart(16, '0')
    }

    private fun luma(color: Int): Int {
        val r = color shr 16 and 0xff
        val g = color shr 8 and 0xff
        val b = color and 0xff
        return (299 * r + 587 * g + 114 * b) / 1000
    }

    private fun saveBitmap(
        bitmap: Bitmap,
        stableKey: String,
        recycleSource: Boolean
    ): String? {
        val scale =
            minOf(1f, MAX_SIDE.toFloat() / max(bitmap.width, bitmap.height).coerceAtLeast(1))
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val resized = if (width == bitmap.width && height == bitmap.height) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        }
        val file = fileFor(stableKey)
        return runCatching {
            FileOutputStream(file).use { output ->
                val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
                check(resized.compress(format, 82, output))
            }
            file.absolutePath
        }.also {
            if (resized !== bitmap) resized.recycle()
            if (recycleSource && !bitmap.isRecycled) bitmap.recycle()
        }.getOrNull()
    }

    private fun fileFor(stableKey: String): File =
        File(directory, "${sha256(stableKey)}.webp")

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private fun android.database.Cursor.stringOrNull(column: Int): String? =
        if (column >= 0 && !isNull(column)) getString(column) else null

    private fun android.database.Cursor.intOrZero(column: Int): Int =
        if (column >= 0 && !isNull(column)) getInt(column) else 0

    private fun android.database.Cursor.longOrZero(column: Int): Long =
        if (column >= 0 && !isNull(column)) getLong(column) else 0L

    companion object {
        private const val MAX_SIDE = 1280
        private const val DECODE_BOUND = 2048
        private const val MAX_QUERY_CANDIDATES = 32
        private const val MAX_STORED_MEDIA_BYTES = 6 * 1024 * 1024
        private const val HASH_WIDTH = 9
        private const val HASH_HEIGHT = 8

        fun stableKey(platformKey: String, postedAt: Long): String =
            platformKey + "\u0000" + postedAt
    }
}
