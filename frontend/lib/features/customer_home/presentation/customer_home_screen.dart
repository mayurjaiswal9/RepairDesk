import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../shared/theme/app_theme.dart';
import '../../requests/presentation/new_request_screen.dart';
import '../data/customer_home_repository.dart';

final _myRepairsProvider = FutureProvider.autoDispose(
    (ref) => ref.watch(customerHomeRepositoryProvider).myRepairs());

class CustomerHomeScreen extends ConsumerWidget {
  const CustomerHomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final repairsAsync = ref.watch(_myRepairsProvider);

    return Scaffold(
      body: RefreshIndicator(
        onRefresh: () => ref.refresh(_myRepairsProvider.future),
        child: repairsAsync.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (e, _) => ListView(
            children: [
              const SizedBox(height: 120),
              Center(
                child: Column(
                  children: [
                    const Icon(Icons.error_outline,
                        size: 48, color: AppTheme.error),
                    const SizedBox(height: AppTheme.spaceM),
                    Text(e.toString(),
                        style: const TextStyle(color: AppTheme.error),
                        textAlign: TextAlign.center),
                    const SizedBox(height: AppTheme.spaceM),
                    FilledButton(
                        onPressed: () => ref.invalidate(_myRepairsProvider),
                        child: const Text('Retry')),
                  ],
                ),
              ),
            ],
          ),
          data: (items) => items.isEmpty
              ? _buildEmpty(context)
              : ListView.separated(
                  padding: const EdgeInsets.all(AppTheme.spaceM),
                  itemCount: items.length,
                  separatorBuilder: (context, index) =>
                      const SizedBox(height: AppTheme.spaceS),
                  itemBuilder: (_, i) => _RepairCard(item: items[i]),
                ),
        ),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () async {
          final submitted = await Navigator.of(context).push<bool>(
            MaterialPageRoute(
                builder: (_) => const NewRequestScreen()),
          );
          if (submitted == true) ref.invalidate(_myRepairsProvider);
        },
        icon: const Icon(Icons.add),
        label: const Text('New Request'),
      ),
    );
  }

  Widget _buildEmpty(BuildContext context) {
    return ListView(
      children: [
        const SizedBox(height: 100),
        Column(
          children: [
            const Icon(Icons.build_circle_outlined,
                size: 80, color: AppTheme.textSecondary),
            const SizedBox(height: AppTheme.spaceM),
            Text('No repairs yet',
                style: Theme.of(context).textTheme.titleLarge),
            const SizedBox(height: AppTheme.spaceXS),
            const Text(
              'Tap the button below to submit\nyour first repair request.',
              style: TextStyle(color: AppTheme.textSecondary),
              textAlign: TextAlign.center,
            ),
          ],
        ),
      ],
    );
  }
}

class _RepairCard extends StatelessWidget {
  const _RepairCard({required this.item});
  final RepairListItem item;

  static const _statusColors = {
    'Submitted — Under Review': AppTheme.warning,
    'More Information Needed': AppTheme.secondary,
    'Accepted — Being Prepared': AppTheme.primary,
    'Assigned to Technician': AppTheme.primary,
    'Appointment Scheduled': AppTheme.primary,
    'Repair In Progress': AppTheme.success,
    'Waiting for Parts': AppTheme.warning,
    'Repair Completed': AppTheme.success,
    'Not Accepted': AppTheme.error,
    'Cancelled': AppTheme.textSecondary,
  };

  Color get _statusColor =>
      _statusColors[item.friendlyStatus] ?? AppTheme.textSecondary;

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: EdgeInsets.zero,
      child: Padding(
        padding: const EdgeInsets.all(AppTheme.spaceM),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    item.referenceNumber,
                    style: Theme.of(context).textTheme.titleMedium?.copyWith(
                          color: AppTheme.primary,
                          fontWeight: FontWeight.bold,
                        ),
                  ),
                ),
                const Icon(Icons.chevron_right, color: AppTheme.textSecondary),
              ],
            ),
            const SizedBox(height: AppTheme.spaceXS),
            Text(item.deviceCategoryName,
                style: Theme.of(context).textTheme.bodyMedium),
            const SizedBox(height: AppTheme.spaceS),
            Container(
              padding:
                  const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
              decoration: BoxDecoration(
                color: _statusColor.withValues(alpha: 0.10),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(
                    color: _statusColor.withValues(alpha: 0.35)),
              ),
              child: Text(
                item.friendlyStatus,
                style: TextStyle(
                  fontSize: 12,
                  color: _statusColor,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
