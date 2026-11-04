/// Customer service address sent to and returned by the API.
class ServiceLocation {
  const ServiceLocation({
    this.addressLine,
    this.landmark,
    this.city,
    this.pincode,
    this.latitude,
    this.longitude,
    this.pinSource,
  });

  final String? addressLine;
  final String? landmark;
  final String? city;
  final String? pincode;
  final double? latitude;
  final double? longitude;

  /// `GEOCODED`, `MANUAL_PIN`, `CHECK_IN`, or `CUSTOMER_LINK`.
  final String? pinSource;

  bool get hasPin => latitude != null && longitude != null;

  /// Google Maps directions to a saved pin.
  static Uri directionsTo(double latitude, double longitude) {
    return Uri.https('www.google.com', '/maps/dir/', {
      'api': '1',
      'destination': '$latitude,$longitude',
      'travelmode': 'driving',
    });
  }

  /// Google Maps directions. A phone opens the Maps app; a browser opens a new tab.
  Uri? get directionsUri {
    if (hasPin) return directionsTo(latitude!, longitude!);
    final text = displayText;
    if (text.isEmpty) return null;
    return Uri.https('www.google.com', '/maps/dir/', {
      'api': '1',
      'destination': text,
      'travelmode': 'driving',
    });
  }

  bool get hasContent =>
      _filled(addressLine) ||
      _filled(landmark) ||
      _filled(city) ||
      _filled(pincode) ||
      hasPin;

  String get displayText {
    final parts = [addressLine, landmark, city, pincode]
        .whereType<String>()
        .map((s) => s.trim())
        .where((s) => s.isNotEmpty);
    return parts.join(', ');
  }

  Map<String, dynamic> toJson() => {
        if (_filled(addressLine)) 'addressLine': addressLine!.trim(),
        if (_filled(landmark)) 'landmark': landmark!.trim(),
        if (_filled(city)) 'city': city!.trim(),
        if (_filled(pincode)) 'pincode': pincode!.trim(),
        if (latitude != null) 'latitude': latitude,
        if (longitude != null) 'longitude': longitude,
        if (_filled(pinSource)) 'pinSource': pinSource,
      };

  static ServiceLocation? fromJson(Object? json) {
    if (json is! Map) return null;
    final map = Map<String, dynamic>.from(json);
    final location = ServiceLocation(
      addressLine: map['addressLine'] as String?,
      landmark: map['landmark'] as String?,
      city: map['city'] as String?,
      pincode: map['pincode'] as String?,
      latitude: (map['latitude'] as num?)?.toDouble(),
      longitude: (map['longitude'] as num?)?.toDouble(),
      pinSource: map['pinSource'] as String?,
    );
    return location.hasContent ? location : null;
  }

  static bool _filled(String? value) => value != null && value.trim().isNotEmpty;
}
