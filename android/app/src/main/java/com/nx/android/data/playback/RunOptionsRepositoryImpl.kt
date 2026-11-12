package com.nx.android.data.playback

import com.nx.android.core.di.IoDispatcher
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

    private fun snapshot(pack: File): RunOptionsSnapshot {
        val current = PackEnvStore.current(pack)
        return RunOptionsSnapshot(
            languages = PackEnvStore.languages(pack),
            packDrivers = PackEnvStore.drivers(pack),
            language = current.first,
            driver = current.second,
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RunOptionsDataModule {
    @Binds
    @Singleton
    abstract fun bindRunOptionsRepository(impl: RunOptionsRepositoryImpl): RunOptionsRepository
}
