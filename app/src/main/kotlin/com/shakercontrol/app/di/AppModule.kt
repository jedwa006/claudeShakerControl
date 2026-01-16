package com.shakercontrol.app.di

import com.shakercontrol.app.data.repository.BleMachineRepository
import com.shakercontrol.app.data.repository.MachineRepository
import com.shakercontrol.app.data.repository.MockMachineRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    /**
     * Bind the BLE repository as the primary MachineRepository.
     * For development/testing, you can switch to MockMachineRepository.
     */
    @Binds
    @Singleton
    abstract fun bindMachineRepository(
        bleRepository: BleMachineRepository
    ): MachineRepository

    /**
     * Also provide the mock repository for testing/preview.
     */
    @Binds
    @Singleton
    @Named("mock")
    abstract fun bindMockMachineRepository(
        mockRepository: MockMachineRepository
    ): MachineRepository
}
