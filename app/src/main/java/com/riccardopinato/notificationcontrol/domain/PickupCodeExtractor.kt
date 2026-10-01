package com.riccardopinato.notificationcontrol.domain

import com.riccardopinato.notificationcontrol.capture.CapturedNotification
import com.riccardopinato.notificationcontrol.data.MessageEntity
import com.riccardopinato.notificationcontrol.data.NotificationEntity

data class PickupCodeCandidate(
    val code: String,
    val context: String
)

object PickupCodeExtractor {
    private val contextKeywords = listOf(
        "code", "codice", "codigo", "código", "code de",
        "pin", "locker", "pickup", "ritiro", "retiro", "retrait",
        "gate", "booking", "prenotazione", "reserva", "réservation",
        "parcel", "pacco", "colis", "encomenda"
    )
    private val candidateRegex = Regex("(?<![A-Z0-9])[A-Z0-9]{4,8}(?![A-Z0-9])")

    fun extract(event: CapturedNotification): PickupCodeCandidate? =
        extract(
            title = event.title,
            text = event.text,
            bigText = event.bigText,
            messageTexts = event.messages.map { it.text }
        )

    fun extract(
        notification: NotificationEntity,
        messages: List<MessageEntity> = emptyList()
    ): PickupCodeCandidate? =
        extract(
            title = notification.title,
            text = notification.text,
            bigText = notification.bigText,
            messageTexts = messages.map { it.text }
        )

    private fun extract(
        title: String?,
        text: String?,
        bigText: String?,
        messageTexts: List<String>
    ): PickupCodeCandidate? {
        val merged = buildString {
            append(title.orEmpty())
            append(' ')
            append(text.orEmpty())
            append(' ')
            append(bigText.orEmpty())
            messageTexts.forEach {
                append(' ')
                append(it)
            }
        }.trim()

        if (merged.isBlank()) return null
        val lower = merged.lowercase()
        if (contextKeywords.none { lower.contains(it) }) return null

        val upper = merged.uppercase()
        val matches = candidateRegex.findAll(upper)
            .map { it.value }
            .filter { candidate ->
                candidate.any(Char::isDigit) &&
                    (candidate.length >= 5 || candidate.any(Char::isLetter))
            }
            .toList()

        val code = matches.firstOrNull() ?: return null
        return PickupCodeCandidate(code = code, context = merged.take(240))
    }
}
