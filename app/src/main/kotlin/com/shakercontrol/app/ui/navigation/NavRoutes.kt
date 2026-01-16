package com.shakercontrol.app.ui.navigation

/**
 * Navigation routes for the app.
 * Screen titles from docs/ui-copy-labels-v1.md section 1.1.
 */
sealed class NavRoutes(val route: String, val title: String) {
    data object Home : NavRoutes("home", "Home")
    data object Run : NavRoutes("run", "Run")
    data object Devices : NavRoutes("devices", "Devices")
    data object Alarms : NavRoutes("alarms", "Alarms")
    data object Diagnostics : NavRoutes("diagnostics", "Diagnostics")
    data object Settings : NavRoutes("settings", "Settings")
    data object Pid1 : NavRoutes("pid/1", "PID 1")
    data object Pid2 : NavRoutes("pid/2", "PID 2")
    data object Pid3 : NavRoutes("pid/3", "PID 3")

    companion object {
        fun fromRoute(route: String?): NavRoutes? = when {
            route == null -> null
            route == Home.route -> Home
            route == Run.route -> Run
            route == Devices.route -> Devices
            route == Alarms.route -> Alarms
            route == Diagnostics.route -> Diagnostics
            route == Settings.route -> Settings
            route == Pid1.route -> Pid1
            route == Pid2.route -> Pid2
            route == Pid3.route -> Pid3
            else -> null
        }
    }
}
