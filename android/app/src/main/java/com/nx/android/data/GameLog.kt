package com.nx.android.data

import java.io.File
import java.io.IOException

/**
 * The game's runtime log: the host writes its whole stderr into `log.txt` in the game's data pack
 * (front_android.c's host_start_common), so a run's lines - the host's notices and the backtrace of a
 * crash - lie in the pack. The app shares that log: the file is copied into the download folder and sent
 * from there, the way the save packages go out.
 */
object GameLog {

    /** the host's log in the data pack */
    const val FILE = "log.txt"

    /** the log of the last run in that pack, or null when the game has never run */
    fun inPack(pack: File): File? = File(pack, FILE).takeIf { it.isFile }

    /** the name the shared copy carries: the game's own name, safe in a file name */
    fun shareName(game: String): String = "${SaveBackup.sanitize(game).ifEmpty { "game" }}-log.txt"

    /**
     * A copy of the game's log in [dest] (the folder the share hands out), under the game's name.
     * Null when the game has never run: there is no log to copy. Throws when the copy itself fails.
     */
    @Throws(IOException::class)
    fun copyTo(pack: File, dest: File, game: String): File? {
        val log = inPack(pack) ?: return null
        if (!dest.isDirectory && !dest.mkdirs()) throw IOException("cannot make ${dest.absolutePath}")
        val out = File(dest, shareName(game))
        val part = File(dest, ".${out.name}.part")
        part.delete()
        try {
            log.copyTo(part, overwrite = true)
        } catch (e: Exception) {
            part.delete()
            throw IOException("cannot copy the log: ${e.message}")
        }
        if (out.exists()) out.delete()
        if (!part.renameTo(out)) {
            part.copyTo(out, overwrite = true)
            part.delete()
        }
        return out
    }
}
