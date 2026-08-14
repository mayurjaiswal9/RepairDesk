import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../shared/theme/app_theme.dart';
import '../data/admin_repository.dart';
import 'settings_edit_screen.dart';

final _settingsProvider = FutureProvider.autoDispose(
    (ref) => ref.watch(adminRepositoryProvider).schedulingConfig());

class SettingsScreen extends ConsumerWidget {
  const SettingsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final configAsync = ref.watch(_settingsProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Business Settings'),
        actions: [
          IconButton(
            icon: const Icon(Icons.edit_outlined),
            tooltip: 'Edit',
            onPressed: () async {
              final config = ref.read(_settingsProvider).value;
              if (config == null) return;
              final result = await Navigator.of(context).push<bool>(
                MaterialPageRoute(
                    builder: (_) => SettingsEditScreen(current: config)),
              );
              if (result == true) ref.invalidate(_settingsProvider);
            },
          ),
        ],
      ),
      body: configAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Icon(Icons.error_outline,
                  size: 48, color: AppTheme.error),
              const SizedBox(height: AppTheme.spaceM),
              Text(e.toString(),
                  style: const TextStyle(color: AppTheme.error),
                  textAlign: TextAlign.center),
              FilledButton(
                  onPressed: () => ref.invalidate(_settingsProvider),
                  child: const Text('Retry')),
            ],
          ),
        ),
        data: (config) => RefreshIndicator(
          onRefresh: () => ref.refresh(_settingsProvider.future),
          child: ListView(
            padding: const EdgeInsets.all(AppTheme.spaceM),
            children: [
              // ── Scheduling ─────────────────────────────────────────
              _Section(
                title: 'Scheduling',
                icon: Icons.calendar_today_outlined,
                children: [
                  _InfoRow(
                    label: 'Slot duration',
                    value: '${config.slotDurationMinutes} minutes',
                  ),
                  _InfoRow(
                    label: 'Booking horizon',
                    value: '${config.bookingHorizonDays} days ahead',
                  ),
                ],
              ),
              const SizedBox(height: AppTheme.spaceM),

              // ── Working hours ──────────────────────────────────────
              _Section(
                title: 'Working Hours',
                icon: Icons.access_time_outlined,
                children: [
                  if (config.workingHours.isEmpty)
                    const Padding(
                      padding: EdgeInsets.symmetric(vertical: AppTheme.spaceM),
                      child: Text(
                        'No working hours configured.',
                        style:
                            TextStyle(color: AppTheme.textSecondary),
                      ),
                    )
                  else
                    ...config.workingHours.map((wh) => _WorkingHourRow(wh: wh)),
                ],
              ),

              const SizedBox(height: AppTheme.spaceL),
              const _InfoBanner(),
            ],
          ),
        ),
      ),
    );
  }
}

class _Section extends StatelessWidget {
  const _Section(
      {required this.title,
      required this.icon,
      required this.children});
  final String title;
  final IconData icon;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Icon(icon, color: AppTheme.primary, size: 18),
            const SizedBox(width: AppTheme.spaceS),
            Text(title,
                style: Theme.of(context)
                    .textTheme
                    .titleMedium
                    ?.copyWith(color: AppTheme.primary)),
          ],
        ),
        const Divider(),
        ...children,
      ],
    );
  }
}

class _InfoRow extends StatelessWidget {
  const _InfoRow({required this.label, required this.value});
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: AppTheme.spaceXS),
      child: Row(
        children: [
          SizedBox(
              width: 130,
              child: Text(label,
                  style: const TextStyle(
                      color: AppTheme.textSecondary, fontSize: 13))),
          Expanded(
              child: Text(value,
                  style:
                      const TextStyle(fontWeight: FontWeight.w500))),
        ],
      ),
    );
  }
}

class _WorkingHourRow extends StatelessWidget {
  const _WorkingHourRow({required this.wh});
  final WorkingHoursConfig wh;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: AppTheme.spaceS),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.center,
        children: [
          SizedBox(
            width: 130,
            child: Text(wh.dayName,
                style: const TextStyle(
                    color: AppTheme.textSecondary, fontSize: 13)),
          ),
          Expanded(
            child: Align(
              alignment: Alignment.centerLeft,
              child: wh.isClosed
                  ? const Chip(
                      label: Text('Closed',
                          style: TextStyle(fontSize: 12)),
                      backgroundColor: AppTheme.divider,
                      padding: EdgeInsets.symmetric(horizontal: 4),
                      materialTapTargetSize:
                          MaterialTapTargetSize.shrinkWrap,
                    )
                  : Text(
                      '${_fmt(wh.openTime)} – ${_fmt(wh.closeTime)}',
                      style: const TextStyle(fontWeight: FontWeight.w500),
                    ),
            ),
          ),
        ],
      ),
    );
  }

  static String _fmt(String? t) {
    if (t == null) return '--:--';
    // Backend sends "HH:mm:ss" or "HH:mm", strip seconds
    final parts = t.split(':');
    if (parts.length >= 2) {
      final h = int.tryParse(parts[0]) ?? 0;
      final m = parts[1];
      final period = h < 12 ? 'AM' : 'PM';
      final displayH = h == 0 ? 12 : (h > 12 ? h - 12 : h);
      return '$displayH:$m $period';
    }
    return t;
  }
}

class _InfoBanner extends StatelessWidget {
  const _InfoBanner();

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(AppTheme.spaceM),
      decoration: BoxDecoration(
        color: AppTheme.primary.withValues(alpha: 0.06),
        borderRadius: BorderRadius.circular(AppTheme.radiusM),
        border: Border.all(
            color: AppTheme.primary.withValues(alpha: 0.2)),
      ),
      child: const Row(
        children: [
          Icon(Icons.info_outline, color: AppTheme.primary, size: 18),
          SizedBox(width: AppTheme.spaceS),
          Expanded(
            child: Text(
              'To change scheduling settings, contact your system administrator.',
              style: TextStyle(
                  fontSize: 12, color: AppTheme.textSecondary),
            ),
          ),
        ],
      ),
    );
  }
}
