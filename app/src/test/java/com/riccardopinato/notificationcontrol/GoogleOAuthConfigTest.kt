package com.riccardopinato.notificationcontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleOAuthConfigTest {
    @Test
    fun webClientIdIsConfiguredForNotificationControl() {
        val clientId = BuildConfig.GOOGLE_WEB_CLIENT_ID

        assertTrue(clientId.isNotBlank())
        assertTrue(clientId.endsWith(".apps.googleusercontent.com"))
        assertEquals(
            "72691779013-hci9cr313a5ado5skdghs6h7a5pg84md.apps.googleusercontent.com",
            clientId
        )
    }
}
