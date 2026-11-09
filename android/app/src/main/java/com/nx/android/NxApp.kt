package com.nx.android

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nx.android.appstate.DriverState
import com.nx.android.appstate.LibraryState
import com.nx.android.appstate.OnboardingState
import com.nx.android.appstate.PlayerState
import com.nx.android.appstate.PortState
import com.nx.android.data.Env
import com.nx.android.data.Player
import com.nx.android.data.PrefsStore

/**
 * The app's own wiring: every long-lived object is built here, once, and flows one way from here — the UI
 * takes what it shows, the service takes what it runs. Nothing else builds these.
 */
class NxApp : Application() {

    val container by lazy { AppContainer(this) }
}

/**
 * 手动依赖注入（仓库零框架传统下的官方等价物）：依赖只从这里往下流，上层拿构造好的对象，不找全局。
 * 存储权限是唯一由 Activity 喂进来的状态（只有 Activity 能感知它的变化），这里持有、UI 观察。
 */
class AppContainer(app: NxApp) {

    val prefs = PrefsStore(app)
    val env = Env(app)

    /** 所有文件访问权限：MainActivity 在 onResume 时更新 */
    var storageOk by mutableStateOf(false)

    val port = PortState(prefs)
    val onboarding = OnboardingState(prefs, env, storageOk = { storageOk })
    val library = LibraryState(prefs, port)

    /** 玩家装好的 Vulkan 驱动：设置页和驱动页读同一份 */
    val drivers = DriverState()

    /** 正在玩的游戏（:play 进程的存在与否，两秒一查） */
    val player = PlayerState(alive = { Player.playProcessAlive(app) })
}
