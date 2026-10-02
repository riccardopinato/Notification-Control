package com.riccardopinato.notificationcontrol.data

import org.json.JSONObject

data class SavedVaultFilter(
    val id: String,
    val name: String,
    val query: String,
    val packageName: String?
)

object SavedVaultFilterCodec {
    private const val MAX_NAME = 80
    private const val MAX_QUERY = 500
    private const val MAX_PACKAGE = 250

    fun encode(filter: SavedVaultFilter): String =
        JSONObject()
            .put("id", filter.id)
            .put("name", filter.name)
            .put("query", filter.query)
            .put("packageName", filter.packageName ?: JSONObject.NULL)
            .toString()

    fun decode(encoded: String): SavedVaultFilter? = runCatching {
        val objectValue = JSONObject(encoded)
        val id = objectValue.optString("id").trim()
        val name = objectValue.optString("name").trim().take(MAX_NAME)
        val query = objectValue.optString("query").trim().take(MAX_QUERY)
        val packageName = if (objectValue.isNull("packageName")) {
            null
        } else {
            objectValue.optString("packageName")
                .trim()
                .take(MAX_PACKAGE)
                .takeIf(String::isNotBlank)
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
}
