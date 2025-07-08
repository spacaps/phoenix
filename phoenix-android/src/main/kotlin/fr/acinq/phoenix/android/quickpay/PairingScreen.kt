package fr.acinq.phoenix.android.quickpay

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PairingScreen(
    appLabel: String,
    packageName: String,
    certSha: String,
    onConfirm: (perDaySat: Long) -> Unit,
    onCancel: () -> Unit
) {
    var perDaySat by remember { mutableStateOf("500_000") }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Quick-Pay pairing", style = MaterialTheme.typography.titleLarge)
                Text(appLabel, style = MaterialTheme.typography.titleMedium)
                Text(packageName, style = MaterialTheme.typography.bodySmall)

                OutlinedTextField(
                    value = certSha,
                    onValueChange = {},
                    readOnly = true,
                    leadingIcon = { Icon(Icons.Filled.Info, null) },
                    label = { Text("App certificate (SHA-256)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))
                Text("Per-day spending limit (sats)", style = MaterialTheme.typography.titleSmall)

                OutlinedTextField(
                    value = perDaySat,
                    onValueChange = { perDaySat = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.weight(1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) { Text("Decline") }

                    Button(
                        onClick = {
                            val day = perDaySat.filter(Char::isDigit).toLongOrNull() ?: 100_000
                            onConfirm(day)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) { Text("Allow") }
                }
            }
        }
    }
}
