import 'package:flutter/material.dart';

class AppTheme {
  static const primary = Color(0xFF12141C);
  static const secondary = Color(0xFF1E2230);
  static const tertiary = Color(0xFF2B3044);

  static const accent = Color(0xFFA8C7FA);
  static const focusBg = Color(0xFF2A3B5C);

  static const textPrimary = Color(0xFFE3E2E6);
  static const textSecondary = Color(0xFFC4C6D0);

  static const success = Color(0xFF00C600);

  static ThemeData buildTheme() {
    return ThemeData(
      brightness: Brightness.dark,

      scaffoldBackgroundColor: primary,

      colorScheme: const ColorScheme.dark(
        primary: accent,
        surface: secondary,
      ),

      appBarTheme: const AppBarTheme(
        backgroundColor: secondary,
        foregroundColor: textPrimary,
        centerTitle: false,
      ),

      drawerTheme: const DrawerThemeData(
        backgroundColor: secondary,
      ),

      cardTheme: CardThemeData(
        color: tertiary,
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(12),
        ),
      ),
    );
  }
}