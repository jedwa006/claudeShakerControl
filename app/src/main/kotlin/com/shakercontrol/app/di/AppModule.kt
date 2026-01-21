package com.shakercontrol.app.di

import android.util.Log
import com.shakercontrol.app.data.preferences.DevicePreferences
import com.shakercontrol.app.data.repository.BleMachineRepository
import com.shakercontrol.app.data.repository.MachineRepository
import com.shakercontrol.app.data.repository.MockMachineRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private const val TAG = "AppModule"

    /**
     * Provide the repository based on demo mode preference.
     *
     * When demo mode is enabled (checked at app startup), uses MockMachineRepository
     * which simulates a connected controller with working telemetry.
     *
     * When demo mode is disabled (default), uses BleMachineRepository
     * for real BLE communication with the ESP32-S3 MCU.
     *
     * Note: Changing this setting requires an app restart to take effect.
     */
    @Provides
    @Singleton
    fun provideMachineRepository(
        devicePreferences: DevicePreferences,
        bleRepository: BleMachineRepository,
        mockRepository: MockMachineRepository
    ): MachineRepository {
        val isDemoMode = devicePreferences.isDemoModeEnabledSync()
        Log.i(TAG, "Providing MachineRepository: demoMode=$isDemoMode")
        return if (isDemoMode) {
            Log.i(TAG, "Using MockMachineRepository (demo mode)")
            mockRepository
        } else {
            Log.i(TAG, "Using BleMachineRepository (production mode)")
            bleRepository
        }
    }
}
