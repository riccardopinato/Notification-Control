package com.riccardopinato.notificationcontrol

import android.app.Application
import android.util.Base64
import com.riccardopinato.notificationcontrol.account.GoogleIdTokenClaimsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class GoogleIdTokenClaimsParserTest {
    @Test
    fun extractsStableSubAndEmail() {
        val token = token(
            """{"sub":"google-user-123","email":"user@example.com"}"""
        )

        val claims = GoogleIdTokenClaimsParser.parse(token)

        assertEquals("google-user-123", claims.uniqueId)
        assertEquals("user@example.com", claims.email)
    }

    @Test
    fun malformedTokenReturnsEmptyClaims() {
        val claims = GoogleIdTokenClaimsParser.parse("not-a-jwt")

        assertNull(claims.uniqueId)
        assertNull(claims.email)
    }

    private fun token(payloadJson: String): String {
        val header = encode("""{"alg":"none"}""")
        val payload = encode(payloadJson)
        return "$header.$payload."
    }

    private fun encode(value: String): String =
        Base64.encodeToString(
            value.toByteArray(),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
}
