import 'package:flutter/material.dart';

import '../widgets/app_drawer.dart';
import '../widgets/setting_row.dart';

class SetupWatchPage extends StatelessWidget {
  const SetupWatchPage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      drawer: const AppDrawer(),

      appBar: AppBar(
        title: const Text('Setup Watch'),
      ),

      body: const Column(
        children: [
          SettingRow(
            title: 'Device Name',
            value: '--',
          ),

          SettingRow(
            title: 'Firmware',
            value: '--',
          ),

          SettingRow(
            title: 'Connection',
            value: 'Disconnected',
          ),
        ],
      ),
    );
  }
}