package com.shakercontrol.app.ui.pid

import androidx.lifecycle.ViewModel
import com.shakercontrol.app.data.repository.MachineRepository
import com.shakercontrol.app.domain.model.PidData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@HiltViewModel
class PidDetailViewModel @Inject constructor(
    private val machineRepository: MachineRepository
) : ViewModel() {

    fun getPidData(pidId: Int): Flow<PidData?> {
        return machineRepository.pidData.map { pidList ->
            pidList.find { it.controllerId == pidId }
        }
    }
}
