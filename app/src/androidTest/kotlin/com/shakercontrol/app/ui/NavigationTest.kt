package com.shakercontrol.app.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.shakercontrol.app.MainActivity
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Navigation tests to verify screen transitions work correctly.
 *
 * These tests use MockMachineRepository (via TestRepositoryModule) so they
 * can run without real BLE hardware. The mock starts in CONNECTED (LIVE) state.
 *
 * Note on drawer structure:
 * - Home is only shown in drawer when disconnected (acts as landing/status screen)
 * - Devices is accessed via Settings > Scan (not in drawer)
 * - I/O Control is only visible in drawer when service mode is enabled
 *
 * Test scenarios cover:
 * - Drawer navigation between top-level screens
 * - Back stack behavior after navigation
 * - Service mode toggle
 * - Device management via Settings
 */
@HiltAndroidTest
class NavigationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        hiltRule.inject()
    }

    // ============================================
    // Basic Screen Verification Tests
    // ============================================

    @Test
    fun homeScreen_displaysRunCard() {
        // Home screen should contain the Run card
        // Just verify "Run" text exists somewhere on the screen
        composeTestRule.onAllNodesWithText("Run").onFirst().assertIsDisplayed()
    }

    @Test
    fun homeScreen_displaysTemperaturesCard() {
        // Should show Temperatures card
        composeTestRule.onNodeWithText("Temperatures").assertIsDisplayed()
    }

    @Test
    fun homeScreen_showsConnectedStatus_whenMockIsConnected() {
        // Mock starts connected, so should show connection status indicators
        // Look for MCU chip (green when connected)
        composeTestRule.onNodeWithText("MCU").assertIsDisplayed()
    }

    // ============================================
    // Drawer Navigation Tests
    // ============================================

    @Test
    fun drawerNavigation_toRunScreen() {
        // Open drawer by clicking menu icon
        composeTestRule.onNodeWithContentDescription("Menu").performClick()
        composeTestRule.waitForIdle()

        // Find and click Run in the drawer (it has NavigationDrawer testTag ancestor)
        composeTestRule.onNode(
            hasText("Run") and hasAnyAncestor(hasTestTag("NavigationDrawer"))
        ).performClick()
        composeTestRule.waitForIdle()

        // Verify we're on Run screen - should see Recipe section
        composeTestRule.onNodeWithText("Recipe").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_hasDeviceSection() {
        // Navigate to Settings via drawer
        composeTestRule.onNodeWithContentDescription("Menu").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNode(
            hasText("Settings") and hasAnyAncestor(hasTestTag("NavigationDrawer"))
        ).performClick()
        composeTestRule.waitForIdle()

        // Verify Device section is present in Settings
        composeTestRule.onNodeWithText("Device").assertIsDisplayed()
        composeTestRule.onNodeWithText("Status").assertIsDisplayed()
    }

    @Test
    fun settingsScreen_canNavigateToDevicesViaScan() {
        // Navigate to Settings via drawer
        composeTestRule.onNodeWithContentDescription("Menu").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNode(
            hasText("Settings") and hasAnyAncestor(hasTestTag("NavigationDrawer"))
        ).performClick()
        composeTestRule.waitForIdle()

        // Note: BleManager connection state is DISCONNECTED in tests (only MachineRepository is mocked)
        // So Scan button should be visible (shown when BLE is disconnected)
        composeTestRule.onNodeWithText("Scan").performClick()
        composeTestRule.waitForIdle()

        // Verify we're on Devices screen
        // The title "Devices" will show in the status strip and back button appears
        composeTestRule.onNodeWithContentDescription("Go back").assertIsDisplayed()
    }

    @Test
    fun drawer_hidesHomeWhenConnected() {
        // Mock starts connected, so Home should NOT be in drawer
        composeTestRule.onNodeWithContentDescription("Menu").performClick()
        composeTestRule.waitForIdle()

        // Home should not exist in drawer when connected
        composeTestRule.onNode(
            hasText("Home") and hasAnyAncestor(hasTestTag("NavigationDrawer"))
        ).assertDoesNotExist()
    }

    @Test
    fun drawer_doesNotShowDevices() {
        // Devices is no longer in drawer (accessed via Settings > Scan)
        composeTestRule.onNodeWithContentDescription("Menu").performClick()
        composeTestRule.waitForIdle()

        // Devices should not exist in drawer
        composeTestRule.onNode(
            hasText("Devices") and hasAnyAncestor(hasTestTag("NavigationDrawer"))
        ).assertDoesNotExist()
    }

    @Test
    fun drawerNavigation_toSettingsScreen() {
        // Open drawer
        composeTestRule.onNodeWithContentDescription("Menu").performClick()
        composeTestRule.waitForIdle()

        // Click Settings in drawer
        composeTestRule.onNode(
            hasText("Settings") and hasAnyAncestor(hasTestTag("NavigationDrawer"))
        ).performClick()
        composeTestRule.waitForIdle()

        // Verify we're on Settings screen - check for settings-specific content
        // Service Mode section is always visible on Settings screen
        composeTestRule.onNodeWithText("Service Mode").assertIsDisplayed()
    }

    @Test
    fun drawerNavigation_toIoScreen_requiresServiceMode() {
        // I/O Control is only visible in drawer when service mode is enabled
        // First navigate to Settings and enable service mode
        composeTestRule.onNodeWithContentDescription("Menu").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNode(
            hasText("Settings") and hasAnyAncestor(hasTestTag("NavigationDrawer"))
        ).performClick()
        composeTestRule.waitForIdle()

        // Enable service mode using testTag
        composeTestRule.onNodeWithTag("ServiceModeSwitch").performClick()
        composeTestRule.waitForIdle()

        // Now open drawer and navigate to I/O Control
        composeTestRule.onNodeWithContentDescription("Menu").performClick()
        composeTestRule.waitForIdle()

        // Click I/O Control in drawer (now visible in service mode)
        composeTestRule.onNode(
            hasText("I/O Control") and hasAnyAncestor(hasTestTag("NavigationDrawer"))
        ).performClick()
        composeTestRule.waitForIdle()

        // Verify we're on I/O screen - check for screen-specific content
        composeTestRule.onNodeWithText("Digital Inputs").assertIsDisplayed()
    }

    // ============================================
    // Back Stack Tests
    // ============================================

    @Test
    fun navigation_multipleDrawerNavigations_maintainsCorrectBackStack() {
        // Navigate: Run → Settings → Diagnostics → Run
        // Each drawer navigation should work correctly

        // 1. Go to Run
        composeTestRule.onNodeWithContentDescription("Menu").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNode(
            hasText("Run") and hasAnyAncestor(hasTestTag("NavigationDrawer"))
        ).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Recipe").assertIsDisplayed()

        // 2. Go to Settings
        composeTestRule.onNodeWithContentDescription("Menu").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNode(
            hasText("Settings") and hasAnyAncestor(hasTestTag("NavigationDrawer"))
        ).performClick()
        composeTestRule.waitForIdle()
        // Service Mode section is always visible on Settings screen
        composeTestRule.onNodeWithText("Service Mode").assertIsDisplayed()

        // 3. Go to Diagnostics
        composeTestRule.onNodeWithContentDescription("Menu").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNode(
            hasText("Diagnostics") and hasAnyAncestor(hasTestTag("NavigationDrawer"))
        ).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("PID Controllers (RS-485)").assertIsDisplayed()

        // 4. Go back to Run
        composeTestRule.onNodeWithContentDescription("Menu").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNode(
            hasText("Run") and hasAnyAncestor(hasTestTag("NavigationDrawer"))
        ).performClick()
        composeTestRule.waitForIdle()

        // Should be on Run screen
        composeTestRule.onNodeWithText("Recipe").assertIsDisplayed()
    }

    // ============================================
    // Service Mode Tests
    // ============================================

    /**
     * Helper to enable service mode via Settings screen.
     * Service mode toggle was removed from drawer for cleaner UX.
     */
    private fun enableServiceModeViaSettings() {
        // Navigate to Settings via drawer
        composeTestRule.onNodeWithContentDescription("Menu").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNode(
            hasText("Settings") and hasAnyAncestor(hasTestTag("NavigationDrawer"))
        ).performClick()
        composeTestRule.waitForIdle()

        // Click Service Mode switch using testTag
        composeTestRule.onNodeWithTag("ServiceModeSwitch").performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun serviceMode_canBeToggledFromSettings() {
        // Enable service mode via Settings
        enableServiceModeViaSettings()

        // Service mode banner should appear
        composeTestRule.onNodeWithText("Service mode is on", substring = true).assertIsDisplayed()
    }

    @Test
    fun serviceMode_showsSvcBadgeOnRunScreen() {
        // Enable service mode via Settings
        enableServiceModeViaSettings()

        // Navigate to Run
        composeTestRule.onNodeWithContentDescription("Menu").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNode(
            hasText("Run") and hasAnyAncestor(hasTestTag("NavigationDrawer"))
        ).performClick()
        composeTestRule.waitForIdle()

        // Service mode SVC badge should be visible
        composeTestRule.onNodeWithText("SVC").assertIsDisplayed()
    }

    @Test
    fun serviceMode_showsIoSectionOnRunScreen() {
        // Enable service mode via Settings
        enableServiceModeViaSettings()

        // Navigate to Run
        composeTestRule.onNodeWithContentDescription("Menu").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNode(
            hasText("Run") and hasAnyAncestor(hasTestTag("NavigationDrawer"))
        ).performClick()
        composeTestRule.waitForIdle()

        // I/O section should be visible on Run screen in service mode
        composeTestRule.onNodeWithText("I/O").assertIsDisplayed()
    }

    @Test
    fun serviceMode_showsIoControlInDrawer() {
        // I/O Control should NOT be in drawer initially
        composeTestRule.onNodeWithContentDescription("Menu").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNode(
            hasText("I/O Control") and hasAnyAncestor(hasTestTag("NavigationDrawer"))
        ).assertDoesNotExist()

        // Close drawer
        composeTestRule.onNodeWithContentDescription("Menu").performClick()
        composeTestRule.waitForIdle()

        // Enable service mode
        enableServiceModeViaSettings()

        // Open drawer again
        composeTestRule.onNodeWithContentDescription("Menu").performClick()
        composeTestRule.waitForIdle()

        // I/O Control SHOULD now be in drawer
        composeTestRule.onNode(
            hasText("I/O Control") and hasAnyAncestor(hasTestTag("NavigationDrawer"))
        ).assertIsDisplayed()
    }
}
