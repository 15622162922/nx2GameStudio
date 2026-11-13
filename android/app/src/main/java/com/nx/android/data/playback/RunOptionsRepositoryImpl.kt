package com.nx.android.data.playback

import com.nx.android.core.di.IoDispatcher
import com.nx.android.domain.drivers.DriverRepository
import com.nx.android.domain.playback.RunOptionsRepository
import com.nx.android.domain.playback.RunOptionsSnapshot
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class RunOptionsRepositoryImpl @Inject constructor(
    @IoDispatcher private val io: CoroutineDispatcher,
    private val driverRepository: DriverRepository,
) : RunOptionsRepository {
    private val locks = ConcurrentHashMap<String, Mutex>()

    override suspend fun read(pack: File): RunOptionsSnapshot = withContext(io) {
        lock(pack).withLock { snapshot(pack) }
    }

    override suspend fun update(pack: File, language: String?, driver: String?): RunOptionsSnapshot = withContext(io) {
        lock(pack).withLock {
            PackEnvStore.set(pack, language, driver)
            snapshot(pack)
        }
    }

    private fun lock(pack: File): Mutex = locks.computeIfAbsent(pack.canonicalPath) { Mutex() }

    /**
     * The default driver: a game whose driver was never picked runs on the app's built-in one. The row is written
     * once — from then on the player's own choice is what the row says, the device's own driver included (an empty
     * row). No built-in driver (an app built without it, or it could not be unpacked) leaves the row as it is.
     */
    private suspend fun snapshot(pack: File): RunOptionsSnapshot {
        applyDefaultDriver(pack)
        val current = PackEnvStore.current(pack)
        return RunOptionsSnapshot(
            languages = PackEnvStore.languages(pack),
            packDrivers = PackEnvStore.drivers(pack),
            language = current.first,
            driver = current.second,
        )
    }

    private suspend fun applyDefaultDriver(pack: File) {
        if (PackEnvStore.current(pack).second != null) return
        val builtIn = driverRepository.builtIn() ?: return
        PackEnvStore.ensureDriverDefault(pack, builtIn.library.absolutePath)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RunOptionsDataModule {
    @Binds
    @Singleton
    abstract fun bindRunOptionsRepository(impl: RunOptionsRepositoryImpl): RunOptionsRepository
}
