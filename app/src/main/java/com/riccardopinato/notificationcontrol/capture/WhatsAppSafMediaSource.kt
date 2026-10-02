package com.riccardopinato.notificationcontrol.capture

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

class WhatsAppSafMediaSource(private val context: Context) {
    fun hasPersistedAccess(treeUriString: String?): Boolean {
        if (treeUriString.isNullOrBlank()) return false
        val uri = runCatching { Uri.parse(treeUriString) }.getOrNull() ?: return false
        if (!isWhatsAppImagesTree(uri)) return false
        return context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
    }

    fun isWhatsAppImagesTree(uri: Uri): Boolean {
        val treeId = runCatching { DocumentsContract.getTreeDocumentId(uri) }
            .getOrNull()
            ?.replace('\\', '/')
            ?.lowercase()
            ?: return false
        if (treeId.contains("/sent")) return false
        return treeId.contains("whatsapp/media/whatsapp images") ||
            treeId.endsWith("whatsapp images")
    }

    fun queryCandidates(
        treeUriString: String?,
        postedAt: Long,
        now: Long = System.currentTimeMillis()
    ): List<MediaRecoveryCandidate> {
        if (!hasPersistedAccess(treeUriString)) return emptyList()
        val treeUri = Uri.parse(treeUriString)
        val resolver = context.contentResolver
        val treeId = runCatching {
            DocumentsContract.getTreeDocumentId(treeUri)
        }.getOrNull() ?: return emptyList()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_SIZE
        )

        val earliest = postedAt - WhatsAppMediaRecoveryPolicy.SEARCH_BEFORE_MS
        val latest = minOf(
            now + WhatsAppMediaRecoveryPolicy.FUTURE_TOLERANCE_MS,
            postedAt + WhatsAppMediaRecoveryPolicy.SEARCH_AFTER_MS
        )
        val result = mutableListOf<MediaRecoveryCandidate>()

        runCatching {
            resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID
                )
                val nameColumn = cursor.getColumnIndex(
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                )
                val mimeColumn = cursor.getColumnIndex(
                    DocumentsContract.Document.COLUMN_MIME_TYPE
                )
                val modifiedColumn = cursor.getColumnIndex(
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED
                )
                val sizeColumn = cursor.getColumnIndex(
                    DocumentsContract.Document.COLUMN_SIZE
                )

                while (cursor.moveToNext() && result.size < MAX_CANDIDATES) {
                    val mime = cursor.stringOrNull(mimeColumn)
                    if (mime?.startsWith("image/", true) != true) continue
                    val modified = cursor.longOrZero(modifiedColumn)
                    if (modified <= 0L || modified !in earliest..latest) continue

                    val documentId = cursor.getString(idColumn)
                    val documentUri =
                        DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
                    val name = cursor.stringOrNull(nameColumn)
                    result += MediaRecoveryCandidate(
                        sourceKey = "saf:$documentId",
                        sourceKind = MediaCorrelationEngine.SOURCE_SAF,
                        sourceUri = documentUri.toString(),
                        timestampMillis = modified,
                        path = name,
                        mimeType = mime,
                        sizeBytes = cursor.longOrZero(sizeColumn)
                    )
                }
            }
        }.getOrElse { return emptyList() }

        return result
    }

    private fun android.database.Cursor.stringOrNull(column: Int): String? =
        if (column >= 0 && !isNull(column)) getString(column) else null

    private fun android.database.Cursor.longOrZero(column: Int): Long =
        if (column >= 0 && !isNull(column)) getLong(column) else 0L

    companion object {
        private const val MAX_CANDIDATES = 32
    }
}
