package com.riccardopinato.notificationcontrol.data

import java.net.URLDecoder
import java.net.URLEncoder

data class SavedVaultFilter(
    val id: String,
    val name: String,
    val query: String,
    val packageName: String?
)

object SavedVaultFilterCodec {
    private const val VERSION = "v1"
    private const val MAX_NAME = 80
    private const val MAX_QUERY = 500
    private const val MAX_PACKAGE = 250
    private const val CHARSET = "UTF-8"

    fun encode(filter: SavedVaultFilter): String =
        listOf(
            VERSION,
            encodePart(filter.id),
            encodePart(filter.name),
            encodePart(filter.query),
            if (filter.packageName == null) "0" else "1",
            encodePart(filter.packageName.orEmpty())
        ).joinToString("|")

    fun decode(encoded: String): SavedVaultFilter? = runCatching {
        val parts = encoded.split('|')
        if (parts.size != 6 || parts[0] != VERSION) return@runCatching null

        val id = decodePart(parts[1]).trim()
        val name = decodePart(parts[2]).trim().take(MAX_NAME)
        val query = decodePart(parts[3]).trim().take(MAX_QUERY)
        val packageName = if (parts[4] == "1") {
            decodePart(parts[5])
                .trim()
                .take(MAX_PACKAGE)
                .takeIf(String::isNotBlank)
        } else {
            null
        }

        if (id.isBlank() || name.isBlank()) return@runCatching null
        if (query.isBlank() && packageName == null) return@runCatching null

        SavedVaultFilter(
            id = id,
            name = name,
            query = query,
            packageName = packageName
        )
    }.getOrNull()

    private fun encodePart(value: String): String =
        URLEncoder.encode(value, CHARSET)

    private fun decodePart(value: String): String =
        URLDecoder.decode(value, CHARSET)
}
