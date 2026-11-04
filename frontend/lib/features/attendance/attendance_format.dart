/// Labels and India-time formatting for attendance. India has no daylight saving.
String indiaClock(String iso) {
  final ist = DateTime.parse(iso).toUtc().add(const Duration(hours: 5, minutes: 30));
  final hour = ist.hour.toString().padLeft(2, '0');
  final minute = ist.minute.toString().padLeft(2, '0');
  return '$hour:$minute';
}

String workDateLabel(String isoDate) {
  final parts = isoDate.split('-');
  if (parts.length != 3) return isoDate;
  return '${parts[2]}-${parts[1]}-${parts[0]}';
}

String attendanceStatus({
  required String status,
  String? startAt,
}) {
  switch (status) {
    case 'ON_DUTY':
      return startAt == null ? 'On duty' : 'On duty since ${indiaClock(startAt)}';
    case 'ENDED':
      return 'Ended';
    case 'AUTO_ENDED':
      return 'Auto-ended';
    default:
      return 'Not started';
  }
}

String attendanceFlag(String? flag) {
  switch (flag) {
    case 'INSIDE':
      return 'Inside shop';
    case 'OUTSIDE':
      return 'Outside shop';
    case 'UNCERTAIN':
      return 'Location uncertain';
    case 'MISSING':
      return 'Location missing';
    case 'MOCKED':
      return 'Mocked location';
    default:
      return '';
  }
}

String attendanceReason(String? reason) {
  switch (reason) {
    case 'MANUAL':
      return 'Manual';
    case 'AUTO':
      return 'Auto';
    default:
      return '';
  }
}

String attendanceHours(int? minutes) {
  if (minutes == null || minutes < 0) return '';
  final hours = minutes ~/ 60;
  final rest = (minutes % 60).toString().padLeft(2, '0');
  return '${hours}h ${rest}m';
}

/// Inclusive day count. Negative when [from] is after [to].
int inclusiveDaySpan(DateTime from, DateTime to) {
  final start = DateTime(from.year, from.month, from.day);
  final end = DateTime(to.year, to.month, to.day);
  return end.difference(start).inDays;
}
