package com.riccardopinato.notificationcontrol

import com.riccardopinato.notificationcontrol.account.GoogleIdTokenClaimsParser
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoogleIdTokenClaimsParserTest {
    @Test
    fun extractsStableSubAndEmail() {
        val token = token(
            """{"sub":"google-user-123","email":"user@example.com"}"""
        )

        val claims = GoogleIdTokenClaimsParser.parseWithDecoder(
            token,
            Base64.getUrlDecoder()::decode
        )

        assertEquals("google-user-123", claims.uniqueId)
        assertEquals("user@example.com", claims.email)
    }

    @Test
    fun decodesEscapedJsonClaimValues() {
        val claims = GoogleIdTokenClaimsParser.parsePayloadJson(
            """{"sub":"google-user-123","email":"user\u0040example.com"}"""
        )

        assertEquals("google-user-123", claims.uniqueId)
        assertEquals("user@example.com", claims.email)
    }

    @Test
    fun malformedTokenReturnsEmptyClaimsWithoutCallingDecoder() {
        var decoderCalled = false

        val claims = GoogleIdTokenClaimsParser.parseWithDecoder("not-a-jwt") {
            decoderCalled = true
            error("decoder should not be called")
        }

        assertNull(claims.uniqueId)
        assertNull(claims.email)
        assertEquals(false, decoderCalled)
    }

    @Test
    fun malformedPayloadReturnsEmptyClaims() {
        val claims = GoogleIdTokenClaimsParser.parsePayloadJson(
            """{"sub":"unterminated}"""
        )

        assertNull(claims.uniqueId)
        assertNull(claims.email)
    }

    private fun token(payloadJson: String): String {
        val header = encode("""{"alg":"none"}""")
        val payload = encode(payloadJson)
        return "$header.$payload."
    }

    private fun encode(value: String): String =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(value.toByteArray())
}
