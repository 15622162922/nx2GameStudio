package com.nx.android.appstate

import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LatestScanCoordinatorTest {
    @Test
    fun `a stale scan result is discarded and overlapping requests are coalesced`() {
        val executor: ExecutorService = Executors.newSingleThreadExecutor()
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val scans = AtomicInteger()
        val published = mutableListOf<Int>()
        val coordinator = LatestScanCoordinator(
            executor = executor,
            scan = {
                when (scans.incrementAndGet()) {
                    1 -> { firstStarted.countDown(); releaseFirst.await(); 1 }
                    else -> 2
                }
            },
            publish = { synchronized(published) { published += it; finished.countDown() } },
            setScanning = {},
            onFailure = { throw AssertionError(it) },
        )
        try {
            coordinator.request()
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS))
            coordinator.request()
            coordinator.request()
            releaseFirst.countDown()
            assertTrue(finished.await(2, TimeUnit.SECONDS))
            assertEquals(listOf(2), synchronized(published) { published.toList() })
            assertEquals(2, scans.get())
        } finally {
            releaseFirst.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `scan failure ends the loading state without an automatic retry loop`() {
        val executor: ExecutorService = Executors.newSingleThreadExecutor()
        val finished = CountDownLatch(1)
        val scans = AtomicInteger()
        val loading = mutableListOf<Boolean>()
        val coordinator = LatestScanCoordinator(
            executor = executor,
            scan = { scans.incrementAndGet(); throw IllegalStateException("offline volume") },
            publish = {},
            setScanning = { if (!it) finished.countDown(); synchronized(loading) { loading += it } },
            onFailure = {},
        )
        try {
            coordinator.request()
            assertTrue(finished.await(2, TimeUnit.SECONDS))
            assertEquals(1, scans.get())
            assertFalse(synchronized(loading) { loading.last() })
        } finally {
            executor.shutdownNow()
        }
    }
}
