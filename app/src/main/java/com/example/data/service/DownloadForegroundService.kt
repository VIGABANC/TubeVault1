package com.example.data.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.model.DownloadStatus

class DownloadForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "tubevault_downloads_channel"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START_OR_UPDATE = "com.example.tubevault.ACTION_START_OR_UPDATE"
        const val ACTION_QUEUE_IDLE = "com.example.tubevault.ACTION_QUEUE_IDLE"

        fun start(context: Context) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_START_OR_UPDATE
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // In background restriction or transient error
            }
        }

        fun updateNotification(context: Context) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_START_OR_UPDATE
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {}
        }

        fun onQueueIdle(context: Context) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_QUEUE_IDLE
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {}
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == ACTION_QUEUE_IDLE) {
            val downloadManager = DownloadManager.getInstance(this)
            val hasActive = downloadManager.tasks.value.any {
                it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED
            }
            if (!hasActive) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val notification = buildCurrentNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                }
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    private fun buildCurrentNotification(): Notification {
        val downloadManager = DownloadManager.getInstance(this)
        val allTasks = downloadManager.tasks.value
        val activeTasks = allTasks.filter { it.status == DownloadStatus.DOWNLOADING }
        val queuedTasks = allTasks.filter { it.status == DownloadStatus.QUEUED }
        val pausedTasks = allTasks.filter { it.status == DownloadStatus.PAUSED }

        val contentIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(activeTasks.isNotEmpty())
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        when {
            activeTasks.isNotEmpty() -> {
                val primary = activeTasks.first()
                val percent = (primary.progress * 100).toInt().coerceIn(0, 100)
                val activeCount = activeTasks.size
                val title = if (activeCount > 1) {
                    "Téléchargements TubeVault ($activeCount en cours)"
                } else {
                    "Téléchargement de « ${primary.metadata.title.take(30)} »"
                }

                val sizeInfo = if (primary.totalBytes > 0) {
                    " • ${formatBytes(primary.bytesDownloaded)} / ${formatBytes(primary.totalBytes)}"
                } else ""
                val speed = if (primary.speedText.isNotBlank()) " (${primary.speedText})" else ""

                builder.setContentTitle(title)
                    .setContentText("$percent%$sizeInfo$speed")
                    .setProgress(100, percent, false)
            }
            queuedTasks.isNotEmpty() -> {
                builder.setContentTitle("Téléchargements TubeVault")
                    .setContentText("${queuedTasks.size} vidéo(s) en attente...")
                    .setProgress(100, 0, true)
            }
            pausedTasks.isNotEmpty() -> {
                builder.setContentTitle("Téléchargements TubeVault")
                    .setContentText("${pausedTasks.size} téléchargement(s) en pause")
                    .setProgress(0, 0, false)
                    .setSmallIcon(android.R.drawable.ic_media_pause)
            }
            else -> {
                builder.setContentTitle("TubeVault")
                    .setContentText("Téléchargements terminés avec succès")
                    .setProgress(0, 0, false)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
            }
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Téléchargements TubeVault",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Progression et alertes de téléchargement en arrière-plan"
                setShowBadge(false)
                enableVibration(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f Mo", bytes / (1024f * 1024f))
            bytes >= 1024 -> String.format(java.util.Locale.US, "%d Ko", bytes / 1024)
            else -> "$bytes o"
        }
    }
}
