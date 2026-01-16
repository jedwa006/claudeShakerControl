package com.shakercontrol.app.di

import com.shakercontrol.app.data.repository.MachineRepository
import com.shakercontrol.app.data.repository.MockMachineRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindMachineRepository(
        mockRepository: MockMachineRepository
    ): MachineRepository
}
