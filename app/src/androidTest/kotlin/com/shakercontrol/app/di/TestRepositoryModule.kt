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
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [AppModule::class]
)
object TestRepositoryModule {

    @Provides
    @Singleton
    fun provideMachineRepository(): MachineRepository {
        return MockMachineRepository()
    }

    @Provides
    @Singleton
    @Named("mock")
    fun provideMockMachineRepository(): MachineRepository {
        return MockMachineRepository()
    }
}
