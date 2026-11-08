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
import com.nx.android.appstate.LibraryState
import com.nx.android.appstate.OnboardingState
import com.nx.android.appstate.PortState
import com.nx.android.data.ApkProvider
import com.nx.android.data.AppLog
import com.nx.android.data.Env
import com.nx.android.data.Picker
import com.nx.android.data.PrefsStore
import com.nx.android.ui.AppActions
import com.nx.android.ui.AppRoot
import java.io.File

/**
 * The shell: it owns what only an activity can own — the storage permission, the system pickers, the APK
 * install — and puts the screen up. Everything long-lived comes from the AppContainer (NxApp); the screens
 * take state holders and action lambdas, never this activity.
 */
class MainActivity : ComponentActivity() {

    companion object {
        /** The game files the app's own picker list shows (the system picker's fallback) */
        val GAME_EXTS = listOf(".nsp", ".xci", ".nsz", ".xcz")
        const val EXTRA_TAB = "tab"   // the notification's tap lands on the builds page
    }

    private val container by lazy { (application as NxApp).container }

    /** Which tab the screen opens on (the notification's tap says the builds page) */
    var launchTab = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container.storageOk = storageGranted()
        launchTab = intent?.getIntExtra(EXTRA_TAB, 0) ?: 0
        if (!container.prefs.bool(PrefsStore.OB_COMPLETED)) container.onboarding.open()
        AppLog.init(this)
        container.port.attach()   // a port the system killed shows up as interrupted
        setContent { AppRoot(container, actions, launchTab) }
        // the small tools come with the app: an update brings new ones, and the phone has to get them
        Thread { container.env.refreshTools() }.start()
    }

    /** The screens' way back to the activity's own privileges: the actions they may ask for, nothing else */
    private val actions = AppActions(
        onAskStorage = { askStorage() },
        storageOk = { container.storageOk },
        onPickKeys = { cb -> pickKeys(cb) },
        onPickFolder = { start, cb -> pickFolder(start, cb) },
        onPickLibraryFolder = { cb -> pickLibraryFolder(cb) },
        onStartPort = { file, cfg ->
            PortService.start(this, file.absolutePath, container.prefs.keys, container.prefs.out,
                              cfg.touch, cfg.fps60, cfg.experimental)
        },
        onCancelPort = { PortService.cancel(this) },
        onInstall = { install(it) },
        onToast = { toast(it) },
        version = { versionName() },
        keys = { container.prefs.keys },
        out = { container.prefs.out },
        onSetOut = { container.prefs.writeOut(it) },
        onShareLog = {
            val text = AppLog.text()
            if (text.isNotEmpty()) {
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, text), "分享日志"))
            } else {
                toast("日志还是空的")
            }
        },
        onClearLog = {
            AppLog.clear()
            toast("日志已清空")
        },
        readLog = { AppLog.text(2000) },
    )

    override fun onResume() {
        super.onResume()
        container.storageOk = storageGranted()   // the permission may have been given in the background
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
                toast("这台设备没有可用的权限设置页")
            }
        }
    }

    // ---- the pickers (the activity's own privilege) -------------------------------------------------------------

    /** The keys file: the system picker first, the app's own list where there is none. The saved path follows */
    fun pickKeys(onPick: (String) -> Unit = {}) {
        try {
            picker.file { path -> container.prefs.writeKeys(path); onPick(path) }
        } catch (e: Exception) {
            // this tablet has no app that handles the system picker: the app's own list takes over
            toast("系统没有文件选择器，用应用内的列表")
            pickOwn(container.prefs.keys, false) { path -> container.prefs.writeKeys(path); onPick(path) }
        }
    }

    /** A folder on the card (the game folder, the output folder) */
    fun pickFolder(start: String, onPick: (String) -> Unit) {
        try {
            picker.tree(start, onPick)
        } catch (e: Exception) {
            toast("系统没有文件选择器，用应用内的列表")
            pickOwn(start, true) { path -> onPick(path) }
        }
    }

    /** The library's game folder: the picker comes back with the folder, the library rescans */
    fun pickLibraryFolder(onPick: (String) -> Unit) {
        val start = container.library.folder.trim().ifEmpty { "/sdcard" }
        pickFolder(start) { dir -> onPick(dir) }
    }

    /** The app's own folder list: the folders of a path, and the files of it */
    private fun pickOwn(start: String, foldersOnly: Boolean, onPick: (String) -> Unit) {
        val path = start.trim().ifEmpty { "/sdcard" }
        val names = ArrayList<String>()
        val paths = ArrayList<String>()
        try {
            val f = File(path)
            if (f.isDirectory) {
                names.add(".."); paths.add(f.parentFile?.absolutePath ?: path)
                f.listFiles()?.sortedBy { it.name }?.forEach {
                    if (it.isHidden) return@forEach
                    if (foldersOnly && !it.isDirectory) return@forEach
                    if (!foldersOnly && !it.isDirectory && GAME_EXTS.none { ext -> it.name.endsWith(ext) }) return@forEach
                    names.add(it.name); paths.add(it.absolutePath)
                }
            }
        } catch (e: Exception) {
        }
        android.app.AlertDialog.Builder(this)
            .setTitle(path)
            .setItems(names.toTypedArray()) { _, which ->
                if (names[which] == "..") pickOwn(paths[which], foldersOnly, onPick)
                else if (foldersOnly) pickOwn(paths[which], foldersOnly, onPick)
                else onPick(paths[which])
            }
            .setPositiveButton("用这个") { _, _ -> onPick(path) }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        try {
            picker.onResult(requestCode, resultCode, data)
        } catch (e: Exception) {
            toast("选择失败：${e.message}")
        }
    }

    // ---- after the port (the activity's own privilege) -----------------------------------------------------------

    /** Installs the port: the APK goes through the app's provider to the system installer (the shared storage's
     * own permissions are not the app's to hand out, and the installer could not read them) */
    fun install(apk: File) {
        if (!apk.exists()) { toast("找不到 ${apk.absolutePath}"); return }
        val local = File(filesDir, "install.apk")
        try {
            apk.inputStream().use { src -> local.outputStream().use { src.copyTo(it) } }
        } catch (e: Exception) {
            toast("复制 APK 失败：${e.message}"); return
        }
        val uri = ApkProvider.uriFor(this, local)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            toast("系统没有安装器：${apk.absolutePath}")
        }
    }

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
    val storageOk: Boolean get() = container.storageOk
    private val picker by lazy { Picker(this) }
}
