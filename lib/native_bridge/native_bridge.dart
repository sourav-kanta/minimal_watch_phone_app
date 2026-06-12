import 'package:flutter/services.dart';

class NativeBleClient {
  static const MethodChannel _method = MethodChannel('com.example.mw_watch_companion/methods');
  static const EventChannel _event = EventChannel('com.example.mw_watch_companion/events');


  static Future<String?> pairAndAssociateNewDevice() async {
    try {
      final String? macAddress = await _method.invokeMethod('startCompanionAssociation');
      return macAddress;
    } on PlatformException catch (e) {
      print('Companion pairing pipeline failure: ${e.message}');
      return null;
    }
  }

  Future<void> disconnect() async {
    await _method.invokeMethod('disconnect');
  }

  Stream<dynamic> get bleLifecycleStream => _event.receiveBroadcastStream();
}