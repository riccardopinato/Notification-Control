package com.riccardopinato.notificationcontrol.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class StartupAndJankBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun coldStartup() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = 8,
        startupMode = StartupMode.COLD,
        setupBlock = {
            pressHome()
        }
    ) {
        startActivityAndWait()
    }

    @Test
    fun tabSwitchAndVaultScrollFrames() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = 5,
        startupMode = StartupMode.WARM,
        setupBlock = {
            pressHome()
            startActivityAndWait()
        }
    ) {
        val width = device.displayWidth
        val height = device.displayHeight
        val navigationY = (height * 0.93f).toInt()

        // Requires one-time onboarding on the benchmark device.
        device.click((width * 0.30f).toInt(), navigationY)
        device.waitForIdle()
        repeat(4) {
            device.swipe(
                width / 2,
                (height * 0.78f).toInt(),
                width / 2,
                (height * 0.28f).toInt(),
                18
            )
        }
        device.click((width * 0.70f).toInt(), navigationY)
        device.waitForIdle()
        device.click((width * 0.90f).toInt(), navigationY)
        device.waitForIdle()
    }

    companion object {
        private const val PACKAGE_NAME =
            "com.riccardopinato.notificationcontrol"
    }
}
