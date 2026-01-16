package com.shakercontrol.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shakercontrol.app.data.repository.MachineRepository
import com.shakercontrol.app.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val machineRepository: MachineRepository
) : ViewModel() {

    val systemStatus: StateFlow<SystemStatus> = machineRepository.systemStatus
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SystemStatus.DISCONNECTED
        )

    val pidData: StateFlow<List<PidData>> = machineRepository.pidData
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val recipe: StateFlow<Recipe> = machineRepository.recipe
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = Recipe.DEFAULT
        )

    val runProgress: StateFlow<RunProgress?> = machineRepository.runProgress
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val interlockStatus: StateFlow<InterlockStatus> = machineRepository.interlockStatus
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = InterlockStatus(
                isEStopActive = false,
                isDoorLocked = false,
                isLn2Present = false,
                isPowerEnabled = false,
                isHeatersEnabled = false,
                isMotorEnabled = false
            )
        )
}
