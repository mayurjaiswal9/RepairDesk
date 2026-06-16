import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/auth/session_controller.dart';
import '../../../shared/theme/app_theme.dart';

/// Dashboard placeholder — full role-based implementation in DASH tasks.
class DashboardScreen extends ConsumerWidget {
  const DashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final session = ref.watch(sessionControllerProvider);
    return Scaffold(
      appBar: AppBar(
        title: const Text('RepairDesk'),
        actions: [
          IconButton(
            icon: const Icon(Icons.logout),
            onPressed: () =>
                ref.read(sessionControllerProvider.notifier).signOut(),
          ),
        ],
      ),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.dashboard, size: 64, color: AppTheme.primary),
            const SizedBox(height: AppTheme.spaceM),
            Text('Dashboard', style: Theme.of(context).textTheme.titleLarge),
            Text('Role: ${session.role ?? 'unknown'}',
                style: Theme.of(context).textTheme.bodySmall),
            const SizedBox(height: AppTheme.spaceM),
            const Text('Full dashboard coming in DASH tasks.',
                textAlign: TextAlign.center),
          ],
        ),
      ),
    );
  }
}
