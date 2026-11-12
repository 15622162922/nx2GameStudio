package com.nx.android.appstate

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nx.android.data.AppVersion
import com.nx.android.data.UpdateChecker
import com.nx.android.domain.UpdateFeed
import com.nx.android.domain.UpdateVersion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 有没有新版本。
 *
 * 冷启动时安静地查一次，查不到就安静地过去；玩家在设置页可以自己再查一次，那一次把结果都摆出来。
 * 查到的新版本比 App 现在的版本新，就记下来，连同那个 release 自己的更新说明（release 的正文）。
 * 玩家点「稍后」＝这一次启动不再问；下次冷启动还会问（这是玩家自己的选择，不是永久的拒绝）。
 */
@Singleton
class UpdateState @Inject constructor(@ApplicationContext private val context: Context) {

    enum class Phase { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, FAILED }

    /** 上一次检查的结果 */
    var phase by mutableStateOf(Phase.IDLE); private set

    /** 新版本的版本号（canonical 的写法：0.3.0-alpha02）；没有新版本时是空的 */
    var latest by mutableStateOf(""); private set

    /** App 自己的版本号（0.3.0-alpha02）——弹框里告诉玩家他现在在哪 */
    var mine by mutableStateOf(""); private set

    /** 新版本的更新说明：那个 release 的正文（HTML，GitHub 自己渲染好的） */
    var notes by mutableStateOf(""); private set

    /** 玩家说了「稍后」：这一次启动不再弹 */
    var later by mutableStateOf(false); private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** 自动的那一次只查一次（一个进程一次） */
    private var checked = false

    /** 冷启动的那一次：查不到不出声 */
    fun checkQuietly() {
        if (checked) return
        checked = true
        scope.launch { run(manual = false) }
    }

    /** 玩家在设置页自己点的：结果都摆出来 */
    fun checkNow() {
        scope.launch { run(manual = true) }
    }

    /** 玩家说「稍后」：这一次启动不再问 */
    fun postpone() {
        later = true
    }

    private suspend fun run(manual: Boolean) {
        phase = Phase.CHECKING
        val tag = UpdateChecker.latestTag().getOrElse {
            // 自动的那一次安静地过去；玩家自己问的那一次告诉他查不成
            phase = if (manual) Phase.FAILED else Phase.IDLE
            return
        }
        val release = UpdateVersion.parse(tag)
        val ownText = AppVersion.of(context)
        val own = UpdateVersion.parseOwn(ownText)
        if (release == null || own == null || release <= own) {
            phase = Phase.UP_TO_DATE
            latest = ""
            notes = ""
            return
        }
        latest = release.toString()
        mine = ownText
        notes = ""
        later = false
        phase = Phase.AVAILABLE
        // 更新说明随后到：弹框不等它（网络慢的时候玩家先看到版本号，说明自己补上去）
        scope.launch {
            val said = notesOf(tag)
            // 期间又查了一次、版本换了一个？旧说明不配新版本，丢掉
            if (latest == release.toString()) notes = said
        }
    }

    /**
     * 那个 release 自己的更新说明：feed 里它那一条的正文。取不到就是空的——弹框照样弹，只是没有说明
     * （说明是好的，不是必须的）。
     */
    private suspend fun notesOf(tag: String): String {
        val feed = UpdateChecker.releaseFeed().getOrNull() ?: return ""
        return UpdateFeed.notes(feed, tag) ?: ""
    }
}
