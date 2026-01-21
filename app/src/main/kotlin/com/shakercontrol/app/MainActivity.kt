package com.shakercontrol.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.shakercontrol.app.ui.ShakerControlApp
import com.shakercontrol.app.ui.theme.ShakerControlTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Deep link actions that trigger behavior rather than navigation.
 */
sealed class DeepLinkAction {
    data object ServiceModeEnable : DeepLinkAction()
    data object ServiceModeDisable : DeepLinkAction()
    data object ServiceModeToggle : DeepLinkAction()
    data object Connect : DeepLinkAction()
    data object Disconnect : DeepLinkAction()
    data object Reconnect : DeepLinkAction()
    data object DemoModeEnable : DeepLinkAction()
    data object DemoModeDisable : DeepLinkAction()

    // Test actions for automated testing
    data class SetRelay(val channel: Int, val on: Boolean) : DeepLinkAction()
    data class SetCapability(val subsystemId: Int, val level: Int) : DeepLinkAction()
    data class SetSafetyGate(val gateId: Int, val enabled: Boolean) : DeepLinkAction()
    data object ToggleLight : DeepLinkAction()
    data object ToggleDoor : DeepLinkAction()
    data object StartChilldown : DeepLinkAction()
}

/**
 * Result of parsing a deep link URI.
 */
data class DeepLinkResult(
    val route: String? = null,
    val action: DeepLinkAction? = null
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Extract initial route and action from deep link if present
        val deepLinkResult = parseDeepLink(intent)

        setContent {
            ShakerControlTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ShakerControlApp(
                        initialRoute = deepLinkResult.route,
                        initialAction = deepLinkResult.action
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle deep links when app is already running
        val result = parseDeepLink(intent)
        android.util.Log.d("MainActivity", "Deep link received: route=${result.route}, action=${result.action}")
    }

    /**
     * Parse deep link URI to extract navigation route and/or action.
     *
     * Navigation routes:
     * - shaker://run, shaker://settings, shaker://devices, shaker://pid/1, etc.
     *
     * Actions (trigger behavior, may include navigation):
     * - shaker://action/service-mode/enable
     * - shaker://action/service-mode/disable
     * - shaker://action/service-mode/toggle
     * - shaker://action/connect (navigates to Devices)
     * - shaker://action/disconnect
     * - shaker://action/reconnect
     *
     * Test actions (for automated testing):
     * - shaker://test/relay/{channel}/{state} - Set relay (channel 1-8, state 0/1)
     * - shaker://test/capability/{subsystem}/{level} - Set capability (subsystem 0-6, level 0-2)
     * - shaker://test/gate/{gateId}/{enabled} - Set safety gate (gateId 0-8, enabled 0/1)
     * - shaker://test/light - Toggle chamber light
     * - shaker://test/door - Toggle door lock
     * - shaker://test/chilldown - Start chilldown
     */
    private fun parseDeepLink(intent: Intent?): DeepLinkResult {
        val uri = intent?.data ?: return DeepLinkResult()
        if (uri.scheme != "shaker") return DeepLinkResult()

        val host = uri.host ?: return DeepLinkResult()
        val pathSegments = uri.pathSegments

        return when (host) {
            "action" -> {
                // Action-based deep links
                val action = when {
                    pathSegments.getOrNull(0) == "service-mode" -> {
                        when (pathSegments.getOrNull(1)) {
                            "enable" -> DeepLinkAction.ServiceModeEnable
                            "disable" -> DeepLinkAction.ServiceModeDisable
                            "toggle" -> DeepLinkAction.ServiceModeToggle
                            else -> DeepLinkAction.ServiceModeToggle
                        }
                    }
                    pathSegments.getOrNull(0) == "connect" -> DeepLinkAction.Connect
                    pathSegments.getOrNull(0) == "disconnect" -> DeepLinkAction.Disconnect
                    pathSegments.getOrNull(0) == "reconnect" -> DeepLinkAction.Reconnect
                    pathSegments.getOrNull(0) == "demo-mode" -> {
                        when (pathSegments.getOrNull(1)) {
                            "enable" -> DeepLinkAction.DemoModeEnable
                            "disable" -> DeepLinkAction.DemoModeDisable
                            else -> null
                        }
                    }
                    else -> null
                }
                DeepLinkResult(action = action)
            }
            "test" -> {
                // Test action deep links (for automated testing)
                val action = when (pathSegments.getOrNull(0)) {
                    "relay" -> {
                        val channel = pathSegments.getOrNull(1)?.toIntOrNull() ?: return DeepLinkResult()
                        val state = pathSegments.getOrNull(2)?.toIntOrNull() ?: return DeepLinkResult()
                        DeepLinkAction.SetRelay(channel, state == 1)
                    }
                    "capability" -> {
                        val subsystem = pathSegments.getOrNull(1)?.toIntOrNull() ?: return DeepLinkResult()
                        val level = pathSegments.getOrNull(2)?.toIntOrNull() ?: return DeepLinkResult()
                        DeepLinkAction.SetCapability(subsystem, level)
                    }
                    "gate" -> {
                        val gateId = pathSegments.getOrNull(1)?.toIntOrNull() ?: return DeepLinkResult()
                        val enabled = pathSegments.getOrNull(2)?.toIntOrNull() ?: return DeepLinkResult()
                        DeepLinkAction.SetSafetyGate(gateId, enabled == 1)
                    }
                    "light" -> DeepLinkAction.ToggleLight
                    "door" -> DeepLinkAction.ToggleDoor
                    "chilldown" -> DeepLinkAction.StartChilldown
                    else -> null
                }
                DeepLinkResult(action = action)
            }
            else -> {
                // Navigation route
                val path = uri.path?.removePrefix("/") ?: ""
                val route = if (path.isNotEmpty()) "$host/$path" else host
                DeepLinkResult(route = route)
            }
        }
    }
}
