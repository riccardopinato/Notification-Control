package com.riccardopinato.notificationcontrol.capture

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build
import androidx.core.graphics.drawable.toBitmap
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class NotificationMediaStore(private val context: Context) {
    private val directory = File(context.filesDir, "notification_thumbnails").apply { mkdirs() }

    fun savePicture(notification: Notification, stableKey: String): String? {
        val raw = notification.extras?.get(Notification.EXTRA_PICTURE) ?: return null
        val bitmap = when (raw) {
            is Bitmap -> raw
            is Icon -> runCatching { raw.loadDrawable(context)?.toBitmap() }.getOrNull()
            else -> null
        } ?: return null

        val maxSide = 320
        val scale = minOf(1f, maxSide.toFloat() / maxOf(bitmap.width, bitmap.height).coerceAtLeast(1))
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val resized = if (width == bitmap.width && height == bitmap.height) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        }
        val file = File(directory, "${sha256(stableKey)}.webp")
        return runCatching {
            FileOutputStream(file).use { output ->
                val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
                check(resized.compress(format, 72, output))
            }
            if (resized !== bitmap) resized.recycle()
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
        val referenced = referencedPaths.mapNotNull { runCatching { File(it).canonicalPath }.getOrNull() }.toSet()
        directory.listFiles()?.forEach { file ->
            val canonical = runCatching { file.canonicalPath }.getOrNull() ?: return@forEach
            if (canonical !in referenced) runCatching { file.delete() }
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}
