package org.kerifoundation.fortandroid

import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
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
    fun appBootsToVaultPickerScreen() {
        assertTrue(
            "Expected vault picker to render",
            device.wait(Until.hasObject(By.textContains("Your Vaults")), DEFAULT_TIMEOUT_MS)
        )
    }

    @Test
    fun settingsTabShowsDangerZone() {
        assertTrue(
            "Expected settings tab button to appear",
            device.wait(Until.hasObject(By.text("Settings")), DEFAULT_TIMEOUT_MS)
        )

        device.findObject(By.text("Settings"))?.click()

        assertTrue(
            "Expected danger zone section to appear in settings",
            device.wait(Until.hasObject(By.text("Danger Zone")), DEFAULT_TIMEOUT_MS)
        )
    }

    @Test
    fun keyboardAppearsWhenInputFieldTapped() {
        assertTrue(
            "Expected vault picker to render before creating vault",
            device.wait(Until.hasObject(By.textContains("Your Vaults")), DEFAULT_TIMEOUT_MS)
        )

        val createButton = device.findObject(By.textContains("Create"))
            ?: device.findObject(By.textContains("Add"))
        assertNotNull("Expected a Create or Add button on vault picker", createButton)
        createButton.click()

        assertTrue(
            "Expected vault creation dialog to appear",
            device.wait(Until.hasObject(By.textContains("Name")), DEFAULT_TIMEOUT_MS)
        )

        val nameField = device.findObject(By.textContains("Name"))
        assertNotNull("Expected Name input field in dialog", nameField)
        nameField.click()

        Thread.sleep(KEYBOARD_SETTLE_MS)

        var keyboardVisible = false
        activityRule.scenario.onActivity { activity ->
            val rootView = activity.findViewById<android.view.View>(R.id.main)
            val insets = ViewCompat.getRootWindowInsets(rootView)
            keyboardVisible = insets?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }

        assertTrue(
            "Expected soft keyboard to be visible after tapping input field",
            keyboardVisible
        )

        device.pressBack()
        Thread.sleep(KEYBOARD_SETTLE_MS)

        var keyboardHidden = false
        activityRule.scenario.onActivity { activity ->
            val rootView = activity.findViewById<android.view.View>(R.id.main)
            val insets = ViewCompat.getRootWindowInsets(rootView)
            keyboardHidden = insets?.isVisible(WindowInsetsCompat.Type.ime()) != true
        }

        assertTrue(
            "Expected soft keyboard to be hidden after pressing back",
            keyboardHidden
        )
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 20_000L
        const val KEYBOARD_SETTLE_MS = 1_500L
    }
}