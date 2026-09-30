package com.riccardopinato.notificationcontrol.domain

import com.riccardopinato.notificationcontrol.capture.CapturedNotification
import com.riccardopinato.notificationcontrol.data.RuleWithActions

object RuleMatcher {
    fun matches(rule: RuleWithActions, event: CapturedNotification): Boolean {
        val conditions = buildList {
            rule.rule.packageName?.takeIf { it.isNotBlank() }?.let {
                add(event.packageName == it)
            }
            rule.rule.senderQuery?.takeIf { it.isNotBlank() }?.let { query ->
                add(senderHaystack(event).contains(query, ignoreCase = true))
            }
            rule.rule.textQuery?.takeIf { it.isNotBlank() }?.let { query ->
                add(combinedText(event).contains(query, ignoreCase = true))
            }
        }

        if (conditions.isEmpty()) return false
        return if (rule.rule.matchMode == "ANY") {
            conditions.any { it }
        } else {
            conditions.all { it }
        }
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
