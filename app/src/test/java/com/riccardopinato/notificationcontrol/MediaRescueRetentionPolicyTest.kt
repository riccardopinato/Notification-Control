package com.riccardopinato.notificationcontrol

import com.riccardopinato.notificationcontrol.capture.MediaRescueRetentionPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaRescueRetentionPolicyTest {
    @Test
    fun unrelatedForeverRetentionDoesNotMakeWhatsAppRescuePermanent() {
        val result = MediaRescueRetentionPolicy.daysForPackage(
            packageName = "com.whatsapp",
            globalDays = 7,
            perAppDays = mapOf(
                "com.example.other" to Int.MAX_VALUE
            )
        )

        assertEquals(30, result)
    }

    @Test
    fun whatsAppForeverRetentionKeepsItsOwnRescueMedia() {
        val result = MediaRescueRetentionPolicy.daysForPackage(
            packageName = "com.whatsapp",
            globalDays = 7,
            perAppDays = mapOf(
                "com.whatsapp" to Int.MAX_VALUE
            )
        )

        assertEquals(Int.MAX_VALUE, result)
    }

    @Test
    fun rescueRetentionUsesAtLeastThirtyDays() {
        assertEquals(
            30,
            MediaRescueRetentionPolicy.daysForPackage(
                packageName = "com.whatsapp",
                globalDays = 1,
                perAppDays = emptyMap()
            )
        )
        assertEquals(
            90,
            MediaRescueRetentionPolicy.daysForPackage(
                packageName = "com.whatsapp",
                globalDays = 7,
                perAppDays = mapOf("com.whatsapp" to 90)
            )
        )
    }
}
