package com.nx.android

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.nx.android.R
import com.nx.android.appstate.LibraryState
import com.nx.android.appstate.OnboardingState
import com.nx.android.appstate.PlayerState
import com.nx.android.appstate.PortState
import com.nx.android.data.AppLocale
import com.nx.android.data.AppLog
import com.nx.android.data.Env
import com.nx.android.data.Player
import com.nx.android.data.PrefsStore
import com.nx.android.domain.FolderScanner
import com.nx.android.ui.AppActions
import com.nx.android.ui.AppRoot
import java.io.File

/**
 * The shell: it owns what only an activity can own — the storage permission, the system pickers — and puts
 * the screen up. Everything long-lived comes from the AppContainer (NxApp); the screens take state holders
 * and action lambdas, never this activity.
 */
class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TAB = "tab"   // the notification's tap lands on the builds page
    }

    private val container by lazy { (application as NxApp).container }

    /** 界面语言：toast 与分享面板的标题从这里取词 */
    private val locale: AppLocale get() = container.locale

    /** Which tab the screen opens on (the notification's tap says the builds page) */
    var launchTab = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 全屏沉浸：内容铺满整块屏幕，系统栏隐藏（下滑临时呼出）——capyps5 CapyActivity 同款
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        container.storageOk = storageGranted()
        launchTab = intent?.getIntExtra(EXTRA_TAB, 0) ?: 0
        if (!container.prefs.bool(PrefsStore.OB_COMPLETED)) container.onboarding.open()
        AppLog.init(this)
        container.port.attach()   // a port the system killed shows up as interrupted
        setContent { AppRoot(container, actions, launchTab) }
        // the small tools come with the app: an update brings new ones, and the phone has to get them
        // (the app's own Vulkan driver too: it is unpacked into the app's folder, ready to be picked)
        Thread {
            container.env.refreshTools()
            container.drivers.ensureShipped(this)
            container.drivers.refresh(this)
        }.start()
    }

    /** The screens' way back to the activity's own privileges: the actions they may ask for, nothing else */
    private val actions = AppActions(
        onAskStorage = { askStorage() },
        storageOk = { container.storageOk },
        onSetKeys = { container.prefs.writeKeys(it) },
        onStartPort = { file, cfg ->
            PortService.start(this, file.absolutePath, container.prefs.keys, container.prefs.out,
                              cfg.touch, cfg.fps60, cfg.experimental)
        },
        onCancelPort = { PortService.cancel(this) },
        onRun = { pack, name ->
            // 运行一个完成的移植：宿主与驱动拷进私有目录后拉起 :play（包是唯一真源，拷贝覆盖旧副本）
            FolderScanner.portFromPack(pack)?.let { ported ->
                container.player.start(ported.titleId, name)
                Player.run(this, ported, name) { ok ->
                    if (!ok) {
                        container.player.stop()
                        toast(locale.str(R.string.main_cannot_run_no_host, pack.name))
                    }
                }
            } ?: toast(locale.str(R.string.main_cannot_run_not_a_pack, pack.name))
        },
        onResumeGame = {
            container.player.start(container.player.titleId, container.player.name)
            com.nx.android.game.GameActivity.resume(this)
        },
        onStopGame = {
            container.player.stop()
            com.nx.android.game.GameActivity.stop(this)
        },
        onToast = { toast(it) },
        version = { versionName() },
        keys = { container.prefs.keys },
        out = { container.prefs.out },
        onSetOut = { container.prefs.writeOut(it) },
        onShareLog = {
            val text = AppLog.text()
            if (text.isNotEmpty()) {
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, text), locale.str(R.string.main_share_log)))
            } else {
                toast(locale.str(R.string.main_log_empty))
            }
        },
        onClearLog = {
            AppLog.clear()
            toast(locale.str(R.string.main_log_cleared))
        },
        readLog = { AppLog.text(2000) },
    )

    override fun onResume() {
        super.onResume()
        container.storageOk = storageGranted()   // the permission may have been given in the background
    }

    /** The phone's language changed (configChanges holds it): the app's words are read again */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        container.locale.configurationChanged()
    }

    // ---- the storage (the activity's own privilege) ----------------------------------------------------------

    /** The shared storage: the game files and the ports the tool makes. From Android 11 the old permissions are
     * not enough: the app needs the all-files access, which the user gives once */
    private fun storageGranted(): Boolean {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager()
        return checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    fun askStorage() {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE,
                                          android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
            }
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            } catch (e2: Exception) {
                toast(locale.str(R.string.main_no_permission_settings))
            }
        }
    }

    // ---- after the port (the activity's own privilege) -----------------------------------------------------------

    fun versionName(): String = try {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }

    fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    // ---- the container's state holders, for the wiring that builds the screens ------------------------------------

    val prefs: PrefsStore get() = container.prefs
    val env: Env get() = container.env
    val ob: OnboardingState get() = container.onboarding
    val lib: LibraryState get() = container.library
    val port: PortState get() = container.port
    val player: PlayerState get() = container.player
    val storageOk: Boolean get() = container.storageOk
}
