package com.riccardopinato.notificationcontrol

import com.riccardopinato.notificationcontrol.data.SavedVaultFilter
import com.riccardopinato.notificationcontrol.data.SavedVaultFilterCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SavedVaultFilterCodecTest {
    @Test
    fun roundTripPreservesSearchAndPackage() {
        val filter = SavedVaultFilter(
            id = "one",
            name = "Anna / lavoro",
            query = "preventivo \"urgente\"",
            packageName = "com.whatsapp"
        )

        assertEquals(
            filter,
            SavedVaultFilterCodec.decode(SavedVaultFilterCodec.encode(filter))
        )
    }

    @Test
    fun malformedOrEmptyCriteriaAreRejected() {
        assertNull(SavedVaultFilterCodec.decode("not-json"))
        assertNull(
            SavedVaultFilterCodec.decode(
                SavedVaultFilterCodec.encode(
                    SavedVaultFilter(
                        id = "empty",
                        name = "Empty",
                        query = "",
                        packageName = null
                    )
                )
            )
        )
    }
}
