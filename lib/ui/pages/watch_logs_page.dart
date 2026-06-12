import 'package:flutter/material.dart';

import '../widgets/app_drawer.dart';

class WatchLogsPage extends StatelessWidget {
  const WatchLogsPage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      drawer: const AppDrawer(),

      appBar: AppBar(
        title: const Text('Watch Logs'),
      ),

      body: ListView(
        children: const [
          ListTile(
            title: Text('[BLE] Waiting for connection'),
          ),
          ListTile(
            title: Text('[SYS] Ready'),
          ),
        ],
      ),
    );
  }
}