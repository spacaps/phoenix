package fr.acinq.phoenix.android.quickpay

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import fr.acinq.lightning.utils.UUID
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.android.utils.datastore.TrustedAppsRepository.GrantInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.security.MessageDigest

const val EXTRA_CALLER_PKG = "org.lightning.extra.CALLER_PACKAGE"
private const val TAG = "QuickPayPairingAct"

class QuickPayPairingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /* identify caller app */

        val callerPkg = intent.getStringExtra(EXTRA_CALLER_PKG)
            ?: callingPackage
            ?: run { Log.w(TAG, "no callingPkg"); finish(); return }

        Log.i(TAG, "pair requested by $callerPkg  intent=$intent  extras=${intent.extras}")

        val pm = packageManager
        val appLabel = runCatching {
            pm.getApplicationLabel(pm.getApplicationInfo(callerPkg, 0)).toString()
        }.getOrDefault(callerPkg)

        val certSha = runCatching {
            val sigBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(callerPkg, PackageManager.GET_SIGNING_CERTIFICATES)
                    .signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(callerPkg, PackageManager.GET_SIGNATURES)
                    .signatures?.firstOrNull()?.toByteArray()
            }
            sigBytes?.sha256() ?: "N/A"
        }.getOrDefault("N/A")

        val repo = (application as PhoenixApplication).trustedAppsRepo

        /* UI */

        setContent {
            PairingScreen(
                appLabel = appLabel,
                packageName = callerPkg,
                certSha = certSha,
                onConfirm = { perDay ->
                    val grantId = UUID.randomUUID().toString()
                    lifecycleScope.launch(Dispatchers.IO) {
                        repo.saveGrant(
                            pkg = callerPkg,
                            gi = GrantInfo(
                                certSha256 = certSha,
                                perDayMax = perDay,
                                grantId = grantId
                            )
                        )
                        Log.i(TAG, "grant stored for $callerPkg  perDay=$perDay")
                    }
                    Intent().apply {
                        putExtra("grantId", grantId)
                    }.also { resultIntent ->
                        setResult(RESULT_OK, resultIntent)
                    }
                    finish()
                },
                onCancel = { setResult(RESULT_CANCELED); finish() }
            )
        }
    }
}

private fun ByteArray.sha256(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { "%02x".format(it) }
