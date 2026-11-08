package com.nx.android.data

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * The user's own Lossless.dll (frame generation's shaders live inside it; never bundled, never downloaded):
 * looked up in the known spots, picked with the system picker, kept as a private copy that refreshes itself
 * when the source changes (the host reads the copy at the game's start; sdcard would do, but the private
 * folder is one place for every device). Validity: a PE image ("MZ") of at least 100 KB — the deeper check
 * (the SPIR-V shaders inside) is the host's, at the first enable.
 */
object LosslessDll {

    /** where a Lossless.dll is looked for (the common emulation folders, then the app's own) */
    fun knownPaths(context: Context): List<File> = listOf(
        File("/storage/emulated/0/Documents/Emulation/bios/Lossless.dll"),
        File("/storage/emulated/0/Switch/Lossless.dll"),
        File("/storage/emulated/0/Download/Lossless.dll"),
        File(context.getExternalFilesDir(null), "Lossless.dll"),
    )

    /** the private copy the game reads (DM_LSFG_DLL) */
    fun copyInPrivate(context: Context): File = File(File(context.filesDir, "lsfg"), "Lossless.dll")

    /** a file that looks like a Lossless.dll: big enough and a PE image */
    fun looksValid(f: File?): Boolean =
        f != null && f.isFile && f.length() >= 100_000 && runCatching {
            f.inputStream().use { s -> val h = ByteArray(2); s.read(h) == 2 && h[0] == 'M'.code.toByte() && h[1] == 'Z'.code.toByte() }
        }.getOrDefault(false)

    /** the newest valid source among the known paths (null when none) */
    fun find(context: Context): File? = knownPaths(context).filter { looksValid(it) }.maxByOrNull { it.lastModified() }

    /**
     * The copy to hand the game: refreshed from the newest known source (a changed length or stamp re-copies),
     * kept when no source is there any more. Returns it only when it is itself valid.
     */
    fun ensure(context: Context): File? {
        val dst = copyInPrivate(context)
        val src = find(context)
        if (src != null && (!dst.isFile || src.length() != dst.length() || src.lastModified() > dst.lastModified())) {
            val tmp = File(dst.parentFile, "Lossless.dll.part")
            runCatching {
                dst.parentFile?.mkdirs()
                src.inputStream().use { i -> tmp.outputStream().use { i.copyTo(it) } }
                if (!tmp.renameTo(dst)) { dst.delete(); tmp.renameTo(dst) }
            }
        }
        return dst.takeIf { looksValid(it) }
    }

    /** the system picker's answer: validated and copied (true when it became the private copy) */
    fun install(context: Context, uri: Uri): Boolean = runCatching {
        val tmp = part(context)
        context.contentResolver.openInputStream(uri)?.use { i -> tmp.outputStream().use { i.copyTo(it) } } ?: return false
        finishInstall(context, tmp)
    }.getOrDefault(false)

    /** the in-app file picker's answer (the settings page): same validation and copy */
    fun install(context: Context, file: File): Boolean = runCatching {
        val tmp = part(context)
        file.inputStream().use { i -> tmp.outputStream().use { i.copyTo(it) } }
        finishInstall(context, tmp)
    }.getOrDefault(false)

    private fun part(context: Context) = File(File(context.filesDir, "lsfg"), "Lossless.dll.part")

    private fun finishInstall(context: Context, tmp: File): Boolean {
        if (!looksValid(tmp)) { tmp.delete(); return false }
        val dst = copyInPrivate(context)
        dst.delete()
        return tmp.renameTo(dst)
    }
}
