package com.shakercontrol.app.ui.navigation

/**
 * Navigation routes for the app.
 * Screen titles from docs/ui-copy-labels-v1.md section 1.1.
 *
 * @param isTopLevel True for pages accessible from the navigation drawer.
 *                   Top-level pages don't show a back button.
 */
sealed class NavRoutes(val route: String, val title: String, val isTopLevel: Boolean = false) {
    data object Home : NavRoutes("home", "Home", isTopLevel = true)
    data object Run : NavRoutes("run", "Run", isTopLevel = true)
    data object Devices : NavRoutes("devices", "Devices")  // Accessed via Settings > Scan
    data object Alarms : NavRoutes("alarms", "Alarms", isTopLevel = true)
    data object Diagnostics : NavRoutes("diagnostics", "Diagnostics", isTopLevel = true)
    data object Settings : NavRoutes("settings", "Settings", isTopLevel = true)
    data object Io : NavRoutes("io", "I/O Control", isTopLevel = true)
    // Sub-pages (detail screens) - these show a back button
    data object Pid1 : NavRoutes("pid/1", "PID 1")
    data object Pid2 : NavRoutes("pid/2", "PID 2")
    data object Pid3 : NavRoutes("pid/3", "PID 3")

    // Register editor - uses navigation argument for controller ID
    data object RegisterEditor : NavRoutes("registers/{controllerId}", "Register Editor")

    companion object {
        fun fromRoute(route: String?): NavRoutes? = when {
            route == null -> null
            route == Home.route -> Home
            route == Run.route -> Run
            route == Devices.route -> Devices
            route == Alarms.route -> Alarms
            route == Diagnostics.route -> Diagnostics
            route == Settings.route -> Settings
            route == Io.route -> Io
            route == Pid1.route -> Pid1
            route == Pid2.route -> Pid2
            route == Pid3.route -> Pid3
            route.startsWith("registers/") -> RegisterEditor
            else -> null
        }

        fun registersRoute(controllerId: Int): String = "registers/$controllerId"

        /**
         * Parse a deep link route that may have parameters.
         * Returns the navigation route string for NavController.navigate().
         * E.g., "registers/2" stays as "registers/2" since it's already valid.
         */
        fun parseDeepLinkRoute(host: String, path: String?): String {
            val pathStr = path?.removePrefix("/") ?: ""
            return if (pathStr.isNotEmpty()) "$host/$pathStr" else host
        }
    }
}
