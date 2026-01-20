package com.shakercontrol.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.shakercontrol.app.ui.alarms.AlarmsScreen
import com.shakercontrol.app.ui.devices.DevicesScreen
import com.shakercontrol.app.ui.diagnostics.DiagnosticsScreen
import com.shakercontrol.app.ui.home.HomeScreen
import com.shakercontrol.app.ui.io.IoScreen
import com.shakercontrol.app.ui.pid.PidDetailScreen
import com.shakercontrol.app.ui.run.RunScreen
import com.shakercontrol.app.ui.settings.SettingsScreen

@Composable
fun AppNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = NavRoutes.Home.route,
        modifier = modifier
    ) {
        composable(NavRoutes.Home.route) {
            HomeScreen(
                onNavigateToRun = { navController.navigate(NavRoutes.Run.route) },
                onNavigateToDevices = { navController.navigate(NavRoutes.Devices.route) },
                onNavigateToPid = { pidId ->
                    navController.navigate("pid/$pidId")
                },
                onNavigateToDiagnostics = { navController.navigate(NavRoutes.Diagnostics.route) },
                onNavigateToSettings = { navController.navigate(NavRoutes.Settings.route) }
            )
        }

        composable(NavRoutes.Run.route) {
            RunScreen(
                onNavigateToPid = { pidId ->
                    navController.navigate("pid/$pidId")
                },
                onNavigateToIo = { navController.navigate(NavRoutes.Io.route) },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.Devices.route) {
            DevicesScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToHome = {
                    navController.navigate(NavRoutes.Home.route) {
                        popUpTo(NavRoutes.Home.route) {
                            inclusive = true
                        }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(NavRoutes.Alarms.route) {
            AlarmsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.Diagnostics.route) {
            DiagnosticsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.Io.route) {
            IoScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.Pid1.route) {
            PidDetailScreen(
                pidId = 1,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.Pid2.route) {
            PidDetailScreen(
                pidId = 2,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(NavRoutes.Pid3.route) {
            PidDetailScreen(
                pidId = 3,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
