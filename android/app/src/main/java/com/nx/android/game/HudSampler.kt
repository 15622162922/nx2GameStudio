package com.nx.android.game

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import java.io.File

/**
 * The HUD's device-side readings: the processor, the GPU, the memory and the battery. A reading is taken only
 * when its field is selected — an unselected field costs nothing (no file reads, no broadcast).
 *
 * The processor is the whole chip's busy share, from the cores' idle counters; the process's own times are the
 * fallback for the kernels that hide them. The GPU is the driver's busy share, latched (the driver publishes it
 * about once a second and answers "idle" while the GPU sleeps). The memory is the game's own (the game runs in
 * this process) and the system's. The battery gives its temperature, its level and the current it draws.
 */
object HudSampler {

    private const val SYS_CPU = "/sys/devices/system/cpu"
    private const val KGSL = "/sys/class/kgsl/kgsl-3d0"
    /** How long a stale GPU busy window is held before it reads as idle */
    private const val GPU_HOLD_MS = 5_000L
    /** Android's kernel ticks a second 100 times (USER_HZ) */
    private const val USER_HZ = 100L
    private const val PAGE_SIZE = 4096L

    // the samplers' bases: the difference between two readings
    private var lastIdleUs = -1L
    private var lastIdleCores = 0
    private var lastIdleMs = 0L
    private var lastJiffies = -1L
    private var lastMs = 0L
    private var coreCount = 0
    private var gpuLatched = 0f
    private var gpuWindowAt = 0L

    /** One tick: the readings of the selected fields */
    fun take(ctx: Context, fields: Set<HudField>): HudSample {
        if (HudField.CPU !in fields) resetCpu()
        if (HudField.GPU !in fields) gpuWindowAt = 0L
        val battery = if (HudField.POWER in fields || HudField.TEMP in fields) battery(ctx, HudField.POWER in fields) else null
        return HudSample(
            cpuPercent = if (HudField.CPU in fields) cpuPercent() else null,
            gpuPercent = if (HudField.GPU in fields) gpuBusy() else null,
            appRamMb = if (HudField.APP_RAM in fields) appRamMb() else null,
            sysRamUsedMb = if (HudField.SYS_RAM in fields) sysRamUsedMb() else null,
            sysRamTotalMb = if (HudField.SYS_RAM in fields) sysRamTotalMb() else null,
            tempC = battery?.tempC,
            powerW = battery?.watts,
            capacity = battery?.capacity,
            charging = battery?.charging == true,
        )
    }

    /** Drop the CPU samplers' bases: the next reading after a pause must not diff across it */
    private fun resetCpu() {
        lastIdleUs = -1
        lastIdleMs = 0
        lastJiffies = -1
        lastMs = 0
    }

    /**
     * The whole chip's busy share, the number a "CPU %" is expected to be: per core, idle time straight from
     * cpuidle (busy = elapsed − idle), averaged over the cores. Percent of all the cores together, so it stays
     * in 0–100.
     */
    private fun cpuPercent(): Float? {
        val idle = cpuidleIdle()
        if (idle != null) {
            lastJiffies = -1   // re-base the fallback, should we ever need it again
            val (us, cores) = idle
            val now = SystemClock.elapsedRealtime()
            val prevUs = lastIdleUs
            val prevCores = lastIdleCores
            val prevAt = lastIdleMs
            lastIdleUs = us
            lastIdleCores = cores
            lastIdleMs = now
            if (prevUs < 0 || prevAt == 0L || prevCores != cores) return null
            val elapsedMs = (now - prevAt).coerceAtLeast(1)
            val capacityUs = elapsedMs * 1000L * cores
            // a core's counter occasionally steps backwards across a hotplug: clamp
            val busyUs = (capacityUs - (us - prevUs)).coerceIn(0L, capacityUs)
            return busyUs.toFloat() / capacityUs * 100f
        }
        lastIdleUs = -1
        return processCpuPercent()
    }

    /** Total idle microseconds over every core that exposes cpuidle, with the core count */
    private fun cpuidleIdle(): Pair<Long, Int>? {
        val root = File(SYS_CPU)
        var total = 0L
        var cores = 0
        for (cpu in root.list().orEmpty()) {
            if (!cpu.matches(Regex("cpu\\d+"))) continue
            val states = File(root, "$cpu/cpuidle").list()?.filter { it.startsWith("state") }?.sorted() ?: continue
            if (states.isEmpty()) continue   // a core without idle states cannot be counted
            var idleUs = 0L
            for (state in states) {
                val us = runCatching { File(root, "$cpu/cpuidle/$state/time").readText().trim().toLong() }.getOrNull() ?: return null
                idleUs += us
            }
            total += idleUs
            cores++
        }
        return if (cores == 0) null else total to cores
    }

    /**
     * The CPU fallback for the kernels that hide cpuidle: this process's own times (utime + stime only — cutime
     * would count the children twice), spread over all the cores so the number stays comparable.
     */
    private fun processCpuPercent(): Float? {
        val stat = runCatching { File("/proc/self/stat").readText() }.getOrNull() ?: return null
        // "pid (comm) state ppid ..." — comm can hold anything, so split after the last ')'
        val f = stat.substringAfterLast(')').trim().split(' ')
        val u = f.getOrNull(11)?.toLongOrNull() ?: return null
        val s = f.getOrNull(12)?.toLongOrNull() ?: 0
        val total = u + s
        val now = System.currentTimeMillis()
        val prev = lastJiffies
        val prevAt = lastMs
        lastJiffies = total
        lastMs = now
        if (prev < 0 || prevAt == 0L) return null
        val dt = (now - prevAt).coerceAtLeast(1)
        val cores = cores().coerceAtLeast(1)
        return (total - prev).toFloat() / USER_HZ / (dt / 1000f) / cores * 100f
    }

    private fun cores(): Int {
        if (coreCount > 0) return coreCount
        coreCount = File(SYS_CPU).list().orEmpty().count { it.matches(Regex("cpu\\d+")) }
        return coreCount
    }

    /**
     * The GPU's busy share from the driver's own counter. The kernel publishes a new window about once a second
     * of GPU-on time and answers "0 0" while the GPU sleeps, so a raw poll would read as idle mid-game: latch
     * each published value, hold it through the gaps, and only call it idle after the hold has passed.
     */
    private fun gpuBusy(): Float? {
        val raw = runCatching { File("$KGSL/gpubusy").readText().trim() }.getOrNull()
        val parts = raw?.split(Regex("\\s+"))?.map { it.toLongOrNull() }
        val busy = parts?.getOrNull(0)
        val total = parts?.getOrNull(1)
        if (busy != null && total != null) {
            val now = SystemClock.elapsedRealtime()
            if (total > 0) {
                gpuLatched = (busy * 100f / total).coerceIn(0f, 100f)
                gpuWindowAt = now
            } else if (gpuWindowAt != 0L && now - gpuWindowAt > GPU_HOLD_MS) gpuLatched = 0f
            return gpuLatched
        }
        for (path in listOf("$KGSL/devfreq/gpu_load", "$KGSL/gpu_busy_percentage")) {
            val v = runCatching { Regex("-?\\d+").find(File(path).readText())?.value?.toFloat() }.getOrNull() ?: continue
            return v.coerceIn(0f, 100f)
        }
        return null
    }

    /** This process's resident memory (the game runs in it), in MB */
    private fun appRamMb(): Long? = runCatching {
        val pages = File("/proc/self/statm").readLines()[0].split(' ')[1].toLong()
        pages * PAGE_SIZE / (1024 * 1024)
    }.getOrNull()

    /** The system's used memory (total − available), in MB */
    private fun sysRamUsedMb(): Long? = meminfo()?.let { (total, avail) -> (total - avail) / 1024 }

    /** The system's total memory, in MB */
    private fun sysRamTotalMb(): Long? = meminfo()?.first?.div(1024)

    /** (total, available) in kB */
    private fun meminfo(): Pair<Long, Long>? = runCatching {
        var avail = 0L
        var total = 0L
        File("/proc/meminfo").forEachLine { line ->
            val f = line.split(Regex("[:\\s]+"))
            when (f.getOrNull(0)) {
                "MemAvailable" -> avail = f.getOrNull(1)?.toLongOrNull() ?: 0
                "MemTotal" -> total = f.getOrNull(1)?.toLongOrNull() ?: 0
            }
        }
        if (total <= 0) null else total to avail
    }.getOrNull()

    /**
     * The battery's readings: the temperature and the voltage from the sticky battery intent, the current from
     * the battery service with a unit auto-detect — the vendors split between reporting milliamps and
     * microamps, and the magnitude tells which. Watts = volts × amps, magnitude only (the sign conventions
     * differ per vendor). While it charges this reads the charge current, not the consumption.
     */
    private fun battery(ctx: Context, withPower: Boolean): BatteryReading? = runCatching {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val tempC = i.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            .takeIf { it != Int.MIN_VALUE }?.let { it / 10f }
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0 ||
            status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val capacity = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1).takeIf { it >= 0 }?.let { level ->
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100).takeIf { it > 0 } ?: 100
            level * 100 / scale
        }
        val mv = i.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        val ua = if (!withPower) 0L else runCatching {
            ctx.getSystemService(BatteryManager::class.java)?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        }.getOrNull() ?: 0L
        val amps = if (ua == 0L || ua == Long.MIN_VALUE) -1f
        else Math.abs(ua).let { if (it < 20_000) it / 1000f else it / 1_000_000f }
        val watts = if (mv > 0 && amps > 0f) mv / 1000f * amps else null
        BatteryReading(watts, charging, tempC, capacity)
    }.getOrNull()

    /** One battery sample. A named class, not a Triple: the fields were swapped once. */
    private class BatteryReading(val watts: Float?, val charging: Boolean, val tempC: Float?, val capacity: Int?)
}
