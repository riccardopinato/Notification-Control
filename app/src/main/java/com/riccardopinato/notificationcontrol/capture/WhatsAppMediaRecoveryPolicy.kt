package com.riccardopinato.notificationcontrol.capture

import java.util.Locale
import kotlin.math.abs

object WhatsAppMediaRecoveryPolicy {
    const val SEARCH_BEFORE_MS = 15_000L
    const val SEARCH_AFTER_MS = 45_000L
    const val FUTURE_TOLERANCE_MS = 2_000L
    private const val AMBIGUITY_GAP_MS = 2_500L

    data class Candidate(
        val id: Long,
        val timestampMillis: Long,
        val path: String,
        val ownerPackageName: String? = null
    )

    fun supportsPackage(packageName: String): Boolean =
        packageName == "com.whatsapp" || packageName == "com.whatsapp.w4b"

    fun isCompatibleImagePath(packageName: String, path: String?): Boolean {
        if (!supportsPackage(packageName) || path.isNullOrBlank()) return false
        val normalized = path.replace('\\', '/').lowercase(Locale.ROOT)
        if ("/sent/" in normalized || normalized.endsWith("/sent")) return false

        return when (packageName) {
            "com.whatsapp" -> "whatsapp/media/whatsapp images" in normalized
            "com.whatsapp.w4b" ->
                "whatsapp business/media/whatsapp business images" in normalized
            else -> false
        }
    }

    fun isCompatibleImageCandidate(
        packageName: String,
        path: String?,
        ownerPackageName: String?
    ): Boolean {
        if (!isCompatibleImagePath(packageName, path)) return false
        return ownerPackageName.isNullOrBlank() || ownerPackageName == packageName
    }

    fun hasImageSignal(
        mimeTypes: List<String?>,
        textCandidates: List<String?>,
        templateName: String?
    ): Boolean {
        if (mimeTypes.any { it?.startsWith("image/", ignoreCase = true) == true }) {
            return true
        }
        if (templateName?.contains("BigPictureStyle", ignoreCase = true) == true) {
            return true
        }
        return textCandidates
            .asSequence()
            .mapNotNull { it?.takeIf(String::isNotBlank) }
            .map(::normalizeLabel)
            .any { it in IMAGE_LABELS }
    }

    fun chooseCandidate(postedAt: Long, candidates: List<Candidate>): Candidate? {
        val ranked = candidates
            .filter {
                it.timestampMillis >= postedAt - SEARCH_BEFORE_MS &&
                    it.timestampMillis <= postedAt + SEARCH_AFTER_MS
            }
            .sortedBy { abs(it.timestampMillis - postedAt) }

        val first = ranked.firstOrNull() ?: return null
        val second = ranked.getOrNull(1) ?: return first
        val firstDistance = abs(first.timestampMillis - postedAt)
        val secondDistance = abs(second.timestampMillis - postedAt)
        return if (secondDistance - firstDistance > AMBIGUITY_GAP_MS) first else null
    }

    private fun normalizeLabel(value: String): String =
        value.lowercase(Locale.ROOT)
            .filter { it.isLetterOrDigit() || it.isWhitespace() }
            .trim()
            .replace(Regex("\\s+"), " ")

    private val IMAGE_LABELS = setOf(
        "photo",
        "foto",
        "image",
        "imagen",
        "imagem",
        "picture",
        "immagine"
    )
}
