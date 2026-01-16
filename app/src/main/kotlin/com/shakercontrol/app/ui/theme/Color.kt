package com.shakercontrol.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Color semantics from dashboard-sec-v1.md:
 * - Normal/Ready: neutral + subtle accent
 * - Active/Running: active accent (non-alarming)
 * - Warning: amber/yellow (non-blocking)
 * - Alarm/Fault: red (blocking or urgent)
 * - Disconnected/Stale: gray + "stale age" text
 */

// Background colors
val BackgroundDark = Color(0xFF121212)
val SurfaceDark = Color(0xFF1E1E1E)
val SurfaceVariantDark = Color(0xFF2D2D2D)
val SurfaceContainerDark = Color(0xFF252525)

// Text colors
val OnBackgroundDark = Color(0xFFE1E1E1)
val OnSurfaceDark = Color(0xFFE1E1E1)
val OnSurfaceVariantDark = Color(0xFFB3B3B3)

// Primary accent (subtle, for normal states)
val PrimaryDark = Color(0xFF90CAF9)  // Light blue
val OnPrimaryDark = Color(0xFF003258)
val PrimaryContainerDark = Color(0xFF004880)
val OnPrimaryContainerDark = Color(0xFFD1E4FF)

// Secondary (for less prominent elements)
val SecondaryDark = Color(0xFFB8C8DC)
val OnSecondaryDark = Color(0xFF233240)
val SecondaryContainerDark = Color(0xFF394857)
val OnSecondaryContainerDark = Color(0xFFD4E4F8)

// Semantic colors (strict from spec)
object SemanticColors {
    // Normal/Ready - neutral with subtle accent
    val Normal = Color(0xFF4CAF50)  // Green
    val NormalContainer = Color(0xFF1B3D1E)

    // Active/Running - active accent (non-alarming)
    val Active = Color(0xFF2196F3)  // Blue
    val ActiveContainer = Color(0xFF0D3B5C)
    val Running = Color(0xFF42A5F5)  // Brighter blue for running state

    // Warning - amber/yellow (non-blocking)
    val Warning = Color(0xFFFFC107)  // Amber
    val WarningContainer = Color(0xFF4A3800)
    val OnWarning = Color(0xFF3D2E00)

    // Alarm/Fault - red (blocking or urgent)
    val Alarm = Color(0xFFF44336)  // Red
    val AlarmContainer = Color(0xFF5C1A16)
    val OnAlarm = Color(0xFFFFFFFF)
    val Critical = Color(0xFFD32F2F)  // Darker red for critical

    // Disconnected/Stale - gray
    val Stale = Color(0xFF9E9E9E)  // Gray
    val StaleContainer = Color(0xFF424242)
    val Disconnected = Color(0xFF757575)

    // Input active (for DI indicators)
    val InputActive = Color(0xFF00BCD4)  // Cyan

    // Output active (for LED indicators)
    val OutputActive = Color(0xFF64DD17)  // Lime green
    val OutputInactive = Color(0xFF424242)

    // Enabled/Disabled
    val Enabled = Color(0xFF4CAF50)
    val Disabled = Color(0xFF616161)

    // Fault
    val Fault = Color(0xFFFF5722)  // Deep orange
    val FaultContainer = Color(0xFF5C2416)
}

// Machine state colors
object MachineStateColors {
    val Idle = SemanticColors.Stale
    val Ready = SemanticColors.Normal
    val Running = SemanticColors.Running
    val Paused = SemanticColors.Warning
    val Fault = SemanticColors.Fault
    val EStop = SemanticColors.Critical
}

// Connection state colors
object ConnectionStateColors {
    val Disconnected = SemanticColors.Disconnected
    val Scanning = SemanticColors.Warning
    val Connecting = SemanticColors.Warning
    val Connected = SemanticColors.Active
    val Verified = SemanticColors.Normal
}

// Legacy status colors for backwards compatibility with IoScreen
val StatusActive = SemanticColors.Active
val StatusAlarm = SemanticColors.Alarm
val StatusNormal = SemanticColors.Normal
val StatusWarning = SemanticColors.Warning
