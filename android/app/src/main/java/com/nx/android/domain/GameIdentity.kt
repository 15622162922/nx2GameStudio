package com.nx.android.domain

import java.io.File

/** Pure naming and identity rules shared by library and build features. */
object GameIdentity {
    const val HOST_FRONT = "player-hud"
    const val WORK_SUFFIX = "_build"

    fun titleIdIn(name: String): String? =
        Regex("\\[(0[0-9a-fA-F]{15})]").find(name)?.groupValues?.get(1)?.uppercase()

    fun titleIdInWork(name: String): String? {
        if (!name.endsWith(WORK_SUFFIX)) return null
        val titleId = name.removeSuffix(WORK_SUFFIX)
        if (titleId.length != 16 || !titleId.all { it in "0123456789ABCDEFabcdef" }) return null
        return titleId.uppercase()
    }

    fun labelFor(file: File, ported: Ported?): String =
        ported?.name?.trim()?.takeIf { it.isNotEmpty() } ?: displayName(file)

    fun displayName(file: File): String {
        var name = file.name.substringBeforeLast('.')
        name = name.replace(Regex("\\[[^]]*]"), "").replace(Regex("\\([^)]*\\)"), "")
            .trim(' ', '-', '.', '_')
        return name.ifEmpty { file.name }
    }
}
