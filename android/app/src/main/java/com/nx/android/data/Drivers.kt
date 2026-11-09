package com.nx.android.data

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * 玩家装好的 Vulkan 驱动：App 私有目录里一个驱动一个文件夹，里面是驱动库和它自带的 meta.json。
 * 一个驱动一个文件夹是必须的——AdrenoTools 的每个 Turnip 包里的库都叫 libvulkan_freedreno.so，
 * 两个放在同一个目录里会互相覆盖。
 */
data class Driver(
    val id: String,          // 文件夹的名字（由驱动的名字推出来的）
    val name: String,        // 驱动自己的名字（meta.json 的 name）
    val version: String,     // 它的版本（meta.json 的 driverVersion）
    val vendor: String,      // 谁做的（meta.json 的 vendor）
    val library: File,       // 驱动库本身
    val shipped: Boolean = false,   // App 自带的那个：不是玩家的数据，删不掉
) {
    /** 玩家看到的名字：驱动名，版本跟在后面 */
    val label: String get() = if (version.isBlank()) name else "$name · $version"
}

/** 驱动自带的 meta.json 说了什么 */
data class DriverMeta(val name: String, val version: String, val vendor: String, val library: String)

/**
 * 装好的驱动：装、列、删。驱动放在 App 自己的私有目录里（sdcard 上不能 dlopen），
 * 游戏运行时由宿主按绝对路径加载（dm/android/gfx_vk_core.c 的 DM_VK_DRIVER）。
 */
object Drivers {

    private const val META = "meta.json"

    /**
     * Why a driver was refused. A code, not a sentence: the words for it live in the app's own resources
     * (see `ui/Failures.kt`).
     */
    enum class Refusal { NO_DRIVER, NO_LIBRARY, NOT_A_DRIVER }

    /** A driver that was refused: the reason. */
    class Refused(val refusal: Refusal) : IOException(refusal.name)

    /** App 内置的那个驱动（在 assets 里）：没有网络也能装 */
    const val SHIPPED_ASSET = "drivers/Turnip_Gen8_V39.zip"
    const val SHIPPED_NAME = "Turnip Gen8 V39"

    /** 内置驱动的记号：它在文件夹里，App 自带的那些因此删不掉 */
    private const val SHIPPED_MARK = ".shipped"

    /** 驱动装在哪儿：App 的私有目录，驱动一个文件夹 */
    fun root(context: Context) = File(context.filesDir, "drivers").apply { mkdirs() }

    /** 驱动的元数据：名字、版本、库的名字。解析不出来就是没有 */
    fun parseMeta(json: String): DriverMeta? {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val name = o.optString("name").trim()
        val lib = o.optString("libraryName").trim()
        if (name.isEmpty() || lib.isEmpty()) return null
        return DriverMeta(name, o.optString("driverVersion").trim(), o.optString("vendor").trim(), lib)
    }

    /** 驱动自己的名字变一个文件夹名 */
    fun idOf(name: String) = name.trim().replace(Regex("[^A-Za-z0-9._-]+"), "_")

    /** 装好的驱动，按它们的名字排 */
    fun list(context: Context): List<Driver> =
        root(context).listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.mapNotNull { read(it) }?.sortedBy { it.label.lowercase() } ?: emptyList()

    private fun read(dir: File): Driver? {
        val meta = parseMeta(runCatching { File(dir, META).readText() }.getOrDefault("")) ?: return null
        val lib = File(dir, meta.library)
        if (!lib.isFile) return null
        return Driver(dir.name, meta.name, meta.version, meta.vendor, lib,
            shipped = File(dir, SHIPPED_MARK).isFile)
    }

    /**
     * 把驱动压缩包解开到一个目录里：库的元数据和驱动库都在那里。纯文件的事，没有 Android，
     * 所以能在 JVM 上测。压缩包里的目录层被压平（AdrenoTools 的包是 meta.json 和驱动库各一个文件）。
     */
    fun unpack(archive: InputStream, stage: File): DriverMeta {
        stage.mkdirs()
        var meta: DriverMeta? = null
        ZipInputStream(archive).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val base = entry.name.substringAfterLast('/')
                if (!entry.isDirectory && base.isNotEmpty() && base != "." && base != ".." && !base.startsWith(".")) {
                    val out = File(stage, base)
                    out.outputStream().use { zip.copyTo(it) }
                    if (base == META) meta = parseMeta(out.readText())
                }
                entry = zip.nextEntry
            }
        }
        val m = meta ?: throw Refused(Refusal.NO_DRIVER)
        if (!File(stage, m.library).isFile) throw Refused(Refusal.NO_LIBRARY)
        return m
    }

    /**
     * 装一个驱动（从一个 zip 流）：库和元数据写进驱动自己的文件夹，同名的旧版本被换掉。
     */
    fun install(context: Context, archive: InputStream): Result<Driver> = runCatching {
        val stage = File(root(context), ".install").apply { deleteRecursively() }
        val m = unpack(archive, stage)
        val id = idOf(m.name)
        val dst = File(root(context), id)
        dst.deleteRecursively()
        if (!stage.renameTo(dst)) { stage.copyRecursively(dst, true); stage.deleteRecursively() }
        Driver(id, m.name, m.version, m.vendor, File(dst, m.library))
    }

    /** 装 App 内置的那个驱动（assets 里，不用网络） */
    fun installShipped(context: Context): Result<Driver> = runCatching {
        context.assets.open(SHIPPED_ASSET).use { install(context, it).getOrThrow() }
    }

    /** 这个包里有没有内置的那个驱动（没有这一版的 App 就没有） */
    fun shippedArchivePresent(context: Context): Boolean =
        runCatching { context.assets.open(SHIPPED_ASSET).close(); true }.getOrDefault(false)

    /**
     * 内置的那个驱动自己就位：文件夹不在了就从 assets 里解压回来。
     *
     * 它是 App 的一部分，不是玩家的数据：没有「装上」这一步，也删不掉（玩家不点的那个安装按钮）。
     * 每次 App 启动检查一次——只是一次文件检查，已经在了就什么都不做。
     */
    fun ensureShipped(context: Context): Boolean {
        val root = root(context)
        if (root.listFiles()?.any { it.isDirectory && File(it, SHIPPED_MARK).isFile } == true) return true
        val r = installShipped(context)
        val driver = r.getOrNull() ?: return false
        File(File(root, driver.id), SHIPPED_MARK).writeText("")
        return true
    }

    /**
     * 装一个玩家挑的文件：一个驱动压缩包（AdrenoTools 的那个格式），或者一个驱动库本身。
     * 挑到别的东西就说装不了。
     */
    fun install(context: Context, file: File): Result<Driver> {
        val name = file.name.lowercase()
        return when {
            name.endsWith(".zip") -> runCatching { file.inputStream().use { install(context, it).getOrThrow() } }
            name.endsWith(".so") -> installLibrary(context, file)
            else -> Result.failure(Refused(Refusal.NOT_A_DRIVER))
        }
    }

    /** 装一个没有元数据的驱动库：库放进去，元数据自己写一份 */
    private fun installLibrary(context: Context, file: File): Result<Driver> = runCatching {
        val name = file.name.removeSuffix(".so")
        val dir = File(root(context), idOf(name)).apply { deleteRecursively(); mkdirs() }
        file.copyTo(File(dir, file.name), overwrite = true)
        val meta = JSONObject().put("schemaVersion", 1).put("name", name).put("libraryName", file.name)
        File(dir, META).writeText(meta.toString())
        Driver(dir.name, name, "", "", File(dir, file.name))
    }

    /** 删一个驱动：它的文件夹没了才算删掉。App 自带的那个删不掉（它不是玩家的数据） */
    fun remove(context: Context, id: String): Boolean {
        val dir = File(root(context), id)
        if (!dir.isDirectory) return false
        if (File(dir, SHIPPED_MARK).isFile) return false
        return dir.deleteRecursively()
    }
}
