package com.riccardopinato.notificationcontrol.domain

import com.riccardopinato.notificationcontrol.capture.CapturedNotification
import com.riccardopinato.notificationcontrol.data.AutomationDao

class CriticalMatcher(private val dao: AutomationDao) {
    suspend fun isCritical(event: CapturedNotification): Boolean {
        val text = buildString {
            append(event.title.orEmpty())
            append(' ')
            append(event.text.orEmpty())
            append(' ')
            append(event.bigText.orEmpty())
            event.messages.forEach {
                append(' ')
                append(it.sender.orEmpty())
                append(' ')
                append(it.text)
            }
        }

        return dao.enabledCriticalPatterns().any { pattern ->
            when (pattern.type) {
                CriticalPatternType.APP -> event.packageName == pattern.value
                CriticalPatternType.SENDER -> {
                    val senderText = buildString {
                        append(event.title.orEmpty())
                        event.messages.forEach {
                            append(' ')
                            append(it.sender.orEmpty())
                        }
                    }
                    senderText.contains(pattern.value, ignoreCase = true)
                }
                CriticalPatternType.KEYWORD -> text.contains(pattern.value, ignoreCase = true)
                else -> false
            }
        }
    }
}
