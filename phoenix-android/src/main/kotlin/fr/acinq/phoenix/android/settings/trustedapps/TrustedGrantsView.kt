package fr.acinq.phoenix.android.settings.trustedapps

import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fr.acinq.phoenix.android.R
import fr.acinq.phoenix.android.components.PhoenixIcon
import fr.acinq.phoenix.android.components.layouts.Card
import fr.acinq.phoenix.android.components.settings.Setting
import fr.acinq.phoenix.android.utils.datastore.TrustedAppsRepository
import kotlinx.coroutines.launch

@Composable
fun TrustedGrantsView(
    grants: Map<String, TrustedAppsRepository.GrantInfo>,
    repo: TrustedAppsRepository
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val pm   = ctx.packageManager

    /** Small helper to resolve label once (expensive) */
    @Composable
    fun rememberLabel(pkg: String): String = remember(pkg) {
        runCatching {
            pm.getApplicationLabel(
                pm.getApplicationInfo(pkg, 0)
            ).toString()
        }.getOrDefault(pkg)
    }

    if (grants.isEmpty()) return

    Spacer(Modifier.height(8.dp))

    Card {
        Setting(
            title = "Trusted Apps",
            enabled = false,
            leadingIcon = { PhoenixIcon(R.drawable.ic_trust_badge) }
        )

        grants.entries.sortedBy { it.key }.forEach { (pkg, info) ->
            val label = rememberLabel(pkg)

            Setting(
                title = label,
                subtitle = {
                    Text(
                        info.perDayMax.toString()
                    )
                },
                leadingIcon = { PhoenixIcon(R.drawable.ic_tool) },
                trailingIcon = {
                    Text("revoke")
                },
                onClick = {
                    scope.launch { repo.revokeGrant(pkg) }
                },
                maxTitleLines = 1
            )
        }
    }
}
