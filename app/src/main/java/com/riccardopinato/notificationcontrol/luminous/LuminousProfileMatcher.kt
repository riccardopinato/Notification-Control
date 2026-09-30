package com.riccardopinato.notificationcontrol.luminous

import com.riccardopinato.notificationcontrol.capture.CapturedNotification
import com.riccardopinato.notificationcontrol.data.LuminousProfileEntity

object LuminousProfileMatcher {
    fun matches(profile: LuminousProfileEntity, event: CapturedNotification): Boolean {
        val packageMatches = profile.packageName
            ?.takeIf { it.isNotBlank() }
            ?.let { event.packageName == it }
            ?: true

        val senderMatches = profile.senderQuery
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { query ->
                senderHaystack(event).contains(query, ignoreCase = true)
            }
            ?: true

        val hasSelector =
            !profile.packageName.isNullOrBlank() || !profile.senderQuery.isNullOrBlank()

        return hasSelector && packageMatches && senderMatches
    }

    private fun senderHaystack(event: CapturedNotification): String = buildString {
        append(event.title.orEmpty())
        append(' ')
        append(event.conversationTitle.orEmpty())
        event.messages.forEach { message ->
            append(' ')
            append(message.sender.orEmpty())
        }
    }
}
