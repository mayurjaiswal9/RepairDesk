import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/error/app_exception.dart';
import '../../../core/location/location_service.dart';
import '../../../shared/theme/app_theme.dart';
import '../attendance_format.dart';
import '../data/attendance_repository.dart';

/// Start day / End day for a technician. Hidden while attendance is off.
/// Jobs stay on the home screen either way.
class DutyCard extends ConsumerStatefulWidget {
  const DutyCard({super.key});

  @override
  ConsumerState<DutyCard> createState() => _DutyCardState();
}

class _DutyCardState extends ConsumerState<DutyCard> {
  static const _notice =
      'Your start time, and whether you were at the shop, are stored.';

  MyAttendance? _day;
  bool _enabled = false;
  bool _ready = false;
  bool _dismissed = false;
  bool _busy = false;
  String? _message;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final repo = ref.read(attendanceRepositoryProvider);
    try {
      final config = await repo.config();
      if (!mounted) return;
      if (!config.attendanceEnabled) {
        setState(() {
          _enabled = false;
          _ready = true;
        });
        return;
      }
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _enabled = false;
        _ready = true;
      });
      return;
    }
    try {
      final day = await repo.today();
      if (!mounted) return;
      setState(() {
        _enabled = true;
        _day = day;
        _ready = true;
        _message = null;
      });
    } on AppException catch (error) {
      if (!mounted) return;
      setState(() {
        _enabled = true;
        _ready = true;
        _message = error.userMessage;
      });
    }
  }

  Future<void> _start() async {
    setState(() {
      _busy = true;
      _message = null;
    });
    try {
      final fix = await LocationService().bestFix(timeout: const Duration(seconds: 8));
      final day = await ref.read(attendanceRepositoryProvider).start(fix);
      if (!mounted) return;
      setState(() {
        _day = day;
        _dismissed = false;
      });
    } on AppException catch (error) {
      if (!mounted) return;
      setState(() => _message = error.userMessage);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _end() async {
    setState(() {
      _busy = true;
      _message = null;
    });
    try {
      final fix = await LocationService().bestFix(timeout: const Duration(seconds: 8));
      final day = await ref.read(attendanceRepositoryProvider).end(fix);
      if (!mounted) return;
      setState(() => _day = day);
    } on AppException catch (error) {
      if (!mounted) return;
      setState(() => _message = error.userMessage);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    if (!_ready || !_enabled) return const SizedBox.shrink();
    final day = _day;
    final started = day?.started == true;
    final open = day?.open == true;

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppTheme.spaceM),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Work day', style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: AppTheme.spaceS),
            if (day == null) ...[
              Text(_message ?? 'Work day could not be loaded.'),
              Align(
                alignment: Alignment.centerLeft,
                child: TextButton(onPressed: _busy ? null : _load, child: const Text('Try again')),
              ),
            ] else if (!started && !_dismissed) ...[
              const Text(_notice),
              const SizedBox(height: AppTheme.spaceM),
              Row(
                children: [
                  Expanded(
                    child: FilledButton(
                      onPressed: _busy ? null : _start,
                      child: Text(_busy ? 'Checking location…' : 'Start day'),
                    ),
                  ),
                  const SizedBox(width: AppTheme.spaceS),
                  Expanded(
                    child: OutlinedButton(
                      onPressed: _busy
                          ? null
                          : () => setState(() {
                                _dismissed = true;
                                _message = null;
                              }),
                      child: const Text('Not now'),
                    ),
                  ),
                ],
              ),
            ] else if (!started) ...[
              const Text('Work day not started'),
              const SizedBox(height: AppTheme.spaceS),
              Align(
                alignment: Alignment.centerLeft,
                child: TextButton(
                  onPressed: _busy ? null : () => setState(() => _dismissed = false),
                  child: const Text('Start day'),
                ),
              ),
            ] else if (open) ...[
              Text(day.startAt == null
                  ? 'On duty'
                  : 'On duty since ${indiaClock(day.startAt!)}'),
              if (attendanceFlag(day.startFlag).isNotEmpty)
                Text('Start flag: ${attendanceFlag(day.startFlag)}'),
              const SizedBox(height: AppTheme.spaceM),
              SizedBox(
                width: double.infinity,
                child: FilledButton(
                  onPressed: _busy ? null : _end,
                  child: Text(_busy ? 'Checking location…' : 'End day'),
                ),
              ),
            ] else ...[
              Text(day.endReason == 'AUTO' ? 'Auto-ended' : 'Ended'),
              if (day.startAt != null) Text('Started ${indiaClock(day.startAt!)}'),
              if (day.endAt != null) Text('Ended ${indiaClock(day.endAt!)}'),
              if (attendanceReason(day.endReason).isNotEmpty)
                Text(attendanceReason(day.endReason)),
            ],
            if (_message != null && day != null) ...[
              const SizedBox(height: AppTheme.spaceS),
              Text(_message!, style: const TextStyle(color: AppTheme.error)),
            ],
          ],
        ),
      ),
    );
  }
}
