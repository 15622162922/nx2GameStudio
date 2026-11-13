package com.nx.android.data.drivers

import com.nx.android.core.di.IoDispatcher
import com.nx.android.domain.drivers.Driver
import com.nx.android.domain.drivers.DriverRelease
import com.nx.android.domain.drivers.DriverRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

@Singleton
class DriverRepositoryImpl @Inject constructor(
    private val store: DriverStore,
    @IoDispatcher private val io: CoroutineDispatcher,
) : DriverRepository {
    private val mutableDrivers = MutableStateFlow<List<Driver>>(emptyList())
    override val drivers: StateFlow<List<Driver>> = mutableDrivers.asStateFlow()
    private val mutationLock = Mutex()

    override val releases: List<DriverRelease> get() = DriverCatalog.RELEASES
    override val shippedRelease: DriverRelease get() = DriverCatalog.SHIPPED

    override suspend fun refresh(): List<Driver> = withContext(io) {
        store.list().also { mutableDrivers.value = it }
    }

    override suspend fun ensureShipped(): Boolean = mutationLock.withLock {
        withContext(io) { store.ensureShipped() }.also { refresh() }
    }

    /** The driver the app ships: the one in the app's own folder with the built-in mark. */
    override suspend fun builtIn(): Driver? = withContext(io) {
        store.list().firstOrNull { it.shipped }
    }

    override suspend fun install(file: File): Result<Driver> = mutationLock.withLock {
        withContext(io) { store.install(file) }.also { refresh() }
    }

    override suspend fun download(
        release: DriverRelease,
        onProgress: (Float) -> Unit,
    ): Result<Driver> = mutationLock.withLock {
        withContext(io) { store.download(release) { onProgress(it) } }
            .also { refresh() }
    }

    override suspend fun remove(id: String): Boolean = mutationLock.withLock {
        withContext(io) { store.remove(id) }.also { refresh() }
    }

    override suspend fun shippedArchivePresent(): Boolean = withContext(io) {
        store.shippedArchivePresent()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DriverDataModule {
    @Binds
    @Singleton
    abstract fun bindDriverRepository(impl: DriverRepositoryImpl): DriverRepository
}
