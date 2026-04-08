package org.kerifoundation.fortandroid

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class MainActivityWebViewSmokeTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    private val device: UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun appLaunchesStableShellChrome() {
        assertTextContains("Locksmith shell ready", "Expected shell to reach ready state")
        assertTextVisible("Home", "Expected home tab to appear")
        assertTextVisible("Vault", "Expected vault tab to appear")
        assertTextVisible("Settings", "Expected settings tab to appear")
        assertTextVisible("Open Vault", "Expected primary vault action to appear")
    }

    @Test
    fun settingsTabShowsDiagnosticsPanel() {
        assertTextVisible("Settings", "Expected settings tab button to appear")
        device.findObject(By.text("Settings"))?.click()
        assertTextVisible(
            "Worker diagnostics",
            "Expected diagnostics panel to appear in settings"
        )
    }

    private fun assertTextVisible(text: String, failureMessage: String) {
        assertTrue(failureMessage, device.wait(Until.hasObject(By.text(text)), DEFAULT_TIMEOUT_MS))
    }

    private fun assertTextContains(text: String, failureMessage: String) {
        assertTrue(
            failureMessage,
            device.wait(Until.hasObject(By.textContains(text)), DEFAULT_TIMEOUT_MS)
        )
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 20_000L
    }
}