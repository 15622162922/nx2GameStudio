package com.nx.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import com.nx.android.domain.porting.PortPhase
import javax.inject.Inject
import com.nx.android.R
import com.nx.android.appstate.PortState
import com.nx.android.appstate.porting.PortCompletionCoordinator
import com.nx.android.data.AppLocale
import com.nx.android.data.PortRunner
import com.nx.android.domain.environment.EnvironmentSetup
import com.nx.android.domain.environment.GuestProcessLauncher
import com.nx.android.domain.ALIAS_AUTO
import com.nx.android.domain.GameIdentity
import java.io.File

/**
 * The foreground service that keeps the port alive while the app is in the background or the screen is off: the
 * porter runs in the app's own process, and Android kills idle background processes — the service's notification
 * says a port is on, so the process stays. It carries no state: PortState is the one source; the service keeps
 * the process alive and mirrors the progress into the notification. Not sticky: the porter cannot resume, so a
 * killed service is an interrupted port, not one to restart half-made.
 */
@AndroidEntryPoint
class PortService : Service() {

    companion object {
        const val CHANNEL = "nx2a_port"
        const val ACTION_START = "com.nx.android.port.START"
        const val ACTION_CANCEL = "com.nx.android.port.CANCEL"
        const val GAME = "game"; const val KEYS = "keys"
        const val TOUCH = "touch"; const val FPS60 = "fps60"; const val EXP = "exp"
        const val ALIAS = "alias"; const val PACK_DIR = "packDir"

        /** Starts a port: the state goes up first (the UI turns at once), the service keeps it alive */
        fun start(ctx: Context, game: String, keys: String,
                  touch: Boolean, fps60: Boolean, exp: Boolean, aliasHeadroom: Int,
                  packDir: String? = null) {
            val i = Intent(ctx, PortService::class.java)
                .setAction(ACTION_START)
                .putExtra(GAME, game).putExtra(KEYS, keys)
                .putExtra(TOUCH, touch).putExtra(FPS60, fps60).putExtra(EXP, exp)
                .putExtra(ALIAS, aliasHeadroom)
            if (!packDir.isNullOrBlank()) i.putExtra(PACK_DIR, packDir)
            ContextCompat.startForegroundService(ctx, i)
        }

        /** The stop from the UI: the porter and its tools die, the state marks the cancel */
        fun cancel(ctx: Context) {
            ctx.startService(Intent(ctx, PortService::class.java).setAction(ACTION_CANCEL))
        }
    }

    private var runner: PortRunner? = null
    private lateinit var nm: NotificationManager
    @Inject lateinit var port: PortState
    @Inject lateinit var locale: AppLocale
    @Inject lateinit var environmentSetup: EnvironmentSetup
    @Inject lateinit var guestLauncher: GuestProcessLauncher
    @Inject lateinit var completionCoordinator: PortCompletionCoordinator

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, locale.str(R.string.port_notification_channel), NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            runner?.cancel()
            port.cancel()
            port.history.firstOrNull()?.let(completionCoordinator::complete)
            stopShown()
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val game = intent?.getStringExtra(GAME) ?: return stop()
        val keys = intent.getStringExtra(KEYS) ?: return stop()
        port.begin(game)
        startForeground(1, notification())
        runner = PortRunner(environmentSetup, guestLauncher, locale) { line ->
            port.onLine(line)
            nm.notify(1, notification())
        }
        runner!!.port(game, keys, intent.getBooleanExtra(TOUCH, false),
            intent.getBooleanExtra(FPS60, false), intent.getBooleanExtra(EXP, false),
            intent.getIntExtra(ALIAS, ALIAS_AUTO), intent.getStringExtra(PACK_DIR)) {
            port.finish()
            port.history.firstOrNull()?.let(completionCoordinator::complete)
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
    }

    override fun onDestroy() {
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
            port.phase == PortPhase.DONE -> locale.str(R.string.port_done_path, File(port.outPath).name)
            port.phase == PortPhase.CANCELLED -> locale.str(R.string.common_cancelled)
            port.phase == PortPhase.INTERRUPTED -> locale.str(R.string.port_interrupted_short)
            else -> locale.str(R.string.port_failed_no_pack)
        }
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)   // the mark's shapes: the notification tints the silhouette
            .setContentTitle(if (port.gameFile.isEmpty()) locale.str(R.string.app_name) else GameIdentity.displayName(File(port.gameFile)))
            .setContentText(what)
            .setProgress(100, (port.progress * 100).toInt(), port.running && port.progress <= 0f)
            .setOngoing(port.running)
            .setOnlyAlertOnce(true)
            .setContentIntent(tap)
            .build()
    }
}
