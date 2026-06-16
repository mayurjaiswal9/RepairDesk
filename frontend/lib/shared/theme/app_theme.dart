import 'package:flutter/material.dart';

/// ACE Telecom brand theme tokens and Material 3 theme configuration.
/// All colours, typography, and spacing are defined here so the rest of
/// the app has a single source of truth (THEME-001).
class AppTheme {
  AppTheme._();

  // ---------------------------------------------------------------------------
  // Brand colour palette
  // ---------------------------------------------------------------------------
  static const Color primary = Color(0xFF1565C0);       // ACE Telecom blue
  static const Color primaryDark = Color(0xFF003C8F);
  static const Color primaryLight = Color(0xFF5E92F3);
  static const Color secondary = Color(0xFF00897B);     // Teal accent
  static const Color error = Color(0xFFD32F2F);
  static const Color success = Color(0xFF388E3C);
  static const Color warning = Color(0xFFF57C00);
  static const Color surface = Color(0xFFFFFFFF);
  static const Color background = Color(0xFFF5F5F5);
  static const Color onPrimary = Color(0xFFFFFFFF);
  static const Color textPrimary = Color(0xFF212121);
  static const Color textSecondary = Color(0xFF757575);
  static const Color divider = Color(0xFFBDBDBD);

  // ---------------------------------------------------------------------------
  // Spacing
  // ---------------------------------------------------------------------------
  static const double spaceXS = 4.0;
  static const double spaceS  = 8.0;
  static const double spaceM  = 16.0;
  static const double spaceL  = 24.0;
  static const double spaceXL = 32.0;

  // ---------------------------------------------------------------------------
  // Radius
  // ---------------------------------------------------------------------------
  static const double radiusS = 4.0;
  static const double radiusM = 8.0;
  static const double radiusL = 16.0;

  // ---------------------------------------------------------------------------
  // Theme factory
  // ---------------------------------------------------------------------------
  static ThemeData light() {
    final colorScheme = ColorScheme.fromSeed(
      seedColor: primary,
      primary: primary,
      secondary: secondary,
      error: error,
      surface: surface,
      brightness: Brightness.light,
    );

    return ThemeData(
      useMaterial3: true,
      colorScheme: colorScheme,
      scaffoldBackgroundColor: background,
      appBarTheme: const AppBarTheme(
        backgroundColor: primary,
        foregroundColor: onPrimary,
        elevation: 0,
        centerTitle: false,
      ),
      cardTheme: CardThemeData(
        elevation: 1,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(radiusM),
        ),
        margin: const EdgeInsets.symmetric(
            horizontal: spaceM, vertical: spaceS),
      ),
      inputDecorationTheme: InputDecorationTheme(
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(radiusM),
        ),
        contentPadding: const EdgeInsets.symmetric(
            horizontal: spaceM, vertical: spaceM),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          minimumSize: const Size.fromHeight(48),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(radiusM),
          ),
        ),
      ),
      textTheme: const TextTheme(
        titleLarge:  TextStyle(fontSize: 20, fontWeight: FontWeight.w600, color: textPrimary),
        titleMedium: TextStyle(fontSize: 16, fontWeight: FontWeight.w600, color: textPrimary),
        bodyLarge:   TextStyle(fontSize: 16, color: textPrimary),
        bodyMedium:  TextStyle(fontSize: 14, color: textPrimary),
        bodySmall:   TextStyle(fontSize: 12, color: textSecondary),
        labelLarge:  TextStyle(fontSize: 14, fontWeight: FontWeight.w600),
      ),
      dividerTheme: const DividerThemeData(color: divider, thickness: 1),
    );
  }
}
