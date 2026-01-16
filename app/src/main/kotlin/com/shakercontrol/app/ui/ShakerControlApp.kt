package com.shakercontrol.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.shakercontrol.app.ui.components.ServiceModeBanner
import com.shakercontrol.app.ui.components.SessionLeaseWarningBanner
import com.shakercontrol.app.ui.components.StatusStrip
import com.shakercontrol.app.ui.navigation.AppNavHost
import com.shakercontrol.app.ui.navigation.NavRoutes
import kotlinx.coroutines.launch

@Composable
fun ShakerControlApp(
    viewModel: MainViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val systemStatus by viewModel.systemStatus.collectAsStateWithLifecycle()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = NavRoutes.fromRoute(currentBackStackEntry?.destination?.route)
    val screenTitle = currentRoute?.title ?: "Home"

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                NavigationDrawerContent(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(NavRoutes.Home.route) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                        scope.launch { drawerState.close() }
                    },
                    onServiceModeToggle = {
                        if (systemStatus.isServiceModeEnabled) {
                            viewModel.disableServiceMode()
                        } else {
                            viewModel.enableServiceMode()
                        }
                    },
                    isServiceModeEnabled = systemStatus.isServiceModeEnabled
                )
            }
        }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Global status strip
            StatusStrip(
                screenTitle = screenTitle,
                systemStatus = systemStatus,
                onMenuClick = { scope.launch { drawerState.open() } },
                onConnectionClick = { navController.navigate(NavRoutes.Devices.route) },
                onAlarmsClick = { navController.navigate(NavRoutes.Alarms.route) }
            )

            // Service mode banner
            if (systemStatus.isServiceModeEnabled) {
                ServiceModeBanner()
            }

            // Session lease warning banner
            SessionLeaseWarningBanner(
                sessionLeaseStatus = systemStatus.sessionLeaseStatus
            )

            // Main content
            AppNavHost(
                navController = navController,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun NavigationDrawerContent(
    currentRoute: NavRoutes?,
    onNavigate: (String) -> Unit,
    onServiceModeToggle: () -> Unit,
    isServiceModeEnabled: Boolean
) {
    Spacer(modifier = Modifier.height(16.dp))

    Text(
        text = "Shaker Control",
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    NavigationDrawerItem(
        label = { Text("Home") },
        selected = currentRoute == NavRoutes.Home,
        onClick = { onNavigate(NavRoutes.Home.route) },
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )

    NavigationDrawerItem(
        label = { Text("Run") },
        selected = currentRoute == NavRoutes.Run,
        onClick = { onNavigate(NavRoutes.Run.route) },
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )

    NavigationDrawerItem(
        label = { Text("Devices") },
        selected = currentRoute == NavRoutes.Devices,
        onClick = { onNavigate(NavRoutes.Devices.route) },
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )

    NavigationDrawerItem(
        label = { Text("Alarms") },
        selected = currentRoute == NavRoutes.Alarms,
        onClick = { onNavigate(NavRoutes.Alarms.route) },
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )

    NavigationDrawerItem(
        label = { Text("Diagnostics") },
        selected = currentRoute == NavRoutes.Diagnostics,
        onClick = { onNavigate(NavRoutes.Diagnostics.route) },
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )

    NavigationDrawerItem(
        label = { Text("Settings") },
        selected = currentRoute == NavRoutes.Settings,
        onClick = { onNavigate(NavRoutes.Settings.route) },
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    // Service mode toggle
    NavigationDrawerItem(
        label = {
            Text(
                text = if (isServiceModeEnabled) "Exit service mode" else "Service mode",
                color = if (isServiceModeEnabled)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.onSurface
            )
        },
        selected = false,
        onClick = onServiceModeToggle,
        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
    )
}
