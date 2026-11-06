package com.nx.android

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File

/**
 * The phone's screen: the tool's own steps, with the porter's output as the log.
 *
 * The screen is Jetpack Compose (Screen.kt); the porter is a command-line program, and its output is the
 * screen. Everything runs in the app's own folder (Env).
 */
class MainActivity : ComponentActivity() {

    companion object { val GAME_EXTS = listOf(".nsp", ".xci", ".nsz", ".xcz") }

    private lateinit var env: Env
    private lateinit var picker: Picker
    private val ui = Handler(Looper.getMainLooper())

    // ---- what the screen draws ----
    var status by mutableStateOf("")
    var stageText by mutableStateOf("")
    var progress by mutableStateOf(0f)
    var envReady by mutableStateOf(false)
    var storageOk by mutableStateOf(false)
    var unpacking by mutableStateOf(false)
    var porting by mutableStateOf(false)
    var hasResult by mutableStateOf(false)
    var log by mutableStateOf(listOf<String>())

    var keys by mutableStateOf("/sdcard/switch/prod.keys")
    var out by mutableStateOf("/sdcard/Switch/Ports")
    var game by mutableStateOf("")
    var driverDir by mutableStateOf("/sdcard/Switch/Drivers")
    var touch by mutableStateOf(false)
    var fps60 by mutableStateOf(false)
    var experimental by mutableStateOf(false)

    var apkPath = ""
    private var dataPath = ""
    private var packageName_ = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        env = Env(this)
        picker = Picker(this)
        loadState()
        setContent { AppScreen(this) }
        refresh()
        // the small tools come with the app: an update brings new ones, and the phone has to get them
        // (a phone with an old prebuilt translator would try to build one and wait for a network)
        Thread { env.refreshTools() }.start()
    }

    override fun onResume() {
        super.onResume()
        refresh()   // the storage permission may have been given while the app was in the background
    }

    // ---- what the user typed stays ----

    private val prefs get() = getSharedPreferences("nx2a", MODE_PRIVATE)

    /** The paths and the boxes are remembered: the app is restarted more than once during a port */
    private fun loadState() {
        keys = prefs.getString("keys", keys) ?: keys
        out = prefs.getString("out", out) ?: out
        game = prefs.getString("game", game) ?: game
        driverDir = prefs.getString("driverDir", driverDir) ?: driverDir
        touch = prefs.getBoolean("touch", touch)
        fps60 = prefs.getBoolean("fps60", fps60)
        experimental = prefs.getBoolean("experimental", experimental)
    }

    private fun saveState() {
        prefs.edit().putString("keys", keys.trim()).putString("out", out.trim()).putString("game", game.trim())
            .putString("driverDir", driverDir.trim())
            .putBoolean("touch", touch).putBoolean("fps60", fps60).putBoolean("experimental", experimental).apply()
    }

    // ---- the storage ----------------------------------------------------------------------------------------

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

    // ---- the environment -----------------------------------------------------------------------------------

    private fun refresh() {
        envReady = env.ready
        storageOk = storageGranted()
        if (!porting && !unpacking) {
            status = when {
                !storageOk -> "环境已就绪；还没有存储访问权限"
                envReady -> "环境就绪${if (env.lastCheck.isEmpty()) "" else "（${env.lastCheck}）"}"
                else -> "环境还没准备好（第一次要解压约 1.6 GB）"
            }
        }
    }

    /** What the port is doing now: the stage, its step and how far it is */
    private var stageName = ""
    private var stageStep = ""
    private var stageOf = 0
    private var stages = 0
    private var startedAt = 0L

    /** The clock of the running port: the elapsed time moves on its own */
    private val ticker = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            if (startedAt > 0 && stageOf > 0) showStatus()
            ticker.postDelayed(this, 1000)
        }
    }

    /** The line under the buttons: the stage, its step and the time it has taken */
    private fun showStatus() {
        val secs = (System.currentTimeMillis() - startedAt) / 1000
        val where = if (stageOf > 0) "阶段 $stageOf/$stages：" else ""
        val what = if (stageStep.isEmpty()) stageName else "$stageName · $stageStep"
        stageText = "$where$what　（${secs / 60} 分 ${secs % 60} 秒）"
        if (stageOf > 0 && stages > 0 && progress < (stageOf - 1) * 100f / stages)
            progress = (stageOf - 1) * 100f / stages
    }

    fun unpack() {
        if (unpacking) { toast("正在准备中"); return }
        unpacking = true
        status = "正在准备…"
        Thread {
            try {
                env.unpack { step -> ui.post { status = "正在准备：$step" } }
                ui.post {
                    unpacking = false
                    envReady = env.ready
                    status = "环境就绪${if (env.lastCheck.isEmpty()) "" else "（${env.lastCheck}）"}"
                }
            } catch (e: Exception) {
                // the whole failure in the log: the next round should not need the device to find out
                ui.post {
                    unpacking = false
                    status = "准备失败：${e.message}"
                    say("准备失败：${e.javaClass.name}: ${e.message}")
                    e.stackTrace.take(12).forEach { say("    at $it") }
                }
            }
        }.start()
    }

    fun resetEnv() {
        env.reset()
        toast("已重置：再点一次「准备环境」")
    }

    // ---- the tool ------------------------------------------------------------------------------------------

    fun tools() {
        say("检查工具…")
        Thread {
            try {
                // the .NET SDK and the compiler are Linux programs: their interpreter path has to point at our
                // rootfs, or they cannot start at all
                say("改写解释器路径…")
                env.patchInterpreters()
                ui.post { say("好了") }
                // the keys are given on the command line: the doctor would look at the saved setting and
                // report them as missing
                val k = keys.trim()
                val doctor = if (k.isNotEmpty()) listOf("-m", "porter", "doctor", "-k", k) else listOf("-m", "porter", "doctor")
                runGuest(doctor) { line -> ui.post { say(line) } }
            } catch (e: Exception) {
                ui.post { say("失败：${e.message}") }
            }
        }.start()
    }

    fun port() {
        val f = game.trim()
        if (f.isEmpty()) { toast("先选游戏文件"); return }
        val k = keys.trim()
        if (k.isEmpty()) { toast("先填 prod.keys 的路径"); return }
        if (!storageGranted()) { toast("还没有存储访问权限"); return }
        apkPath = ""; dataPath = ""
        hasResult = false
        val args = mutableListOf("-m", "porter", "port", f, "-k", k, "-o", out.trim(), "--progress", "json")
        // the Vulkan drivers the game's launcher offers: a folder of .so libraries (the APK carries them all)
        val drv = driverDir.trim()
        if (drv.isNotEmpty()) args += listOf("--vulkan-driver-dir", drv)
        if (touch) args += "--touch"
        if (fps60) args += "--fps60"
        if (experimental) args += "--experimental"
        saveState()
        say("移植开始：$f")
        porting = true
        progress = 0f
        startedAt = System.currentTimeMillis()
        stageName = ""; stageStep = ""; stageOf = 0; stages = 0
        ticker.removeCallbacks(tick); ticker.post(tick)
        Thread {
            try {
                runGuest(args) { line ->
                    ui.post {
                        val ev = line.trim().takeIf { it.startsWith("{") }?.let { runCatching { org.json.JSONObject(it) }.getOrNull() }
                        if (ev == null) { say(line); return@post }
                        when (ev.optString("ev")) {
                            "say" -> say(ev.optString("msg"))
                            "stage" -> {
                                stageOf = ev.optInt("k"); stages = ev.optInt("n")
                                stageName = ev.optString("name"); stageStep = ""
                                say("▶ 阶段 $stageOf/$stages：$stageName")
                                showStatus()
                            }
                            "progress" -> {
                                val frac = ev.optDouble("frac", -1.0)
                                if (frac >= 0) progress = (frac * 100).toInt().toFloat()
                                val name = ev.optString("name"); val step = ev.optString("step")
                                if (name.isNotEmpty()) stageName = name
                                if (step.isNotEmpty() && step != stageStep) { stageStep = step; say("  · $stageName $step") }
                                showStatus()
                            }
                            "result" -> {
                                apkPath = ev.optString("apk"); dataPath = ev.optString("data")
                                packageName_ = ev.optString("package")
                                hasResult = true
                                progress = 1f
                                status = "完成：$apkPath"
                            }
                        }
                    }
                }
                ui.post {
                    porting = false
                    startedAt = 0
                    status = if (apkPath.isEmpty()) "移植结束（没有产出 APK）：看看日志" else "完成：$apkPath"
                }
            } catch (e: Exception) {
                ui.post {
                    porting = false
                    startedAt = 0
                    status = "失败：${e.message}"
                    say("失败：${e.message}")
                }
            }
        }.start()
    }

    /** Runs a guest program and hands every line of its output to [onLine] */
    private fun runGuest(args: List<String>, onLine: (String) -> Unit) {
        try {
            val p = env.guest(args)
            running = p
            p.inputStream.bufferedReader().forEachLine { onLine(it) }
        } catch (e: Exception) {
            onLine("[RED] ${e.message}")
        } finally {
            running = null
        }
    }

    /** The guest program that runs now (the port): the cancel button stops it and what it started */
    private var running: Process? = null

    /**
     * Stops the guest programs: the porter and the tools it started (the dotnet build, the compiler). They are
     * the app's own processes, so they can be stopped one by one.
     */
    private fun killGuests() {
        val me = android.os.Process.myPid()
        for (d in File("/proc").listFiles() ?: return) {
            if (!d.name.all { it.isDigit() }) continue
            val pid = d.name.toLong()
            if (pid == me.toLong()) continue
            try {
                val cmd = File(d, "cmdline").readText()
                if (cmd.contains("python3.13") || cmd.contains("dotnet") || cmd.contains("ld-linux") ||
                    cmd.contains("clang") || cmd.contains("nx_extract")) {
                    android.os.Process.killProcess(pid.toInt())
                }
            } catch (e: Exception) {
            }
        }
    }

    /** Stops the port: the guest program and the tools it started */
    fun cancel() {
        val p = running ?: return
        try {
            p.destroyForcibly()
        } catch (e: Exception) {
        }
        killGuests()
        running = null
        say("已取消")
        status = "已取消"
        porting = false
        startedAt = 0
    }

    // ---- the pickers ----------------------------------------------------------------------------------------

    fun pickKeys() {
        try {
            picker.file { path -> keys = path; saveState() }
        } catch (e: Exception) {
            // this tablet has no app that handles the system picker: the app's own list takes over
            say("系统没有文件选择器，用应用内的列表")
            pickOwn(keys, false) { path -> keys = path; saveState() }
        }
    }

    fun pickOutput() {
        pickFolder(out) { path -> out = path; saveState() }
    }

    /**
     * The game file: the folder is picked first (a real path on the card), then the game file in it. The
     * updates and the DLC next to it are found by the tool itself.
     */
    /** The folder with the Vulkan drivers the game offers (the APK carries them all; the game's launcher picks one) */
    fun pickDriverDir() {
        pickFolder(driverDir) { path -> driverDir = path; saveState() }
    }

    fun pickGame() {
        val start = game.trim().ifEmpty { "/sdcard" }
        pickFolder(start) { dir -> pickOwn(dir, false, true) { file -> game = file; saveState() } }
    }

    private fun pickFolder(start: String, onPick: (String) -> Unit) {
        try {
            picker.tree(start, onPick)
        } catch (e: Exception) {
            say("系统没有文件选择器，用应用内的列表")
            pickOwn(start, true) { path -> onPick(path) }
        }
    }

    /** The app's own folder list: the folders of a path, and the files of it */
    private fun pickOwn(start: String, foldersOnly: Boolean, gameFiles: Boolean = false, onPick: (String) -> Unit) {
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
                    if (gameFiles && !it.isDirectory && GAME_EXTS.none { ext -> it.name.endsWith(ext) }) return@forEach
                    names.add(it.name); paths.add(it.absolutePath)
                }
            }
        } catch (e: Exception) {
        }
        android.app.AlertDialog.Builder(this)
            .setTitle(path)
            .setItems(names.toTypedArray()) { _, which ->
                if (names[which] == "..") pickOwn(paths[which], foldersOnly, gameFiles, onPick)
                else if (foldersOnly) pickOwn(paths[which], foldersOnly, gameFiles, onPick)
                else onPick(paths[which])
            }
            .setPositiveButton("用这个") { _, _ -> onPick(path) }
            .setNegativeButton("取消", null)
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        try {
            if (picker.onResult(requestCode, resultCode, data)) say("已选择")
        } catch (e: Exception) {
            say("选择失败：${e.message}")
        }
    }

    // ---- after the port ------------------------------------------------------------------------------------

    fun install() {
        if (apkPath.isEmpty()) return
        val apk = File(apkPath)
        if (!apk.exists()) { toast("找不到 $apkPath"); return }
        // The installer reads the APK through the app's provider: the file goes into the app's own folder first (the
        // shared storage's own permissions are not the app's to hand out, and the installer could not read them)
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
            toast("系统没有安装器：$apkPath")
        }
    }

    /** Copies the data pack into the game's own folder, where the game reads it */
    // ---- the log --------------------------------------------------------------------------------------------

    /**
     * A line in the log. It may come from a worker thread: the state belongs to the main thread. The log is
     * also written to a file (files/nx2a/log.txt), so that it can be read off the phone when something goes wrong.
     */
    private fun say(s: String) {
        ui.post {
            log = (log + s).takeLast(400)
            try {
                val w = logWriter ?: java.io.BufferedWriter(java.io.FileWriter(logFile(), true)).also { logWriter = it }
                w.write(s + "\n")
                w.flush()
            } catch (e: Exception) {
            }
        }
    }

    private var logWriter: java.io.BufferedWriter? = null

    /** Where the log is written */
    private fun logFile(): java.io.File {
        val f = java.io.File(filesDir, "nx2a/log.txt")
        f.parentFile?.mkdirs()
        return f
    }

    fun clearLog() {
        log = listOf()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
