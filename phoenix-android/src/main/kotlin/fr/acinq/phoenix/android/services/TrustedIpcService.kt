package fr.acinq.phoenix.android.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import org.lightning.ITrustedPaymentIpcV1
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.utils.datastore.TrustedAppsRepository
import kotlinx.coroutines.*

class TrustedIpcService : Service() {
    /* ------------------------------------------------------------------ */

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var repo: TrustedAppsRepository

    private val binder = object : ITrustedPaymentIpcV1.Stub() {
        override fun persistMessage(msg: String) {
            val callerPkg = packageManager.getPackagesForUid(Binder.getCallingUid())
                ?.firstOrNull() ?: "unknown"
            scope.launch { repo.addMessage("[$callerPkg] $msg") }
        }
    }
    /* ------------------------------------------------------------------ */

    override fun onCreate() {
        super.onCreate()
        repo = (applicationContext as PhoenixApplication).trustedAppsRepo
        createChannelIfNeeded()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        return START_STICKY
    }

    /* ---------- foreground helpers ---------- */
    private fun startAsForeground() {
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tool)
            .setContentTitle(getString(R.string.trusted_ipc_notif_title))
            .setContentText(getString(R.string.trusted_ipc_notif_text))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        ServiceCompat.startForeground(
            this,
            NOTIF_ID,
            notif,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        )
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.trusted_ipc_channel_name),
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = getString(R.string.trusted_ipc_channel_description)
                    }
                )
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "phoenix_ipc"
        private const val NOTIF_ID   = 73

        fun start(ctx: Context) {
            val i = Intent(ctx, TrustedIpcService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ctx.startForegroundService(i) else ctx.startService(i)
        }
        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, TrustedIpcService::class.java))
        }
    }
}
