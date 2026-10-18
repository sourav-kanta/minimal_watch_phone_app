import 'package:flutter/material.dart';
import '../../native_bridge/native_bridge.dart';
import 'home_page.dart';

class StartupPage extends StatefulWidget {
  const StartupPage({super.key});

  @override
  State<StartupPage> createState() => _StartupPageState();
}

class _StartupPageState extends State<StartupPage> {
  late final Future<Map<String, bool>?> _permissionsFuture;

  @override
  void initState() {
    super.initState();
    _permissionsFuture = NativeBleClient.requestStartupPermissions();
  }

  @override
  Widget build(BuildContext context) {
    return FutureBuilder<Map<String, bool>?>(
      future: _permissionsFuture,
      builder: (context, snapshot) {
        if (snapshot.connectionState != ConnectionState.done) {
          return const Scaffold(
            body: Center(child: CircularProgressIndicator()),
          );
        }

        final permissions = snapshot.data;

        if (permissions == null) {
          return const StartupErrorPage(
            message: 'Unable to request permissions. Please restart the app.',
          );
        }

        final locationGranted = permissions['location'] == true;
        final bluetoothGranted = permissions['bluetoothConnect'] == true;

        if (!locationGranted || !bluetoothGranted) {
          return const StartupErrorPage(
            message:
                'Location and Bluetooth permissions are required to use this app.',
          );
        }

        return const HomePage();
      },
    );
  }
}

class StartupErrorPage extends StatelessWidget {
  final String message;

  const StartupErrorPage({super.key, required this.message});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Text(message, textAlign: TextAlign.center),
        ),
      ),
    );
  }
}
