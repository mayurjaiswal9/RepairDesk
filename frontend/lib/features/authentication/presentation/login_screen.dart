import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../shared/theme/app_theme.dart';

/// Login screen placeholder — full implementation in AUTH-020.
/// Wires to the auth API and session controller.
class LoginScreen extends ConsumerWidget {
  const LoginScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Scaffold(
      backgroundColor: AppTheme.background,
      body: SafeArea(
        child: Center(
          child: Padding(
            padding: const EdgeInsets.all(AppTheme.spaceL),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                // Logo / branding placeholder
                Container(
                  width: 80,
                  height: 80,
                  decoration: BoxDecoration(
                    color: AppTheme.primary,
                    borderRadius: BorderRadius.circular(AppTheme.radiusL),
                  ),
                  child: const Icon(Icons.build_circle,
                      size: 48, color: AppTheme.onPrimary),
                ),
                const SizedBox(height: AppTheme.spaceL),
                Text('RepairDesk',
                    style: Theme.of(context).textTheme.titleLarge),
                Text('ACE Telecom',
                    style: Theme.of(context).textTheme.bodySmall),
                const SizedBox(height: AppTheme.spaceXL),
                // Login form scaffold — full UI in AUTH-020.
                FilledButton(
                  onPressed: () {},
                  child: const Text('Sign In'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
