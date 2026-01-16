package com.shakercontrol.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shakercontrol.app.data.repository.MachineRepository
import com.shakercontrol.app.domain.model.SystemStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val machineRepository: MachineRepository
) : ViewModel() {

    val systemStatus: StateFlow<SystemStatus> = machineRepository.systemStatus
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SystemStatus.DISCONNECTED
        )

    fun enableServiceMode() {
        viewModelScope.launch {
            machineRepository.enableServiceMode()
        }
    }

    fun disableServiceMode() {
        viewModelScope.launch {
            machineRepository.disableServiceMode()
        }
    }
}
