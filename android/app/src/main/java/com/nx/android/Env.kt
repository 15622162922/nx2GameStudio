package com.nx.android

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import java.util.zip.GZIPInputStream

/**
 * The environment the porter runs in.
 *
 * The app is a shell: it unpacks the rootfs (from the nixport payload), the build tools, the .NET SDK and the tool's
 * own tree into its files folder, and runs the porter inside it. The guest programs are Linux binaries: Android has
 * no /lib and the guest is not a chroot, so they are started through the rootfs's dynamic loader, and the programs
 * the porter starts itself (the compiler, the .NET SDK) get their interpreter path rewritten to the app's own rootfs.
 */
class Env(private val ctx: Context) {

    companion object { const val TAG = "Nx2aEnv" }

    val files = ctx.filesDir
    val rootfs = File(files, "rootfs")
    val tool = File(files, "tool")
    val home = File(files, "home")
    val nxHome = File(files, "nx2a")
    val tmp = File(files, "tmp")

    val loader = File(rootfs, "usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1")
    val python = File(rootfs, "usr/bin/python3.13")
    val interpPath = "${rootfs.absolutePath}/usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1"

    /** The environment is ready when the rootfs, the tool and the compiler are there */
    val ready: Boolean
        get() = python.exists() && File(tool, "porter").isDirectory && File(tool, "toolchain/clang/bin/clang").exists()

    /** The .NET SDK (the extractor and the shader translator are C#): where the porter looks for it */
    val dotnet: File get() = File(nxHome, "toolchains/dotnet/dotnet")

    /** The environment every guest program gets */
    fun guestEnv(): MutableMap<String, String> {
        home.mkdirs(); tmp.mkdirs(); nxHome.mkdirs()
        return mutableMapOf(
            "PATH" to "${tool}/toolchain/clang/bin:${tool}/toolchain/bin:${rootfs}/usr/bin",
            "HOME" to home.absolutePath,
            "TMPDIR" to tmp.absolutePath,
            "PYTHONHOME" to "${rootfs}/usr",
            "PYTHONPATH" to "${tool}/pylibs",
            "LD_LIBRARY_PATH" to "${rootfs}/usr/lib/aarch64-linux-gnu:${rootfs}/usr/lib",
            "NX2A_HOME" to nxHome.absolutePath,
            "NX2A_NDK" to "${tool}/toolchain/ndk",
            "NX2A_REPO" to tool.absolutePath,
            // nx_extract is built on the computer and shipped: the phone's .NET SDK cannot restore packages
            // (no network) and its globalization mode makes the SDK itself crash
            "NX2A_NX_EXTRACT" to "${tool}/tools/nx_extract",
            // the build folder: Android does not let the app run a program on the shared storage, so the
            // port's own tools and the unpacked game live in the app's own folder
            "NX2A_WORK" to "${files}/work",
            "PYTHONUNBUFFERED" to "1",
            "PYTHONIOENCODING" to "utf-8",
            "DOTNET_SYSTEM_GLOBALIZATION_INVARIANT" to "1",
            "DOTNET_EnableWriteXorExecute" to "0",
            "DOTNET_CLI_TELEMETRY_OPTOUT" to "1",
            "DOTNET_SKIP_FIRST_TIME_EXPERIENCE" to "1",
            // the NuGet packages are restored from the app's own folder: the phone has no network
            "NUGET_PACKAGES" to "${nxHome}/nuget",
        )
    }

    /** Starts a guest program: Python through the loader, the tool itself as it is */
    fun guest(args: List<String>, cwd: File = tool): Process {
        val cmd = mutableListOf(loader.absolutePath, python.absolutePath)
        cmd.addAll(args)
        val pb = ProcessBuilder(cmd).directory(cwd)
        // one stream for both: a program that fills the pipe of the one we do not read would wait forever
        pb.redirectErrorStream(true)
        pb.environment().putAll(guestEnv())
        return pb.start()
    }

    // ---- the first start: the environment out of the assets -----------------------------------------------

    /**
     * Unpacks the environment. The rootfs and the .NET SDK are tarballs in the assets; the rest is a folder tree.
     *
     * @param onStep what is being unpacked, for the screen
     */
    fun unpack(onStep: (String) -> Unit) {
        var rootfsDone = false
        try {
            unpackNow(onStep) { rootfsDone = true }
        } catch (e: Exception) {
            // a half unpacked rootfs is worse than none: it goes, so that the next try starts clean. What came
            // out whole stays.
            if (!rootfsDone) rootfs.deleteRecursively()
            throw e
        }
    }

    private fun unpackNow(onStep: (String) -> Unit, rootfsFinished: () -> Unit) {
        fun step(s: String) { Log.i(TAG, "step: $s"); onStep(s) }
        // The environment is already there (the app was updated, not reinstalled): only the check.
        if (ready && dotnet.exists() && marker.exists()) {
            // the environment is already there: only the tool's own scripts and the interpreter paths are new
            copyAssets("tool/tools", File(tool, "tools"))
            step("the interpreter paths")
            patchInterpreters()
            step("the check")
            check()
            return
        }
        if (!python.exists()) {
            step("the Linux environment")
            ctx.assets.open("rootfs.pkg").use { raw ->
                GZIPInputStream(raw).use { gz -> Tar.unpack(gz, rootfs) }
            }
            rootfsFinished()
        }
        if (!File(files, ".nx2a-tool").exists()) {
            step("the tool")
            copyAssets("tool", tool)
            File(files, ".nx2a-tool").writeText("1")
        }
        if (!File(files, ".nx2a-toolchain").exists()) {
            step("the build tools")
            copyAssets("toolchain", File(tool, "toolchain"))
            File(files, ".nx2a-toolchain").writeText("1")
        }
        if (!dotnet.exists()) {
            step("the .NET SDK")
            try {
                // the SDK's own folder: the porter looks for toolchains/dotnet/dotnet
                ctx.assets.open("dotnet.pkg").use { raw ->
                    GZIPInputStream(raw).use { gz -> Tar.unpack(gz, File(nxHome, "toolchains/dotnet")) }
                }
            } catch (e: IOException) {
                // not shipped with the app: the Setup screen downloads it
            }
        }
        // the tool's own scripts are small and change with the app: they are always the ones that are here
        copyAssets("tool/tools", File(tool, "tools"))
        fixGlobalization()
        step("the interpreter paths")
        patchInterpreters()
        step("the check")
        check()
        marker.writeText("1")
    }

    /** The mark that says the environment is unpacked (so an update does not do it again) */
    private val marker: File get() = File(files, ".nx2a-unpacked")

    /** Throws the environment away: the next unpack does everything again */
    fun reset() {
        marker.delete()
        File(files, ".nx2a-tool").delete()
        File(files, ".nx2a-toolchain").delete()
        // the whole environment goes: what was unpacked with the wrong permissions stays wrong
        rootfs.deleteRecursively()
        tool.deleteRecursively()
        nxHome.deleteRecursively()
        home.deleteRecursively()
        tmp.deleteRecursively()
    }

    /**
     * Runs a guest program to see that the environment works: the loader, the rootfs's Python and the modules.
     * The failure says what is wrong, instead of leaving it to the next step.
     */
    private fun check() {
        val p = guest(listOf("-c", "import sys, lz4.block, capstone, os; " +
                "print('guest ok', sys.version.split()[0], 'storage', 'yes' if os.access('/sdcard', os.R_OK) else 'no')"))
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        val err = p.errorStream.bufferedReader().readText()
        if (p.exitValue() != 0 || !out.contains("guest ok")) {
            Log.e(TAG, "the guest does not run (exit ${p.exitValue()}): ${(err + out).trim().take(400)}")
            throw IOException("the guest does not run (exit ${p.exitValue()}): ${(err + out).trim().take(400)}")
        }
        lastCheck = out.trim()
        Log.i(TAG, "check: ${out.trim()}")
    }

    /** What the check said (the screen shows it) */
    var lastCheck: String = ""
        private set

    /**
     * Copies a folder out of the assets. The APK's assets carry no permissions, so the modes come from the file
     * the build wrote (modes.json): the guest's programs have to be executable, or the porter cannot start them.
     */
    private fun copyAssets(path: String, dest: File) {
        val names = ctx.assets.list(path) ?: return
        if (names.isEmpty()) {   // an empty folder in the assets is not listed
            dest.mkdirs(); return
        }
        dest.mkdirs()
        for (name in names) {
            val child = "$path/$name"
            val target = File(dest, name)
            val sub = ctx.assets.list(child)
            if (sub != null && sub.isNotEmpty()) copyAssets(child, target)
            else {
                ctx.assets.open(child).use { src -> target.outputStream().use { src.copyTo(it) } }
                Tar.applyMode(target, modes[child] ?: 420L)
            }
        }
    }

    /** The permissions of the files in the assets (path -> mode), as the build wrote them */
    private val modes: Map<String, Long> by lazy {
        try {
            ctx.assets.open("modes.json").use { raw ->
                val text = raw.readBytes().toString(Charsets.UTF_8)
                val json = org.json.JSONObject(text)
                val out = HashMap<String, Long>()
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    out[k] = json.getLong(k)
                }
                out
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Rewrites the interpreter of the programs that are started directly (the compiler, the .NET SDK): Android
     * has no /lib, and the rootfs the payload carries was built for another app's path.
     */
    fun patchInterpreters() {
        Log.i(TAG, "python3.13 before patching: ${python.length()} bytes")
        val script = File(tool, "tools/patch_interp.py")
        if (!script.exists()) return
        val targets = listOf(File(tool, "toolchain"), File(rootfs, "usr/bin"), File(rootfs, "usr/sbin"),
                              File(nxHome, "toolchains"), File(tool, "tools/nx_extract"))
            .filter { it.exists() }
        nxHome.mkdirs()
        for (t in targets) {
            val log = File(nxHome, "patch-${t.name}.log")
            try {
                val pb = ProcessBuilder(loader.absolutePath, python.absolutePath,
                    script.absolutePath, t.absolutePath, interpPath)
                pb.directory(tool)
                pb.redirectErrorStream(true)
                pb.redirectOutput(log)
                pb.environment().putAll(guestEnv())
                val p = pb.start()
                val code = p.waitFor()
                val tail = log.readText().lines().lastOrNull { it.isNotBlank() } ?: ""
                Log.i(TAG, "patch ${t.absolutePath}: exit $code $tail")
            } catch (e: Exception) {
                Log.e(TAG, "patch ${t.absolutePath}: ${e.message}")
                // a tree that cannot be patched is a tree whose programs cannot run: the doctor says so
            }
            Log.i(TAG, "python3.13 after patching: ${python.length()} bytes")
        }
        restoreModes()
    }

    /**
     * The permissions of the tool's tree, as the build wrote them: the patch's write may have taken the execute
     * bit away, and a program that cannot be started cannot be patched either.
     */
    /**
     * The .NET SDK on the phone stops itself without ICU (the rootfs has none): the runtime config has to
     * declare the invariant mode, and the SDK's own files have to be told.
     */
    private fun fixGlobalization() {
        val script = File(tool, "tools/fix_globalization.py")
        val sdk = File(nxHome, "toolchains/dotnet")
        if (!script.exists() || !sdk.isDirectory) return
        try {
            val p = guest(listOf(script.absolutePath, sdk.absolutePath))
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor()
            Log.i(TAG, "globalization: ${out.trim()}")
        } catch (e: Exception) {
            Log.e(TAG, "globalization: ${e.message}")
        }
    }

    private fun restoreModes() {
        var n = 0
        for ((path, mode) in modes) {
            val f = if (path.startsWith("tool/")) File(tool, path.removePrefix("tool/")) else File(tool, path)
            if (!f.exists()) continue
            val now = try { f.canExecute() } catch (e: Exception) { false }
            if (now && (mode and 64L == 0L)) continue
            Tar.applyMode(f, mode)
            n++
        }
        Log.i(TAG, "permissions restored for $n file(s)")
    }
}
