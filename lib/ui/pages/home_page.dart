import 'package:flutter/material.dart';

import '../widgets/app_drawer.dart';
import '../widgets/setting_row.dart';

class HomePage extends StatelessWidget {
  const HomePage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      drawer: const AppDrawer(),

      appBar: AppBar(
        title: const Text('Minimal Watch'),
      ),

      body: const Column(
        children: [
          SettingRow(
            title: 'Watch Status',
            value: 'Disconnected',
          ),

          SettingRow(
            title: 'Firmware',
            value: '--',
          ),

          SettingRow(
            title: 'Battery',
            value: '--%',
          ),
        ],
      ),
    );
  }
}