package com.nx.android.domain.drivers

import java.io.File
import kotlinx.coroutines.flow.StateFlow

/** Shared driver catalog and installed-driver operations. */
interface DriverRepository {
    val drivers: StateFlow<List<Driver>>
    val releases: List<DriverRelease>
    val shippedRelease: DriverRelease

    suspend fun refresh(): List<Driver>
    suspend fun ensureShipped(): Boolean
    /** The driver the app ships, when it is installed (the built-in one), null when there is none. */
    suspend fun builtIn(): Driver?
    suspend fun install(file: File): Result<Driver>
    suspend fun download(release: DriverRelease, onProgress: (Float) -> Unit): Result<Driver>
    suspend fun remove(id: String): Boolean
    suspend fun shippedArchivePresent(): Boolean
}
