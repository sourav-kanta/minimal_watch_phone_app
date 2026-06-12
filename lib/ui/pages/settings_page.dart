import 'package:flutter/material.dart';

import '../widgets/app_drawer.dart';
import '../widgets/setting_row.dart';
import '../widgets/setting_action_row.dart';
import '../../native_bridge/native_bridge.dart';

class SettingsPage extends StatelessWidget {
  const SettingsPage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      drawer: const AppDrawer(),

      appBar: AppBar(
        title: const Text('Settings'),
      ),

      body: Column(
        children: [
          const SettingRow(
            title: 'Theme',
            value: 'Dark',
          ),

          const SettingRow(
            title: 'Debug Logs',
            value: 'Off',
          ),

          const SettingRow(
            title: 'Version',
            value: '0.1.0',
          ),
          SettingActionRow(
            title: 'New Device',
            onClick: NativeBleClient.pairAndAssociateNewDevice,
            value: 'Connect',
          ),
        ],
      ),
    );
  }
}