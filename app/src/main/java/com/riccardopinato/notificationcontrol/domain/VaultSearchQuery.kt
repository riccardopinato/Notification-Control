package com.riccardopinato.notificationcontrol.domain

object VaultSearchQuery {
    private val unsafe = Regex("""[^\p{L}\p{N}_-]+""")

    fun toFtsQuery(input: String): String? {
        val tokens = input.trim()
            .split(Regex("""\s+"""))
            .map { it.replace(unsafe, "") }
            .filter { it.isNotBlank() }
        if (tokens.isEmpty()) return null
        return tokens.joinToString(" AND ") { "$it*" }
    }
}
