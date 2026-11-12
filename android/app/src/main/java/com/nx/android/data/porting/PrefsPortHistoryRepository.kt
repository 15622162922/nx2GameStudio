package com.nx.android.data.porting

import com.nx.android.data.PrefsStore
import com.nx.android.data.PrefsStore.Companion.PORT_HISTORY
import com.nx.android.domain.porting.PortHistoryEntry
import com.nx.android.domain.porting.PortHistoryRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class PrefsPortHistoryRepository @Inject constructor(private val prefs: PrefsStore) : PortHistoryRepository {
    override fun load(): List<PortHistoryEntry> = try {
        val array = prefs.array(PORT_HISTORY)
        (0 until array.length()).mapNotNull { index ->
            val item = array.getJSONObject(index)
            PortHistoryEntry(item.getString("game"), item.getString("phase"), item.optString("out"),
                item.optLong("startedAt"), item.optLong("seconds"))
        }
    } catch (_: Exception) { emptyList() }

    override fun save(entries: List<PortHistoryEntry>) {
        val array = JSONArray()
        entries.take(20).forEach { entry ->
            array.put(JSONObject().put("game", entry.game).put("phase", entry.phase).put("out", entry.out)
                .put("startedAt", entry.startedAt).put("seconds", entry.seconds))
        }
        prefs.putArray(PORT_HISTORY, array)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PortHistoryDataModule {
    @Binds @Singleton abstract fun bindPortHistoryRepository(impl: PrefsPortHistoryRepository): PortHistoryRepository
}
