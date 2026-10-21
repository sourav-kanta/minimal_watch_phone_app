import 'package:flutter/services.dart';
import 'package:flutter/foundation.dart';

class NativeBleClient {
  static const MethodChannel _method = MethodChannel(
    'com.example.mw_watch_companion/methods',
  );
  static const EventChannel _event = EventChannel(
    'com.example.mw_watch_companion/events',
  );

  static Future<Map<String, bool>?> requestStartupPermissions() async {
    try {
      final result = await _method.invokeMapMethod<String, dynamic>(
        'requestStartupPermissions',
      );
      if (result == null) return null;
      return result.map((key, value) => MapEntry(key, value == true));
    } on PlatformException catch (e) {
      debugPrint('Permission request failed: ${e.message}');
      return null;
    } on MissingPluginException catch (e) {
      debugPrint('Native permission method unavailable: ${e.message}');
      return null;
    }
  }

  static Future<void> startNavigation() async {
    await _method.invokeMethod('startNavigation', {
      'latitude': 22.491874,
      'longitude': 22.491874,
    });
  }

  static Future<void> stopNavigation() async {
    await _method.invokeMethod('stopNavigation');
  }

  static Future<String?> pairAndAssociateNewDevice() async {
    try {
      final String? macAddress = await _method.invokeMethod(
        'startCompanionAssociation',
      );
      return macAddress;
    } on PlatformException catch (e) {
      debugPrint('Companion pairing pipeline failure: ${e.message}');
      return null;
    }
  }

  Future<void> disconnect() async {
    await _method.invokeMethod('disconnect');
  }

  Stream<dynamic> get bleLifecycleStream => _event.receiveBroadcastStream();
}
