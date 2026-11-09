package com.nx.android.appstate

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nx.android.data.AppVersion
import com.nx.android.data.PrefsStore
import com.nx.android.data.UpdateChecker
import com.nx.android.domain.UpdateVersion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 有没有新版本。
 *
 * 冷启动时安静地查一次，查不到就安静地过去；玩家在设置页可以自己再查一次，那一次把结果都摆出来。
 * 查到的新版本比 App 现在的版本新，就记下来，主页放一条提示。玩家关掉提示＝这个版本不再提
 * （记在 prefs 里），但他自己去问，还是得到真答案。
 */
class UpdateState(private val prefs: PrefsStore, private val context: Context) {

    enum class Phase { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, FAILED }

    /** 上一次检查的结果 */
    var phase by mutableStateOf(Phase.IDLE); private set

    /** 新版本的版本号（canonical 的写法：0.3.0-alpha02）；没有新版本时是空的 */
    var latest by mutableStateOf(""); private set

    /** 玩家关掉了这条提示：主页不再弹，但设置页照实说 */
    var dismissed by mutableStateOf(false); private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 冷启动的那一次：查不到不出声 */
    fun checkQuietly() {
        scope.launch { run(manual = false) }
    }

    /** 玩家在设置页自己点的：结果都摆出来 */
    fun checkNow() {
        scope.launch { run(manual = true) }
    }

    /** 玩家关掉了提示：这个版本不再提（他自己去问还是查得出来） */
    fun dismiss() {
        if (latest.isNotEmpty()) prefs.put(PrefsStore.UPDATE_IGNORED, latest)
        dismissed = true
    }

    private suspend fun run(manual: Boolean) {
        phase = Phase.CHECKING
        val tag = UpdateChecker.latestTag().getOrElse {
            // 自动的那一次安静地过去；玩家自己问的那一次告诉他查不成
            phase = if (manual) Phase.FAILED else Phase.IDLE
            return
        }
        val release = UpdateVersion.parse(tag)
        val mine = UpdateVersion.parseOwn(AppVersion.of(context))
        if (release == null || mine == null || release <= mine) {
            phase = Phase.UP_TO_DATE
            latest = ""
            dismissed = false
            return
        }
        latest = release.toString()
        // 自动的那一次：玩家关掉过的版本不再提；玩家自己问的那一次照实说
        dismissed = !manual && prefs.str(PrefsStore.UPDATE_IGNORED) == latest
        phase = Phase.AVAILABLE
    }
}
