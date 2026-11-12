package com.nx.android.domain.library

import com.nx.android.domain.GameConfig
import com.nx.android.domain.GameEntry

interface GameConfigRepository {
    fun migrateLegacyConfigs(games: List<GameEntry>)
    fun migrateSourceConfig(sourcePath: String, titleId: String)
    fun forTitle(titleId: String): GameConfig
    fun forSource(sourcePath: String, titleId: String?): GameConfig
    fun set(titleId: String?, sourcePath: String?, config: GameConfig)
}
