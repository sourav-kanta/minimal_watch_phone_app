package com.example.mw_watch_companion.common

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.content.Context
import androidx.core.content.ContextCompat

class PermissionManager(private val context: Context) {

    companion object {
        const val REQUEST_CODE = 8999
    }

    fun hasPermission(permission: String): Boolean {
        if (permission == Manifest.permission.BLUETOOTH_CONNECT &&
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S
        ) {
            return true
        }

        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasLocationPermission(): Boolean {
        return hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
            hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    fun hasBluetoothConnectPermission(): Boolean {
        return hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
    }

    fun requestStartupPermissions() {
        val activity = context as? Activity ?: return
        val permissions = mutableListOf<String>()

        if (!hasLocationPermission()) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (!hasBluetoothConnectPermission()) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        if (permissions.isNotEmpty()) {
            activity.requestPermissions(
                permissions.toTypedArray(),
                REQUEST_CODE
            )
        }
    }
}