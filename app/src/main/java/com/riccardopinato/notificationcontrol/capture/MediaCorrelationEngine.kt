package com.riccardopinato.notificationcontrol.capture

import com.riccardopinato.notificationcontrol.data.MediaRecoveryPendingEntity
import kotlin.math.abs

data class MediaRecoveryCandidate(
    val sourceKey: String,
    val sourceKind: String,
    val sourceUri: String,
    val mediaStoreId: Long? = null,
    val timestampMillis: Long,
    val path: String? = null,
    val ownerPackageName: String? = null,
    val generationAdded: Long? = null,
    val mimeType: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val sizeBytes: Long = 0L,
    val perceptualHash: String? = null
)

data class MediaRecoveryAssignment(
    val target: MediaRecoveryPendingEntity,
    val candidate: MediaRecoveryCandidate,
    val confidence: Int
)

object MediaCorrelationEngine {
    const val MIN_ASSIGN_CONFIDENCE = 70
    const val MIN_RESCUE_CONFIDENCE = 45
    private const val TARGET_MARGIN = 12
    private const val CANDIDATE_MARGIN = 10

    fun score(
        target: MediaRecoveryPendingEntity,
        candidate: MediaRecoveryCandidate
    ): Int? {
        if (!WhatsAppMediaRecoveryPolicy.supportsPackage(target.packageName)) return null
        if (candidate.mimeType != null && !candidate.mimeType.startsWith("image/", true)) {
            return null
        }

        val delta = candidate.timestampMillis - target.postedAt
        if (
            delta < -WhatsAppMediaRecoveryPolicy.SEARCH_BEFORE_MS ||
            delta > WhatsAppMediaRecoveryPolicy.SEARCH_AFTER_MS
        ) {
            return null
        }

        var score = 0
        when (candidate.sourceKind) {
            SOURCE_MEDIASTORE -> {
                if (
                    !WhatsAppMediaRecoveryPolicy.isCompatibleImageCandidate(
                        packageName = target.packageName,
                        path = candidate.path,
                        ownerPackageName = candidate.ownerPackageName
                    )
                ) {
                    return null
                }
                score += 25
                score += when (candidate.ownerPackageName) {
                    target.packageName -> 25
                    null, "" -> 6
                    else -> return null
                }

                val baseline = target.baselineGeneration
                val generation = candidate.generationAdded
                if (baseline != null && generation != null) {
                    if (generation <= baseline) return null
                    score += 25
                }
            }

            SOURCE_SAF -> {
                // The user explicitly selected the WhatsApp Images directory.
                score += 38
            }

            else -> return null
        }

        score += when {
            delta in 0L..5_000L -> 25
            delta in 5_001L..15_000L -> 19
            delta > 15_000L -> 10
            delta >= -2_000L -> 12
            else -> 5
        }

        val referenceHash = target.referencePerceptualHash
        if (referenceHash != null) {
            val candidateHash = candidate.perceptualHash ?: return null
            val distance = perceptualDistance(referenceHash, candidateHash) ?: return null
            when {
                distance <= 6 -> score += 45
                distance <= 12 -> score += 30
                distance <= 18 -> score += 15
                else -> return null
            }
        }

        if (candidate.width > 0 && candidate.height > 0) score += 3
        if (candidate.sizeBytes > 0L) score += 2
        return score.coerceAtMost(150)
    }

    fun assign(
        targets: List<MediaRecoveryPendingEntity>,
        candidates: List<MediaRecoveryCandidate>
    ): List<MediaRecoveryAssignment> {
        if (targets.isEmpty() || candidates.isEmpty()) return emptyList()

        val edges = buildList {
            targets.forEach { target ->
                candidates.forEach { candidate ->
                    val score = score(target, candidate) ?: return@forEach
                    if (score >= MIN_ASSIGN_CONFIDENCE) {
                        add(Edge(target, candidate, score))
                    }
                }
            }
        }
        if (edges.isEmpty()) return emptyList()

        val acceptedTargets = targets.associateWith { target ->
            val ranked = edges.filter { it.target.revisionKey == target.revisionKey }
                .sortedByDescending(Edge::score)
            val first = ranked.firstOrNull() ?: return@associateWith null
            val second = ranked.getOrNull(1)
            if (second == null || first.score - second.score >= TARGET_MARGIN) first else null
        }

        val acceptedCandidates = candidates.associateWith { candidate ->
            val ranked = edges.filter { it.candidate.sourceKey == candidate.sourceKey }
                .sortedByDescending(Edge::score)
            val first = ranked.firstOrNull() ?: return@associateWith null
            val second = ranked.getOrNull(1)
            if (second == null || first.score - second.score >= CANDIDATE_MARGIN) first else null
        }

        return edges
            .sortedByDescending(Edge::score)
            .filter { edge ->
                acceptedTargets[edge.target]?.candidate?.sourceKey == edge.candidate.sourceKey &&
                    acceptedCandidates[edge.candidate]?.target?.revisionKey ==
                    edge.target.revisionKey
            }
            .fold(mutableListOf<MediaRecoveryAssignment>()) { result, edge ->
                if (
                    result.none { it.target.revisionKey == edge.target.revisionKey } &&
                    result.none { it.candidate.sourceKey == edge.candidate.sourceKey }
                ) {
                    result += MediaRecoveryAssignment(
                        target = edge.target,
                        candidate = edge.candidate,
                        confidence = edge.score
                    )
                }
                result
            }
    }

    fun rescueConfidence(
        targets: List<MediaRecoveryPendingEntity>,
        candidate: MediaRecoveryCandidate
    ): Int? = targets.mapNotNull { score(it, candidate) }.maxOrNull()
        ?.takeIf { it >= MIN_RESCUE_CONFIDENCE }

    internal fun perceptualDistance(first: String, second: String): Int? {
        val a = runCatching { java.lang.Long.parseUnsignedLong(first, 16) }.getOrNull()
            ?: return null
        val b = runCatching { java.lang.Long.parseUnsignedLong(second, 16) }.getOrNull()
            ?: return null
        return java.lang.Long.bitCount(a xor b)
    }

    private data class Edge(
        val target: MediaRecoveryPendingEntity,
        val candidate: MediaRecoveryCandidate,
        val score: Int
    )

    const val SOURCE_MEDIASTORE = "MEDIASTORE"
    const val SOURCE_SAF = "SAF"
}
