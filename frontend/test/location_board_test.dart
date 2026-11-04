import 'package:flutter_test/flutter_test.dart';
import 'package:repairdesk/core/location/location_models.dart';

void main() {
  test('travel line matches the portal wording', () {
    expect(etaLabel(etaMinutes: null), 'No ETA yet');
    expect(
      etaLabel(etaMinutes: 6, distanceMetres: 1300, etaFrom: 'TECHNICIAN'),
      '6 min from the technician · 1.3 km',
    );
    expect(
      etaLabel(etaMinutes: 12, distanceMetres: 4000, etaFrom: 'OFFICE', estimated: true),
      'estimated 12 min from the office · 4.0 km',
    );
  });

  test('location settings keep the clock and paise from the API', () {
    final settings = LocationSettings.fromJson({
      'shopLatitude': 19.1412127,
      'shopLongitude': 72.8309064,
      'geofenceRadiusM': 150,
      'checkInEnabled': false,
      'attendanceEnabled': false,
      'trackingEnabled': false,
      'travelEnabled': false,
      'etaEnabled': false,
      'pingIntervalMinutes': 5,
      'pingDistanceM': 200,
      'dayAutoEndTime': '19:00:00',
      'allowStartOutsideGeofence': false,
      'farFromAddressM': 300,
      'rateTwoWheelerPaise': 500,
      'rateFourWheelerPaise': 1000,
      'retentionDays': 90,
      'privacyNoticeVersion': '1',
    });

    expect(settings.dayAutoEndTime, '19:00');
    expect(settings.checkInEnabled, isFalse);
    expect(settings.toJson()['rateTwoWheelerPaise'], 500);
    expect(settings.toJson()['dayAutoEndTime'], '19:00');
  });
}
