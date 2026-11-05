package com.nx.android

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

/**
 * The phone's screen: the tool's own steps, with the porter's output as the log.
 *
 * The screen is Android's, not a guest window: the porter is a command-line program, and its output is the
 * screen. Everything runs in the app's own folder (Env).
 */
class MainActivity : Activity() {

    companion object { val GAME_EXTS = listOf(".nsp", ".xci", ".nsz", ".xcz") }

    private lateinit var env: Env
    private lateinit var picker: Picker
    private val ui = Handler(Looper.getMainLooper())

    private lateinit var status: TextView
    private lateinit var keys: EditText
    private lateinit var out: EditText
    private lateinit var folder: EditText
    private lateinit var touch: CheckBox
    private lateinit var fps60: CheckBox
    private lateinit var experimental: CheckBox
    private lateinit var portButton: Button
    private lateinit var cancelButton: Button
    private lateinit var toolsButton: Button
    private lateinit var storageButton: Button
    private lateinit var progress: ProgressBar
    private lateinit var logView: TextView
    private lateinit var installButton: Button
    private lateinit var copyButton: Button

    private var apkPath = ""
    private var dataPath = ""
    private var packageName_ = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        env = Env(this)
        picker = Picker(this)
        setContentView(buildUi())
        loadState()
        refresh()
    }

    // ---- what the user typed stays ----

    private val prefs get() = getSharedPreferences("nx2a", MODE_PRIVATE)

    /** The paths and the boxes are remembered: the app is restarted more than once during a port */
    private fun loadState() {
        keys.setText(prefs.getString("keys", keys.text.toString()))
        out.setText(prefs.getString("out", out.text.toString()))
        folder.setText(prefs.getString("game", folder.text.toString()))
        touch.isChecked = prefs.getBoolean("touch", touch.isChecked)
        fps60.isChecked = prefs.getBoolean("fps60", fps60.isChecked)
        experimental.isChecked = prefs.getBoolean("experimental", experimental.isChecked)
    }

    private fun saveState() {
        prefs.edit().putString("keys", keys.text.toString().trim()).putString("out", out.text.toString().trim())
            .putString("game", folder.text.toString().trim()).putBoolean("touch", touch.isChecked)
            .putBoolean("fps60", fps60.isChecked).putBoolean("experimental", experimental.isChecked).apply()
    }

    // ---- the screen ----------------------------------------------------------------------------------------

    private fun buildUi(): ViewGroup {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        val scroll = ScrollView(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
        }
        scroll.addView(box)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        status = text("", 14f)
        box.addView(status)

        storageButton = button("授予存储访问权限（读取 SD 卡）") { askStorage() }
        box.addView(storageButton)

        val prepare = button("准备环境（解压 rootfs 与工具）") { unpack() }
        val reset = button("重置环境（重新解压）") {
            env.reset()
            toast("已重置：再点一次「准备环境」")
        }
        box.addView(prepare)
        box.addView(reset)

        box.addView(label("prod.keys（自己的 Switch 导出的）"))
        keys = input("/sdcard/switch/prod.keys")
        box.addView(row(keys, button("浏览") { pickFile { path -> keys.setText(path) } }))

        box.addView(label("输出目录"))
        out = input("/sdcard/Switch/Ports")
        box.addView(row(out, button("浏览") { pickFolder(out.text.toString()) { path -> out.setText(path) } }))

        box.addView(label("游戏文件（NSP / XCI / NSZ / XCZ）"))
        folder = input("")
        box.addView(row(folder, button("浏览") { pickGame() }))

        touch = check("触屏游戏（隐藏手柄）")
        fps60 = check("60 帧")
        experimental = check("实验性移植（还没适配的游戏）")
        box.addView(touch); box.addView(fps60); box.addView(experimental)

        toolsButton = button("检查工具（NDK / .NET / Python）") { tools() }
        portButton = button("开始移植") { port() }
        cancelButton = button("取消移植") { cancel() }
        cancelButton.visibility = android.view.View.GONE
        box.addView(toolsButton)
        box.addView(portButton)
        box.addView(cancelButton)

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        box.addView(progress)

        installButton = button("安装 APK") { install() }
        copyButton = button("复制数据包到游戏目录") { copyData() }
        installButton.visibility = android.view.View.GONE
        copyButton.visibility = android.view.View.GONE
        box.addView(installButton)
        box.addView(copyButton)

        logView = text("", 12f)
        val logScroll = ScrollView(this).apply {
            addView(logView)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                (260 * resources.displayMetrics.density).toInt())
        }
        box.addView(logScroll)
        return root
    }

    private fun text(s: String, size: Float) = TextView(this).apply {
        text = s
        textSize = size
        setPadding(0, 8, 0, 8)
    }

    private fun label(s: String) = text(s, 13f).apply { setTypeface(typeface, android.graphics.Typeface.BOLD) }

    private fun input(value: String) = EditText(this).apply {
        setText(value)
        textSize = 14f
        inputType = InputType.TYPE_CLASS_TEXT
        setSingleLine()
    }

    private fun check(s: String) = CheckBox(this).apply { text = s; textSize = 14f }

    private fun button(s: String, onClick: () -> Unit) = Button(this).apply {
        text = s
        textSize = 14f
        setOnClickListener { onClick() }
    }

    private fun row(vararg views: android.view.View): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        views.forEach { addView(it, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)) }
    }

    // ---- the storage ----------------------------------------------------------------------------------------

    /** The shared storage: the game files and the ports the tool makes. From Android 11 the old permissions are
     * not enough: the app needs the all-files access, which the user gives once */
    private fun storageOk(): Boolean {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager()
        return checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    private fun askStorage() {
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
        val ok = env.ready
        status.text = if (ok) "环境就绪" else "环境还没准备好（第一次要解压约 1.6 GB）"
        if (!storageOk()) status.text = status.text.toString() + "；还没有存储访问权限"
        storageButton.visibility = if (storageOk()) android.view.View.GONE else android.view.View.VISIBLE
        portButton.isEnabled = ok && storageOk()
        toolsButton.isEnabled = ok
    }

    private var unpacking = false

    /** What the port is doing now: the stage, its step and how far it is */
    private var stageName = ""
    private var stageStep = ""
    private var stageOf = 0
    private var stages = 0
    private var ticking = false

    /** The clock of the running port: the elapsed time moves on its own */
    private val ticker = android.os.Handler(android.os.Looper.getMainLooper())
    private var startedAt = 0L

    private fun tick() {
        ticker.postDelayed({
            if (startedAt > 0 && stageOf > 0) showStatus(startedAt)
            tick()
        }, 1000)
    }

    /** The line under the buttons: the stage, its step and the time it has taken */
    private fun showStatus(started: Long) {
        val secs = (System.currentTimeMillis() - started) / 1000
        val where = if (stageOf > 0) "阶段 $stageOf/$stages：" else ""
        val what = if (stageStep.isEmpty()) stageName else "$stageName · $stageStep"
        status.text = "$where$what  （${secs / 60} 分 ${secs % 60} 秒）"
        if (stageOf > 0 && stages > 0 && progress.progress < (stageOf - 1) * 100 / stages)
            progress.progress = (stageOf - 1) * 100 / stages
    }

    private fun unpack() {
        if (unpacking) { toast("正在准备中"); return }
        unpacking = true
        Thread {
            try {
                env.unpack { step -> ui.post { status.text = "正在准备：$step" } }
                ui.post {
                    unpacking = false
                    status.text = "环境就绪${if (env.lastCheck.isEmpty()) "" else "（${env.lastCheck}）"}"
                    portButton.isEnabled = storageOk()
                    toolsButton.isEnabled = true
                }
            } catch (e: Exception) {
                // the whole failure in the log: the next round should not need the device to find out
                ui.post {
                    unpacking = false
                    status.text = "准备失败：${e.message}"
                    say("准备失败：${e.javaClass.name}: ${e.message}")
                    e.stackTrace.take(12).forEach { say("    at $it") }
                }
            }
        }.start()
    }

    // ---- the tool ------------------------------------------------------------------------------------------

    private fun tools() {
        say("检查工具…\n")
        Thread {
            try {
                // the .NET SDK and the compiler are Linux programs: their interpreter path has to point at our
                // rootfs, or they cannot start at all
                say("改写解释器路径…\n")
                env.patchInterpreters()
                ui.post { say("好了\n") }
                runGuest(listOf("-m", "porter", "doctor")) { line -> ui.post { say(line) } }
            } catch (e: Exception) {
                ui.post { say("失败：${e.message}") }
            }
        }.start()
    }

    private fun port() {
        val f = folder.text.toString().trim()
        if (f.isEmpty()) { toast("先选游戏文件"); return }
        val k = keys.text.toString().trim()
        if (k.isEmpty()) { toast("先填 prod.keys 的路径"); return }
        if (!storageOk()) { toast("还没有存储访问权限"); return }
        apkPath = ""; dataPath = ""
        installButton.visibility = android.view.View.GONE
        copyButton.visibility = android.view.View.GONE
        val args = mutableListOf("-m", "porter", "port", f, "-k", k, "-o", out.text.toString().trim(), "--progress", "json")
        if (touch.isChecked) args += "--touch"
        if (fps60.isChecked) args += "--fps60"
        if (experimental.isChecked) args += "--experimental"
        saveState()
        say("移植开始：$f\n")
        portButton.isEnabled = false
        cancelButton.visibility = android.view.View.VISIBLE
        val started = System.currentTimeMillis()
        startedAt = started
        stageName = ""; stageStep = ""; stageOf = 0; stages = 0
        if (!ticking) { ticking = true; tick() }
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
                                showStatus(started)
                            }
                            "progress" -> {
                                val frac = ev.optDouble("frac", -1.0)
                                if (frac >= 0) progress.progress = (frac * 100).toInt()
                                val name = ev.optString("name"); val step = ev.optString("step")
                                if (name.isNotEmpty()) stageName = name
                                if (step.isNotEmpty() && step != stageStep) { stageStep = step; say("  · $stageName $step") }
                                showStatus(started)
                            }
                            "result" -> {
                                apkPath = ev.optString("apk"); dataPath = ev.optString("data")
                                packageName_ = ev.optString("package")
                                installButton.visibility = android.view.View.VISIBLE
                                copyButton.visibility = android.view.View.VISIBLE
                                progress.progress = 100
                                status.text = "完成：$apkPath"
                            }
                        }
                    }
                }
                ui.post {
                    status.text = if (apkPath.isEmpty()) "移植结束（没有产出 APK）：看看上面的日志" else "完成：$apkPath"
                    portButton.isEnabled = true
                    cancelButton.visibility = android.view.View.GONE
                    startedAt = 0
                }
            } catch (e: Exception) {
                ui.post {
                    status.text = "失败：${e.message}"; say("失败：${e.message}")
                    portButton.isEnabled = true
                    cancelButton.visibility = android.view.View.GONE
                    startedAt = 0
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
        for (d in java.io.File("/proc").listFiles() ?: return) {
            if (!d.name.all { it.isDigit() }) continue
            val pid = d.name.toLong()
            if (pid == me.toLong()) continue
            try {
                val cmd = java.io.File(d, "cmdline").readText()
                if (cmd.contains("python3.13") || cmd.contains("dotnet") || cmd.contains("ld-linux") ||
                    cmd.contains("clang") || cmd.contains("nx_extract")) {
                    android.os.Process.killProcess(pid.toInt())
                }
            } catch (e: Exception) {
            }
        }
    }

    /** Stops the port: the guest program and the tools it started */
    private fun cancel() {
        val p = running ?: return
        try {
            p.destroyForcibly()
        } catch (e: Exception) {
        }
        killGuests()
        running = null
        say("已取消\n")
        status.text = "已取消"
        portButton.isEnabled = true
        cancelButton.visibility = android.view.View.GONE
        startedAt = 0
    }

    // ---- the pickers ----------------------------------------------------------------------------------------

    private fun pickFile(onPick: (String) -> Unit) {
        try {
            picker.file(onPick)
        } catch (e: Exception) {
            // this tablet has no app that handles the system picker: the app's own list takes over
            say("系统没有文件选择器，用应用内的列表\n")
            pickOwn(keys.text.toString(), false) { path -> keys.setText(path); saveState() }
        }
    }

    private fun pickFolder(start: String, onPick: (String) -> Unit) {
        try {
            picker.tree(start, onPick)
        } catch (e: Exception) {
            say("系统没有文件选择器，用应用内的列表\n")
            pickOwn(start, true) { path -> onPick(path) }
        }
    }

    /**
     * The game file: the folder is picked first (a real path on the card), then the game file in it. The
     * updates and the DLC next to it are found by the tool itself.
     */
    private fun pickGame() {
        val start = folder.text.toString().trim().ifEmpty { "/sdcard" }
        pickFolder(start) { dir -> pickOwn(dir, false, true) { file -> folder.setText(file); saveState() } }
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
        val items = names.map { it }.toTypedArray()
        android.app.AlertDialog.Builder(this)
            .setTitle(path)
            .setItems(items) { _, which ->
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
            if (picker.onResult(requestCode, resultCode, data)) say("已选择\n")
        } catch (e: Exception) {
            say("选择失败：${e.message}\n")
        }
    }

    // ---- after the port ------------------------------------------------------------------------------------

    private fun install() {
        if (apkPath.isEmpty()) return
        val apk = File(apkPath)
        if (!apk.exists()) { toast("找不到 $apkPath"); return }
        val uri = ApkProvider.uriFor(this, apk)
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
    private fun copyData() {
        if (dataPath.isEmpty() || packageName_.isEmpty()) return
        Thread {
            try {
                val src = File(dataPath)
                val dst = File("/sdcard/Android/data/$packageName_/files")
                dst.mkdirs()
                src.copyRecursively(dst, overwrite = true)
                ui.post { toast("数据包已复制到 ${dst.absolutePath}") }
            } catch (e: Exception) {
                ui.post { toast("复制失败：${e.message}") }
            }
        }.start()
    }

    // ---- the log --------------------------------------------------------------------------------------------

    /** A line in the log. It may come from a worker thread: the view belongs to the main thread */
    private fun say(s: String) {
        ui.post {
            logView.append(s + "\n")
            // a long port writes a lot: the log keeps the last lines
            val lines = logView.text.toString().split("\n")
            if (lines.size > 400) logView.text = lines.takeLast(300).joinToString("\n")
            val scroll = logView.parent as? ScrollView
            scroll?.post { scroll.fullScroll(android.view.View.FOCUS_DOWN) }
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
