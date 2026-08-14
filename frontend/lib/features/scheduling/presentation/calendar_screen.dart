import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../../core/auth/roles.dart';
import '../../../core/auth/session_controller.dart';
import '../../../shared/theme/app_theme.dart';
import '../data/scheduling_repository.dart';

final _calendarProvider = FutureProvider.autoDispose
    .family<List<AppointmentItem>, String>((ref, weekStart) {
  final end = DateTime.parse(weekStart).add(const Duration(days: 6));
  final endStr = DateFormat('yyyy-MM-dd').format(end);
  final session = ref.watch(sessionControllerProvider);
  final repo = ref.watch(schedulingRepositoryProvider);
  if (Roles.isTechnician(session.role)) {
    return repo.mySchedule(weekStart, endStr);
  }
  return repo.calendar(weekStart, endStr);
});

class CalendarScreen extends ConsumerStatefulWidget {
  const CalendarScreen({super.key});

  @override
  ConsumerState<CalendarScreen> createState() => _CalendarScreenState();
}

class _CalendarScreenState extends ConsumerState<CalendarScreen> {
  late DateTime _weekStart;

  @override
  void initState() {
    super.initState();
    final now = DateTime.now();
    // Monday of current week
    _weekStart = now.subtract(Duration(days: now.weekday - 1));
    _weekStart = DateTime(_weekStart.year, _weekStart.month, _weekStart.day);
  }

  String get _weekKey => DateFormat('yyyy-MM-dd').format(_weekStart);

  void _prevWeek() => setState(
      () => _weekStart = _weekStart.subtract(const Duration(days: 7)));
  void _nextWeek() =>
      setState(() => _weekStart = _weekStart.add(const Duration(days: 7)));

  @override
  Widget build(BuildContext context) {
    final appts = ref.watch(_calendarProvider(_weekKey));
    final weekEnd = _weekStart.add(const Duration(days: 6));
    final isStaff = ['STAFF', 'ADMIN']
        .contains(ref.watch(sessionControllerProvider).role);

    return Scaffold(
      appBar: AppBar(
        title: Text(
          '${DateFormat('MMM d').format(_weekStart)} – '
          '${DateFormat('MMM d, yyyy').format(weekEnd)}',
        ),
        leading: IconButton(
            icon: const Icon(Icons.chevron_left), onPressed: _prevWeek),
        actions: [
          IconButton(
              icon: const Icon(Icons.chevron_right), onPressed: _nextWeek),
        ],
      ),
      body: appts.when(
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
                  onPressed: () =>
                      ref.invalidate(_calendarProvider(_weekKey)),
                  child: const Text('Retry')),
            ],
          ),
        ),
        data: (items) => RefreshIndicator(
          onRefresh: () =>
              ref.refresh(_calendarProvider(_weekKey).future),
          child: items.isEmpty
              ? Center(
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      const Icon(Icons.calendar_today_outlined,
                          size: 56, color: AppTheme.textSecondary),
                      const SizedBox(height: AppTheme.spaceM),
                      const Text('No appointments this week',
                          style: TextStyle(color: AppTheme.textSecondary)),
                      if (isStaff) ...[
                        const SizedBox(height: AppTheme.spaceM),
                        const Text(
                          'Schedule appointments from the Jobs screen.',
                          style: TextStyle(
                              color: AppTheme.textSecondary, fontSize: 12),
                          textAlign: TextAlign.center,
                        ),
                      ],
                    ],
                  ),
                )
              : ListView(
                  padding: const EdgeInsets.all(AppTheme.spaceM),
                  children: _groupByDay(items)
                      .entries
                      .map((e) => _DaySection(
                            date: e.key,
                            appointments: e.value,
                            onRefresh: () =>
                                ref.invalidate(_calendarProvider(_weekKey)),
                          ))
                      .toList(),
                ),
        ),
      ),
    );
  }

  Map<String, List<AppointmentItem>> _groupByDay(
      List<AppointmentItem> items) {
    final map = <String, List<AppointmentItem>>{};
    for (final item in items) {
      map.putIfAbsent(item.scheduledDate, () => []).add(item);
    }
    return map;
  }
}

class _DaySection extends StatelessWidget {
  const _DaySection(
      {required this.date,
      required this.appointments,
      required this.onRefresh});
  final String date;
  final List<AppointmentItem> appointments;
  final VoidCallback onRefresh;

  @override
  Widget build(BuildContext context) {
    final dt = DateTime.tryParse(date);
    final label = dt != null
        ? DateFormat('EEEE, MMMM d').format(dt)
        : date;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.symmetric(vertical: AppTheme.spaceS),
          child: Text(label,
              style: Theme.of(context).textTheme.titleSmall?.copyWith(
                  color: AppTheme.primary, fontWeight: FontWeight.bold)),
        ),
        ...appointments.map((a) => _AppointmentCard(
              appt: a,
              onRefresh: onRefresh,
            )),
        const SizedBox(height: AppTheme.spaceM),
      ],
    );
  }
}

class _AppointmentCard extends StatelessWidget {
  const _AppointmentCard({required this.appt, required this.onRefresh});
  final AppointmentItem appt;
  final VoidCallback onRefresh;

  @override
  Widget build(BuildContext context) {
    final start = _fmtTime(appt.startTime);
    final end = _fmtTime(appt.endTime);

    return Card(
      margin: const EdgeInsets.only(bottom: AppTheme.spaceS),
      child: ListTile(
        leading: CircleAvatar(
          backgroundColor: AppTheme.primary.withValues(alpha: 0.1),
          child: Text(start.split(':')[0],
              style: const TextStyle(
                  color: AppTheme.primary,
                  fontSize: 12,
                  fontWeight: FontWeight.bold)),
        ),
        title: Text(appt.referenceNumber,
            style: const TextStyle(fontWeight: FontWeight.w600)),
        subtitle: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('$start – $end',
                style: const TextStyle(
                    color: AppTheme.textSecondary, fontSize: 12)),
            if (appt.technicianName != null)
              Text('Tech: ${appt.technicianName}',
                  style: const TextStyle(fontSize: 12)),
            if (appt.customerName != null)
              Text('Customer: ${appt.customerName}',
                  style: const TextStyle(fontSize: 12)),
            if (appt.deviceCategoryName != null)
              Text(appt.deviceCategoryName!,
                  style: const TextStyle(fontSize: 12)),
          ],
        ),
        trailing: _statusChip(appt.status),
      ),
    );
  }

  static String _fmtTime(String t) {
    final parts = t.split(':');
    if (parts.length < 2) return t;
    final h = int.tryParse(parts[0]) ?? 0;
    final m = parts[1];
    final period = h < 12 ? 'AM' : 'PM';
    final displayH = h == 0 ? 12 : (h > 12 ? h - 12 : h);
    return '$displayH:$m $period';
  }

  static Widget _statusChip(String status) {
    final color = status == 'SCHEDULED'
        ? AppTheme.primary
        : status == 'IN_PROGRESS'
            ? AppTheme.success
            : AppTheme.textSecondary;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: color.withValues(alpha: 0.3)),
      ),
      child: Text(status.replaceAll('_', ' '),
          style: TextStyle(
              fontSize: 10, color: color, fontWeight: FontWeight.w600)),
    );
  }
}
