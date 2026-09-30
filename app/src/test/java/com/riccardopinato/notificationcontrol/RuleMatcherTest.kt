package com.riccardopinato.notificationcontrol

import com.riccardopinato.notificationcontrol.capture.CapturedMessage
import com.riccardopinato.notificationcontrol.capture.CapturedNotification
import com.riccardopinato.notificationcontrol.data.RuleActionEntity
import com.riccardopinato.notificationcontrol.data.RuleEntity
import com.riccardopinato.notificationcontrol.data.RuleWithActions
import com.riccardopinato.notificationcontrol.domain.RuleMatcher
import com.riccardopinato.notificationcontrol.domain.RuleRuntimeState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleMatcherTest {
    @Test
    fun allModeRequiresEveryConfiguredCondition() {
        val rule = RuleWithActions(
            rule = RuleEntity(
                id = 1,
                name = "Anna urgent",
                packageName = "com.example.chat",
                senderQuery = "anna",
                textQuery = "urgent",
                matchMode = "ALL"
            ),
            actions = listOf(
                RuleActionEntity(
                    id = 1,
                    ruleId = 1,
                    actionType = "CRITICAL"
                )
            )
        )

        assertTrue(
            RuleMatcher.matches(
                rule,
                event("com.example.chat", "Anna", "URGENT call me")
            )
        )
        assertFalse(
            RuleMatcher.matches(
                rule,
                event("com.example.chat", "Marco", "URGENT call me")
            )
        )
    }

    @Test
    fun anyModeAcceptsOneConfiguredCondition() {
        val rule = RuleWithActions(
            rule = RuleEntity(
                id = 2,
                name = "Any trigger",
                packageName = "com.example.chat",
                textQuery = "urgent",
                matchMode = "ANY"
            ),
            actions = emptyList()
        )

        assertTrue(
            RuleMatcher.matches(
                rule,
                event("com.other", "Anna", "urgent")
            )
        )
    }

    @Test
    fun ruleWithoutConditionsNeverMatches() {
        val rule = RuleWithActions(
            rule = RuleEntity(
                id = 3,
                name = "Invalid"
            ),
            actions = emptyList()
        )

        assertFalse(
            RuleMatcher.matches(
                rule,
                event("com.example.chat", "Anna", "hello")
            )
        )
    }

    @Test
    fun timeWindowAcrossMidnightMatchesOnlyInsideWindow() {
        val rule = RuleWithActions(
            rule = RuleEntity(
                id = 4,
                name = "Night",
                packageName = "com.example.chat",
                timeStartMinutes = 22 * 60,
                timeEndMinutes = 7 * 60
            ),
            actions = emptyList()
        )

        assertTrue(
            RuleMatcher.matches(
                rule,
                event("com.example.chat", "Anna", "hello"),
                RuleRuntimeState(23 * 60, screenInteractive = false)
            )
        )
        assertFalse(
            RuleMatcher.matches(
                rule,
                event("com.example.chat", "Anna", "hello"),
                RuleRuntimeState(12 * 60, screenInteractive = false)
            )
        )
    }

    @Test
    fun screenOffConditionUsesRuntimeState() {
        val rule = RuleWithActions(
            rule = RuleEntity(
                id = 5,
                name = "Screen off",
                packageName = "com.example.chat",
                screenState = "SCREEN_OFF"
            ),
            actions = emptyList()
        )

        assertTrue(
            RuleMatcher.matches(
                rule,
                event("com.example.chat", "Anna", "hello"),
                RuleRuntimeState(12 * 60, screenInteractive = false)
            )
        )
        assertFalse(
            RuleMatcher.matches(
                rule,
                event("com.example.chat", "Anna", "hello"),
                RuleRuntimeState(12 * 60, screenInteractive = true)
            )
        )
    }

    private fun event(
        packageName: String,
        sender: String,
        text: String
    ) = CapturedNotification(
        sbnKey = "key",
        packageName = packageName,
        appLabel = "Chat",
        notificationId = 1,
        tag = null,
        groupKey = null,
        category = null,
        channelId = null,
        title = sender,
        text = text,
        bigText = null,
        subText = null,
        conversationTitle = null,
        thumbnailPath = null,
        postedAt = 1L,
        capturedAt = 2L,
        isOngoing = false,
        isClearable = true,
        messages = listOf(
            CapturedMessage(
                sender = sender,
                text = text,
                timestamp = 1L,
                mimeType = null,
                dataUri = null
            )
        )
    )
}
