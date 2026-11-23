package net.mkhalil.taktik.service

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
import androidx.core.content.ContextCompat
import net.mkhalil.taktik.MainActivity
import net.mkhalil.taktik.R

/**
 * Foreground service that keeps the WebCast connection + DB pipeline alive
 * when the user leaves the app. The session work itself runs inside
 * [net.mkhalil.taktik.data.repo.LiveSessionManager]; this service just owns
 * the foreground-notification lifetime that prevents Android from killing
 * those coroutines.
 */
class TaktikLiveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val host = intent.getStringExtra(EXTRA_HOST) ?: "live stream"
                startInForeground(host)
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> startInForeground("live stream")
        }
        return START_STICKY
    }

    private fun startInForeground(host: String) {
        createChannelIfNeeded()

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPi = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val stopPi = PendingIntent.getService(
            this, 1,
            Intent(this, TaktikLiveService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Tracking @$host")
            .setContentText("TakTik is recording live stream events")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openPi)
            .addAction(0, "Stop", stopPi)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Live tracking",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Notification shown while TakTik is actively tracking a live stream"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "net.mkhalil.taktik.action.START_TRACKING"
        const val ACTION_STOP = "net.mkhalil.taktik.action.STOP_TRACKING"
        const val EXTRA_HOST = "host"

        private const val CHANNEL_ID = "taktik_live"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context, host: String) {
            val intent = Intent(context, TaktikLiveService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_HOST, host)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, TaktikLiveService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
