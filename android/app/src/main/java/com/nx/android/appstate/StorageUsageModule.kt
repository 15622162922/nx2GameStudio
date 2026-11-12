package com.nx.android.appstate

import com.nx.android.domain.StorageUsageController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class StorageUsageModule {
    @Binds
    @Singleton
    abstract fun bindStorageUsageController(implementation: StorageUsageState): StorageUsageController
}
