package com.example.pingclient

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.widget.Toast

@Composable
fun PingClientScreen(
    walletReady: Boolean,
    onPay: (String) -> Unit,
    onSetup: () -> Unit
) {
    val ctx = LocalContext.current
    var invoice by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.Center)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        OutlinedTextField(
            value = invoice,
            onValueChange = { invoice = it },
            label = { Text("BOLT-11 invoice") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            enabled = walletReady && invoice.isNotBlank(),
            onClick = {
                onPay(invoice.trim())
                invoice = ""
                Toast.makeText(ctx, "Invoice sent to wallet", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Pay via Quick-Pay") }

        Button(
            enabled = walletReady,
            onClick = onSetup,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Set up Quick-Pay") }

        if (!walletReady) Text("Waiting for wallet…")
    }
}
