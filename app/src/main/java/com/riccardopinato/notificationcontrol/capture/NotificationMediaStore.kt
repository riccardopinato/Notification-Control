package com.riccardopinato.notificationcontrol.capture

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
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    fun recoverWhatsAppImage(
        packageName: String,
        postedAt: Long,
        stableKey: String,
        now: Long = System.currentTimeMillis()
    ): String? {
        if (!WhatsAppMediaRecoveryPolicy.supportsPackage(packageName)) return null
        if (!canRecoverWhatsAppImages()) return null

        val resolver = context.contentResolver
        val projection = buildList {
            add(MediaStore.Images.Media._ID)
            add(MediaStore.Images.Media.DATE_ADDED)
            add(MediaStore.Images.Media.DATE_MODIFIED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Images.Media.RELATIVE_PATH)
                add(MediaStore.Images.Media.OWNER_PACKAGE_NAME)
            } else {
                @Suppress("DEPRECATION")
                add(MediaStore.Images.Media.DATA)
            }
        }.toTypedArray()

        val startSeconds =
            ((postedAt - WhatsAppMediaRecoveryPolicy.SEARCH_BEFORE_MS).coerceAtLeast(0L)) / 1000L
        val endMillis = minOf(
            now + WhatsAppMediaRecoveryPolicy.FUTURE_TOLERANCE_MS,
            postedAt + WhatsAppMediaRecoveryPolicy.SEARCH_AFTER_MS
        )
        val endSeconds = endMillis / 1000L
        val selection =
            MediaStore.Images.Media.DATE_ADDED + " >= ? AND " +
                MediaStore.Images.Media.DATE_ADDED + " <= ?"
        val args = arrayOf(startSeconds.toString(), endSeconds.toString())

        val candidates = mutableListOf<WhatsAppMediaRecoveryPolicy.Candidate>()
        runCatching {
            resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                args,
                MediaStore.Images.Media.DATE_ADDED + " DESC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val addedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val modifiedColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
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

                while (cursor.moveToNext() && candidates.size < MAX_QUERY_CANDIDATES) {
                    val id = cursor.getLong(idColumn)
                    val added = cursor.getLong(addedColumn)
                    val modified = cursor.getLong(modifiedColumn)
                    val timestampSeconds = if (added > 0L) added else modified
                    val path = if (pathColumn >= 0 && !cursor.isNull(pathColumn)) {
                        cursor.getString(pathColumn)
                    } else {
                        null
                    }
                    val ownerPackageName =
                        if (ownerColumn >= 0 && !cursor.isNull(ownerColumn)) {
                            cursor.getString(ownerColumn)
                        } else {
                            null
                        }
                    if (
                        WhatsAppMediaRecoveryPolicy.isCompatibleImageCandidate(
                            packageName = packageName,
                            path = path,
                            ownerPackageName = ownerPackageName
                        )
                    ) {
                        candidates += WhatsAppMediaRecoveryPolicy.Candidate(
                            id = id,
                            timestampMillis = timestampSeconds * 1000L,
                            path = path.orEmpty(),
                            ownerPackageName = ownerPackageName
                        )
                    }
                }
            }
        }.getOrElse { return null }

        val selected =
            WhatsAppMediaRecoveryPolicy.chooseCandidate(postedAt, candidates) ?: return null
        val uri = ContentUris.withAppendedId(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            selected.id
        )
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                resolver.loadThumbnail(uri, Size(MAX_SIDE, MAX_SIDE), null)
            }.getOrNull()
        } else {
            loadBitmapFromContentUri(uri)
        } ?: return null

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

    private fun saveBitmap(
        bitmap: Bitmap,
        stableKey: String,
        recycleSource: Boolean
    ): String? {
        val scale =
            minOf(1f, MAX_SIDE.toFloat() / maxOf(bitmap.width, bitmap.height).coerceAtLeast(1))
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

    companion object {
        private const val MAX_SIDE = 1280
        private const val DECODE_BOUND = 2048
        private const val MAX_QUERY_CANDIDATES = 12
        private const val MAX_STORED_MEDIA_BYTES = 6 * 1024 * 1024

        fun stableKey(platformKey: String, postedAt: Long): String =
            platformKey + "\u0000" + postedAt
    }
}
