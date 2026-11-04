import 'package:flutter_test/flutter_test.dart';
import 'package:repairdesk/features/attendance/attendance_format.dart';

void main() {
  test('india clock and status labels', () {
    expect(indiaClock('2026-10-18T04:20:00Z'), '09:50');
    expect(workDateLabel('2026-10-18'), '18-10-2026');
    expect(
      attendanceStatus(status: 'ON_DUTY', startAt: '2026-10-18T04:20:00Z'),
      'On duty since 09:50',
    );
    expect(attendanceStatus(status: 'NOT_STARTED'), 'Not started');
    expect(attendanceStatus(status: 'AUTO_ENDED'), 'Auto-ended');
    expect(attendanceFlag('MISSING'), 'Location missing');
    expect(attendanceReason('MANUAL'), 'Manual');
    expect(attendanceReason('AUTO'), 'Auto');
    expect(attendanceHours(460), '7h 40m');
    expect(attendanceHours(null), '');
  });

  test('range is at most 31 days', () {
    final from = DateTime(2026, 10, 1);
    expect(inclusiveDaySpan(from, DateTime(2026, 10, 31)), 30);
    expect(inclusiveDaySpan(from, DateTime(2026, 11, 1)) > 30, isTrue);
    expect(inclusiveDaySpan(DateTime(2026, 10, 2), from) < 0, isTrue);
  });
}
