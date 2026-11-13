package com.nx.android.data.drivers

import android.content.Context
import com.nx.android.R
import com.nx.android.domain.drivers.Driver
import com.nx.android.domain.drivers.DriverNote
import com.nx.android.domain.drivers.DriverRelease
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** 一个可以拿到的驱动：叫什么、在哪儿、给哪一家的 GPU */
/**
 * App 能拿到的驱动：Adreno-Tools-Drivers 的 release v39 的三个构建，加上 Turnip 710 的两个构建
 * （通用版与专为 Switch 模拟器调优的版本），各自有固定的地址。
 *
 * 不去问 GitHub「最近有什么」：未认证的 API 每个地址每小时只给 60 次，手机在同一个出口地址后面，
 * 列表页常常打不开；固定的地址不会。版本要更新，就换一个地址。
 */
object DriverCatalog {

    private const val BASE = "https://github.com/StevenMXZ/Adreno-Tools-Drivers/releases/download/v39/"
    private const val TURNIP_710 = "https://github.com/Vauzi-17/710/releases/download/4.6/"

    val RELEASES = listOf(
        DriverRelease("Turnip_Gen8_V39.zip", "Turnip Gen8 V39", "Turnip Gen8 V39",
            DriverNote.GPU_8XX, BASE + "Turnip_Gen8_V39.zip"),
        // the Switch build first: it is the one this app's games want (a Switch emulator runs on it)
        DriverRelease("Turnip-710-720-722-v4.6-sw.zip", "Turnip 710 v4.6 (Switch)", "710 720 & 722 v4.6-sw",
            DriverNote.GPU_7XX_8XX_SWITCH, TURNIP_710 + "Turnip-710-720-722-v4.6-sw.zip"),
        DriverRelease("Turnip-710-720-722-v4.6.zip", "Turnip 710 v4.6", "710 720 & 722 v4.6",
            DriverNote.GPU_6XX_7XX_8XX, TURNIP_710 + "Turnip-710-720-722-v4.6.zip"),
        DriverRelease("Turnip_v27.0.0-R2.zip", "Turnip 27.0.0-R2", "Turnip 27.0.0-R2",
            DriverNote.GPU_7XX, BASE + "Turnip_v27.0.0-R2.zip"),
        DriverRelease("Turnip_v27.0.0-R2-Oneui.zip", "Turnip 27.0.0-R2 (OneUI)", "Turnip 27.0.0-R2-Oneui",
            DriverNote.GPU_7XX_ONE_UI, BASE + "Turnip_v27.0.0-R2-Oneui.zip"),
    )

    /** 内置的那个（assets 里，不用网络） */
    val SHIPPED = RELEASES.first { it.file == "Turnip_Gen8_V39.zip" }

    /**
     * 下载一个驱动并装好。进度从 0 到 1。下载写进 cache 里的 .part 文件，装好了才算数——
     * 装坏了不会在 App 的目录里留下半个驱动。
     */
    suspend fun download(context: Context, release: DriverRelease, onProgress: (Float) -> Unit): Result<Driver> =
        withContext(Dispatchers.IO) {
            val tmp = File(File(context.cacheDir, "driver_download"), release.file + ".part")
            runCatching {
                tmp.parentFile?.mkdirs()
                val conn = (URL(release.url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 60000
                    setRequestProperty("User-Agent", "nx2android")
                }
                try {
                    if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${conn.responseCode}")
                    val total = conn.contentLengthLong.takeIf { it > 0 } ?: 0L
                    conn.inputStream.use { input ->
                        tmp.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var done = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                                done += read
                                if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                } finally {
                    conn.disconnect()
                }
                tmp.inputStream().use { Drivers.install(context, it).getOrThrow() }
            }.also { tmp.delete() }
        }
}
