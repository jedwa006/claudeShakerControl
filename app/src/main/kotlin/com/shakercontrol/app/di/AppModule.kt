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
     * Bind the repository as the primary MachineRepository.
     *
     * For emulator/testing: Use MockMachineRepository (has working timer countdown)
     * For real device with MCU: Use BleMachineRepository
     *
     * TODO: Add build flavor or runtime toggle for this
     */
    @Binds
    @Singleton
    abstract fun bindMachineRepository(
        mockRepository: MockMachineRepository
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
