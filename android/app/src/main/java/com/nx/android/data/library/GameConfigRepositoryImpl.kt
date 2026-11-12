package com.nx.android.data.library

import com.nx.android.data.PrefsStore
import com.nx.android.data.PrefsStore.Companion.LEGACY_EXPERIMENTAL
import com.nx.android.data.PrefsStore.Companion.LEGACY_FPS60
import com.nx.android.data.PrefsStore.Companion.LEGACY_TOUCH
import com.nx.android.data.PrefsStore.Companion.LIB_CONFIGS
import com.nx.android.domain.GameConfig
import com.nx.android.domain.GameConfigJson
import com.nx.android.domain.GameConfigMigration
import com.nx.android.domain.GameEntry
import com.nx.android.domain.library.GameConfigRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

@Singleton
class GameConfigRepositoryImpl @Inject constructor(
    private val prefs: PrefsStore,
) : GameConfigRepository {
    private val configs = HashMap<String, GameConfig>()

    init {
        try {
            val json = prefs.json(LIB_CONFIGS)
            for (key in json.keys()) configs[key] = GameConfigJson.decode(json.getJSONObject(key))
        } catch (_: Exception) { }
    }

    @Synchronized
    override fun migrateLegacyConfigs(games: List<GameEntry>) {
        if (GameConfigMigration.migrateLegacy(configs, games)) save()
    }

    @Synchronized
    override fun migrateSourceConfig(sourcePath: String, titleId: String) {
        val config = configs[sourcePath] ?: return
        if (!configs.containsKey(titleId)) configs[titleId] = config
        configs.remove(sourcePath)
        save()
    }

    @Synchronized
    override fun forTitle(titleId: String): GameConfig = configs[titleId] ?: legacyConfig()

    @Synchronized
    override fun forSource(sourcePath: String, titleId: String?): GameConfig =
        titleId?.let(::forTitle) ?: configs[sourcePath] ?: legacyConfig()

    @Synchronized
    override fun set(titleId: String?, sourcePath: String?, config: GameConfig) {
        val key = titleId ?: sourcePath ?: return
        configs[key] = config
        save()
    }

    private fun legacyConfig() = GameConfig(
        touch = prefs.bool(LEGACY_TOUCH),
        fps60 = prefs.bool(LEGACY_FPS60),
        experimental = prefs.bool(LEGACY_EXPERIMENTAL),
    )

    private fun save() {
        val json = JSONObject()
        configs.forEach { (key, config) -> json.put(key, GameConfigJson.encode(config)) }
        prefs.putJson(LIB_CONFIGS, json)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class GameConfigDataModule {
    @Binds
    @Singleton
    abstract fun bindGameConfigRepository(impl: GameConfigRepositoryImpl): GameConfigRepository
}
