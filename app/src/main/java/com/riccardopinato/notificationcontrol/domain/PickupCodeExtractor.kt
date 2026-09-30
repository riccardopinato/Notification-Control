package com.riccardopinato.notificationcontrol.domain

import com.riccardopinato.notificationcontrol.capture.CapturedNotification

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

    fun extract(event: CapturedNotification): PickupCodeCandidate? {
        val text = buildString {
            append(event.title.orEmpty())
            append(' ')
            append(event.text.orEmpty())
            append(' ')
            append(event.bigText.orEmpty())
            event.messages.forEach {
                append(' ')
                append(it.text)
            }
        }.trim()

        if (text.isBlank()) return null
        val lower = text.lowercase()
        if (contextKeywords.none { lower.contains(it) }) return null

        val upper = text.uppercase()
        val matches = candidateRegex.findAll(upper)
            .map { it.value }
            .filter { candidate ->
                candidate.any(Char::isDigit) &&
                    (candidate.length >= 5 || candidate.any(Char::isLetter))
            }
            .toList()

        val code = matches.firstOrNull() ?: return null
        return PickupCodeCandidate(code = code, context = text.take(240))
    }
}
