package com.riccardopinato.notificationcontrol.account

import android.util.Base64
import org.json.JSONObject

internal data class GoogleIdTokenClaims(
    val uniqueId: String? = null,
    val email: String? = null
)

internal object GoogleIdTokenClaimsParser {
    fun parse(idToken: String): GoogleIdTokenClaims {
        return runCatching {
            val payload = idToken.split('.').getOrNull(1)
                ?: return@runCatching GoogleIdTokenClaims()
            val decoded = Base64.decode(
                payload,
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
            val json = JSONObject(decoded.toString(Charsets.UTF_8))
            GoogleIdTokenClaims(
                uniqueId = json.optString("sub").takeIf { it.isNotBlank() },
                email = json.optString("email").takeIf { it.isNotBlank() }
            )
        }.getOrDefault(GoogleIdTokenClaims())
    }
}
