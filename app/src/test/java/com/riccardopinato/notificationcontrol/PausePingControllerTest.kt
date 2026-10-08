package com.riccardopinato.notificationcontrol

import com.riccardopinato.notificationcontrol.domain.PausePingController
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PausePingControllerTest {
    @Test
    fun repeatedAlertWithinCooldownIsSuppressedButCriticalBypasses() {
        val controller = PausePingController(
            enabledProvider = { true },
            cooldownSecondsProvider = { 20 }
        )

        assertFalse(controller.shouldSuppress("com.example", critical = false, now = 1_000L))
        assertTrue(controller.shouldSuppress("com.example", critical = false, now = 2_000L))
        assertFalse(controller.shouldSuppress("com.example", critical = true, now = 3_000L))
    }

    @Test
    fun freeTierIgnoresPremiumBudget() {
        val controller = PausePingController(
            enabledProvider = { true },
            cooldownSecondsProvider = { 1 },
            premiumProvider = { false },
            budgetEnabledProvider = { true },
            budgetMaxAlertsProvider = { 1 },
            budgetWindowMinutesProvider = { 30 }
        )

        assertFalse(controller.shouldSuppress("com.example", false, 1_000L))
        assertFalse(controller.shouldSuppress("com.example", false, 3_000L))
    }

    @Test
    fun premiumBudgetCountsOnlyEmittedVisualAlerts() {
        val controller = PausePingController(
            enabledProvider = { true },
            cooldownSecondsProvider = { 1 },
            premiumProvider = { true },
            budgetEnabledProvider = { true },
            budgetMaxAlertsProvider = { 3 },
            budgetWindowMinutesProvider = { 30 }
        )

        assertFalse(controller.shouldSuppress("com.example", false, 1_000L))
        assertFalse(controller.shouldSuppress("com.example", false, 3_000L))
        assertFalse(controller.shouldSuppress("com.example", false, 5_000L))
        assertFalse(controller.shouldSuppress("com.example", false, 7_000L))

        controller.recordVisualAlert("com.example", false, 8_000L)
        controller.recordVisualAlert("com.example", false, 10_000L)
        controller.recordVisualAlert("com.example", false, 12_000L)

        assertTrue(controller.shouldSuppress("com.example", false, 14_000L))
    }

    @Test
    fun premiumBudgetResetsAfterRollingWindow() {
        val controller = PausePingController(
            enabledProvider = { true },
            cooldownSecondsProvider = { 1 },
            premiumProvider = { true },
            budgetEnabledProvider = { true },
            budgetMaxAlertsProvider = { 1 },
            budgetWindowMinutesProvider = { 1 }
        )

        assertFalse(controller.shouldSuppress("com.example", false, 1_000L))
        controller.recordVisualAlert("com.example", false, 1_000L)
        assertTrue(controller.shouldSuppress("com.example", false, 3_000L))
        assertFalse(controller.shouldSuppress("com.example", false, 62_000L))
    }

    @Test
    fun criticalAlertBypassesAndDoesNotConsumeBudget() {
        val controller = PausePingController(
            enabledProvider = { true },
            cooldownSecondsProvider = { 1 },
            premiumProvider = { true },
            budgetEnabledProvider = { true },
            budgetMaxAlertsProvider = { 1 },
            budgetWindowMinutesProvider = { 30 }
        )

        assertFalse(controller.shouldSuppress("com.example", true, 1_000L))
        controller.recordVisualAlert("com.example", true, 1_000L)
        assertFalse(controller.shouldSuppress("com.example", false, 3_000L))
        controller.recordVisualAlert("com.example", false, 3_000L)
        assertTrue(controller.shouldSuppress("com.example", false, 5_000L))
    }

    @Test
    fun premiumPerAppZeroExemptsAppFromCooldownAndBudget() {
        val controller = PausePingController(
            enabledProvider = { true },
            cooldownSecondsProvider = { 20 },
            premiumProvider = { true },
            perAppCooldownProvider = { mapOf("com.example" to 0) },
            budgetEnabledProvider = { true },
            budgetMaxAlertsProvider = { 1 },
            budgetWindowMinutesProvider = { 30 }
        )

        assertFalse(controller.shouldSuppress("com.example", false, 1_000L))
        controller.recordVisualAlert("com.example", false, 1_000L)
        assertFalse(controller.shouldSuppress("com.example", false, 2_000L))
        assertFalse(controller.shouldSuppress("com.example", false, 3_000L))
    }

    @Test
    fun premiumBudgetHistorySurvivesControllerRecreation() {
        var persistedHistory: Map<String, List<Long>> = emptyMap()
        val createController = {
            PausePingController(
                enabledProvider = { true },
                cooldownSecondsProvider = { 1 },
                premiumProvider = { true },
                budgetEnabledProvider = { true },
                budgetMaxAlertsProvider = { 2 },
                budgetWindowMinutesProvider = { 30 },
                budgetHistoryProvider = { persistedHistory },
                budgetHistoryConsumer = { persistedHistory = it }
            )
        }

        val first = createController()
        first.recordVisualAlert("com.example", false, 1_000L)
        first.recordVisualAlert("com.example", false, 3_000L)

        val recreated = createController()
        assertTrue(recreated.shouldSuppress("com.example", false, 5_000L))
    }
}
