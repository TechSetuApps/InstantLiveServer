 package app.techsetuapps.instantweb

import app.techsetuapps.instantweb.R

import android.app.*
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat
import fi.iki.elonen.NanoHTTPD

class ServerService : Service() {

    private var httpServer: LocalHttpServer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val ACTION_START    = "iw.START"
        const val ACTION_STOP     = "iw.STOP"
        const val EXTRA_HTML_NAME = "iw.HTML_NAME"   // Main HTML filename (content in VirtualFS)
        const val EXTRA_PORT      = "iw.PORT"
        const val CHANNEL_ID      = "iw_service_ch"
        const val NOTIF_ID        = 1001
        const val BROADCAST_STATUS = "iw.STATUS"
        const val EXTRA_RUNNING    = "iw.RUNNING"
        const val EXTRA_URL        = "iw.URL"
    }

    override fun onCreate() {
        super.onCreate()
        createNotifChannel()
        // WakeLock — prevents Android battery optimization from killing the server
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "InstantLiveServer::ServerWakeLock"
        ).also {
            it.setReferenceCounted(false)
            it.acquire(4 * 60 * 60 * 1000L) // max 4 hours
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val htmlName = intent.getStringExtra(EXTRA_HTML_NAME) ?: return START_NOT_STICKY
                val port = intent.getIntExtra(EXTRA_PORT, 7090)
                startServer(htmlName, port)
            }
            ACTION_STOP -> {
                stopServer()
                stopSelf()
            }
        }
        // START_NOT_STICKY — do NOT restart service after app is killed
        // Prevents zombie notification "Server is running" after app close
        return START_NOT_STICKY
    }

    private fun startServer(htmlName: String, port: Int) {
        try {
            httpServer?.stop()
            val prefs = getSharedPreferences("iw_prefs", android.content.Context.MODE_PRIVATE)
            val savedPort = prefs.getInt("host_port", port)
            // Server reads from VirtualFS (RAM) — no file path needed
            httpServer = LocalHttpServer(htmlName, savedPort, this)
            httpServer!!.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)

            val notif = buildNotif("Hosting Active", "http://127.0.0.1:$savedPort")
            startForeground(NOTIF_ID, notif)

            sendBroadcast(Intent(BROADCAST_STATUS).apply {
                putExtra(EXTRA_RUNNING, true)
                putExtra(EXTRA_URL, "http://127.0.0.1:$savedPort")
            })
        } catch (e: Exception) {
            sendBroadcast(Intent(BROADCAST_STATUS).apply {
                putExtra(EXTRA_RUNNING, false)
                putExtra(EXTRA_URL, "Error: ${e.message}")
            })
            stopSelf()
        }
    }

    private fun stopServer() {
        try { httpServer?.stop() } catch (_: Exception) {}
        httpServer = null
        // Clear VFS — free RAM
        VirtualFS.clear()
        sendBroadcast(Intent(BROADCAST_STATUS).apply {
            putExtra(EXTRA_RUNNING, false)
            putExtra(EXTRA_URL, "")
        })
    }

    private fun buildNotif(title: String, text: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, ServerService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.iw_launcher)
            .setContentTitle("InstantLive Server — $title")
            .setContentText(text)
            .setContentIntent(openIntent)
            .addAction(android.R.drawable.ic_delete, "Stop Hosting", stopIntent)
            .setOngoing(true)
            // Foreground service priority — system cannot kill this
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .build()
    }

    private fun createNotifChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID, "InstantLive Server",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Live HTML hosting service"
                setShowBadge(false)
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(ch)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // When user swipes app from recents, stop server + remove notification
    override fun onTaskRemoved(rootIntent: Intent?) {
        stopServer()
        // Remove the foreground notification explicitly
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        try { wakeLock?.release() } catch (_: Exception) {}
        stopServer()
        // Explicitly remove the foreground notification on destroy
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
