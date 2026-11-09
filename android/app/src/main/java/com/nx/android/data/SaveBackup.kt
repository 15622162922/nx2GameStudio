package com.nx.android.data

import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The save package: the game's save leaves the app as a zip the player can send to someone, and comes back
 * the same way.
 *
 * The live save is the app's own file - the host mounts it at `<internal>/save/<titleId>` (dm_java_start is
 * given the app's files folder and the title ID) - so the panel can work on it directly: no request file for
 * the host, no game start. A package holds the save's files as they are, the layout a console's save tools
 * write and read, under one folder; the device save of a game that mounts one lies beside it.
 */
object SaveBackup {

    /** the package's extension */
    const val EXT = "zip"

    /** how much a package may unpack to (a save is small; a package from elsewhere is not to be trusted) */
    const val MAX_UNPACKED = 512L * 1024 * 1024

    /** how many entries a package may hold */
    const val MAX_ENTRIES = 20000

    /** the live save: `<internal>/save/<titleId>` */
    fun liveDir(internal: File, titleId: String): File = File(internal, "save/$titleId")

    /** the device save: `<internal>/save/<titleId>_device`, beside the save of a game that mounts one */
    fun deviceDir(internal: File, titleId: String): File = File(internal, "save/${titleId}_device")

    /** the package's file name: `<game>-<titleId>-<stamp>.zip` */
    fun packageName(game: String, titleId: String, stamp: String): String =
        "${sanitize(game).ifEmpty { "save" }}-${sanitize(titleId).ifEmpty { "unknown" }}-$stamp.$EXT"

    /** the title ID a package's name carries, or null when it carries none */
    fun titleIdIn(name: String): String? {
        if (!name.endsWith(".$EXT", ignoreCase = true)) return null
        val stem = name.substring(0, name.length - EXT.length - 1)
        val parts = stem.split("-")
        if (parts.size < 3) return null
        val id = parts[parts.size - 3]
        if (id.length != 16 || !id.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) return null
        return id.uppercase()
    }

    /** a name that is safe in a file name and in a zip entry */
    private fun sanitize(name: String): String =
        name.map { if (it.isLetterOrDigit() || it == '-' || it == '_' || it == '.') it else '_' }
            .joinToString("").trim('_', '.', ' ').take(60)

    /**
     * The package: the save tree under `treeName/`, the device save under `treeName.device/` when the game has
     * one. The files are copied byte for byte.
     */
    fun writePackage(main: File?, device: File?, treeName: String, dest: File) {
        dest.parentFile?.mkdirs()
        val part = File(dest.parentFile, ".${dest.name}.part")
        part.delete()
        ZipOutputStream(part.outputStream().buffered()).use { zip ->
            if (main != null) writeTree(zip, main, treeName)
            if (device != null) writeTree(zip, device, "$treeName.device")
        }
        if (dest.exists()) dest.delete()
        if (!part.renameTo(dest)) {
            part.copyTo(dest, overwrite = true)
            part.delete()
        }
    }

    private fun writeTree(zip: ZipOutputStream, src: File, top: String) {
        src.walkTopDown().forEach { f ->
            val rel = f.relativeTo(src).path
            val entry = if (rel.isEmpty()) top else "$top/$rel"
            if (f.isDirectory) {
                if (f.listFiles()?.isEmpty() == true) {   // an empty folder is kept, a filled one comes with its files
                    zip.putNextEntry(ZipEntry("$entry/"))
                    zip.closeEntry()
                }
            } else {
                zip.putNextEntry(ZipEntry(entry))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /** what a package holds: the save tree, and the device save beside it when it has one */
    class Package(val tree: File, val device: File?)

    /**
     * Unpack a package into `dest`: the save tree it holds and the device save beside it. A package from
     * elsewhere is not trusted - an entry whose path points out of `dest` is refused, and the unpacked size
     * is capped.
     */
    fun unpack(pkg: File, dest: File): Package {
        dest.mkdirs()
        var bytes = 0L
        var entries = 0
        ZipInputStream(pkg.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries++
                if (entries > MAX_ENTRIES) throw IOException("包里条目太多")
                val name = entry.name
                if (name.isEmpty() || name.startsWith("/") || name.contains("\\") || name.contains(":")) {
                    throw IOException("包里的路径不对：$name")
                }
                val parts = name.split("/")
                if (parts.any { it == ".." }) throw IOException("包里的路径越界：$name")
                val out = File(dest, name)
                if (!out.canonicalPath.startsWith(dest.canonicalPath + File.separator)) {
                    throw IOException("包里的路径越界：$name")
                }
                if (entry.isDirectory) {
                    out.mkdirs()
                    continue
                }
                out.parentFile?.mkdirs()
                bytes += out.outputStream().use { zip.copyTo(it) }
                if (bytes > MAX_UNPACKED) throw IOException("包解开后太大")
            }
        }
        val dirs = dest.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name }.orEmpty()
        val devices = dirs.filter { it.name.endsWith(".device") }
        val trees = dirs.filter { !it.name.endsWith(".device") }
        return when {
            trees.size == 1 -> Package(trees[0], devices.firstOrNull { it.name == trees[0].name + ".device" })
            trees.isEmpty() -> Package(dest, null)      // the files sit in the package's root
            else -> {                                    // several trees: a save with more than one user in it
                val merged = File(dest, "tree")
                for (t in trees) copyTree(t, File(merged, t.name))
                Package(merged, devices.firstOrNull())
            }
        }
    }

    /**
     * The save an import replaces: a copy in the data pack's `saves/` folder, named as the native save manager
     * names its own (`<stamp>-before-import`). Null when the game has not saved yet.
     */
    fun backup(live: File, device: File?, packSaves: File, stamp: String): File? {
        val files = live.listFiles() ?: return null
        val dev = device?.takeIf { it.isDirectory && it.listFiles()?.isNotEmpty() == true }
        if (files.isEmpty() && dev == null) return null
        packSaves.mkdirs()
        val dir = File(packSaves, "$stamp-before-import")
        dir.deleteRecursively()
        copyTree(live, dir)
        if (dev != null) copyTree(dev, File(packSaves, "$stamp-before-import.device"))
        return dir
    }

    /**
     * Put a tree in the live save's place: the new tree is copied aside, the old one is renamed out of the
     * way, the new one takes its place, and the old one is removed. The game is closed while this happens.
     */
    fun swapIn(tree: File, device: File?, live: File, liveDevice: File) {
        live.parentFile?.mkdirs()
        val newLive = File(live.parentFile, live.name + ".import")
        val newDevice = File(liveDevice.parentFile, liveDevice.name + ".import")
        newLive.deleteRecursively()
        newDevice.deleteRecursively()
        copyTree(tree, newLive)
        if (device != null && device.isDirectory) copyTree(device, newDevice)
        val oldLive = File(live.parentFile, live.name + ".old")
        val oldDevice = File(liveDevice.parentFile, liveDevice.name + ".old")
        oldLive.deleteRecursively()
        oldDevice.deleteRecursively()
        if (live.exists()) live.renameTo(oldLive)
        if (liveDevice.exists()) liveDevice.renameTo(oldDevice)
        if (!newLive.renameTo(live)) copyTree(newLive, live)
        if (newDevice.isDirectory && !newDevice.renameTo(liveDevice)) copyTree(newDevice, liveDevice)
        oldLive.deleteRecursively()
        oldDevice.deleteRecursively()
    }

    /** a directory tree copied file by file, byte for byte (a file that is there is replaced) */
    fun copyTree(src: File, dst: File) {
        if (!src.isDirectory) return
        dst.mkdirs()
        src.walkTopDown().forEach { f ->
            val rel = f.relativeTo(src).path
            if (rel.isEmpty()) return@forEach
            val out = File(dst, rel)
            if (f.isDirectory) {
                out.mkdirs()
            } else {
                out.parentFile?.mkdirs()
                f.inputStream().use { i -> out.outputStream().use { o -> i.copyTo(o) } }
            }
        }
    }

    /**
     * The package of the live save, written into `dest` (the name it gets there is free); null when the game
     * has not saved yet. The tree in the package is named after the package.
     */
    fun export(live: File, device: File?, dest: File): File? {
        val main = live.takeIf { it.isDirectory && it.listFiles()?.isNotEmpty() == true }
        val dev = device?.takeIf { it.isDirectory && it.listFiles()?.isNotEmpty() == true }
        if (main == null && dev == null) return null
        val free = freeName(dest)
        writePackage(main, dev, free.name.removeSuffix(".$EXT"), free)
        return free
    }

    /** a package name that is not taken yet (two exports in the same second would share one name) */
    fun freeName(dest: File): File {
        if (!dest.exists()) return dest
        val stem = dest.name.removeSuffix(".$EXT")
        var k = 2
        while (true) {
            val f = File(dest.parentFile, "$stem-$k.$EXT")
            if (!f.exists()) return f
            k++
        }
    }
}
