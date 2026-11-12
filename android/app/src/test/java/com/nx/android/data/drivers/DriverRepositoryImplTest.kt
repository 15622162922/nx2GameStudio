package com.nx.android.data.drivers

import com.nx.android.domain.drivers.Driver
import com.nx.android.domain.drivers.DriverRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DriverRepositoryImplTest {
    @Test
    fun `shipped driver setup and imported driver update shared state`() = runBlocking {
        val store = FakeDriverStore()
        val repository = DriverRepositoryImpl(store, Dispatchers.Unconfined)
        val settingsConsumer = repository
        val driverPageConsumer = repository

        assertTrue(settingsConsumer.ensureShipped())
        assertEquals(listOf(shipped), settingsConsumer.drivers.value)
        assertSame(settingsConsumer.drivers, driverPageConsumer.drivers)

        assertTrue(settingsConsumer.install(File("imported.zip")).isSuccess)
        assertEquals(listOf(shipped, imported), driverPageConsumer.drivers.value)
        assertTrue(settingsConsumer.remove(imported.id))
        assertEquals(listOf(shipped), driverPageConsumer.drivers.value)
    }

    @Test
    fun `failed import leaves the current driver list available`() = runBlocking {
        val store = FakeDriverStore().apply { installed += shipped; failInstall = true }
        val repository = DriverRepositoryImpl(store, Dispatchers.Unconfined)
        repository.refresh()

        val result = repository.install(File("invalid.zip"))

        assertTrue(result.isFailure)
        assertEquals(listOf(shipped), repository.drivers.value)
        assertFalse(repository.remove("missing"))
        assertEquals(listOf(shipped), repository.drivers.value)
    }

    private class FakeDriverStore : DriverStore {
        val installed = mutableListOf<Driver>()
        var failInstall = false
        override fun list(): List<Driver> = installed.toList()
        override fun ensureShipped(): Boolean {
            if (installed.none { it.shipped }) installed += shipped
            return true
        }
        override fun install(file: File): Result<Driver> = if (failInstall) {
            Result.failure(IllegalArgumentException("invalid driver"))
        } else {
            Result.success(imported.also { installed.removeAll { old -> old.id == it.id }; installed += it })
        }
        override suspend fun download(release: DriverRelease, onProgress: (Float) -> Unit): Result<Driver> =
            Result.success(imported)
        override fun remove(id: String): Boolean = installed.removeAll { it.id == id }
        override fun shippedArchivePresent(): Boolean = true
    }

    private companion object {
        val shipped = Driver("shipped", "Built in", "1", "Mesa", File("/drivers/shipped.so"), shipped = true)
        val imported = Driver("imported", "Imported", "2", "Mesa", File("/drivers/imported.so"))
    }
}
