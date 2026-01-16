package com.shakercontrol.app.domain.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Interval cycle recipe as defined in docs/dashboard-sec-v1.md section 5.1.
 *
 * Hold timing: HOLD × (cycles - 1) — no hold after final milling phase.
 */
data class Recipe(
    val millingDuration: Duration,  // Mill ON duration per cycle
    val holdDuration: Duration,     // Hold duration between cycles
    val cycleCount: Int             // Number of cycles
) {
    init {
        require(cycleCount >= 1) { "Cycle count must be at least 1" }
    }

    /**
     * Total milling time = millingDuration × cycleCount
     */
    val totalMillingTime: Duration
        get() = millingDuration * cycleCount

    /**
     * Total holding time = holdDuration × (cycleCount - 1)
     * No hold after the final milling phase.
     */
    val totalHoldingTime: Duration
        get() = if (cycleCount > 1) holdDuration * (cycleCount - 1) else Duration.ZERO

    /**
     * Grand total runtime = totalMillingTime + totalHoldingTime
     */
    val totalRuntime: Duration
        get() = totalMillingTime + totalHoldingTime

    companion object {
        val DEFAULT = Recipe(
            millingDuration = 300.seconds,  // 5:00
            holdDuration = 60.seconds,       // 1:00
            cycleCount = 5
        )
    }
}

/**
 * Current run progress state.
 */
data class RunProgress(
    val currentCycle: Int,      // 1-indexed
    val totalCycles: Int,
    val currentPhase: RunPhase,
    val phaseElapsed: Duration,
    val phaseRemaining: Duration,
    val totalRemaining: Duration
)

enum class RunPhase {
    MILLING,
    HOLDING;

    val displayName: String
        get() = when (this) {
            MILLING -> "Milling"
            HOLDING -> "Holding"
        }
}
