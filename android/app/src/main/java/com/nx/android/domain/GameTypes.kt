package com.nx.android.domain

/** A game card's status, and the port options a game carries — pure vocabulary, shared by the layers */
enum class GameStatus { NOT_PORTED, PORTING, DONE }

data class GameConfig(val touch: Boolean = false, val fps60: Boolean = false, val experimental: Boolean = false)
