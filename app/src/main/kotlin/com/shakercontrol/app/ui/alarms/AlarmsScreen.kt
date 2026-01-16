package com.shakercontrol.app.ui.alarms

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shakercontrol.app.domain.model.Alarm
import com.shakercontrol.app.domain.model.AlarmSeverity
import com.shakercontrol.app.domain.model.AlarmSource
import com.shakercontrol.app.domain.model.AlarmState
import com.shakercontrol.app.ui.theme.SemanticColors
import com.shakercontrol.app.ui.theme.ShakerControlTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Alarms screen.
 * Spec: docs/dashboard-sec-v1.md section 9 and docs/ui-copy-labels-v1.md section 5.
 */
@Composable
fun AlarmsScreen(
    onNavigateBack: () -> Unit,
    viewModel: AlarmsViewModel = hiltViewModel()
) {
    val alarms by viewModel.alarms.collectAsStateWithLifecycle()

    AlarmsContent(alarms = alarms)
}

@Composable
private fun AlarmsContent(alarms: List<Alarm>) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Active", "History")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Alarms",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                val filteredAlarms = when (selectedTab) {
                    0 -> alarms.filter { it.state == AlarmState.ACTIVE }
                    else -> alarms
                }

                if (filteredAlarms.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No alarms.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredAlarms) { alarm ->
                            AlarmRow(alarm = alarm)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlarmRow(alarm: Alarm) {
    val severityColor = when (alarm.severity) {
        AlarmSeverity.CRITICAL -> SemanticColors.Critical
        AlarmSeverity.ALARM -> SemanticColors.Alarm
        AlarmSeverity.WARNING -> SemanticColors.Warning
        AlarmSeverity.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val formatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    Card(
        colors = CardDefaults.cardColors(
            containerColor = severityColor.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Severity badge
                Surface(
                    color = severityColor.copy(alpha = 0.2f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = alarm.severity.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = severityColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Column {
                    Text(
                        text = alarm.message,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "${alarm.source.displayName} • ${formatter.format(alarm.timestamp)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = alarm.state.displayName,
                    style = MaterialTheme.typography.labelMedium
                )
                if (alarm.isAcknowledged) {
                    Text(
                        text = "Acknowledged",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (alarm.state == AlarmState.ACTIVE) {
                    OutlinedButton(
                        onClick = { /* TODO: Stage 5 */ },
                        enabled = false,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Acknowledge", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Preview(widthDp = 800, heightDp = 600)
@Composable
private fun AlarmsScreenPreview() {
    ShakerControlTheme {
        Surface {
            AlarmsContent(
                alarms = listOf(
                    Alarm(
                        id = "1",
                        eventId = 0x1001,
                        severity = AlarmSeverity.CRITICAL,
                        source = AlarmSource.SYSTEM,
                        message = "E-stop asserted",
                        timestamp = Instant.now(),
                        state = AlarmState.ACTIVE,
                        isAcknowledged = false
                    ),
                    Alarm(
                        id = "2",
                        eventId = 0x1301,
                        severity = AlarmSeverity.WARNING,
                        source = AlarmSource.PID_3,
                        message = "RS-485 device offline",
                        timestamp = Instant.now().minusSeconds(300),
                        state = AlarmState.CLEARED,
                        isAcknowledged = true
                    )
                )
            )
        }
    }
}

@Preview(widthDp = 800, heightDp = 600)
@Composable
private fun AlarmsScreenEmptyPreview() {
    ShakerControlTheme {
        Surface {
            AlarmsContent(alarms = emptyList())
        }
    }
}
