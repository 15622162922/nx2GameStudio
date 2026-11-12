package com.nx.android.data.drivers

import android.content.Context
import com.nx.android.domain.drivers.Driver
import com.nx.android.domain.drivers.DriverRelease
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import java.io.File

/** Platform file and network boundary used by the repository. */
interface DriverStore {
    fun list(): List<Driver>
    fun ensureShipped(): Boolean
    fun install(file: File): Result<Driver>
    suspend fun download(release: DriverRelease, onProgress: (Float) -> Unit): Result<Driver>
    fun remove(id: String): Boolean
    fun shippedArchivePresent(): Boolean
}

@Singleton
class ContextDriverStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : DriverStore {
    override fun list(): List<Driver> = Drivers.list(context)
    override fun ensureShipped(): Boolean = Drivers.ensureShipped(context)
    override fun install(file: File): Result<Driver> = Drivers.install(context, file)
    override suspend fun download(
        release: DriverRelease,
        onProgress: (Float) -> Unit,
    ): Result<Driver> = DriverCatalog.download(context, release, onProgress)
    override fun remove(id: String): Boolean = Drivers.remove(context, id)
    override fun shippedArchivePresent(): Boolean = Drivers.shippedArchivePresent(context)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DriverStoreModule {
    @Binds
    @Singleton
    abstract fun bindDriverStore(impl: ContextDriverStore): DriverStore
}
