package com.shakercontrol.app.di

import com.shakercontrol.app.data.repository.MachineRepository
import com.shakercontrol.app.data.repository.MockMachineRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Named
import javax.inject.Singleton

/**
 * Test module that replaces the production AppModule with mock implementations.
 * This allows UI tests to run without real BLE hardware.
 *
 * Uses a single shared MockMachineRepository instance for all injections
 * to ensure state is consistent across ViewModels.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [AppModule::class]
)
object TestRepositoryModule {

    // Single shared instance for all injections
    private val sharedMockRepository = MockMachineRepository()

    @Provides
    @Singleton
    fun provideMachineRepository(): MachineRepository {
        return sharedMockRepository
    }

    @Provides
    @Singleton
    @Named("mock")
    fun provideMockMachineRepository(): MachineRepository {
        return sharedMockRepository
    }
}
