import 'package:flutter_test/flutter_test.dart';

import 'package:repairdesk/core/location/service_location.dart';

void main() {
  test('toJson omits empty fields and keeps a pin', () {
    const location = ServiceLocation(
      addressLine: '  Lokhandwala ',
      city: 'Mumbai',
      pincode: '',
      latitude: 19.14,
      longitude: 72.82,
      pinSource: 'MANUAL_PIN',
    );

    expect(location.toJson(), {
      'addressLine': 'Lokhandwala',
      'city': 'Mumbai',
      'latitude': 19.14,
      'longitude': 72.82,
      'pinSource': 'MANUAL_PIN',
    });
    expect(location.displayText, 'Lokhandwala, Mumbai');
  });

  test('fromJson returns null when the server sent nothing', () {
    expect(ServiceLocation.fromJson(null), isNull);
    expect(ServiceLocation.fromJson(<String, dynamic>{}), isNull);
  });
}
