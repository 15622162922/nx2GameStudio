package com.nx.android.domain

/** porter 接受的游戏文件后缀（白名单，小写、不带点） */
val GAME_FILE_EXTENSIONS = setOf("nsp", "xci", "nsz", "xcz")

/** A game card's status, and the port options a game carries — pure vocabulary, shared by the layers */
enum class GameStatus { NOT_PORTED, PORTING, DONE }

data class GameConfig(val touch: Boolean = false, val fps60: Boolean = false, val experimental: Boolean = false)
