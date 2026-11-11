package com.nx.android.domain

import org.json.JSONObject
import java.io.File

/** porter 接受的游戏文件后缀（白名单，小写、不带点） */
val GAME_FILE_EXTENSIONS = setOf("nsp", "xci", "nsz", "xcz")

/** A game card's status, and the port options a game carries — pure vocabulary, shared by the layers */
enum class GameStatus { NOT_PORTED, PORTING, DONE, FAILED }

/**
 * 额外地址空间（别名区）：-1 = Auto（不指定，由移植器按自己的规则决定：档案调通过的版本不加，
 * 没调通过的加 8 GB），0~48 GB 为玩家手动指定。它编译进宿主的内存布局，改了要重新构建。
 */
const val ALIAS_AUTO = -1

data class GameConfig(
    val touch: Boolean = false,
    val fps60: Boolean = false,
    val experimental: Boolean = false,
    val aliasHeadroom: Int = ALIAS_AUTO,
)

/**
 * 按游戏配置的存取：JSON 一个游戏一条。纯值，不碰 Android，测试跑在 JVM 上。
 * 旧版本存下的配置没有 "alias" 这一项，读出来是 Auto，构建行为与那时一致。
 */
object GameConfigJson {

    fun encode(c: GameConfig): JSONObject = JSONObject()
        .put("touch", c.touch).put("fps60", c.fps60).put("exp", c.experimental)
        .put("alias", c.aliasHeadroom)

    fun decode(o: JSONObject): GameConfig = GameConfig(
        o.optBoolean("touch"), o.optBoolean("fps60"), o.optBoolean("exp"), o.optInt("alias", ALIAS_AUTO))
}

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
    val status: String = "", // 清单里的档案状态（playable / wip / boots / experimental）：experimental 的移植提醒玩家它还没被正式调通
    val hostFront: String = "", // 清单里的宿主前端（"player"）：别的值（没有这个字段 = 旧版宿主）要用当前版本重新移植
    val display: String = "", // 清单里的显示版本（游戏自己的版本号，如 1.1.1）：老的数据包没有这一项
) {
    /** 这个包是用当前版本的前端编的吗？不是的话要重新移植（宿主的前端变了，老的宿主没有新的入口） */
    val needsRePort: Boolean get() = hostFront != FolderScanner.HOST_FRONT
}
