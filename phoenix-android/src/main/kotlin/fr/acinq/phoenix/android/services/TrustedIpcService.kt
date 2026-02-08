package fr.acinq.phoenix.android.services

import android.app.*
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import fr.acinq.phoenix.android.BusinessManager
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.utils.datastore.TrustedAppsRepository
import fr.acinq.phoenix.managers.SendManager
import kotlinx.coroutines.*
import org.androidln.IQuickPayV1
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import java.security.MessageDigest


class TrustedIpcService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var repo: TrustedAppsRepository

    override fun onCreate() {
        super.onCreate()
        repo = (applicationContext as PhoenixApplication).trustedAppsRepo
        createChannelIfNeeded()
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        startAsForeground()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private val binder = object : IQuickPayV1.Stub() {

        override fun persistMessage(msg: String) {
            if (!acceptingRequests.get()) return
            val caller = packageManager.getPackagesForUid(Binder.getCallingUid())
                ?.firstOrNull() ?: "unknown"
            scope.launch { repo.addMessage("[$caller] $msg") }
        }

        override fun payInvoice(invoice: String){
            if (!acceptingRequests.get()) return

            val callerPkg = callingPackageName() ?: return

            scope.launch {
                /* 1) Retrieve grant (if any) */
                val gi = repo.getGrant(callerPkg)
                if (gi == null) {
                    repo.addMessage("[DENY $callerPkg] no-grant")
                    return@launch
                }

                /* 2) Verify certificate hash matches stored value */
                val currentSha = packageCertSha256(callerPkg)
                if (currentSha == null || currentSha != gi.certSha256) {
                    repo.addMessage("[DENY $callerPkg] cert-mismatch")
                    return@launch
                }

                /* 3) Parse invoice to obtain exact amount (sats) */
                val business = BusinessManager.businessFlow
                    .first { it.isNotEmpty() }
                    .values
                    .first()
                    .business

                val parseResult = business.sendManager.parse(
                    request = invoice,
                    progress = {}
                )

                val bolt = (parseResult as? SendManager.ParseResult.Bolt11Invoice) ?: run {
                    repo.addMessage("[DENY $callerPkg] invalid-invoice")
                    return@launch
                }
                val msat = bolt.invoice.amount ?: run {
                    repo.addMessage("[DENY $callerPkg] amountless-invoice")
                    return@launch
                }
                val sats = msat.toLong() / 1_000   // msat → sat

                /* 4)TODO Check / consume daily allowance */

                /* 5) Trampoline fees (required to send) */
                val peerState = business.peerManager.peerState.filterNotNull().first()
                val trampolineFees = peerState.walletParams?.trampolineFees?.firstOrNull()

                if (trampolineFees == null) {
                    repo.addMessage("[DENY $callerPkg] trampoline-fees-missing")
                    return@launch
                }

                /* 6) Do the payment */
//                try {
//                    business.sendManager.payBolt11Invoice(
//                        amountToSend   = msat,
//                        trampolineFees = trampolineFees,
//                        invoice        = bolt.invoice,
//                        metadata       = null
//                    )
//                    repo.addMessage("[OK   $callerPkg] sent $sats sat")
//                } catch (e: Exception) {
//                    repo.addMessage("[FAIL $callerPkg] ${e.javaClass.simpleName}")
//                    // TODO roll-back allowance on failure
//                }
            }
        }
    }

    private fun callingPackageName(): String? =
        packageManager.getPackagesForUid(Binder.getCallingUid())?.firstOrNull()

    private fun packageCertSha256(pkg: String): String? = try {
        val sigBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageManager.getPackageInfo(pkg,
                PackageManager.GET_SIGNING_CERTIFICATES
            ).signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(pkg,
                PackageManager.GET_SIGNATURES
            ).signatures?.firstOrNull()?.toByteArray()
        }
        sigBytes?.sha256()
    } catch (e: Exception) { null }

    private fun ByteArray.sha256(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(this)
            .joinToString("") { "%02x".format(it) }

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

        private val acceptingRequests = AtomicBoolean(true)

        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, TrustedIpcService::class.java))
            acceptingRequests.set(true)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, TrustedIpcService::class.java))
        }

        fun disableComponent(ctx: Context) {
            acceptingRequests.set(false)
            setEnabled(ctx, false)
            stop(ctx)
        }

        fun enableComponent(ctx: Context) {
            setEnabled(ctx, true)
            acceptingRequests.set(true)
        }

        private fun setEnabled(ctx: Context, enable: Boolean) {
            val pm   = ctx.packageManager
            val comp = ComponentName(ctx, TrustedIpcService::class.java)
            pm.setComponentEnabledSetting(
                comp,
                if (enable)
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        }
    }
}
