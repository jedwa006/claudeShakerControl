package com.shakercontrol.app.ui.registers

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shakercontrol.app.data.repository.MachineRepository
import com.shakercontrol.app.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Modbus Register Editor screen.
 * Manages register reading, editing, and writing for PID controllers.
 */
@HiltViewModel
class RegisterEditorViewModel @Inject constructor(
    private val repository: MachineRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "RegisterEditorVM"
    }

    // Controller ID from navigation args
    val controllerId: Int = savedStateHandle.get<Int>("controllerId") ?: 1

    // Connection state
    val isConnected = repository.systemStatus
        .map { it.connectionState == ConnectionState.LIVE || it.connectionState == ConnectionState.DEGRADED }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Service mode state
    val isServiceMode = repository.systemStatus
        .map { it.isServiceModeEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Currently expanded category (null = all collapsed)
    private val _expandedCategory = MutableStateFlow<RegisterCategory?>(null)
    val expandedCategory: StateFlow<RegisterCategory?> = _expandedCategory.asStateFlow()

    // Currently selected register for editing
    private val _selectedRegister = MutableStateFlow<ModbusRegister?>(null)
    val selectedRegister: StateFlow<ModbusRegister?> = _selectedRegister.asStateFlow()

    // Register values (address -> RegisterValue)
    private val _registerValues = MutableStateFlow<Map<Int, RegisterValue>>(emptyMap())
    val registerValues: StateFlow<Map<Int, RegisterValue>> = _registerValues.asStateFlow()

    // Pending changes count
    val pendingChangesCount: StateFlow<Int> = _registerValues
        .map { values -> values.count { it.value.hasChanges } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Error message
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Success message (for write confirmation)
    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    // Available registers based on service mode
    val availableRegisters: StateFlow<List<ModbusRegister>> = isServiceMode
        .map { serviceMode ->
            if (serviceMode) {
                Lc108Registers.allRegisters
            } else {
                Lc108Registers.commonRegisters
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Lc108Registers.commonRegisters)

    // Registers grouped by category (filtered by access level)
    val registersByCategory: StateFlow<Map<RegisterCategory, List<ModbusRegister>>> = isServiceMode
        .map { serviceMode ->
            Lc108Registers.byCategory.mapValues { (_, registers) ->
                registers.filter { reg ->
                    serviceMode || reg.accessLevel == RegisterAccessLevel.COMMON
                }
            }.filterValues { it.isNotEmpty() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /**
     * Toggle category expansion.
     * Automatically reads all registers in the category when expanding.
     */
    fun toggleCategory(category: RegisterCategory) {
        val newValue = if (_expandedCategory.value == category) null else category
        _expandedCategory.value = newValue

        // Auto-read registers when expanding a category
        if (newValue != null) {
            readCategory(newValue)
        }
    }

    /**
     * Select a register for editing.
     * Automatically reads the current value if not already loaded.
     */
    fun selectRegister(register: ModbusRegister) {
        _selectedRegister.value = register

        // Read the register if we don't have a value yet
        val currentValue = _registerValues.value[register.address]
        if (currentValue == null || currentValue.currentValue == null) {
            readRegister(register)
        }
    }

    /**
     * Clear register selection.
     */
    fun clearSelection() {
        _selectedRegister.value = null
    }

    /**
     * Read a single register from the controller.
     */
    fun readRegister(register: ModbusRegister) {
        viewModelScope.launch {
            // Mark as loading
            updateRegisterState(register.address) { it.copy(isLoading = true, error = null) }

            val result = repository.readRegisters(controllerId, register.address, 1)

            result.onSuccess { values ->
                if (values.isNotEmpty()) {
                    updateRegisterState(register.address) {
                        it.copy(
                            currentValue = values[0],
                            isLoading = false,
                            error = null
                        )
                    }
                    Log.d(TAG, "Read ${register.name}: ${values[0]}")
                }
            }.onFailure { e ->
                updateRegisterState(register.address) {
                    it.copy(isLoading = false, error = e.message)
                }
                Log.e(TAG, "Failed to read ${register.name}: ${e.message}")
            }
        }
    }

    /**
     * Read all registers in a category.
     */
    fun readCategory(category: RegisterCategory) {
        val registers = Lc108Registers.byCategory[category] ?: return

        viewModelScope.launch {
            _isLoading.value = true

            for (register in registers) {
                // Mark as loading
                updateRegisterState(register.address) { it.copy(isLoading = true, error = null) }

                val result = repository.readRegisters(controllerId, register.address, 1)

                result.onSuccess { values ->
                    if (values.isNotEmpty()) {
                        updateRegisterState(register.address) {
                            it.copy(currentValue = values[0], isLoading = false, error = null)
                        }
                    }
                }.onFailure { e ->
                    updateRegisterState(register.address) {
                        it.copy(isLoading = false, error = e.message)
                    }
                }
            }

            _isLoading.value = false
        }
    }

    /**
     * Stage a value change (doesn't write to controller yet).
     */
    fun stageValueChange(register: ModbusRegister, newValue: Int) {
        updateRegisterState(register.address) {
            it.copy(pendingValue = newValue)
        }
    }

    /**
     * Discard pending change for a register.
     */
    fun discardChange(register: ModbusRegister) {
        updateRegisterState(register.address) {
            it.copy(pendingValue = null)
        }
    }

    /**
     * Discard all pending changes.
     */
    fun discardAllChanges() {
        _registerValues.value = _registerValues.value.mapValues {
            it.value.copy(pendingValue = null)
        }
    }

    /**
     * Write a single register to the controller.
     * Used for common registers with individual confirmation.
     */
    fun writeRegister(register: ModbusRegister) {
        val regValue = _registerValues.value[register.address] ?: return
        val pendingValue = regValue.pendingValue ?: return

        viewModelScope.launch {
            updateRegisterState(register.address) { it.copy(isLoading = true, error = null) }

            val result = repository.writeRegister(controllerId, register.address, pendingValue)

            result.onSuccess {
                // Update current value and clear pending
                updateRegisterState(register.address) {
                    it.copy(
                        currentValue = pendingValue,
                        pendingValue = null,
                        isLoading = false,
                        error = null
                    )
                }
                _successMessage.value = "${register.name} updated successfully"
                Log.d(TAG, "Wrote ${register.name}: $pendingValue")
            }.onFailure { e ->
                updateRegisterState(register.address) {
                    it.copy(isLoading = false, error = e.message)
                }
                _errorMessage.value = "Failed to write ${register.name}: ${e.message}"
                Log.e(TAG, "Failed to write ${register.name}: ${e.message}")
            }
        }
    }

    /**
     * Write all pending changes to the controller.
     * Used in service mode for batch writes.
     */
    fun writeAllChanges() {
        val changedRegisters = _registerValues.value.filter { it.value.hasChanges }
        if (changedRegisters.isEmpty()) return

        viewModelScope.launch {
            _isLoading.value = true
            var successCount = 0
            var failCount = 0

            for ((address, regValue) in changedRegisters) {
                val pendingValue = regValue.pendingValue ?: continue

                updateRegisterState(address) { it.copy(isLoading = true) }

                val result = repository.writeRegister(controllerId, address, pendingValue)

                result.onSuccess {
                    updateRegisterState(address) {
                        it.copy(
                            currentValue = pendingValue,
                            pendingValue = null,
                            isLoading = false,
                            error = null
                        )
                    }
                    successCount++
                }.onFailure { e ->
                    updateRegisterState(address) {
                        it.copy(isLoading = false, error = e.message)
                    }
                    failCount++
                }
            }

            _isLoading.value = false

            if (failCount == 0) {
                _successMessage.value = "$successCount register(s) updated successfully"
            } else {
                _errorMessage.value = "$successCount succeeded, $failCount failed"
            }
        }
    }

    /**
     * Check if a register is protected (requires extra confirmation).
     */
    fun isProtectedRegister(register: ModbusRegister): Boolean {
        return register.address in Lc108Registers.protectedRegisters
    }

    /**
     * Clear error message.
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * Clear success message.
     */
    fun clearSuccess() {
        _successMessage.value = null
    }

    /**
     * Format a raw register value for display based on its data type.
     */
    fun formatValue(register: ModbusRegister, rawValue: Int): String {
        return when (val dataType = register.dataType) {
            is RegisterDataType.ScaledInt -> {
                val scaled = rawValue.toShort() * dataType.scale
                // Format number separately, then append unit to avoid % in unit breaking String.format
                val formatted = String.format("%.${dataType.decimalPlaces}f", scaled)
                "$formatted${dataType.unit}"
            }
            is RegisterDataType.RawInt -> {
                "$rawValue${dataType.unit}"
            }
            is RegisterDataType.Enum -> {
                dataType.options[rawValue] ?: "Unknown ($rawValue)"
            }
            is RegisterDataType.Boolean -> {
                if (rawValue != 0) "On" else "Off"
            }
            is RegisterDataType.Bitfield -> {
                val activeFlags = dataType.flags.filter { (bit, _) ->
                    (rawValue and (1 shl bit)) != 0
                }.values.joinToString(", ")
                activeFlags.ifEmpty { "None" }
            }
        }
    }

    /**
     * Parse a display value back to raw register value.
     */
    fun parseValue(register: ModbusRegister, displayValue: String): Int? {
        return when (val dataType = register.dataType) {
            is RegisterDataType.ScaledInt -> {
                val numericValue = displayValue.replace(Regex("[^\\d.-]"), "").toFloatOrNull()
                numericValue?.let { (it / dataType.scale).toInt() }
            }
            is RegisterDataType.RawInt -> {
                displayValue.replace(Regex("[^\\d]"), "").toIntOrNull()
            }
            is RegisterDataType.Enum -> {
                dataType.options.entries.find { it.value == displayValue }?.key
            }
            is RegisterDataType.Boolean -> {
                when (displayValue.lowercase()) {
                    "on", "true", "1" -> 1
                    "off", "false", "0" -> 0
                    else -> null
                }
            }
            is RegisterDataType.Bitfield -> {
                // Bitfields are typically read-only
                null
            }
        }
    }

    private fun updateRegisterState(address: Int, update: (RegisterValue) -> RegisterValue) {
        val current = _registerValues.value.toMutableMap()
        val register = Lc108Registers.findByAddress(address)
        if (register != null) {
            val existing = current[address] ?: RegisterValue(register = register, currentValue = null)
            current[address] = update(existing)
            _registerValues.value = current
        }
    }

    /**
     * Get controller name for display.
     */
    fun getControllerName(): String {
        return when (controllerId) {
            1 -> "LN2 Cold (PID 1)"
            2 -> "Axle Bearings (PID 2)"
            3 -> "Orbital (PID 3)"
            else -> "Controller $controllerId"
        }
    }
}
