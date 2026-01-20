package com.shakercontrol.app.domain.model

/**
 * Represents a configurable display slot in the Run screen.
 * Slots can show different data sources (temperature history, thermal camera, vibration, etc.)
 * The user can select a slot to view it in detail and change its source.
 * Configuration persists for the session.
 */
data class DisplaySlot(
    val index: Int,
    val source: DisplaySource,
    val title: String
)

/**
 * Available data sources for display slots.
 * Future sources can be added as hardware capabilities expand.
 */
enum class DisplaySource(val displayName: String) {
    EMPTY("Not configured"),
    TEMPERATURE_HISTORY("Temperature Plot"),
    THERMAL_CAMERA("Thermal Camera"),
    VIBRATION("Vibration Monitor"),
    POWER_CONSUMPTION("Power Usage");

    companion object {
        /** Sources that require additional hardware */
        val hardwareDependent = setOf(THERMAL_CAMERA, VIBRATION)
    }
}
