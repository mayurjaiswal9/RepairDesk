import 'package:flutter_test/flutter_test.dart';
import 'package:repairdesk/core/location/trip_models.dart';

void main() {
  test('hasTripBy only counts this technician active or ended trips', () {
    const trips = JobTrips(trips: [
      Trip(
        id: '1',
        jobId: 'j',
        jobReference: 'RD-1',
        technicianId: 'tech-a',
        status: 'ENDED',
      ),
      Trip(
        id: '2',
        jobId: 'j',
        jobReference: 'RD-1',
        technicianId: 'tech-b',
        status: 'CANCELLED',
      ),
    ]);
    expect(trips.hasTripBy('tech-a'), isTrue);
    expect(trips.hasTripBy('tech-b'), isFalse);
    expect(trips.hasTripBy(null), isTrue);
  });

  test('OTHER needs a typed note; the others do not', () {
    expect(NoTripReason.other.needsNote, isTrue);
    expect(NoTripReason.atShop.needsNote, isFalse);
  });

  test('indiaDate uses Asia/Kolkata', () {
    expect(indiaDate(DateTime.utc(2026, 11, 1, 18, 30)), '2026-11-02');
    expect(indiaDate(DateTime.utc(2026, 11, 1, 18, 29)), '2026-11-01');
  });
}
