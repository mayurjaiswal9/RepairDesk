import 'package:flutter/material.dart';

/// ACE Telecom brand theme tokens and Material 3 theme configuration.
/// All colours, typography, and spacing are defined here so the rest of
/// the app has a single source of truth (THEME-001).
class AppTheme {
  AppTheme._();

  // ---------------------------------------------------------------------------
  // Brand colour palette
  // ---------------------------------------------------------------------------
  static const Color primary = Color(0xFF1552CC);       // ACE Telecom blue
  static const Color primaryDark = Color(0xFF0F3FA3);
  static const Color primaryLight = Color(0xFF4C86E8);
  static const Color primarySoft = Color(0xFFE8F1FD);
  static const Color brandNavy = Color(0xFF14306E);     // Logo wordmark blue
  static const Color brandAccent = Color(0xFFC2185B);   // Logo "TELECOM" magenta
  static const Color secondary = Color(0xFF00897B);     // Teal accent
  static const Color error = Color(0xFFD32F2F);
  static const Color success = Color(0xFF388E3C);
  static const Color warning = Color(0xFFF57C00);
  static const Color surface = Color(0xFFFFFFFF);
  static const Color background = Color(0xFFF6FAFF);
  static const Color backgroundMid = Color(0xFFE3EEFB);
  static const Color waveLight = Color(0xFFE4EEFB);
  static const Color waveMid = Color(0xFFD0E2F7);
  static const Color fieldBorder = Color(0xFFD3E1F5);
  static const Color onPrimary = Color(0xFFFFFFFF);
  static const Color textPrimary = Color(0xFF1B2A41);
  static const Color textSecondary = Color(0xFF6B7A90);
  static const Color divider = Color(0xFFD9E4F3);

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

  /// Shared shape for text fields and primary buttons.
  static const double radiusControl = 12.0;

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
        filled: true,
        fillColor: surface,
        hintStyle: const TextStyle(color: textSecondary, fontSize: 16),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(radiusControl),
          borderSide: const BorderSide(color: fieldBorder),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(radiusControl),
          borderSide: const BorderSide(color: fieldBorder),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(radiusControl),
          borderSide: const BorderSide(color: primary, width: 1.6),
        ),
        prefixIconColor: primary,
        suffixIconColor: primary,
        contentPadding: const EdgeInsets.symmetric(
            horizontal: spaceM, vertical: spaceM),
      ),
      filledButtonTheme: FilledButtonThemeData(
        style: FilledButton.styleFrom(
          minimumSize: const Size.fromHeight(52),
          backgroundColor: primary,
          foregroundColor: onPrimary,
          textStyle: const TextStyle(fontSize: 16, fontWeight: FontWeight.w700),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(radiusControl),
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
