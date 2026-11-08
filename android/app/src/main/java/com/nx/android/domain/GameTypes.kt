package com.nx.android.domain

import java.io.File

/** porter 接受的游戏文件后缀（白名单，小写、不带点） */
val GAME_FILE_EXTENSIONS = setOf("nsp", "xci", "nsz", "xcz")

/** A game card's status, and the port options a game carries — pure vocabulary, shared by the layers */
enum class GameStatus { NOT_PORTED, PORTING, DONE }

data class GameConfig(val touch: Boolean = false, val fps60: Boolean = false, val experimental: Boolean = false)

/**
 * 一个完成的移植：数据包是唯一产物（手机线不再产出 APK），宿主程序（libdmplay.so）在包里，App 的
 * 玩家把它拷进私有目录后直接运行。「已完成」以包与宿主齐备为准。
 */
data class Ported(
    val pack: File,        // <App>-data/
    val host: File,        // 包内的宿主（默认 host/libdmplay.so，清单的 "host" 字段同名）
    val icon: File?,       // 包内的图标（icon.png），没有为 null
    val titleId: String,   // 清单里的 title id（存档、私有目录都按它分）
    val name: String = "", // 清单里的游戏名
)
