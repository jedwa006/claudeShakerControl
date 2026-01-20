package com.shakercontrol.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shakercontrol.app.ui.theme.SemanticColors
import com.shakercontrol.app.ui.theme.ShakerControlTheme

/**
 * Settings screen.
 * Spec: docs/dashboard-sec-v1.md section 11 and docs/ui-copy-labels-v1.md section 8.
 */
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val controllerVersion by viewModel.controllerVersion.collectAsStateWithLifecycle()
    val isServiceModeEnabled by viewModel.isServiceModeEnabled.collectAsStateWithLifecycle()

    SettingsScreenContent(
        controllerVersion = controllerVersion,
        isServiceModeEnabled = isServiceModeEnabled,
        onServiceModeToggle = viewModel::toggleServiceMode
    )
}

@Composable
private fun SettingsScreenContent(
    controllerVersion: String,
    isServiceModeEnabled: Boolean,
    onServiceModeToggle: () -> Unit
) {
    var selectedTheme by remember { mutableStateOf("Dark") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Theme setting
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                SettingRow(
                    title = "Theme",
                    value = selectedTheme,
                    onClick = {
                        selectedTheme = if (selectedTheme == "Dark") "System" else "Dark"
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                SettingRow(
                    title = "Time input format",
                    value = "mm:ss"
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Export logs", style = MaterialTheme.typography.bodyLarge)
                    OutlinedButton(onClick = { /* TODO: Future */ }) {
                        Text("Export")
                    }
                }
            }
        }

        // Service Mode section
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Service Mode",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Enables manual control of outputs and diagnostics",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isServiceModeEnabled,
                        onCheckedChange = { onServiceModeToggle() },
                        modifier = Modifier.testTag("ServiceModeSwitch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SemanticColors.Warning,
                            checkedTrackColor = SemanticColors.Warning.copy(alpha = 0.5f)
                        )
                    )
                }
                if (isServiceModeEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = SemanticColors.Warning.copy(alpha = 0.2f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "Service mode active - manual controls enabled",
                            style = MaterialTheme.typography.labelMedium,
                            color = SemanticColors.Warning,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // About section
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "About",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(16.dp))

                SettingRow(title = "App version", value = "0.1.0")
                SettingRow(title = "Controller version", value = controllerVersion)
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    value: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(widthDp = 800, heightDp = 600)
@Composable
private fun SettingsScreenPreview() {
    ShakerControlTheme {
        Surface {
            SettingsScreenContent(
                controllerVersion = "0.2.0+26011901",
                isServiceModeEnabled = false,
                onServiceModeToggle = {}
            )
        }
    }
}

@Preview(widthDp = 800, heightDp = 600)
@Composable
private fun SettingsScreenServiceModePreview() {
    ShakerControlTheme {
        Surface {
            SettingsScreenContent(
                controllerVersion = "0.2.0+26011901",
                isServiceModeEnabled = true,
                onServiceModeToggle = {}
            )
        }
    }
}
