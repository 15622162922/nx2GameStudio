package com.nx.android.domain

import java.io.File

/**
 * What the game files and the finished data packs say about a game: its title ID, its display name, the
 * pack it was ported into. Free of Android, so the tests run on the JVM.
 */
object FolderScanner {

    /** 宿主的前端（数据包清单的 "host_front"）：当前版本要的前端。老的包里没有这个字段，那样的包要重新移植 */
    const val HOST_FRONT = "player-hud"

    /** A title ID in a file's name: `Zelda [0100000000010000][v0].nsp` → 0100000000010000 */
    fun titleIdIn(name: String): String? =
        Regex("\\[(0[0-9a-fA-F]{15})]").find(name)?.groupValues?.get(1)?.uppercase()

    /**
     * The title ID of every finished port the output folder holds: the port wrote `<stem>-data/` with
     * `nx2a-data.json` (the game, its title ID) and the host the player runs (host/libdmplay.so). A pack
     * without its host (a rebuild in flight, a deleted file) does not count as done.
     */
    fun outIndex(outDir: File): Map<String, Ported> {
        if (!outDir.isDirectory) return emptyMap()
        val out = HashMap<String, Ported>()
        for (d in outDir.listFiles() ?: return emptyMap()) {
            if (!d.isDirectory || !d.name.endsWith("-data")) continue
            portFromPack(d)?.let { out[it.titleId.uppercase()] = it }
        }
        return out
    }

    /**
     * The port a data pack holds, or null when the pack is not runnable (no manifest, or the host file is
     * gone): the title id, the icon and the game's name come from the manifest, the paths are conventions.
     */
    fun portFromPack(pack: File): Ported? {
        val man = try {
            org.json.JSONObject(File(pack, "nx2a-data.json").readText())
        } catch (e: Exception) {
            return null
        }
        val id = man.optString("title_id")
        if (id.isEmpty()) return null
        val host = File(pack, man.optString("host", "host/libdmplay.so"))
        if (!host.isFile) return null
        return Ported(pack = pack, host = host,
                      icon = File(pack, "icon.png").takeIf { it.isFile },
                      titleId = id, name = man.optString("name"),
                      status = man.optString("status"),
                      hostFront = man.optString("host_front"))
    }

    /** The game's display name: the file's name without its brackets and extension */
    fun displayName(file: File): String {
        var n = file.name.substringBeforeLast('.')
        n = n.replace(Regex("\\[[^]]*]"), "").replace(Regex("\\([^)]*\\)"), "").trim(' ', '-', '.', '_')
        return n.ifEmpty { file.name }
    }
}
