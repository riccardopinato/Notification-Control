package com.riccardopinato.notificationcontrol.domain

import com.riccardopinato.notificationcontrol.capture.CapturedNotification
import com.riccardopinato.notificationcontrol.data.RuleWithActions

data class RuleRuntimeState(
    val minuteOfDay: Int,
    val screenInteractive: Boolean
)

object RuleScreenState {
    const val ANY = "ANY"
    const val SCREEN_ON = "SCREEN_ON"
    const val SCREEN_OFF = "SCREEN_OFF"
}

object RuleMatcher {
    fun matches(
        rule: RuleWithActions,
        event: CapturedNotification,
        runtime: RuleRuntimeState = RuleRuntimeState(
            minuteOfDay = 0,
            screenInteractive = true
        )
    ): Boolean {
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

            val start = rule.rule.timeStartMinutes
            val end = rule.rule.timeEndMinutes
            if (start != null && end != null) {
                add(
                    QuietHoursPolicy.isActive(
                        enabled = true,
                        startMinutes = start.coerceIn(0, 1439),
                        endMinutes = end.coerceIn(0, 1439),
                        nowMinutes = runtime.minuteOfDay.coerceIn(0, 1439)
                    )
                )
            }

            when (rule.rule.screenState) {
                RuleScreenState.SCREEN_ON -> add(runtime.screenInteractive)
                RuleScreenState.SCREEN_OFF -> add(!runtime.screenInteractive)
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
