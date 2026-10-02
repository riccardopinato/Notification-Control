package com.riccardopinato.notificationcontrol

import com.riccardopinato.notificationcontrol.capture.MediaCorrelationEngine
import com.riccardopinato.notificationcontrol.capture.MediaRecoveryCandidate
import com.riccardopinato.notificationcontrol.data.MediaRecoveryPendingEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaCorrelationEngineTest {
    @Test
    fun assignsBurstCandidatesOneToOne() {
        val first = target("r1", postedAt = 100_000L, baseline = 10L)
        val second = target("r2", postedAt = 110_000L, baseline = 11L)
        val candidates = listOf(
            candidate("c1", timestamp = 101_000L, generation = 12L),
            candidate("c2", timestamp = 111_000L, generation = 13L)
        )

        val assignments = MediaCorrelationEngine.assign(
            targets = listOf(first, second),
            candidates = candidates
        )

        assertEquals(2, assignments.size)
        assertEquals("c1", assignments.first { it.target.revisionKey == "r1" }.candidate.sourceKey)
        assertEquals("c2", assignments.first { it.target.revisionKey == "r2" }.candidate.sourceKey)
    }

    @Test
    fun rejectsCandidateThatPredatesGenerationCheckpoint() {
        val target = target("r1", postedAt = 100_000L, baseline = 20L)
        val candidate = candidate("c1", timestamp = 101_000L, generation = 20L)
        assertNull(MediaCorrelationEngine.score(target, candidate))
    }

    @Test
    fun visualReferenceRequiresPerceptualMatch() {
        val reference = "0000000000000000"
        val target = target(
            revision = "r1",
            postedAt = 100_000L,
            baseline = 10L,
            referenceHash = reference
        )

        val missingHash = candidate("c1", 101_000L, 11L)
        assertNull(MediaCorrelationEngine.score(target, missingHash))

        val mismatch = candidate(
            "c2",
            101_000L,
            11L,
            perceptualHash = "ffffffffffffffff"
        )
        assertNull(MediaCorrelationEngine.score(target, mismatch))

        val matching = candidate(
            "c3",
            101_000L,
            11L,
            perceptualHash = "0000000000000001"
        )
        assertTrue((MediaCorrelationEngine.score(target, matching) ?: 0) >= 100)
    }

    @Test
    fun ambiguousBurstIsKeptUnassigned() {
        val target = target("r1", postedAt = 100_000L, baseline = null)
        val first = candidate("c1", 100_500L, null, owner = null)
        val second = candidate("c2", 101_000L, null, owner = null)

        val assignments = MediaCorrelationEngine.assign(
            targets = listOf(target),
            candidates = listOf(first, second)
        )

        assertTrue(assignments.isEmpty())
        assertTrue(
            (MediaCorrelationEngine.rescueConfidence(listOf(target), first) ?: 0) >=
                MediaCorrelationEngine.MIN_RESCUE_CONFIDENCE
        )
    }

    private fun target(
        revision: String,
        postedAt: Long,
        baseline: Long?,
        referenceHash: String? = null
    ) = MediaRecoveryPendingEntity(
        revisionKey = revision,
        notificationKey = "event-$revision",
        packageName = "com.whatsapp",
        postedAt = postedAt,
        capturedAt = postedAt,
        createdAt = postedAt,
        expiresAt = postedAt + 600_000L,
        baselineGeneration = baseline,
        mediaStoreVersion = "v",
        referencePerceptualHash = referenceHash
    )

    private fun candidate(
        key: String,
        timestamp: Long,
        generation: Long?,
        owner: String? = "com.whatsapp",
        perceptualHash: String? = null
    ) = MediaRecoveryCandidate(
        sourceKey = key,
        sourceKind = MediaCorrelationEngine.SOURCE_MEDIASTORE,
        sourceUri = "content://media/$key",
        mediaStoreId = key.hashCode().toLong(),
        timestampMillis = timestamp,
        path = "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/",
        ownerPackageName = owner,
        generationAdded = generation,
        mimeType = "image/jpeg",
        width = 1000,
        height = 800,
        sizeBytes = 100_000L,
        perceptualHash = perceptualHash
    )
}
