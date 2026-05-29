package com.vita.healthtracker.data.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.vita.healthtracker.R
import com.vita.healthtracker.VitaApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class GarminSyncForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForeground(NotificationId, buildNotification(0, "Vita 正在同步健康数据"))
        acquireWakeLock()
        observeJob = serviceScope.launch {
            (application as VitaApplication).container.syncCoordinator.status
                .map { it.running to it.fraction }
                .distinctUntilChanged()
                .collect { (running, fraction) ->
                    if (running) {
                        notificationManager.notify(
                            NotificationId,
                            buildNotification((fraction * 100).toInt().coerceIn(0, 100), "Vita 正在后台同步"),
                        )
                    } else {
                        stopSelf()
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ActionStop) {
            (application as VitaApplication).container.syncCoordinator.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        observeJob?.cancel()
        releaseWakeLock()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(percent: Int, title: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            packageManager.getLaunchIntentForPackage(packageName) ?: Intent(),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, GarminSyncForegroundService::class.java).setAction(ActionStop),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, ChannelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(if (percent > 0) "$percent%" else "准备同步")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent.coerceIn(0, 100), percent <= 0)
            .addAction(Notification.Action.Builder(R.mipmap.ic_launcher, "停止", stopIntent).build())
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    ChannelId,
                    "Vita 数据同步",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "保持 Garmin / Health Connect 同步在锁屏或切后台时继续运行"
                },
            )
        }
    }

    private val notificationManager: NotificationManager
        get() = getSystemService(NotificationManager::class.java)

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        runCatching {
            val powerManager = getSystemService(PowerManager::class.java)
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WakeLockTag).apply {
                setReferenceCounted(false)
                acquire(WakeLockTimeoutMs)
            }
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            wakeLock?.takeIf { it.isHeld }?.release()
        }
        wakeLock = null
    }

    companion object {
        private const val ChannelId = "vita_sync"
        private const val NotificationId = 8101
        private const val ActionStop = "com.vita.healthtracker.STOP_SYNC"
        private const val WakeLockTag = "Vita:GarminSync"
        private const val WakeLockTimeoutMs = 6 * 60 * 60 * 1000L

        fun start(context: Context) {
            runCatching {
                val intent = Intent(context, GarminSyncForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, GarminSyncForegroundService::class.java)) }
        }
    }
}
