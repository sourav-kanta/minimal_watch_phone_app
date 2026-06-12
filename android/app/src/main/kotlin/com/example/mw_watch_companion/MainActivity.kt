package com.example.mw_watch_companion

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

import com.example.mw_watch_companion.bridge.BlePlugin

class MainActivity : FlutterActivity() {
    override fun configureFlutterEngine(flutterEngine : FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        flutterEngine.plugins.add(BlePlugin())
    }
}
