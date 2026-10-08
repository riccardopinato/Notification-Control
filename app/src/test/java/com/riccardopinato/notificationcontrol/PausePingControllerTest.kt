package com.riccardopinato.notificationcontrol

import com.riccardopinato.notificationcontrol.domain.PausePingController
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PausePingControllerTest {
    @Test
    fun delayedEmissionAnchorsCooldownAtActualEmissionTime() {
        val controller = PausePingController(
            enabledProvider = { true },
            cooldownSecondsProvider = { 20 }
        )
        val reservation =
            controller.evaluateAndReserve("com.example", false, 1_000L).reservation!!
        controller.confirmVisualAlert(reservation, now = 9_000L)

        assertTrue(
            controller.evaluateAndReserve("com.example", false, 20_000L).suppress
        )
        assertFalse(
            controller.evaluateAndReserve("com.example", false, 29_001L).suppress
        )
    }

    @Test
    fun burstReservationSuppressesSecondAlertBeforeAsyncEmission() {
        val controller = PausePingController(
            enabledProvider = { true },
            cooldownSecondsProvider = { 20 }
        )
        val first = controller.evaluateAndReserve("com.example", false, 1_000L)
        assertFalse(first.suppress)
        assertNotNull(first.reservation)

        val burst = controller.evaluateAndReserve("com.example", false, 1_001L)
        assertTrue(burst.suppress)
        assertNull(burst.reservation)

        controller.cancelVisualAlert(first.reservation!!)
        assertFalse(
            controller.evaluateAndReserve("com.example", false, 1_002L).suppress
        )
    }

    @Test
    fun confirmedEmissionAnchorsCooldownButFailedEmissionDoesNot() {
        val controller = PausePingController(
            enabledProvider = { true },
            cooldownSecondsProvider = { 20 }
        )
        val failed = controller.evaluateAndReserve("com.example", false, 1_000L)
        controller.cancelVisualAlert(failed.reservation!!)
        assertFalse(
            controller.evaluateAndReserve("com.example", false, 2_000L).suppress
        )

        val emitted = controller.evaluateAndReserve("com.other", false, 3_000L)
        controller.confirmVisualAlert(emitted.reservation!!, 3_000L)
        assertTrue(
            controller.evaluateAndReserve("com.other", false, 4_000L).suppress
        )
        assertFalse(
            controller.evaluateAndReserve("com.other", true, 5_000L).suppress
        )
    }

    @Test
    fun freeTierUsesCooldownButIgnoresPremiumBudget() {
        val controller = PausePingController(
            enabledProvider = { true },
            cooldownSecondsProvider = { 1 },
            premiumProvider = { false },
            budgetEnabledProvider = { true },
            budgetMaxAlertsProvider = { 1 }
        )
        val first = controller.evaluateAndReserve("com.example", false, 1_000L)
        controller.confirmVisualAlert(first.reservation!!, 1_000L)
        assertFalse(
            controller.evaluateAndReserve("com.example", false, 3_000L).suppress
        )
    }

    @Test
    fun premiumBudgetCountsConfirmedAndPendingReservations() {
        val controller = PausePingController(
            enabledProvider = { true },
            cooldownSecondsProvider = { 1 },
            premiumProvider = { true },
            budgetEnabledProvider = { true },
            budgetMaxAlertsProvider = { 2 },
            budgetWindowMinutesProvider = { 30 }
        )
        val first = controller.evaluateAndReserve("com.example", false, 1_000L)
        controller.confirmVisualAlert(first.reservation!!, 1_000L)

        val second = controller.evaluateAndReserve("com.example", false, 3_000L)
        assertFalse(second.suppress)
        assertTrue(
            controller.evaluateAndReserve("com.example", false, 5_000L).suppress
        )

        controller.cancelVisualAlert(second.reservation!!)
        val replacement =
            controller.evaluateAndReserve("com.example", false, 5_001L)
        assertFalse(replacement.suppress)
        controller.confirmVisualAlert(replacement.reservation!!, 5_001L)
        assertTrue(
            controller.evaluateAndReserve("com.example", false, 7_000L).suppress
        )
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
        val first = controller.evaluateAndReserve("com.example", false, 1_000L)
        controller.confirmVisualAlert(first.reservation!!, 1_000L)
        assertTrue(
            controller.evaluateAndReserve("com.example", false, 3_000L).suppress
        )
        assertFalse(
            controller.evaluateAndReserve("com.example", false, 62_000L).suppress
        )
    }

    @Test
    fun criticalBypassesWithoutConsumingCooldownOrBudget() {
        val controller = PausePingController(
            enabledProvider = { true },
            cooldownSecondsProvider = { 20 },
            premiumProvider = { true },
            budgetEnabledProvider = { true },
            budgetMaxAlertsProvider = { 1 }
        )
        val critical = controller.evaluateAndReserve("com.example", true, 1_000L)
        assertFalse(critical.suppress)
        assertNull(critical.reservation)

        val normal = controller.evaluateAndReserve("com.example", false, 2_000L)
        assertFalse(normal.suppress)
        controller.confirmVisualAlert(normal.reservation!!, 2_000L)
        assertTrue(
            controller.evaluateAndReserve("com.example", false, 3_000L).suppress
        )
    }

    @Test
    fun premiumPerAppZeroExemptsAppFromCooldownAndBudget() {
        val controller = PausePingController(
            enabledProvider = { true },
            cooldownSecondsProvider = { 20 },
            premiumProvider = { true },
            perAppCooldownProvider = { mapOf("com.example" to 0) },
            budgetEnabledProvider = { true },
            budgetMaxAlertsProvider = { 1 }
        )
        repeat(3) { index ->
            val decision = controller.evaluateAndReserve(
                "com.example",
                false,
                1_000L + index
            )
            assertFalse(decision.suppress)
            assertNull(decision.reservation)
        }
    }

    @Test
    fun confirmedPremiumHistorySurvivesControllerRecreation() {
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
        val one = first.evaluateAndReserve("com.example", false, 1_000L)
        first.confirmVisualAlert(one.reservation!!, 1_000L)
        val two = first.evaluateAndReserve("com.example", false, 3_000L)
        first.confirmVisualAlert(two.reservation!!, 3_000L)

        val recreated = createController()
        assertTrue(
            recreated.evaluateAndReserve("com.example", false, 5_000L).suppress
        )
    }
}
