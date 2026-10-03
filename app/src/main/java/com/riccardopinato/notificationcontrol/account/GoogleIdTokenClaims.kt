package com.riccardopinato.notificationcontrol.account

import android.util.Base64

internal data class GoogleIdTokenClaims(
    val uniqueId: String? = null,
    val email: String? = null
)

internal object GoogleIdTokenClaimsParser {
    fun parse(idToken: String): GoogleIdTokenClaims =
        parseWithDecoder(idToken) { payload ->
            Base64.decode(
                payload,
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
        }

    internal fun parseWithDecoder(
        idToken: String,
        decoder: (String) -> ByteArray
    ): GoogleIdTokenClaims = runCatching {
        val payload = idToken.split('.').getOrNull(1)
            ?: return@runCatching GoogleIdTokenClaims()
        parsePayloadJson(decoder(payload).toString(Charsets.UTF_8))
    }.getOrDefault(GoogleIdTokenClaims())

    internal fun parsePayloadJson(json: String): GoogleIdTokenClaims =
        GoogleIdTokenClaims(
            uniqueId = stringClaim(json, "sub"),
            email = stringClaim(json, "email")
        )

    private fun stringClaim(json: String, key: String): String? {
        val token = "\"$key\""
        var searchFrom = 0

        while (searchFrom < json.length) {
            val keyStart = json.indexOf(token, searchFrom)
            if (keyStart < 0) return null

            var cursor = keyStart + token.length
            cursor = skipWhitespace(json, cursor)
            if (json.getOrNull(cursor) != ':') {
                searchFrom = keyStart + token.length
                continue
            }

            cursor = skipWhitespace(json, cursor + 1)
            if (json.getOrNull(cursor) != '"') return null

            return readJsonString(json, cursor)
                ?.takeIf { it.isNotBlank() }
        }

        return null
    }

    private fun skipWhitespace(value: String, start: Int): Int {
        var index = start
        while (index < value.length && value[index].isWhitespace()) index++
        return index
    }

    private fun readJsonString(value: String, openingQuote: Int): String? {
        if (value.getOrNull(openingQuote) != '"') return null

        val output = StringBuilder()
        var index = openingQuote + 1
        while (index < value.length) {
            when (val char = value[index++]) {
                '"' -> return output.toString()
                '\\' -> {
                    if (index >= value.length) return null
                    when (val escaped = value[index++]) {
                        '"', '\\', '/' -> output.append(escaped)
                        'b' -> output.append('\b')
                        'f' -> output.append('\u000C')
                        'n' -> output.append('\n')
                        'r' -> output.append('\r')
                        't' -> output.append('\t')
                        'u' -> {
                            if (index + 4 > value.length) return null
                            val codePoint = value.substring(index, index + 4)
                                .toIntOrNull(16) ?: return null
                            output.append(codePoint.toChar())
                            index += 4
                        }
                        else -> return null
                    }
                }
                else -> output.append(char)
            }
        }
        return null
    }
}
