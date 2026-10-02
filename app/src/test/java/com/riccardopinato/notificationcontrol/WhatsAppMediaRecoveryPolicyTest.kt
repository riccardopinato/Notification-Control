package com.riccardopinato.notificationcontrol

import com.riccardopinato.notificationcontrol.capture.WhatsAppMediaRecoveryPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsAppMediaRecoveryPolicyTest {
    @Test
    fun acceptsIncomingWhatsAppImagePathsButRejectsSentMedia() {
        assertTrue(
            WhatsAppMediaRecoveryPolicy.isCompatibleImagePath(
                "com.whatsapp",
                "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/"
            )
        )
        assertFalse(
            WhatsAppMediaRecoveryPolicy.isCompatibleImagePath(
                "com.whatsapp",
                "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/Sent/"
            )
        )
        assertFalse(
            WhatsAppMediaRecoveryPolicy.isCompatibleImagePath(
                "com.example.other",
                "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/"
            )
        )
    }

    @Test
    fun recognizesStructuredAndLocalizedImageSignals() {
        assertTrue(
            WhatsAppMediaRecoveryPolicy.hasImageSignal(
                mimeTypes = listOf("image/jpeg"),
                textCandidates = listOf("anything"),
                templateName = null
            )
        )
        assertTrue(
            WhatsAppMediaRecoveryPolicy.hasImageSignal(
                mimeTypes = emptyList(),
                textCandidates = listOf("📷 Foto"),
                templateName = null
            )
        )
        assertFalse(
            WhatsAppMediaRecoveryPolicy.hasImageSignal(
                mimeTypes = emptyList(),
                textCandidates = listOf("Ciao, come stai?"),
                templateName = null
            )
        )
    }

    @Test
    fun choosesUniqueNearestCandidateAndRejectsAmbiguousPairs() {
        val postedAt = 100_000L
        val clear = listOf(
            WhatsAppMediaRecoveryPolicy.Candidate(
                id = 1L,
                timestampMillis = 100_500L,
                path = "WhatsApp/Media/WhatsApp Images/"
            ),
            WhatsAppMediaRecoveryPolicy.Candidate(
                id = 2L,
                timestampMillis = 105_000L,
                path = "WhatsApp/Media/WhatsApp Images/"
            )
        )
        assertEquals(1L, WhatsAppMediaRecoveryPolicy.chooseCandidate(postedAt, clear)?.id)

        val ambiguous = listOf(
            WhatsAppMediaRecoveryPolicy.Candidate(
                id = 1L,
                timestampMillis = 100_500L,
                path = "WhatsApp/Media/WhatsApp Images/"
            ),
            WhatsAppMediaRecoveryPolicy.Candidate(
                id = 2L,
                timestampMillis = 101_500L,
                path = "WhatsApp/Media/WhatsApp Images/"
            )
        )
        assertNull(WhatsAppMediaRecoveryPolicy.chooseCandidate(postedAt, ambiguous))
    }

    @Test
    fun validatesOwnerPackageWhenMediaStoreProvidesIt() {
        val path = "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/"

        assertTrue(
            WhatsAppMediaRecoveryPolicy.isCompatibleImageCandidate(
                packageName = "com.whatsapp",
                path = path,
                ownerPackageName = "com.whatsapp"
            )
        )
        assertTrue(
            WhatsAppMediaRecoveryPolicy.isCompatibleImageCandidate(
                packageName = "com.whatsapp",
                path = path,
                ownerPackageName = null
            )
        )
        assertFalse(
            WhatsAppMediaRecoveryPolicy.isCompatibleImageCandidate(
                packageName = "com.whatsapp",
                path = path,
                ownerPackageName = "com.example.camera"
            )
        )
    }

    @Test
    fun keepsLateMediaStoreCandidatesButRejectsTooLateFiles() {
        val postedAt = 100_000L
        val lateButValid = WhatsAppMediaRecoveryPolicy.Candidate(
            id = 7L,
            timestampMillis = postedAt + 35_000L,
            path = "WhatsApp/Media/WhatsApp Images/",
            ownerPackageName = "com.whatsapp"
        )
        val tooLate = WhatsAppMediaRecoveryPolicy.Candidate(
            id = 8L,
            timestampMillis = postedAt + 50_000L,
            path = "WhatsApp/Media/WhatsApp Images/",
            ownerPackageName = "com.whatsapp"
        )

        assertEquals(
            7L,
            WhatsAppMediaRecoveryPolicy.chooseCandidate(
                postedAt,
                listOf(lateButValid, tooLate)
            )?.id
        )
        assertNull(
            WhatsAppMediaRecoveryPolicy.chooseCandidate(
                postedAt,
                listOf(tooLate)
            )
        )
    }

}
