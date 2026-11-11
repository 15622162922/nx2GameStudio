package com.nx.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.nx.android.R
import com.nx.android.appstate.PortState
import com.nx.android.data.AppLocale
import com.nx.android.data.Env
import com.nx.android.data.PortRunner
import com.nx.android.domain.ALIAS_AUTO
import com.nx.android.domain.FolderScanner
import java.io.File

/**
 * The foreground service that keeps the port alive while the app is in the background or the screen is off: the
 * porter runs in the app's own process, and Android kills idle background processes — the service's notification
 * says a port is on, so the process stays. It carries no state: PortState is the one source; the service keeps
 * the process alive and mirrors the progress into the notification. Not sticky: the porter cannot resume, so a
 * killed service is an interrupted port, not one to restart half-made.
 */
class PortService : Service() {

    companion object {
        const val CHANNEL = "nx2a_port"
        const val GAME = "game"; const val KEYS = "keys"
        const val TOUCH = "touch"; const val FPS60 = "fps60"; const val EXP = "exp"
        const val ALIAS = "alias"; const val PACK_DIR = "packDir"
        private var instance: PortService? = null

        /** Starts a port: the state goes up first (the UI turns at once), the service keeps it alive */
        fun start(ctx: Context, game: String, keys: String,
                  touch: Boolean, fps60: Boolean, exp: Boolean, aliasHeadroom: Int,
                  packDir: String? = null) {
            (ctx.applicationContext as NxApp).container.port.begin(game)
            val i = Intent(ctx, PortService::class.java)
                .putExtra(GAME, game).putExtra(KEYS, keys)
                .putExtra(TOUCH, touch).putExtra(FPS60, fps60).putExtra(EXP, exp)
                .putExtra(ALIAS, aliasHeadroom)
            if (!packDir.isNullOrBlank()) i.putExtra(PACK_DIR, packDir)
            ctx.startForegroundService(i)
        }

        /** The stop from the UI: the porter and its tools die, the state marks the cancel */
        fun cancel(ctx: Context) {
            (ctx.applicationContext as NxApp).container.port.cancel()
            instance?.runner?.cancel()
            instance?.stopShown()
        }
    }

    private var runner: PortRunner? = null
    private lateinit var nm: NotificationManager
    private val port: PortState by lazy { (applicationContext as NxApp).container.port }
    private val locale: AppLocale by lazy { (applicationContext as NxApp).container.locale }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, locale.str(R.string.port_notification_channel), NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val game = intent?.getStringExtra(GAME) ?: return stop()
        val keys = intent.getStringExtra(KEYS) ?: return stop()
        startForeground(1, notification())
        runner = PortRunner(Env(applicationContext), locale) { line ->
            port.onLine(line)
            nm.notify(1, notification())
        }
        runner!!.port(game, keys, intent.getBooleanExtra(TOUCH, false),
            intent.getBooleanExtra(FPS60, false), intent.getBooleanExtra(EXP, false),
            intent.getIntExtra(ALIAS, ALIAS_AUTO), intent.getStringExtra(PACK_DIR)) {
            port.finish()
            nm.notify(1, notification())
            stopShown()   // the notification stays (the result), the service goes
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun stop(): Int { stopSelf(); return START_NOT_STICKY }

    /** Lets the notification show the port's outcome while the service itself ends */
    private fun stopShown() {
        try { stopForeground(STOP_FOREGROUND_DETACH) } catch (e: Exception) {}
        instance = null
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun notification(): Notification {
        val tap = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, 1)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val what = when {
            port.running ->
                if (port.stageName.isEmpty()) locale.str(R.string.port_notification_running) else locale.str(R.string.port_stage_of, port.stageOf, port.stages, port.stageName) +
                    (if (port.stageStep.isEmpty()) "" else " · ${port.stageStep}")
            port.phase == PortState.Phase.DONE -> locale.str(R.string.port_done_path, File(port.outPath).name)
            port.phase == PortState.Phase.CANCELLED -> locale.str(R.string.common_cancelled)
            port.phase == PortState.Phase.INTERRUPTED -> locale.str(R.string.port_interrupted_short)
            else -> locale.str(R.string.port_failed_no_pack)
        }
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)   // the mark's shapes: the notification tints the silhouette
            .setContentTitle(if (port.gameFile.isEmpty()) locale.str(R.string.app_name) else FolderScanner.displayName(File(port.gameFile)))
            .setContentText(what)
            .setProgress(100, (port.progress * 100).toInt(), port.running && port.progress <= 0f)
            .setOngoing(port.running)
            .setOnlyAlertOnce(true)
            .setContentIntent(tap)
            .build()
    }
}
