package com.nx.android.data.saves

import com.nx.android.data.SaveBackup
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

interface SaveSwap {
    suspend fun install(tree: File, device: File?, live: File, liveDevice: File)
}

@Singleton
class FileSaveSwap @Inject constructor() : SaveSwap {
    override suspend fun install(tree: File, device: File?, live: File, liveDevice: File) {
        SaveBackup.swapIn(tree, device, live, liveDevice)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SaveSwapModule {
    @Binds
    @Singleton
    abstract fun bindSaveSwap(impl: FileSaveSwap): SaveSwap
}
