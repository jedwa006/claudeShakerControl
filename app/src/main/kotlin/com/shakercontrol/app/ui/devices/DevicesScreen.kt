package com.shakercontrol.app.ui.devices

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.shakercontrol.app.ui.theme.ShakerControlTheme

/**
 * Devices screen for BLE scan/connect.
 * Spec: docs/dashboard-sec-v1.md section 6 and docs/ui-copy-labels-v1.md section 4.
 *
 * Placeholder for Stage 1 - full implementation in Stage 2.
 */
@Composable
fun DevicesScreen(
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Known device section
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Known device",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Last connected: SYS-CTRL-001",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { /* TODO: Stage 2 */ }) {
                        Text("Connect")
                    }
                    OutlinedButton(onClick = { /* TODO: Stage 2 */ }) {
                        Text("Forget")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Available devices section
        Card(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Available devices",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Button(onClick = { /* TODO: Stage 2 */ }) {
                        Text("Scan")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No devices found.",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Make sure the controller is powered and in range.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Implementation note
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Text(
                text = "BLE implementation coming in Stage 2",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Preview(widthDp = 800, heightDp = 600)
@Composable
private fun DevicesScreenPreview() {
    ShakerControlTheme {
        Surface {
            DevicesScreen(onNavigateBack = {})
        }
    }
}
