package com.riccardopinato.notificationcontrol

import com.riccardopinato.notificationcontrol.domain.VaultSearchQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VaultSearchQueryTest {
    @Test
    fun emptyInputDoesNotProduceFtsQuery() {
        assertNull(VaultSearchQuery.toFtsQuery("   "))
    }

    @Test
    fun wordsBecomePrefixAndTerms() {
        assertEquals(
            "casa* AND anna*",
            VaultSearchQuery.toFtsQuery("casa anna")
        )
    }

    @Test
    fun punctuationIsRemoved() {
        assertEquals(
            "codice* AND 1234*",
            VaultSearchQuery.toFtsQuery("codice: 1234!")
        )
    }
}
