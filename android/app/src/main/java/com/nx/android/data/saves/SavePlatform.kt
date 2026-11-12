package com.nx.android.data.saves

import android.content.Context
import com.nx.android.data.Player
import com.nx.android.data.SaveShare
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

interface SavePlatform {
    val filesDir: File
    val cacheDir: File
    fun packagesDirectory(): File
    fun isGameRunning(): Boolean
}

@Singleton
class AndroidSavePlatform @Inject constructor(
    @ApplicationContext private val context: Context,
) : SavePlatform {
    override val filesDir: File get() = context.filesDir
    override val cacheDir: File get() = context.cacheDir
    override fun packagesDirectory(): File = SaveShare.packagesDir(context)
    override fun isGameRunning(): Boolean = Player.playProcessAlive(context)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SavePlatformModule {
    @Binds
    @Singleton
    abstract fun bindSavePlatform(impl: AndroidSavePlatform): SavePlatform
}
