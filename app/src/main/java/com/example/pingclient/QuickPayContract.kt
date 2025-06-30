package com.example.pingclient

object QuickPayContract {
    const val ACTION_SETUP_TRUST      = "org.androidln.action.SETUP_TRUST"
    const val EXTRA_PACKAGE_NAME      = "pkgName"
    const val EXTRA_APP_NAME          = "appName"
    const val EXTRA_REQUESTED_LIMIT   = "dailyLimit"      // Long (sats/day)
}
