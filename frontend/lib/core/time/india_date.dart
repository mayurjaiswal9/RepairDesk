// Shop calendar dates. The API treats these strings as India dates.

String indiaToday() {
  final ist = DateTime.now().toUtc().add(const Duration(hours: 5, minutes: 30));
  return _iso(ist.year, ist.month, ist.day);
}

String indiaYesterday() {
  final parts = indiaToday().split('-').map(int.parse).toList();
  final previous = DateTime.utc(parts[0], parts[1], parts[2])
      .subtract(const Duration(days: 1));
  return _iso(previous.year, previous.month, previous.day);
}

String formatIndiaDate(String iso) {
  final parts = iso.split('-');
  if (parts.length != 3) return iso;
  const months = [
    'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
    'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec',
  ];
  final month = int.tryParse(parts[1]);
  final day = int.tryParse(parts[2]);
  if (month == null || day == null || month < 1 || month > 12) return iso;
  return '$day ${months[month - 1]} ${parts[0]}';
}

DateTime indiaDateValue(String iso) {
  final parts = iso.split('-').map(int.parse).toList();
  return DateTime(parts[0], parts[1], parts[2]);
}

String isoFromDate(DateTime date) => _iso(date.year, date.month, date.day);

String _iso(int year, int month, int day) =>
    '${year.toString().padLeft(4, '0')}-'
    '${month.toString().padLeft(2, '0')}-'
    '${day.toString().padLeft(2, '0')}';

const openJobStatuses = 'CREATED,ASSIGNED,SCHEDULED,IN_PROGRESS,AWAITING_PARTS';
