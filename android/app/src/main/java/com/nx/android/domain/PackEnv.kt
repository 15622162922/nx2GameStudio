package com.nx.android.domain

import java.io.File

/**
 * A data pack's run options: the game's language and the Vulkan driver, as env.txt rows the host reads at its
 * start (DM_LANG / DM_VK_DRIVER — the choices the per-game APK's launcher page used to keep). The options come
 * from the pack's lists (languages.txt, drivers.txt); writing replaces the two rows in place, everything else
 * (the profile's rows, the comments) stays. Free of Android, so the tests run on the JVM.
 */
object PackEnv {

    /** The pack's languages (languages.txt rows of `code<TAB>name`), code first */
    fun languages(pack: File): List<Pair<String, String>> =
        text(File(pack, "languages.txt")).mapNotNull { l ->
            val (code, name) = l.split('\t', limit = 2)
            code.trim().takeIf { it.isNotEmpty() }?.let { it to (name.trim().ifEmpty { it }) }
        }

    /** The pack's Vulkan drivers (drivers.txt names, kept when the pack really carries the file) */
    fun drivers(pack: File): List<String> {
        val dir = File(pack, "drivers")
        return text(File(pack, "drivers.txt")).map { it.trim() }.filter { it.isNotEmpty() && File(dir, it).isFile }
    }

    /** The rows' current values (DM_LANG, DM_VK_DRIVER), null when the row is absent */
    fun current(pack: File): Pair<String?, String?> {
        var lang: String? = null; var drv: String? = null
        for (l in lines(pack)) {
            val (k, v) = splitRow(l)
            if (k == "DM_LANG") lang = v else if (k == "DM_VK_DRIVER") drv = v
        }
        return lang to drv
    }

    /** Sets the rows (null removes one); the file's other lines keep their places */
    fun set(pack: File, lang: String?, driver: String?) {
        val rest = lines(pack).filter { l ->
            val (k, _) = splitRow(l)
            k != "DM_LANG" && k != "DM_VK_DRIVER"
        }.toMutableList()
        lang?.let { rest.add("DM_LANG=$it") }
        driver?.let { rest.add("DM_VK_DRIVER=$it") }
        val env = File(pack, "env.txt")
        val tmp = File(pack, "env.txt.new")
        tmp.writeText((rest + "").joinToString("\n"), Charsets.UTF_8)   // trailing newline, as the porter wrote it
        if (!tmp.renameTo(env)) {
            env.writeText((rest + "").joinToString("\n"), Charsets.UTF_8)   // a replace-in-place refusal: write directly
            tmp.delete()
        }
    }

    private fun lines(pack: File): List<String> =   // env.txt: the rows and the porter's comments, both (set() only rewrites its own two rows)
        try { File(pack, "env.txt").readText(Charsets.UTF_8).split('\n').map { it.trimEnd('\r') }.filter { it.isNotEmpty() } }
        catch (e: Exception) { emptyList() }

    private fun text(f: File): List<String> =
        try { f.readText(Charsets.UTF_8).split('\n').map { it.trimEnd('\r') }.filter { it.isNotEmpty() && !it.startsWith("#") } }
        catch (e: Exception) { emptyList() }

    private fun splitRow(l: String): Pair<String, String> {
        val i = l.indexOf('=')
        return if (i <= 0) "" to "" else l.substring(0, i) to l.substring(i + 1)
    }
}
