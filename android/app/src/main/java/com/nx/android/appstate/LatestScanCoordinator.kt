package com.nx.android.appstate

import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/** Runs one scan at a time and publishes only the newest requested result. */
internal class LatestScanCoordinator<T>(
    private val executor: Executor,
    private val scan: () -> T,
    private val publish: (T) -> Unit,
    private val setScanning: (Boolean) -> Unit,
    private val onFailure: (Exception) -> Unit,
) {
    private val lock = Any()
    private var requested = 0L
    private val scheduled = AtomicBoolean(false)
    private var published = 0L

    fun request() {
        synchronized(lock) {
            requested++
            setScanning(true)
        }
        schedule()
    }

    private fun schedule() {
        if (!scheduled.compareAndSet(false, true)) return
        executor.execute {
            try {
                while (true) {
                    val generation = synchronized(lock) { requested }
                    val result = try {
                        Result.success(scan())
                    } catch (failure: Exception) {
                        Result.failure(failure)
                    }
                    val isCurrent = synchronized(lock) {
                        if (generation != requested) return@synchronized false
                        result.fold(publish) { onFailure(it as Exception) }
                        published = generation
                        setScanning(false)
                        true
                    }
                    if (!isCurrent) continue
                    break
                }
            } finally {
                scheduled.set(false)
                if (synchronized(lock) { requested != published }) schedule()
            }
        }
    }
}
