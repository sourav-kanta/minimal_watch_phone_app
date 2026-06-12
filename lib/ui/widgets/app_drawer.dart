import 'package:flutter/material.dart';

import '../pages/home_page.dart';
import '../pages/settings_page.dart';
import '../pages/setup_watch_page.dart';
import '../pages/watch_logs_page.dart';

class AppDrawer extends StatelessWidget {
  const AppDrawer({super.key});

  @override
  Widget build(BuildContext context) {
    return Drawer(
      child: ListView(
        padding: EdgeInsets.zero,
        children: [
          Container(
            padding: const EdgeInsets.only(
              left: 16,
              top: 48,
              bottom: 16,
            ),
            child: const Text(
              'Minimal Watch',
              style: TextStyle(
                fontSize: 22,
                fontWeight: FontWeight.bold,
              ),
            ),
          ),

          _tile(
            context,
            Icons.home_outlined,
            'Home',
            const HomePage(),
          ),

          _tile(
            context,
            Icons.watch_outlined,
            'Setup Watch',
            const SetupWatchPage(),
          ),

          _tile(
            context,
            Icons.terminal_outlined,
            'Watch Logs',
            const WatchLogsPage(),
          ),

          _tile(
            context,
            Icons.settings_outlined,
            'Settings',
            const SettingsPage(),
          ),
        ],
      ),
    );
  }

  Widget _tile(
    BuildContext context,
    IconData icon,
    String title,
    Widget page,
  ) {
    return ListTile(
      leading: Icon(icon),
      title: Text(title),
      onTap: () {
        Navigator.of(context).pushReplacement(
          MaterialPageRoute(
            builder: (_) => page,
          ),
        );
      },
    );
  }
}