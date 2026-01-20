package com.shakercontrol.app.ui.registers

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shakercontrol.app.domain.model.*
import com.shakercontrol.app.ui.theme.StatusActive
import com.shakercontrol.app.ui.theme.StatusWarning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterEditorScreen(
    onNavigateBack: () -> Unit,
    viewModel: RegisterEditorViewModel = hiltViewModel()
) {
    val isConnected by viewModel.isConnected.collectAsStateWithLifecycle()
    val isServiceMode by viewModel.isServiceMode.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val expandedCategory by viewModel.expandedCategory.collectAsStateWithLifecycle()
    val selectedRegister by viewModel.selectedRegister.collectAsStateWithLifecycle()
    val registerValues by viewModel.registerValues.collectAsStateWithLifecycle()
    val registersByCategory by viewModel.registersByCategory.collectAsStateWithLifecycle()
    val pendingChangesCount by viewModel.pendingChangesCount.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val successMessage by viewModel.successMessage.collectAsStateWithLifecycle()

    // Confirmation dialog state
    var showWriteConfirmation by remember { mutableStateOf(false) }
    var showDiscardConfirmation by remember { mutableStateOf(false) }
    var showProtectedWarning by remember { mutableStateOf<ModbusRegister?>(null) }

    // Snackbar host state
    val snackbarHostState = remember { SnackbarHostState() }

    // Show error/success messages
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.clearError()
        }
    }

    LaunchedEffect(successMessage) {
        successMessage?.let {
            snackbarHostState.showSnackbar(it, duration = SnackbarDuration.Short)
            viewModel.clearSuccess()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Register Editor")
                        Text(
                            text = viewModel.getControllerName(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Pending changes badge
                    if (pendingChangesCount > 0) {
                        Badge(
                            containerColor = StatusWarning,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("$pendingChangesCount")
                        }
                    }

                    // Service mode indicator
                    if (isServiceMode) {
                        AssistChip(
                            onClick = { },
                            label = { Text("Service") },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Build,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (pendingChangesCount > 0) {
                BottomAppBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showDiscardConfirmation = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Discard")
                        }

                        Button(
                            onClick = { showWriteConfirmation = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Write Changes")
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (!isConnected) {
            // Not connected state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.WifiOff,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Connect to device to edit registers")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Info card
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = if (isServiceMode) {
                                    "Full register access enabled. Select a category to view registers."
                                } else {
                                    "Common registers only. Enable service mode for full access."
                                },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                // Category sections
                registersByCategory.forEach { (category, registers) ->
                    item {
                        CategoryHeader(
                            category = category,
                            registerCount = registers.size,
                            isExpanded = expandedCategory == category,
                            onClick = { viewModel.toggleCategory(category) }
                        )
                    }

                    if (expandedCategory == category) {
                        items(registers) { register ->
                            RegisterItem(
                                register = register,
                                value = registerValues[register.address],
                                isSelected = selectedRegister == register,
                                isServiceMode = isServiceMode,
                                onSelect = { viewModel.selectRegister(register) },
                                onRefresh = { viewModel.readRegister(register) },
                                onValueChange = { newValue ->
                                    if (viewModel.isProtectedRegister(register)) {
                                        showProtectedWarning = register
                                    }
                                    viewModel.stageValueChange(register, newValue)
                                },
                                onWrite = {
                                    if (!isServiceMode) {
                                        // Individual confirmation for common registers
                                        showProtectedWarning = register
                                    } else {
                                        viewModel.writeRegister(register)
                                    }
                                },
                                onDiscard = { viewModel.discardChange(register) },
                                formatValue = { viewModel.formatValue(register, it) }
                            )
                        }
                    }
                }

                // Loading indicator
                if (isLoading) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }

    // Write confirmation dialog
    if (showWriteConfirmation) {
        AlertDialog(
            onDismissRequest = { showWriteConfirmation = false },
            icon = { Icon(Icons.Default.Save, contentDescription = null) },
            title = { Text("Write Changes?") },
            text = {
                Text("This will write $pendingChangesCount register(s) to the controller. Changes take effect immediately.")
            },
            confirmButton = {
                Button(onClick = {
                    showWriteConfirmation = false
                    viewModel.writeAllChanges()
                }) {
                    Text("Write")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showWriteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Discard confirmation dialog
    if (showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirmation = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null) },
            title = { Text("Discard Changes?") },
            text = {
                Text("This will discard all $pendingChangesCount pending change(s).")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardConfirmation = false
                        viewModel.discardAllChanges()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Discard")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDiscardConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Protected register warning
    showProtectedWarning?.let { register ->
        AlertDialog(
            onDismissRequest = { showProtectedWarning = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text("Confirm Write") },
            text = {
                Column {
                    Text("Write ${register.name} to controller?")
                    if (register.address in Lc108Registers.protectedRegisters) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Warning: This is a protected register. Incorrect values may affect communication.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showProtectedWarning = null
                    viewModel.writeRegister(register)
                }) {
                    Text("Write")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showProtectedWarning = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun CategoryHeader(
    category: RegisterCategory,
    registerCount: Int,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (isExpanded) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = category.displayName,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Badge {
                Text("$registerCount")
            }
        }
    }
}

@Composable
private fun RegisterItem(
    register: ModbusRegister,
    value: RegisterValue?,
    isSelected: Boolean,
    isServiceMode: Boolean,
    onSelect: () -> Unit,
    onRefresh: () -> Unit,
    onValueChange: (Int) -> Unit,
    onWrite: () -> Unit,
    onDiscard: () -> Unit,
    formatValue: (Int) -> String
) {
    val hasChanges = value?.hasChanges == true
    val isLoading = value?.isLoading == true
    val hasError = value?.error != null

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                hasChanges -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                isSelected -> MaterialTheme.colorScheme.surfaceContainerHighest
                else -> MaterialTheme.colorScheme.surfaceContainer
            }
        )
    ) {
        Column(
            modifier = Modifier
                .clickable(enabled = !register.isReadOnly) { onSelect() }
                .padding(16.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Register name and address
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = register.name,
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (register.isReadOnly) {
                            Spacer(Modifier.width(8.dp))
                            AssistChip(
                                onClick = { },
                                label = { Text("R/O", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.height(24.dp)
                            )
                        }
                        if (hasChanges) {
                            Spacer(Modifier.width(8.dp))
                            Badge(containerColor = StatusWarning) {
                                Text("*")
                            }
                        }
                    }
                    Text(
                        text = register.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Address
                Text(
                    text = "0x${register.address.toString(16).uppercase().padStart(4, '0')}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))

            // Value row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Reading...", style = MaterialTheme.typography.bodyMedium)
                } else if (hasError) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = value?.error ?: "Error",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                } else if (value?.displayValue != null) {
                    Text(
                        text = formatValue(value.displayValue!!),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (hasChanges && value.currentValue != null) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "(was: ${formatValue(value.currentValue!!)})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Text(
                        text = "Tap to read",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.weight(1f))

                // Action buttons
                IconButton(onClick = onRefresh, enabled = !isLoading) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }

            // Editor (when selected and not read-only)
            AnimatedVisibility(
                visible = isSelected && !register.isReadOnly && value?.currentValue != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                RegisterEditor(
                    register = register,
                    currentValue = value?.displayValue ?: 0,
                    onValueChange = onValueChange,
                    onWrite = onWrite,
                    onDiscard = onDiscard,
                    hasChanges = hasChanges,
                    formatValue = formatValue
                )
            }

            // Help text
            if (isSelected && register.helpText != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = register.helpText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RegisterEditor(
    register: ModbusRegister,
    currentValue: Int,
    onValueChange: (Int) -> Unit,
    onWrite: () -> Unit,
    onDiscard: () -> Unit,
    hasChanges: Boolean,
    formatValue: (Int) -> String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))

        when (val dataType = register.dataType) {
            is RegisterDataType.ScaledInt -> {
                ScaledIntEditor(
                    value = currentValue,
                    scale = dataType.scale,
                    min = dataType.min,
                    max = dataType.max,
                    unit = dataType.unit,
                    decimalPlaces = dataType.decimalPlaces,
                    onValueChange = onValueChange
                )
            }
            is RegisterDataType.RawInt -> {
                RawIntEditor(
                    value = currentValue,
                    min = dataType.min,
                    max = dataType.max,
                    unit = dataType.unit,
                    onValueChange = onValueChange
                )
            }
            is RegisterDataType.Enum -> {
                EnumEditor(
                    value = currentValue,
                    options = dataType.options,
                    onValueChange = onValueChange
                )
            }
            is RegisterDataType.Boolean -> {
                BooleanEditor(
                    value = currentValue != 0,
                    onValueChange = { onValueChange(if (it) 1 else 0) }
                )
            }
            is RegisterDataType.Bitfield -> {
                // Read-only display
                Text(
                    text = formatValue(currentValue),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Write/Discard buttons for individual register
        if (hasChanges) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDiscard) {
                    Text("Discard")
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onWrite) {
                    Text("Write")
                }
            }
        }
    }
}

@Composable
private fun ScaledIntEditor(
    value: Int,
    scale: Float,
    min: Float?,
    max: Float?,
    unit: String,
    decimalPlaces: Int,
    onValueChange: (Int) -> Unit
) {
    var textValue by remember(value) {
        mutableStateOf(String.format("%.${decimalPlaces}f", value.toShort() * scale))
    }

    OutlinedTextField(
        value = textValue,
        onValueChange = { newText ->
            textValue = newText
            newText.toFloatOrNull()?.let { floatVal ->
                val rawValue = (floatVal / scale).toInt()
                onValueChange(rawValue and 0xFFFF)
            }
        },
        label = { Text("Value ($unit)") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        supportingText = {
            if (min != null || max != null) {
                Text("Range: ${min ?: "..."} to ${max ?: "..."} $unit")
            }
        }
    )
}

@Composable
private fun RawIntEditor(
    value: Int,
    min: Int?,
    max: Int?,
    unit: String,
    onValueChange: (Int) -> Unit
) {
    var textValue by remember(value) { mutableStateOf(value.toString()) }

    OutlinedTextField(
        value = textValue,
        onValueChange = { newText ->
            textValue = newText
            newText.toIntOrNull()?.let { intVal ->
                onValueChange(intVal.coerceIn(0, 65535))
            }
        },
        label = { Text("Value${if (unit.isNotEmpty()) " ($unit)" else ""}") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        supportingText = {
            if (min != null || max != null) {
                Text("Range: ${min ?: 0} to ${max ?: 65535}")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnumEditor(
    value: Int,
    options: Map<Int, String>,
    onValueChange: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedOption = options[value] ?: "Unknown ($value)"

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedOption,
            onValueChange = { },
            readOnly = true,
            label = { Text("Value") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (optionValue, optionName) ->
                DropdownMenuItem(
                    text = { Text(optionName) },
                    onClick = {
                        onValueChange(optionValue)
                        expanded = false
                    },
                    leadingIcon = if (optionValue == value) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else null
                )
            }
        }
    }
}

@Composable
private fun BooleanEditor(
    value: Boolean,
    onValueChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Value",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = value,
            onCheckedChange = onValueChange
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (value) "On" else "Off",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
