package com.example.pingclient

import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.mutableStateOf
import org.androidln.IQuickPayV1

class MainActivity : ComponentActivity() {

    private var api: IQuickPayV1? = null
    private val walletReady = mutableStateOf(false)

    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
            try {
                /* 1)  Verify that the remote binder exposes the interface we expect.   */
                val expected = IQuickPayV1::class.java.name
                val remote   = binder.interfaceDescriptor    // may throw RemoteException

                if (remote != expected) {
                    Toast.makeText(this@MainActivity,
                        "Wallet exposes $remote, expected $expected", Toast.LENGTH_LONG).show()
                    unbindService(this)          // detach – wrong interface
                    return
                }

                /* 2)  Safe to create proxy now. */
                api = IQuickPayV1.Stub.asInterface(binder)
                walletReady.value = true

            } catch (e: SecurityException) {     // mismatching descriptor
                Toast.makeText(this@MainActivity,
                    "Wallet uses incompatible IPC interface", Toast.LENGTH_LONG).show()
            } catch (e: RemoteException) {
                Toast.makeText(this@MainActivity,
                    "Failed to query wallet binder: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            api = null
            walletReady.value = false
        }
    }

    private val pairingLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == RESULT_OK) {
            Toast.makeText(this, "Wallet paired", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Rejected by wallet", Toast.LENGTH_LONG).show();
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PingClientScreen(
                walletReady = walletReady.value,
                onPay   = { api?.payInvoice(it) },
                onSetup = { launchPairing() }
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onStart() {
        super.onStart()
        val query = Intent("org.androidln.action.BIND_PAYMENT_IPC")
        val svc = packageManager.queryIntentServices(
            query, PackageManager.ResolveInfoFlags.of(0)
        ).firstOrNull()?.serviceInfo ?: run {
            Toast.makeText(this, "No wallet found", Toast.LENGTH_LONG).show(); return
        }
        bindService(Intent(query).setClassName(svc.packageName, svc.name), conn, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop(); runCatching { unbindService(conn) }
    }

    private fun launchPairing() {
        val i = Intent(QuickPayContract.ACTION_SETUP_TRUST).apply {
            putExtra(QuickPayContract.EXTRA_PACKAGE_NAME, packageName)
            putExtra(QuickPayContract.EXTRA_APP_NAME,  applicationInfo.loadLabel(packageManager))
            putExtra(QuickPayContract.EXTRA_REQUESTED_LIMIT, 500_000L)
        }
        pairingLauncher.launch(i)
    }

}
