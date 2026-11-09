package com.nx.android.game

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.nx.android.NxApp
import com.nx.android.R
import com.nx.android.data.AppLocale
import com.nx.android.data.FrameGenSettings
import com.nx.android.data.LosslessDll
import com.nx.android.data.PrefsStore
import com.nx.android.ui.ProvideAppLocale
import com.nx.android.ui.game.GameDrawer
import com.nx.android.ui.game.PadView
import com.nx.android.ui.theme.NxTheme

/**
 * One game in this process (":play"): the host (libdmplay.so, the copy the library made in the app's private
 * folder) is loaded here and started with the pack folder, the SurfaceView's surface goes to the host's Vulkan
 * swapchain and every input event is forwarded as the snapshot the shared core reads (jni_android.h). The
 * drawer (Compose, over the surface) holds the game's live switches — the frame generation among them; Back
 * stays with the game's own pause menu. The host ends the process when the game ends (one game per process),
 * and [endGame] kills it too — a started host cannot be started again in the same process, so a recreated
 * activity of a running game only re-hands its window ([hostRunning]).
 */
class GameActivity : ComponentActivity(), SurfaceHolder.Callback {

    companion object {
        const val EXTRA_PACK = "pack"          // the data pack folder (the port's output, shared storage)
        const val EXTRA_HOST = "host"          // the game's libdmplay.so in the app's private folder
        const val EXTRA_TITLE_ID = "title_id"  // keeps the saves, the temp and the log apart per game
        const val EXTRA_NAME = "name"
        const val EXTRA_EXIT = "exit"

        /** a start already happened in this process: a recreated activity takes the running game's window over */
        @Volatile var hostRunning = false

        /** ends a running game from the app side (singleTask: the intent lands in onNewIntent) */
        fun stop(context: Context) {
            val i = Intent(context, GameActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(EXTRA_EXIT, true)
            context.startActivity(i)
        }

        /** the game again (back from the app): the running activity comes to the front */
        fun resume(context: Context) {
            context.startActivity(Intent(context, GameActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** 界面语言：toast 与抽屉里的词从这里取 */
    private val locale: AppLocale get() = (application as NxApp).container.locale

    private var paused by mutableStateOf(false)
    private var drawerOpen by mutableStateOf(false)
    private var fgSettings: FrameGenSettings? = null
    private var packPath: String? = null
    private lateinit var padView: PadView

    private fun immersive() {
        window.decorView   // the decor must exist: the insets controller hangs off it (a plain Activity has none until it is asked for)
        if (Build.VERSION.SDK_INT >= 30) window.insetsController?.let {
            it.hide(WindowInsets.Type.systemBars())
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else @Suppress("DEPRECATION") window.decorView.systemUiVisibility =
            (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val frame = FrameLayout(this)
        val surface = SurfaceView(this)
        surface.holder.addCallback(this)
        frame.addView(surface, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        // 虚拟手柄绘制层（android-virtual-pad）：画在游戏画面之上、抽屉之下；不消费触点——命中与分流在宿主
        packPath = intent.getStringExtra(EXTRA_PACK)
        padView = PadView(this, PrefsStore(this), packPath?.let { java.io.File(it) })
        frame.addView(padView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        // the drawer's layer: a ComposeView over the game's surface (the composition needs the activity's owners on the view)
        val compose = ComposeView(this)
        compose.setViewTreeLifecycleOwner(this)
        compose.setViewTreeViewModelStoreOwner(this)
        compose.setViewTreeSavedStateRegistryOwner(this)
        compose.setContent { NxTheme { ProvideAppLocale(locale) { DrawerRoot() } } }
        frame.addView(compose, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        setContentView(frame)
        immersive()
        if (hostRunning) return   // a recreation (the system rebuilt the activity): the running game takes the new window in surfaceCreated
        if (intent.getBooleanExtra(EXTRA_EXIT, false)) return endGame()
        val pack = intent.getStringExtra(EXTRA_PACK)
        val host = intent.getStringExtra(EXTRA_HOST)
        if (pack.isNullOrEmpty() || host.isNullOrEmpty()) { toast(locale.str(R.string.game_no_game_to_run)); finish(); return }
        try {
            System.load(host)
        } catch (t: Throwable) {
            toast(locale.str(R.string.game_cannot_load_host, t.message))
            finish()
            return
        }
        // the title id keeps this game's saves, temp and log apart from the others' (host_start_common)
        val titleId = intent.getStringExtra(EXTRA_TITLE_ID)?.ifEmpty { null }
        fgSettings = titleId?.let { FrameGenSettings(PrefsStore(this), it) }
        val env = fgSettings?.load()?.envLines(LosslessDll.ensure(this)?.absolutePath) ?: emptyArray()
        if (!Native.start(pack, filesDir.path, titleId, resources.displayMetrics.densityDpi, env, this)) {
            finish()   // the save import asked to end: it happens at the next start
            return
        }
        hostRunning = true
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    /**
     * 宿主的上行通知（front_android.c 的 dm_app_notice）：宿主不知道抽屉，它只说发生了什么。
     * 1 = 玩家点了 ☰（轻点不是拖动）→ 抽屉开关；2 = 宿主打开了自己的面板（文本输入/布局编辑器/存档选择）
     * → 抽屉收起，面板画在游戏画面上，不被抽屉盖住。
     * 通知可能来自宿主的任何线程（触摸那条在主线程，文本输入那条在游戏的线程上），状态只在主线程上改。
     */
    fun onHostNotice(what: Int) {
        runOnUiThread {
            when (what) {
                1 -> drawerOpen = !drawerOpen
                2 -> drawerOpen = false
            }
        }
    }

    /** the drawer's tree: handle, panel, and the paused overlay (the surface keeps presenting through it) */
    @androidx.compose.runtime.Composable
    private fun DrawerRoot() {
        Box(Modifier.fillMaxSize()) {
            if (paused) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null) { paused = false; Native.lifecycle(1) },
                    contentAlignment = androidx.compose.ui.Alignment.Center,
                ) { androidx.compose.material3.Text(locale.str(R.string.drawer_paused_overlay), color = Color.White,
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge) }
            }
            GameDrawer(
                paused = paused,
                open = drawerOpen,
                onOpenChange = { drawerOpen = it },
                settings = fgSettings,
                padShown = { padView.shown },
                onPadShown = { padView.shown = it },
                padOpacity = { padView.opacity },
                onPadOpacity = { padView.opacity = it },
                onPauseResume = {
                    paused = !paused
                    Native.lifecycle(if (paused) 0 else 1)
                },
                onExit = {
                    drawerOpen = false
                    endGame()
                },
            )
        }
    }

    // ---- the surface: the window passes over owned, and dropping it destroys the swapchain before it returns ----
    override fun surfaceCreated(h: SurfaceHolder) = Native.setSurface(h.surface)
    override fun surfaceChanged(h: SurfaceHolder, format: Int, width: Int, height: Int) = Native.setSurface(h.surface)
    override fun surfaceDestroyed(h: SurfaceHolder) = Native.setSurface(null)

    // ---- input: raw snapshots, the shared core classifies (jni_android.c) ----
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val n = minOf(e.pointerCount, 16)
        val ids = IntArray(n) { e.getPointerId(it) }
        val xs = FloatArray(n) { e.getX(it) }
        val ys = FloatArray(n) { e.getY(it) }
        Native.motion(e.source, e.deviceId, e.action, ids, xs, ys, axes(e))
        return true
    }

    override fun onGenericMotionEvent(e: MotionEvent): Boolean {
        Native.motion(e.source, e.deviceId, e.action, IntArray(0), FloatArray(0), FloatArray(0), axes(e))
        return true
    }

    private fun axes(e: MotionEvent): FloatArray = floatArrayOf(
        e.getAxisValue(MotionEvent.AXIS_X), e.getAxisValue(MotionEvent.AXIS_Y), e.getAxisValue(MotionEvent.AXIS_Z), e.getAxisValue(MotionEvent.AXIS_RZ),
        e.getAxisValue(MotionEvent.AXIS_LTRIGGER), e.getAxisValue(MotionEvent.AXIS_RTRIGGER), e.getAxisValue(MotionEvent.AXIS_HAT_X), e.getAxisValue(MotionEvent.AXIS_HAT_Y),
        e.getAxisValue(MotionEvent.AXIS_BRAKE), e.getAxisValue(MotionEvent.AXIS_GAS))

    /**
     * everything is consumed here (as the NativeActivity's queue did). Back goes to the frontmost thing:
     * the drawer closes it, the host's own panel (text entry, layout editor, save picker) steps back in it
     * (dm_menu_state: -1 closed, else a panel is up), and with nothing up Back opens the drawer.
     */
    override fun dispatchKeyEvent(e: KeyEvent): Boolean {
        if (e.keyCode == KeyEvent.KEYCODE_BACK && !paused) {
            if (e.action == KeyEvent.ACTION_DOWN) {
                val hostPanel = !drawerOpen && hostPanelUp()   // 抽屉在前面时，Back 归抽屉
                when {
                    drawerOpen -> drawerOpen = false
                    hostPanel -> Native.key(e.deviceId, e.source, e.keyCode, e.action, e.metaState, e.repeatCount)   // 宿主的面板在后退一步
                    else -> drawerOpen = true
                }
            }
            return true   // Back 只在宿主的面板开着时交给宿主
        }
        if (e.action == KeyEvent.ACTION_DOWN || e.action == KeyEvent.ACTION_UP)
            Native.key(e.deviceId, e.source, e.keyCode, e.action, e.metaState, e.repeatCount)
        return true
    }

    /** 宿主自己的面板开着吗（文本输入、布局编辑器、存档选择；dm_menu_state: -1 是关着的） */
    private fun hostPanelUp(): Boolean =
        runCatching { Native.menuState().substringBefore(' ').toIntOrNull() ?: -1 }.getOrDefault(-1) != -1

    // ---- lifecycle: the same dance as the NativeActivity's callbacks ----
    override fun onResume() { super.onResume(); Native.lifecycle(1) }

    /** The phone's language changed (configChanges holds it): the app's words are read again */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        locale.configurationChanged()
    }
    override fun onPause() { super.onPause(); Native.lifecycle(0) }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersive()
        Native.lifecycle(if (hasFocus) 2 else 3)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_EXIT, false)) endGame()
    }

    /** the game stays up in the background (the app can come back to it); leaving ends the process, the host cannot run twice in one */
    private fun endGame() {
        finish()
        Handler(Looper.getMainLooper()).postDelayed({ Process.killProcess(Process.myPid()) }, 300)
    }
}
