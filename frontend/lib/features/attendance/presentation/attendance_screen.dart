import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/error/app_exception.dart';
import '../../../shared/theme/app_theme.dart';
import '../attendance_format.dart';
import '../data/attendance_repository.dart';

/// Staff and Admin work-day list. Technicians do not open this screen.
class AttendanceScreen extends ConsumerStatefulWidget {
  const AttendanceScreen({super.key});

  @override
  ConsumerState<AttendanceScreen> createState() => _AttendanceScreenState();
}

class _AttendanceScreenState extends ConsumerState<AttendanceScreen> {
  late DateTime _from;
  late DateTime _to;
  List<AttendanceRow> _rows = const [];
  bool _loading = true;
  bool _attendanceOn = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    final today = _indiaToday();
    _from = today;
    _to = today;
    _load();
  }

  DateTime _indiaToday() {
    final ist = DateTime.now().toUtc().add(const Duration(hours: 5, minutes: 30));
    return DateTime(ist.year, ist.month, ist.day);
  }

  String _iso(DateTime date) {
    final month = date.month.toString().padLeft(2, '0');
    final day = date.day.toString().padLeft(2, '0');
    return '${date.year}-$month-$day';
  }

  String _label(DateTime date) => workDateLabel(_iso(date));

  Future<void> _load() async {
    final span = inclusiveDaySpan(_from, _to);
    if (span < 0) {
      setState(() {
        _loading = false;
        _error = 'From date must be on or before the to date.';
        _rows = const [];
      });
      return;
    }
    if (span > 30) {
      setState(() {
        _loading = false;
        _error = 'Date range must be 31 days or less.';
        _rows = const [];
      });
      return;
    }
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final repo = ref.read(attendanceRepositoryProvider);
      final config = await repo.config();
      final rows = await repo.list(from: _iso(_from), to: _iso(_to));
      if (!mounted) return;
      setState(() {
        _attendanceOn = config.attendanceEnabled;
        _rows = rows;
        _loading = false;
      });
    } on AppException catch (error) {
      if (!mounted) return;
      setState(() {
        _error = error.userMessage;
        _loading = false;
      });
    }
  }

  Future<void> _pick(bool from) async {
    final current = from ? _from : _to;
    final picked = await showDatePicker(
      context: context,
      initialDate: current,
      firstDate: DateTime(2024),
      lastDate: DateTime(2100),
    );
    if (picked == null) return;
    setState(() {
      if (from) {
        _from = DateTime(picked.year, picked.month, picked.day);
      } else {
        _to = DateTime(picked.year, picked.month, picked.day);
      }
    });
    await _load();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Attendance')),
      body: RefreshIndicator(
        onRefresh: _load,
        child: ListView(
          padding: const EdgeInsets.all(AppTheme.spaceM),
          children: [
            const Text(
              'One row per technician per day. Times are India time.',
              style: TextStyle(color: AppTheme.textSecondary, fontSize: 13),
            ),
            if (!_attendanceOn) ...[
              const SizedBox(height: AppTheme.spaceM),
              const Text(
                'Attendance is off. Days already recorded stay in this list. An administrator turns it on from Office location.',
              ),
            ],
            const SizedBox(height: AppTheme.spaceM),
            Row(
              children: [
                Expanded(child: _DateButton(label: 'From ${_label(_from)}', onTap: () => _pick(true))),
                const SizedBox(width: AppTheme.spaceS),
                Expanded(child: _DateButton(label: 'To ${_label(_to)}', onTap: () => _pick(false))),
              ],
            ),
            const SizedBox(height: AppTheme.spaceM),
            if (_loading)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 32),
                child: Center(child: CircularProgressIndicator()),
              )
            else if (_error != null)
              Text(_error!, style: const TextStyle(color: AppTheme.error))
            else if (_rows.isEmpty)
              const Text('No active technicians.')
            else
              ..._rows.map(_rowCard),
          ],
        ),
      ),
    );
  }

  Widget _rowCard(AttendanceRow row) {
    final flag = attendanceFlag(row.startFlag);
    final reason = attendanceReason(row.endReason);
    final hours = attendanceHours(row.durationMinutes);
    final lines = [
      workDateLabel(row.workDate),
      attendanceStatus(status: row.status, startAt: row.startAt),
      if (flag.isNotEmpty) 'Start flag: $flag',
      if (row.endAt != null) 'End time: ${indiaClock(row.endAt!)}',
      if (reason.isNotEmpty) reason,
      if (hours.isNotEmpty) hours,
    ];
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppTheme.spaceM),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(row.technicianName, style: const TextStyle(fontWeight: FontWeight.w700)),
            const SizedBox(height: 4),
            for (final line in lines)
              Text(line, style: const TextStyle(color: AppTheme.textSecondary)),
          ],
        ),
      ),
    );
  }
}

class _DateButton extends StatelessWidget {
  const _DateButton({required this.label, required this.onTap});

  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return OutlinedButton(onPressed: onTap, child: Text(label));
  }
}
