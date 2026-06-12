import 'package:flutter/material.dart';

import 'theme/app_theme.dart';
import 'ui/pages/home_page.dart';

void main() {
  runApp(
    const MinimalWatchApp(),
  );
}

class MinimalWatchApp extends StatelessWidget {
  const MinimalWatchApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Minimal Watch',

      debugShowCheckedModeBanner: false,

      theme: AppTheme.buildTheme(),

      home: const HomePage(),
    );
  }
}