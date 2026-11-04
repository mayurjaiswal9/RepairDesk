import 'package:flutter_test/flutter_test.dart';

import 'package:repairdesk/core/location/suggestion_schedule.dart';

void main() {
  test('waits for 5 letters, then at most one request every 5 seconds', () {
    final schedule = SuggestionSchedule();
    final start = DateTime.utc(2026, 10, 18, 12);

    expect(schedule.waitFor('Khai', start), isNull);

    expect(schedule.waitFor('Khair', start), Duration.zero);
    schedule.markSent('Khair', start);

    expect(
      schedule.waitFor('Khaira', start.add(const Duration(seconds: 2))),
      const Duration(seconds: 3),
    );
    expect(
      schedule.waitFor('Khairatabad', start.add(const Duration(seconds: 5))),
      Duration.zero,
    );
    expect(schedule.waitFor('Khair', start.add(const Duration(seconds: 9))), isNull);
  });
}
