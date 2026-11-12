package com.nx.android.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nx.android.R
import com.nx.android.domain.PhantomCheck
import org.conscrypt.Conscrypt
import java.io.File
import java.net.InetAddress
import java.net.NetworkInterface
import java.security.Provider
import javax.net.ssl.SSLSocket
import kotlin.concurrent.thread

/**
 * Android 12 and newer stop an app's "phantom" child processes above a limit (32, counted for the whole
 * system), and the Linux environment starts many of them: the porter is one, and so are the compiler and
 * the shader translator below it. When the list overflows, the oldest one goes first - the porter - and
 * the port dies with no error at all (the App sees exit 137 and nothing else).
 *
 * The switch is only writable by the shell user, so the fix uses Wireless debugging on the phone itself:
 * the player pairs the App once with a pairing code (typed into a notification, so the code dialog in
 * Settings stays open), then the App connects to adbd over 127.0.0.1 and changes the setting (AdbWire.kt).
 * The setting survives reboots; Wireless debugging is switched off again afterwards when it was off before.
 *
 * Ported from the same author's fexdroid project.
 */
object PhantomFix {
    private const val KEY = "settings_enable_monitor_phantom_procs"
    private const val MAX = "2147483647"
    private const val CHANNEL = "phantom-fix"
    private const val NOTIFY_ID = 7
    private const val PREFS = "phantom-fix"
    const val EXTRA_CODE = "code"
    private const val LOOPBACK = "127.0.0.1"
    private const val TAG = "nx2a-phantom"

    /** What the fix is doing now */
    sealed interface State {
        /** Nothing started yet */
        data object Idle : State

        /** Waiting for the pairing code; [pairPort] once Wireless debugging's pairing service is seen */
        data class Waiting(val pairPort: Int?) : State

        /** Working; [step] says what */
        data class Working(val step: String) : State

        /** The limit is off */
        data object Done : State

        /** It did not work; [message] says why */
        data class Failed(val message: String) : State
    }

    var state by mutableStateOf<State>(State.Idle); private set

    @Volatile private var pairPort: Int? = null
    @Volatile private var connectPort: Int? = null
    private var discoveries = listOf<Mdns>()

    /** Wireless debugging's state before the fix: switched off again only if it was off */
    private var wifiAdbWasOn = true

    /** The app and the words it speaks: set once, when the app starts */
    private var app: Context? = null
    private var locale: AppLocale? = null

    /** Hands the fix the app and its words: the App wires this once, at startup */
    fun attach(ctx: Context, loc: AppLocale) {
        app = ctx.applicationContext
        locale = loc
    }

    private fun str(@StringRes id: Int, vararg args: Any?): String {
        val l = locale
        if (l != null) return l.str(id, *args)
        val c = app ?: return ""
        return c.getString(id, *args)
    }

    /**
     * Whether Android may kill the guests' processes now. Runs a command: not on the main thread.
     *
     * The decision itself is in domain/PhantomCheck.kt, free of Android, so it can be tested on the JVM.
     */
    fun status(ctx: Context): PhantomCheck.Status {
        val sdk = Build.VERSION.SDK_INT
        if (sdk < Build.VERSION_CODES.S) return PhantomCheck.Status.NOT_NEEDED
        // Android 12: the app may not read device_config at all, so our own fix is the only sign
        if (sdk == Build.VERSION_CODES.S && prefs(ctx).getBoolean("fixed", false)) return PhantomCheck.Status.OFF
        val max = runCmd("/system/bin/device_config", "get", "activity_manager", "max_phantom_processes").trim()
        val global = runCatching { Settings.Global.getString(ctx.contentResolver, KEY) }.getOrNull().orEmpty()
        val flag = getprop("persist.sys.fflag.override.$KEY")
        return PhantomCheck.status(sdk, max, global, flag)
    }

    /** The fix's own note: it was done here before, so Android 12's unreadable setting is known */
    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Runs a program and gives back what it printed */
    private fun runCmd(vararg cmd: String): String = runCatching {
        val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        out
    }.getOrElse { "<${it.javaClass.simpleName}: ${it.message}>" }

    /** A system property's value */
    private fun getprop(name: String): String = runCmd("/system/bin/getprop", name).trim()

    /** Shows the code notification and starts looking for Wireless debugging's ports. */
    fun begin(ctx: Context) {
        val app = ctx.applicationContext
        if (state is State.Working) return
        wifiAdbWasOn = runCatching { Settings.Global.getInt(app.contentResolver, "adb_wifi_enabled", 0) == 1 }.getOrDefault(true)
        pairPort = null
        connectPort = null
        stopDiscovery()
        discoveries = listOf(
            Mdns(app, "_adb-tls-pairing._tcp") { pairPort = it; if (state is State.Waiting) state = State.Waiting(it) },
            Mdns(app, "_adb-tls-connect._tcp") { connectPort = it },
        ).onEach { it.start() }
        state = State.Waiting(null)
        notifyCodeRequest(app)
    }

    fun cancel(ctx: Context) {
        stopDiscovery()
        ctx.getSystemService(NotificationManager::class.java)?.cancel(NOTIFY_ID)
        if (state !is State.Working) state = State.Idle
    }

    private fun stopDiscovery() { discoveries.forEach { it.stop() }; discoveries = emptyList() }

    /**
     * Pairs with [code], then changes the setting. [port] overrides the discovered pairing port
     * (the "IP address & Port" line of the code dialog). [done] runs on the worker thread.
     */
    fun submit(ctx: Context, code: String, port: Int? = null, done: () -> Unit = {}) {
        val app = ctx.applicationContext
        if (state is State.Working) { done(); return }
        state = State.Working(str(R.string.phantom_step_pairing))
        notifyProgress(app)
        thread(name = "phantom-fix") {
            try {
                run(app, code.filter(Char::isDigit), port)
                prefs(app).edit().putBoolean("fixed", true).apply()
                state = State.Done
            } catch (e: AdbWire.PairingRejected) {
                state = State.Failed(str(R.string.phantom_err_code))
            } catch (e: Exception) {
                Log.w(TAG, "fix failed", e)
                state = State.Failed(e.message ?: e.javaClass.simpleName)
            } finally {
                if (state is State.Done) stopDiscovery()
                notifyResult(app)
                done()
            }
        }
    }

    private fun run(app: Context, code: String, portOverride: Int?) {
        if (code.length != 6) throw IllegalArgumentException(str(R.string.phantom_err_code_format))
        val wire = AdbWire(key(app), ConscryptTls, "nx2a@${Build.MODEL}")
        val pPort = portOverride ?: waitFor(15_000) { pairPort } ?: throw IllegalStateException(str(R.string.phantom_err_no_pairing))
        wire.pair(LOOPBACK, pPort, code)
        state = State.Working(str(R.string.phantom_step_connecting))
        // The connection port: Android publishes it as a property on some ROMs, always over mDNS.
        val cPort = getprop("service.adb.tls.port").toIntOrNull()?.takeIf { it in 1..65535 }
            ?: waitFor(15_000) { connectPort } ?: throw IllegalStateException(str(R.string.phantom_err_no_connect))
        state = State.Working(str(R.string.phantom_step_changing))
        val sdk = Build.VERSION.SDK_INT
        val cmds = buildList {
            if (sdk > Build.VERSION_CODES.S) add("settings put global $KEY false")
            // Android 12 has only the limit; raising it helps on later versions too.
            add("device_config set_sync_disabled_for_tests persistent")
            add("device_config put activity_manager max_phantom_processes $MAX")
            add("echo =")
            add("settings get global $KEY")
            add("device_config get activity_manager max_phantom_processes")
        }
        val out = wire.exec(LOOPBACK, cPort, cmds.joinToString("; ")).substringAfterLast("=\n").lines().map { it.trim() }
        val ok = if (sdk > Build.VERSION_CODES.S) out.getOrNull(0) == "false" || out.getOrNull(1) == MAX else out.getOrNull(1) == MAX
        if (!ok) throw IllegalStateException(str(R.string.phantom_err_not_confirmed, out.joinToString(" / ")))
        Log.i(TAG, "limit switched off (${out.joinToString(" / ")}); Wireless debugging was on before: $wifiAdbWasOn")
        // Switching it off closes this very connection, so the command never answers (EOF): expected.
        if (!wifiAdbWasOn) runCatching { wire.exec(LOOPBACK, cPort, "settings put global adb_wifi_enabled 0", 5_000) }
    }

    private fun waitFor(ms: Long, value: () -> Int?): Int? {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) { value()?.let { return it }; Thread.sleep(250) }
        return value()
    }

    /** The App's adb key, made once; kept out of backups. */
    private fun key(app: Context): java.security.interfaces.RSAPrivateCrtKey {
        val f = File(app.noBackupFilesDir, "adb-key.pk8")
        if (f.isFile) runCatching { return AdbWire.loadKey(f.readBytes()) }
        val k = AdbWire.newKey()
        val tmp = File(f.path + ".tmp")
        tmp.writeBytes(k.encoded)
        tmp.renameTo(f)
        return k
    }

    private object ConscryptTls : AdbWire.Tls {
        override val provider: Provider by lazy { Conscrypt.newProvider() }
        override fun exportKeyingMaterial(socket: SSLSocket, label: String, length: Int): ByteArray =
            Conscrypt.exportKeyingMaterial(socket, label, null, length)
    }

    fun developerOptionsOn(ctx: Context) = runCatching {
        Settings.Global.getInt(ctx.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
    }.getOrDefault(true)

    /**
     * Opens Developer options at the Wireless debugging row, or "About phone" (where the build
     * number is tapped 7 times) while Developer options are off: their page would only show a
     * toast and close.
     */
    fun openDeveloperOptions(ctx: Context) {
        // Settings scrolls to (and highlights) the preference named in its fragment arguments.
        val key = "toggle_adb_wireless"
        val dev = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            .putExtra(":settings:fragment_args_key", key)
            .putExtra(":settings:show_fragment_args", android.os.Bundle().apply { putString(":settings:fragment_args_key", key) })
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val about = Intent(Settings.ACTION_DEVICE_INFO_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(if (developerOptionsOn(ctx)) dev else about) }
    }

    // --- Notification: the code is typed here while Settings shows it --------------------------

    /**
     * Creates the channel early, while the app is in front: on Android 13+ an app that targets an
     * older SDK gets the notification permission prompt only then, not once Settings is open.
     */
    fun prepareNotifications(ctx: Context): Boolean = channel(ctx.applicationContext).areNotificationsEnabled()

    private fun channel(app: Context): NotificationManager {
        val nm = app.getSystemService(NotificationManager::class.java)
        // High importance: shown over Settings as a heads-up, where the code is.
        nm.createNotificationChannel(NotificationChannel(CHANNEL, str(R.string.phantom_channel), NotificationManager.IMPORTANCE_HIGH))
        return nm
    }

    private fun base(app: Context) = Notification.Builder(app, CHANNEL)
        .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
        .setContentTitle(str(R.string.phantom_notify_title))
        .setOnlyAlertOnce(true)

    /** Asks for the code; [problem] (the last attempt's error) goes first. */
    private fun notifyCodeRequest(app: Context, problem: String? = null) {
        val nm = channel(app)
        val input = RemoteInput.Builder(EXTRA_CODE).setLabel(str(R.string.phantom_code_label)).build()
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
        val reply = PendingIntent.getBroadcast(app, 0, Intent(app, PhantomFixReceiver::class.java), flags)
        val action = Notification.Action.Builder(null, str(R.string.phantom_code_action), reply).addRemoteInput(input).build()
        val text = listOfNotNull(problem, str(R.string.phantom_notify_code)).joinToString("\n")
        nm.notify(NOTIFY_ID, base(app).setContentText(problem ?: text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .addAction(action).setOngoing(true).build())
    }

    private fun notifyProgress(app: Context) {
        channel(app).notify(NOTIFY_ID, base(app).setContentText(str(R.string.phantom_step_pairing))
            .setProgress(0, 0, true).setOngoing(true).build())
    }

    private fun notifyResult(app: Context) {
        when (val st = state) {
            is State.Done -> channel(app).notify(NOTIFY_ID, base(app).setContentText(str(R.string.phantom_done)).setAutoCancel(true).build())
            // Ask again (a mistyped code is the usual cause), with the reason.
            is State.Failed -> notifyCodeRequest(app, st.message)
            else -> {}
        }
    }

    /** One mDNS service type, resolved to a port when the service runs on this phone. */
    private class Mdns(app: Context, private val type: String, private val onPort: (Int) -> Unit) {
        private val nsd = app.getSystemService(NsdManager::class.java)
        private val queue = ArrayDeque<NsdServiceInfo>()
        private var resolving = false
        @Volatile private var stopped = false

        private val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(t: String) {}
            override fun onDiscoveryStopped(t: String) {}
            override fun onStartDiscoveryFailed(t: String, e: Int) {}
            override fun onStopDiscoveryFailed(t: String, e: Int) {}
            override fun onServiceLost(s: NsdServiceInfo) {}
            override fun onServiceFound(s: NsdServiceInfo) { synchronized(queue) { queue.addLast(s) }; next() }
        }

        fun start() = runCatching { nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener) }
        fun stop() { stopped = true; runCatching { nsd.stopServiceDiscovery(listener) } }

        // NsdManager resolves one service at a time (FAILURE_ALREADY_ACTIVE otherwise).
        private fun next() {
            val s = synchronized(queue) { if (resolving || stopped) null else queue.removeFirstOrNull()?.also { resolving = true } } ?: return
            @Suppress("DEPRECATION")
            runCatching {
                nsd.resolveService(s, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(si: NsdServiceInfo, e: Int) { done() }
                    override fun onServiceResolved(si: NsdServiceInfo) {
                        @Suppress("DEPRECATION") val host = si.host
                        if (host != null && isThisPhone(host) && si.port in 1..65535) onPort(si.port)
                        done()
                    }
                })
            }.onFailure { done() }
        }

        private fun done() { synchronized(queue) { resolving = false }; next() }

        private fun isThisPhone(a: InetAddress): Boolean = a.isLoopbackAddress || runCatching {
            NetworkInterface.getNetworkInterfaces().toList().any { i -> i.inetAddresses.toList().any { it.address.contentEquals(a.address) } }
        }.getOrDefault(false)
    }
}

/** The pairing code typed into PhantomFix's notification. */
class PhantomFixReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val code = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(PhantomFix.EXTRA_CODE)?.toString() ?: return
        val pending = goAsync()
        PhantomFix.submit(context, code) { pending.finish() }
    }
}
