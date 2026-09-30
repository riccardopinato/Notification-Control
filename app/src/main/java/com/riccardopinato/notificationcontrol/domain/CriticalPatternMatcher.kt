package com.riccardopinato.notificationcontrol.domain

import com.riccardopinato.notificationcontrol.capture.CapturedNotification
import com.riccardopinato.notificationcontrol.data.CriticalPatternEntity

object CriticalPatternMatcher {
    fun matches(
        pattern: CriticalPatternEntity,
        event: CapturedNotification
    ): Boolean = when (pattern.type) {
        CriticalPatternType.APP ->
            event.packageName == pattern.value

        CriticalPatternType.SENDER ->
            senderHaystack(event).contains(
                pattern.value,
                ignoreCase = true
            )

        CriticalPatternType.KEYWORD ->
            combinedText(event).contains(
                pattern.value,
                ignoreCase = true
            )

        else -> false
    }

    private fun senderHaystack(event: CapturedNotification): String = buildString {
        append(event.title.orEmpty())
        append(' ')
        append(event.conversationTitle.orEmpty())
        event.messages.forEach {
            append(' ')
            append(it.sender.orEmpty())
        }
    }

    private fun combinedText(event: CapturedNotification): String = buildString {
        append(event.title.orEmpty())
        append(' ')
        append(event.text.orEmpty())
        append(' ')
        append(event.bigText.orEmpty())
        append(' ')
        append(event.conversationTitle.orEmpty())
        event.messages.forEach {
            append(' ')
            append(it.sender.orEmpty())
            append(' ')
            append(it.text)
        }
    }
}
