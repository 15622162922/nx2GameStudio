package com.nx.android.data

import com.nx.android.domain.GameInfo
import com.nx.android.domain.GameInfoJson
import com.nx.android.domain.GameInspector
import com.nx.android.domain.environment.GuestProcessLauncher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The porter's own reading of a game file: `porter inspect --json` in the guest.
 *
 * It is the same reading the desktop GUI takes when the player picks a file, so the app and the desktop tool
 * say the same things about the same game. The guest's Python starts in seconds, so the caller keeps the answer
 * and does not ask twice for the same file.
 */
@Singleton
class PortInspector @Inject constructor(
    private val launcher: GuestProcessLauncher,
) : GameInspector {

    override suspend fun inspect(gameFile: String, keys: String): GameInfo? = withContext(Dispatchers.IO) {
        val args = mutableListOf("-m", "porter", "inspect", gameFile, "--json")
        if (keys.isNotBlank()) args += listOf("-k", keys)
        val process = runCatching { launcher.guest(args) }.getOrNull() ?: return@withContext null
        GameInfoJson.decode(readOutput(process, TIMEOUT_MS))
    }

    /**
     * The guest's output, read until it is done.
     *
     * The report comes on stdout and the rest of the text around it is not ours to read; the reading stops when
     * the process is gone. A guest that hangs is killed and nothing comes back — the caller treats it as "no
     * answer" rather than waiting forever.
     */
    private fun readOutput(process: Process, timeoutMs: Long): String {
        val out = StringBuilder()
        val reader = Thread {
            runCatching { process.inputStream.bufferedReader().forEachLine { out.append(it).append('\n') } }
        }
        reader.isDaemon = true
        reader.start()
        if (!process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) {
            process.destroyForcibly()
            return ""
        }
        reader.join(1000)
        return out.toString()
    }

    companion object {
        /** The guest Python starts in seconds; a read that takes longer than this is not going to arrive. */
        const val TIMEOUT_MS = 90_000L
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class InspectorDataModule {
    @Binds
    @Singleton
    abstract fun bindGameInspector(impl: PortInspector): GameInspector
}
