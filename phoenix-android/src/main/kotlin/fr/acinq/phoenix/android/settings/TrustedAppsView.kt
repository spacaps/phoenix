/*
 * TrustedAppsView – lists stored IPC messages and lets the user:
 *   • start / stop TrustedIpcService (foreground)
 *   • send a demo “persist message” call via Binder
 *
 * Place in: app/src/main/java/fr/acinq/phoenix/android/settings
 */

package fr.acinq.phoenix.android.settings

import android.content.*
import android.os.IBinder
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.lightning.ITrustedPaymentIpcV1
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.Card
import fr.acinq.phoenix.android.components.DefaultScreenHeader
import fr.acinq.phoenix.android.components.DefaultScreenLayout
import fr.acinq.phoenix.android.components.PhoenixIcon
import fr.acinq.phoenix.android.components.settings.Setting
import fr.acinq.phoenix.android.services.TrustedIpcService
import fr.acinq.phoenix.android.utils.negativeColor
import fr.acinq.phoenix.android.utils.positiveColor
import kotlinx.coroutines.delay

/* --------------------------------------------------------- */

@Composable
fun TrustedAppsView(onBackClick: () -> Unit) {

    val ctx = LocalContext.current
    val serviceRunning by rememberIpcRunning()

    /* Repository with DataStore-backed messages */
    val repo = (ctx.applicationContext as PhoenixApplication).trustedAppsRepo
    val messages by repo.messages.collectAsState(initial = emptyList())

    /* Binder reference (null until bound) */
    var binder by remember { mutableStateOf<ITrustedPaymentIpcV1?>(null) }

    DisposableEffect(serviceRunning) {
        var conn: ServiceConnection? = null
        if (serviceRunning) {
            conn = object : ServiceConnection {
                override fun onServiceConnected(c: ComponentName?, ib: IBinder?) {
                    binder = ITrustedPaymentIpcV1.Stub.asInterface(ib)
                }
                override fun onServiceDisconnected(c: ComponentName?) {
                    binder = null
                }
            }
            ctx.bindService(
                Intent(ctx, TrustedIpcService::class.java),
                conn,
                Context.BIND_AUTO_CREATE
            )
        }
        onDispose {
            conn?.let { runCatching { ctx.unbindService(it) } }
            binder = null
        }
    }

    DefaultScreenLayout {

        /* header */
        DefaultScreenHeader(
            onBackClick = onBackClick,
            title = stringResource(R.string.trusted_apps_title)
        )

        /* description */
        Card(Modifier.padding(16.dp)) {
            Text(text = stringResource(R.string.trusted_apps_list_header))
        }

        /* start / stop toggle */
        Card {
            Setting(
                title = if (serviceRunning)
                    stringResource(R.string.trusted_apps_service_running)
                else
                    stringResource(R.string.trusted_apps_service_stopped),
                leadingIcon = {
                    PhoenixIcon(
                        R.drawable.ic_tool,
                        tint = if (serviceRunning) positiveColor else negativeColor
                    )
                },
                trailingIcon = {
                    Text(
                        stringResource(
                            if (serviceRunning)
                                R.string.trusted_apps_service_stop_button
                            else
                                R.string.trusted_apps_service_start_button
                        )
                    )
                },
                onClick = {
                    if (serviceRunning) TrustedIpcService.stop(ctx)
                    else TrustedIpcService.start(ctx)
                }
            )
        }

        Spacer(Modifier.height(8.dp))

        /* demo “persist message” action (enabled only when bound) */
        Card {
            Setting(
                title = "Add test message",
                enabled = binder != null,
                leadingIcon = { PhoenixIcon(R.drawable.ic_tool) },
                onClick = {
                    binder?.persistMessage("Demo @ ${System.currentTimeMillis()}")
                }
            )
        }

        Spacer(Modifier.height(8.dp))

        /* list of stored messages */
        Card {
            if (messages.isEmpty()) {
                Setting(
                    title = stringResource(R.string.trusted_apps_empty),
                    leadingIcon = { PhoenixIcon(R.drawable.ic_trust_badge) },
                    enabled = false
                )
            } else {
                messages.forEach { msg ->
                    Setting(
                        title = msg,
                        leadingIcon = { PhoenixIcon(R.drawable.ic_trust_badge) },
                        maxTitleLines = 1,
                        enabled = false
                    )
                }
            }
        }
    }
}

/* -------- helper: poll running-services list -------- */

@Composable
fun rememberIpcRunning(): State<Boolean> {
    val ctx = LocalContext.current.applicationContext
    return produceState(false) {
        while (true) {
            value = isServiceRunning(ctx)
            delay(1_000)
        }
    }
}

private fun isServiceRunning(ctx: Context): Boolean {
    val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
    return am.getRunningServices(Int.MAX_VALUE).any {
        it.foreground && it.service.className == TrustedIpcService::class.qualifiedName
    }
}
