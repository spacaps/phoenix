package fr.acinq.phoenix.android.settings.trustedapps

import android.content.*
import android.os.IBinder
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.PhoenixApplication
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.*
import fr.acinq.phoenix.android.components.settings.Setting
import fr.acinq.phoenix.android.services.TrustedIpcService
import fr.acinq.phoenix.android.utils.negativeColor
import fr.acinq.phoenix.android.utils.positiveColor
import kotlinx.coroutines.delay
import org.androidln.IQuickPayV1
import androidx.compose.runtime.rememberCoroutineScope
import fr.acinq.phoenix.android.components.layouts.Card
import fr.acinq.phoenix.android.components.layouts.DefaultScreenHeader
import fr.acinq.phoenix.android.components.layouts.DefaultScreenLayout
import kotlinx.coroutines.launch


@Composable
fun TrustedAppsView(onBackClick: () -> Unit) {

    val ctx = LocalContext.current
    val serviceRunning by rememberIpcRunning()
    val scope = rememberCoroutineScope()

    val repo = (ctx.applicationContext as PhoenixApplication).trustedAppsRepo
    val messages by repo.messages.collectAsState(initial = emptyList())
    val grants by repo.grantsFlow.collectAsState(initial = emptyMap())

    var binder by remember { mutableStateOf<IQuickPayV1?>(null) }
    var connection by remember { mutableStateOf<ServiceConnection?>(null) }

    DisposableEffect(serviceRunning) {
        if (serviceRunning && connection == null) {
            connection = object : ServiceConnection {
                override fun onServiceConnected(c: ComponentName?, ib: IBinder?) {
                    binder = IQuickPayV1.Stub.asInterface(ib)
                }
                override fun onServiceDisconnected(c: ComponentName?) {
                    binder = null
                }
            }.also { conn ->
                ctx.bindService(
                    Intent(ctx, TrustedIpcService::class.java),
                    conn,
                    Context.BIND_AUTO_CREATE
                )
            }
        }
        onDispose {
            connection?.let { runCatching { ctx.unbindService(it) } }
            connection = null
            binder = null
        }
    }

    DefaultScreenLayout {

        DefaultScreenHeader(
            onBackClick = onBackClick,
            title = stringResource(R.string.trusted_apps_title)
        )

        Card(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.trusted_apps_list_header))
        }

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
                onClick = {
                    if (serviceRunning) {
                        connection?.let {
                            runCatching { ctx.unbindService(it) }
                            connection = null
                            binder = null
                        }
                        TrustedIpcService.disableComponent(ctx)   // no more new connections
                    } else {
                        TrustedIpcService.enableComponent(ctx)
                        TrustedIpcService.start(ctx)
                    }
                }
            )
        }

        Spacer(Modifier.height(8.dp))

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

        if (messages.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Card {
                Setting(
                    title = "Clear log",
                    leadingIcon = { PhoenixIcon(R.drawable.ic_delete) },
                    onClick = {
                        scope.launch { repo.clearMessages() }
                    }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

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
        Spacer(Modifier.height(8.dp))

        TrustedGrantsView(grants = grants, repo = repo)
    }
}

@Composable
fun rememberIpcRunning(): State<Boolean> {
    val ctx = LocalContext.current.applicationContext
    return produceState(initialValue = false) {
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
