package com.nx.android.feature.library

import com.nx.android.domain.GameEntry
import com.nx.android.domain.PlayerApiRevision
import com.nx.android.domain.GameIdentity
import com.nx.android.domain.GameStatus
import com.nx.android.domain.Ported
import java.io.File

/**
 * 预览用的假数据：四个游戏，四种状态。
 *
 * 这里没有 @Preview —— 预览在各自的组件文件里（IDE 的惯例：预览就写在它预览的那个组件旁边），
 * 这个文件只放它们共用的数据。
 */

/**
 * 假的宿主文件：包里没有它，那个包就「不能跑」，按钮会变成「重新移植」。
 *
 * 所以它得是真的存在的文件（空的也算）——这是预览里唯一一处碰磁盘的地方。
 */
internal val previewHost: File by lazy {
    File.createTempFile("nx2a-preview", ".so").apply {
        writeBytes(ByteArray(0))
        deleteOnExit()
    }
}

/** 一个假的游戏：包、封面、档案状态都由这里给 */
internal fun previewEntry(
    titleId: String,
    name: String,
    withPack: Boolean = true,
    stale: Boolean = false,
    display: String = "",
    profileStatus: String = "playable",
): GameEntry = GameEntry(
    titleId = titleId,
    pack = if (withPack) Ported(
        pack = File("/data/ports/$name-data"),
        host = previewHost,
        icon = null,
        titleId = titleId,
        name = name,
        status = profileStatus,
        playerApiRevision = if (stale) 0 else PlayerApiRevision.CURRENT,
        display = display,
    ) else null,
    work = null,
    sourcePath = "/sdcard/Download/$name.nsp",
)

/** 预览里的一个游戏：它的卡片与它的状态 */
internal data class PreviewGame(val entry: GameEntry, val status: GameStatus)

/** 四种状态各一个：能玩的、构建中的、没构建的、要重新移植的 */
internal val previewGames = listOf(
    PreviewGame(previewEntry("0100000000010000", "Hades II", display = "1.0.0"), GameStatus.DONE),
    PreviewGame(previewEntry("0100000000020000", "Super Mario Odyssey"), GameStatus.PORTING),
    PreviewGame(previewEntry("0100000000030000", "Metroid Dread", withPack = false), GameStatus.NEEDS_BUILD),
    PreviewGame(previewEntry("0100000000040000", "Dragon Ball Xenoverse 2", stale = true), GameStatus.NEEDS_BUILD),
)

/** 预览里的状态：按 title ID 找，找不到就是没构建过 */
internal val previewStatus: (GameEntry) -> GameStatus = { e ->
    previewGames.firstOrNull { it.entry.titleId == e.titleId }?.status ?: GameStatus.NOT_PORTED
}

/** 预览里的动作：点了什么都不做 */
internal fun previewActions() = LibraryActions(
    onSelect = {},
    onBuild = {},
    onRun = {},
    onViewBuild = {},
    onConfigure = {},
)
