package com.nx.android.domain

import java.io.File

/**
 * The game files of a folder, and what the output folder says about them. Free of Android, so the tests run on
 * the JVM.
 */
object FolderScanner {

    val GAME_EXTS = listOf(".nsp", ".xci", ".nsz", ".xcz")

    fun isGameFile(f: File): Boolean = f.isFile && GAME_EXTS.any { f.name.lowercase().endsWith(it) }

    /** Every game file of the folder and its subfolders, by name */
    fun scan(folder: File): List<File> {
        if (!folder.isDirectory) return emptyList()
        val out = ArrayList<File>()
        walk(folder, out, 0)
        return out.sortedBy { it.name.lowercase() }
    }

    private fun walk(dir: File, out: MutableList<File>, depth: Int) {
        if (depth > 8) return
        for (f in dir.listFiles() ?: return) {
            if (f.isHidden) continue
            if (isGameFile(f)) out.add(f) else if (f.isDirectory) walk(f, out, depth + 1)
        }
    }

    /** A title ID in a file's name: `Zelda [0100000000010000][v0].nsp` → 0100000000010000 */
    fun titleIdIn(name: String): String? =
        Regex("\\[(0[0-9a-fA-F]{15})]").find(name)?.groupValues?.get(1)?.uppercase()

    /**
     * The title ID of every game the output folder holds: the port wrote `<stem>-data/nx2a-data.json` (the
     * game, its package, its title ID), and the APK sits beside the data pack.
     */
    fun outIndex(outDir: File): Map<String, File> {
        if (!outDir.isDirectory) return emptyMap()
        val out = HashMap<String, File>()
        for (d in outDir.listFiles() ?: return emptyMap()) {
            if (!d.isDirectory || !d.name.endsWith("-data")) continue
            val apk = File(outDir, d.name.removeSuffix("-data") + ".apk")
            if (!apk.isFile) continue
            val id = try {
                org.json.JSONObject(File(d, "nx2a-data.json").readText()).optString("title_id")
            } catch (e: Exception) {
                ""
            }
            if (id.isNotEmpty()) out[id.uppercase()] = apk
        }
        return out
    }

    /** The game's display name: the file's name without its brackets and extension */
    fun displayName(file: File): String {
        var n = file.name.substringBeforeLast('.')
        n = n.replace(Regex("\\[[^]]*]"), "").replace(Regex("\\([^)]*\\)"), "").trim(' ', '-', '.', '_')
        return n.ifEmpty { file.name }
    }
}
