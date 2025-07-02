@file:Suppress("OPT_IN_IS_NOT_ENABLED")

package fr.acinq.phoenix.android.quickpay

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.android.utils.datastore.TrustedAppsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.security.MessageDigest
import fr.acinq.phoenix.android.utils.datastore.TrustedAppsRepository.GrantInfo

/** Extra key a client may supply (optional) */
const val EXTRA_CALLER_PKG = "org.lightning.extra.CALLER_PACKAGE"

class QuickPayPairingActivity : ComponentActivity() {

    // ---------------- lifecycle -----------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ----- identify the external app -----
        val callerPkg: String = intent.getStringExtra(EXTRA_CALLER_PKG)
            ?: callingPackage
            ?: run { finish(); return }

        val pm = packageManager
        val appLabel = try { pm.getApplicationLabel(pm.getApplicationInfo(callerPkg, 0)).toString() }
        catch (_: Exception) { callerPkg }
        val appIcon  = try { pm.getApplicationIcon(callerPkg) } catch (_: Exception) { null }

        val certSha: String = runCatching {
            val sigBytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(
                    callerPkg,
                    PackageManager.GET_SIGNING_CERTIFICATES
                ).signingInfo?.apkContentsSigners?.first()?.toByteArray()
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(
                    callerPkg,
                    PackageManager.GET_SIGNATURES
                ).signatures?.first()?.toByteArray()
            }

            sigBytes?.sha256()                 // <- your extension / helper
        }.getOrElse { "N/A" }.toString()                 // unwrap Result, defaulting on failure


        var perTxSat     by mutableStateOf("50_000")
        var perMinSat    by mutableStateOf("100_000")
        var perDaySat    by mutableStateOf("500_000")

        val repo: TrustedAppsRepository =
            (application as PhoenixApplication).trustedAppsRepo

        // ----------------- UI ------------------------------
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text("QuickPay",
                            style = MaterialTheme.typography.h6,
                            fontWeight = FontWeight.Bold)

                        // Row with icon + name + pkgId
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {

                            Column {
                                Text(appLabel, fontWeight = FontWeight.SemiBold)
                                Text(callerPkg, style = MaterialTheme.typography.caption)
                            }
                        }

                        OutlinedTextField(
                            value = certSha,
                            onValueChange = {},
                            label = { Text("SHA-256 certificate") },
                            readOnly = true,
                            leadingIcon = { Icon(Icons.Default.Info, null) },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(12.dp))
                        Text("limits",
                            fontWeight = FontWeight.Bold)

                        LimitField(
                            label = "per transaction limit",
                            value = perTxSat,
                            onChange = { perTxSat = it }
                        )
                        LimitField(
                            label = "per minute limit",
                            value = perMinSat,
                            onChange = { perMinSat = it }
                        )
                        LimitField(
                            label = "per day limit",
                            value = perDaySat,
                            onChange = { perDaySat = it }
                        )

                        Spacer(Modifier.weight(1f))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = { setResult(RESULT_CANCELED); finish() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text(stringResource(id = android.R.string.cancel)) }

                            Button(
                                onClick = {
                                    // Try converting numbers, fall back to defaults
                                    val tx  = perTxSat.filter { it.isDigit() }.toLongOrNull() ?: 50_000
                                    val min = perMinSat.filter { it.isDigit() }.toLongOrNull() ?: 100_000
                                    val day = perDaySat.filter { it.isDigit() }.toLongOrNull() ?: 500_000
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        repo.saveGrant(
                                            callerPkg,
                                            GrantInfo(
                                                certSha256 = certSha,
                                                perTxMax = tx,
                                                perMinuteMax = min,
                                                perDayMax = day
                                            )
                                        )
                                    }
                                    setResult(RESULT_OK)
                                    finish()
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) { Text(stringResource(id = android.R.string.ok)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LimitField(
    label: String,
    value: String,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

/* uick SHA-256 here too (for unit test!) */
private fun ByteArray.sha256(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { "%02x".format(it) }
