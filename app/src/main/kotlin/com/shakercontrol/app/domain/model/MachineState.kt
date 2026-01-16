package com.shakercontrol.app.domain.model

/**
 * Machine states as defined in docs/ui-copy-labels-v1.md
 */
enum class MachineState {
    IDLE,
    READY,
    RUNNING,
    PAUSED,
    FAULT,
    E_STOP;

    val displayName: String
        get() = when (this) {
            IDLE -> "Idle"
            READY -> "Ready"
            RUNNING -> "Running"
            PAUSED -> "Paused"
            FAULT -> "Fault"
            E_STOP -> "E-stop"
        }

    val canStart: Boolean
        get() = this in listOf(IDLE, READY)

    val canPause: Boolean
        get() = this == RUNNING

    val canResume: Boolean
        get() = this == PAUSED

    val canStop: Boolean
        get() = this in listOf(RUNNING, PAUSED)

    val isOperating: Boolean
        get() = this in listOf(RUNNING, PAUSED)
}
