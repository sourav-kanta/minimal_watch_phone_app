package com.example.mw_watch_companion.services

import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import android.content.Intent
import android.os.Build

import com.example.mw_watch_companion.BLE.BleConnectionManager
import android.util.Log

class MWCompanionService : CompanionDeviceService() {

    override fun onCreate() {
        super.onCreate()
        Log.i("Companion service", "Started service")
    }

    override fun onDeviceAppeared(associationInfo: AssociationInfo) {        
        Log.i("Comapanion_service", "Device appeared, trying to connect")
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            associationInfo.associatedDevice?.bleDevice?.device
        } else {
            null
        }
        device?.let {
            BleConnectionManager.connect(applicationContext, it)
        }
    }

    override fun onDeviceAppeared(address: String) {
        Log.i("Comapanion_service", "Device appeared, deprecated")
        BleConnectionManager.connect(applicationContext, address)
    }

    override fun onDeviceDisappeared(associationInfo: AssociationInfo) {
        Log.i("Comapanion_service", "Device disappeared")
        BleConnectionManager.closeGatt()
    }
    override fun onDeviceDisappeared(address:String) {
        BleConnectionManager.closeGatt()
    }
}