package com.shakercontrol.app.domain.model

/**
 * BLE connection states as defined in docs/MCU_docs/96-state-machine.md
 */
enum class ConnectionState {
    DISCONNECTED,
    PERMISSION_REQUIRED,
    SCANNING,
    DEVICE_SELECTED,
    CONNECTING,
    DISCOVERING,
    SUBSCRIBING,
    SESSION_OPENING,
    LIVE,
    DEGRADED,
    ERROR;

    val displayName: String
        get() = when (this) {
            DISCONNECTED -> "Disconnected"
            PERMISSION_REQUIRED -> "Permission required"
            SCANNING -> "Scanning..."
            DEVICE_SELECTED -> "Device selected"
            CONNECTING -> "Connecting..."
            DISCOVERING -> "Discovering..."
            SUBSCRIBING -> "Subscribing..."
            SESSION_OPENING -> "Opening session..."
            LIVE -> "Connected"
            DEGRADED -> "Degraded"
            ERROR -> "Error"
        }

    val isConnected: Boolean
        get() = this in listOf(LIVE, DEGRADED)

    val isConnecting: Boolean
        get() = this in listOf(CONNECTING, DISCOVERING, SUBSCRIBING, SESSION_OPENING)
}
