import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../shared/theme/app_theme.dart';
import '../data/admin_repository.dart';

class SettingsEditScreen extends ConsumerStatefulWidget {
  const SettingsEditScreen({super.key, required this.current});
  final SchedulingConfig current;

  @override
  ConsumerState<SettingsEditScreen> createState() =>
      _SettingsEditScreenState();
}

class _SettingsEditScreenState extends ConsumerState<SettingsEditScreen> {
  late int _slotDuration;
  late int _horizonDays;
  late List<_DayEdit> _days;
  bool _saving = false;

  @override
  void initState() {
    super.initState();
    _slotDuration = widget.current.slotDurationMinutes;
    _horizonDays = widget.current.bookingHorizonDays;
    _days = widget.current.workingHours.map((wh) {
      return _DayEdit(
        dayOfWeek: wh.dayOfWeek,
        dayName: wh.dayName,
        isClosed: wh.isClosed,
        openTime: _parseTime(wh.openTime),
        closeTime: _parseTime(wh.closeTime),
      );
    }).toList();
    if (_days.isEmpty) {
      // Backend `dayOfWeek` is 0=Monday .. 6=Sunday (see WorkingHours.java) —
      // this fallback (used when no scheduling config exists yet) previously
      // generated 1..7 and indexed `dayNames[i + 1]`, which both mismatched
      // the backend convention and (after fixing `dayNames` to 7 entries)
      // would have thrown a RangeError on index 7.
      _days = List.generate(
          7,
          (i) => _DayEdit(
              dayOfWeek: i,
              dayName: WorkingHoursConfig.dayNames[i],
              isClosed: i == 5 || i == 6));
    }
  }

  static TimeOfDay? _parseTime(String? t) {
    if (t == null) return null;
    final parts = t.split(':');
    if (parts.length < 2) return null;
    final h = int.tryParse(parts[0]);
    final m = int.tryParse(parts[1]);
    if (h == null || m == null) return null;
    return TimeOfDay(hour: h, minute: m);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Edit Business Settings'),
        actions: [
          TextButton(
            onPressed: _saving ? null : _save,
            child: _saving
                ? const SizedBox(
                    width: 20,
                    height: 20,
                    child: CircularProgressIndicator(
                        strokeWidth: 2, color: AppTheme.primary))
                : const Text('Save'),
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(AppTheme.spaceM),
        children: [
          // ── Scheduling ───────────────────────────────────────────────
          _SectionHeader(
              title: 'Scheduling',
              icon: Icons.calendar_today_outlined),
          const SizedBox(height: AppTheme.spaceS),
          DropdownButtonFormField<int>(
            // ignore: deprecated_member_use
            value: _slotDuration,
            decoration: const InputDecoration(
                labelText: 'Appointment slot duration',
                border: OutlineInputBorder()),
            items: const [
              DropdownMenuItem(value: 30, child: Text('30 minutes')),
              DropdownMenuItem(value: 60, child: Text('60 minutes')),
              DropdownMenuItem(value: 90, child: Text('90 minutes')),
              DropdownMenuItem(value: 120, child: Text('120 minutes')),
            ],
            onChanged: (v) => setState(() => _slotDuration = v ?? 60),
          ),
          const SizedBox(height: AppTheme.spaceM),
          TextFormField(
            initialValue: _horizonDays.toString(),
            decoration: const InputDecoration(
                labelText: 'Booking horizon (days ahead)',
                border: OutlineInputBorder()),
            keyboardType: TextInputType.number,
            onChanged: (v) {
              final n = int.tryParse(v);
              if (n != null && n > 0 && n <= 365) _horizonDays = n;
            },
          ),
          const SizedBox(height: AppTheme.spaceL),

          // ── Working Hours ─────────────────────────────────────────────
          _SectionHeader(
              title: 'Working Hours', icon: Icons.access_time_outlined),
          const SizedBox(height: AppTheme.spaceS),
          ..._days.map((day) => _DayRow(
                day: day,
                onChanged: () => setState(() {}),
              )),
        ],
      ),
    );
  }

  Future<void> _save() async {
    setState(() => _saving = true);
    try {
      final hours = _days
          .map((d) => {
                'dayOfWeek': d.dayOfWeek,
                'isClosed': d.isClosed,
                'openTime': d.isClosed
                    ? null
                    : d.openTime != null
                        ? '${d.openTime!.hour.toString().padLeft(2, '0')}:${d.openTime!.minute.toString().padLeft(2, '0')}:00'
                        : null,
                'closeTime': d.isClosed
                    ? null
                    : d.closeTime != null
                        ? '${d.closeTime!.hour.toString().padLeft(2, '0')}:${d.closeTime!.minute.toString().padLeft(2, '0')}:00'
                        : null,
              })
          .toList();

      await ref.read(adminRepositoryProvider).updateSettings(
            slotDurationMinutes: _slotDuration,
            bookingHorizonDays: _horizonDays,
          );
      await ref
          .read(adminRepositoryProvider)
          .updateWorkingHours(hours);

      if (mounted) {
        Navigator.of(context).pop(true);
        ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Settings saved')));
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(
            content: Text('Error: $e'), backgroundColor: AppTheme.error));
      }
    } finally {
      if (mounted) setState(() => _saving = false);
    }
  }
}

// ── Day editor row ─────────────────────────────────────────────────────────

class _DayEdit {
  final int dayOfWeek;
  final String dayName;
  bool isClosed;
  TimeOfDay? openTime;
  TimeOfDay? closeTime;

  _DayEdit({
    required this.dayOfWeek,
    required this.dayName,
    this.isClosed = false,
    this.openTime,
    this.closeTime,
  });
}

class _DayRow extends StatelessWidget {
  const _DayRow({required this.day, required this.onChanged});
  final _DayEdit day;
  final VoidCallback onChanged;

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.only(bottom: AppTheme.spaceS),
      child: Padding(
        padding: const EdgeInsets.symmetric(
            horizontal: AppTheme.spaceM, vertical: AppTheme.spaceS),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                    child: Text(day.dayName,
                        style:
                            const TextStyle(fontWeight: FontWeight.w600))),
                Switch(
                  value: !day.isClosed,
                  onChanged: (open) {
                    day.isClosed = !open;
                    if (open && day.openTime == null) {
                      day.openTime = const TimeOfDay(hour: 9, minute: 0);
                      day.closeTime = const TimeOfDay(hour: 18, minute: 0);
                    }
                    onChanged();
                  },
                ),
                Text(day.isClosed ? 'Closed' : 'Open',
                    style: TextStyle(
                        color: day.isClosed
                            ? AppTheme.textSecondary
                            : AppTheme.success,
                        fontSize: 12)),
              ],
            ),
            if (!day.isClosed) ...[
              const SizedBox(height: AppTheme.spaceS),
              Row(
                children: [
                  Expanded(
                    child: _TimePicker(
                      label: 'Open',
                      time: day.openTime,
                      onPicked: (t) {
                        day.openTime = t;
                        onChanged();
                      },
                    ),
                  ),
                  const SizedBox(width: AppTheme.spaceM),
                  Expanded(
                    child: _TimePicker(
                      label: 'Close',
                      time: day.closeTime,
                      onPicked: (t) {
                        day.closeTime = t;
                        onChanged();
                      },
                    ),
                  ),
                ],
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _TimePicker extends StatelessWidget {
  const _TimePicker(
      {required this.label,
      required this.time,
      required this.onPicked});
  final String label;
  final TimeOfDay? time;
  final void Function(TimeOfDay) onPicked;

  @override
  Widget build(BuildContext context) {
    final display = time != null ? time!.format(context) : '--:--';
    return InkWell(
      onTap: () async {
        final picked = await showTimePicker(
          context: context,
          initialTime: time ?? const TimeOfDay(hour: 9, minute: 0),
        );
        if (picked != null) onPicked(picked);
      },
      child: Container(
        padding: const EdgeInsets.symmetric(
            horizontal: AppTheme.spaceS, vertical: AppTheme.spaceXS),
        decoration: BoxDecoration(
          border: Border.all(color: AppTheme.divider),
          borderRadius: BorderRadius.circular(AppTheme.radiusS),
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(label,
                style: const TextStyle(
                    fontSize: 12, color: AppTheme.textSecondary)),
            Text(display,
                style: const TextStyle(fontWeight: FontWeight.w500)),
          ],
        ),
      ),
    );
  }
}

// ── Section header ─────────────────────────────────────────────────────────

class _SectionHeader extends StatelessWidget {
  const _SectionHeader({required this.title, required this.icon});
  final String title;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Icon(icon, color: AppTheme.primary, size: 18),
        const SizedBox(width: AppTheme.spaceS),
        Text(title,
            style: Theme.of(context)
                .textTheme
                .titleMedium
                ?.copyWith(color: AppTheme.primary)),
      ],
    );
  }
}
