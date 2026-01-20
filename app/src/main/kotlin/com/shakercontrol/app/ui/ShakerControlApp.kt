package com.shakercontrol.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.shakercontrol.app.DeepLinkAction
import com.shakercontrol.app.ui.components.ServiceModeBanner
import com.shakercontrol.app.ui.components.SessionLeaseWarningBanner
import com.shakercontrol.app.ui.components.StatusStrip
import com.shakercontrol.app.ui.navigation.AppNavHost
import com.shakercontrol.app.ui.navigation.NavRoutes
import kotlinx.coroutines.launch

/**
 * Main app composable with navigation drawer and status strip.
 * @param initialRoute Optional route from deep link (e.g., "settings", "run", "pid/1")
 * @param initialAction Optional action from deep link (e.g., enable service mode, connect)
 */
@Composable
fun ShakerControlApp(
    initialRoute: String? = null,
    initialAction: DeepLinkAction? = null,
    viewModel: MainViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val systemStatus by viewModel.systemStatus.collectAsStateWithLifecycle()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = NavRoutes.fromRoute(currentBackStackEntry?.destination?.route)
    val screenTitle = currentRoute?.title ?: "Home"

    // Navigate to initial route from deep link on first composition
    LaunchedEffect(initialRoute) {
        initialRoute?.let { route ->
            // Small delay to ensure nav graph is ready
            kotlinx.coroutines.delay(100)
            navController.navigate(route) {
                popUpTo(NavRoutes.Home.route) {
                    saveState = true
                }
                launchSingleTop = true
            }
        }
    }

    // Handle initial action from deep link
    LaunchedEffect(initialAction) {
        initialAction?.let { action ->
            kotlinx.coroutines.delay(100)
            when (action) {
                DeepLinkAction.ServiceModeEnable -> viewModel.enableServiceMode()
                DeepLinkAction.ServiceModeDisable -> viewModel.disableServiceMode()
                DeepLinkAction.ServiceModeToggle -> {
                    if (systemStatus.isServiceModeEnabled) {
                        viewModel.disableServiceMode()
                    } else {
                        viewModel.enableServiceMode()
                    }
                }
                DeepLinkAction.Connect -> {
                    navController.navigate(NavRoutes.Devices.route)
                    // Trigger connect after navigating
                    kotlinx.coroutines.delay(500)
                    viewModel.connect()
                }
                DeepLinkAction.Disconnect -> viewModel.disconnect()
                DeepLinkAction.Reconnect -> viewModel.reconnect()
            }
        }
    }

    // Show back button only for sub-pages (not top-level drawer destinations)
    val canNavigateBack = currentRoute?.isTopLevel == false && navController.previousBackStackEntry != null

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.testTag("NavigationDrawer")
            ) {
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
                    isServiceModeEnabled = systemStatus.isServiceModeEnabled
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Global status strip with back button when applicable
            StatusStrip(
                screenTitle = screenTitle,
                systemStatus = systemStatus,
                onMenuClick = { scope.launch { drawerState.open() } },
                onConnectionClick = {
                    // Use same navigation pattern as drawer to avoid back stack issues
                    navController.navigate(NavRoutes.Devices.route) {
                        popUpTo(NavRoutes.Home.route) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onAlarmsClick = {
                    // Use same navigation pattern as drawer to avoid back stack issues
                    navController.navigate(NavRoutes.Alarms.route) {
                        popUpTo(NavRoutes.Home.route) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                canNavigateBack = canNavigateBack,
                onBackClick = { navController.popBackStack() }
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

    // I/O Control only visible in service mode
    if (isServiceModeEnabled) {
        NavigationDrawerItem(
            label = { Text("I/O Control") },
            selected = currentRoute == NavRoutes.Io,
            onClick = { onNavigate(NavRoutes.Io.route) },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
    }

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
}
