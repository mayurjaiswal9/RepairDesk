/// Office, technician positions, and travel time from `GET /location/board`.
class LocationBoard {
  const LocationBoard({
    required this.shopLatitude,
    required this.shopLongitude,
    required this.radiusM,
    required this.technicians,
    required this.jobs,
  });

  final double? shopLatitude;
  final double? shopLongitude;
  final int radiusM;
  final List<TechnicianMarker> technicians;
  final List<JobMarker> jobs;

  bool get hasOffice => shopLatitude != null && shopLongitude != null;

  factory LocationBoard.fromJson(Map<String, dynamic> json) {
    final shop = json['shop'] as Map<String, dynamic>? ?? const {};
    return LocationBoard(
      shopLatitude: (shop['latitude'] as num?)?.toDouble(),
      shopLongitude: (shop['longitude'] as num?)?.toDouble(),
      radiusM: (shop['radiusM'] as num?)?.toInt() ?? 0,
      technicians: (json['technicians'] as List<dynamic>? ?? const [])
          .cast<Map<String, dynamic>>()
          .map(TechnicianMarker.fromJson)
          .toList(),
      jobs: (json['jobs'] as List<dynamic>? ?? const [])
          .cast<Map<String, dynamic>>()
          .map(JobMarker.fromJson)
          .toList(),
    );
  }
}

class TechnicianMarker {
  const TechnicianMarker({
    required this.id,
    required this.name,
    this.latitude,
    this.longitude,
    this.recordedAt,
  });

  final String id;
  final String name;
  final double? latitude;
  final double? longitude;
  final DateTime? recordedAt;

  bool get hasPosition => latitude != null && longitude != null;

  factory TechnicianMarker.fromJson(Map<String, dynamic> json) {
    return TechnicianMarker(
      id: json['id'] as String,
      name: json['name'] as String? ?? 'Technician',
      latitude: (json['latitude'] as num?)?.toDouble(),
      longitude: (json['longitude'] as num?)?.toDouble(),
      recordedAt: DateTime.tryParse(json['recordedAt'] as String? ?? ''),
    );
  }
}

class JobMarker {
  const JobMarker({
    required this.id,
    required this.referenceNumber,
    this.address,
    this.latitude,
    this.longitude,
    this.technicianName,
    this.etaMinutes,
    this.distanceMetres,
    this.etaEstimated = false,
    this.etaFrom,
  });

  final String id;
  final String referenceNumber;
  final String? address;
  final double? latitude;
  final double? longitude;
  final String? technicianName;
  final int? etaMinutes;
  final int? distanceMetres;
  final bool etaEstimated;
  final String? etaFrom;

  bool get hasPin => latitude != null && longitude != null;

  factory JobMarker.fromJson(Map<String, dynamic> json) {
    return JobMarker(
      id: json['id'] as String,
      referenceNumber: json['referenceNumber'] as String? ?? '',
      address: json['address'] as String?,
      latitude: (json['latitude'] as num?)?.toDouble(),
      longitude: (json['longitude'] as num?)?.toDouble(),
      technicianName: json['technicianName'] as String?,
      etaMinutes: (json['etaMinutes'] as num?)?.toInt(),
      distanceMetres: (json['distanceMetres'] as num?)?.toInt(),
      etaEstimated: json['etaEstimated'] as bool? ?? false,
      etaFrom: json['etaFrom'] as String?,
    );
  }
}

/// Same wording as the portal Locations page.
String etaLabel({
  int? etaMinutes,
  int? distanceMetres,
  bool estimated = false,
  String? etaFrom,
}) {
  if (etaMinutes == null) return 'No ETA yet';
  final from = etaFrom == 'TECHNICIAN' ? 'from the technician' : 'from the office';
  final estimate = estimated ? 'estimated ' : '';
  final km = distanceMetres == null
      ? ''
      : ' · ${(distanceMetres / 1000).toStringAsFixed(1)} km';
  return '$estimate$etaMinutes min $from$km';
}

/// Admin office pin and the attendance switches. Rates are stored in paise.
class LocationSettings {
  const LocationSettings({
    this.shopLatitude,
    this.shopLongitude,
    required this.geofenceRadiusM,
    required this.checkInEnabled,
    required this.attendanceEnabled,
    required this.trackingEnabled,
    required this.travelEnabled,
    required this.etaEnabled,
    required this.pingIntervalMinutes,
    required this.pingDistanceM,
    required this.dayAutoEndTime,
    required this.allowStartOutsideGeofence,
    required this.farFromAddressM,
    required this.rateTwoWheelerPaise,
    required this.rateFourWheelerPaise,
    required this.retentionDays,
    required this.privacyNoticeVersion,
    this.strictTripMode = true,
    this.tripOpenMaps = true,
    this.travelPingSeconds = 120,
    this.onSitePingMinutes = 60,
  });

  final double? shopLatitude;
  final double? shopLongitude;
  final int geofenceRadiusM;
  final bool checkInEnabled;
  final bool attendanceEnabled;
  final bool trackingEnabled;
  final bool travelEnabled;
  final bool etaEnabled;
  final int pingIntervalMinutes;
  final int pingDistanceM;
  final String dayAutoEndTime;
  final bool allowStartOutsideGeofence;
  final int farFromAddressM;
  final int rateTwoWheelerPaise;
  final int rateFourWheelerPaise;
  final int retentionDays;
  final String privacyNoticeVersion;
  final bool strictTripMode;
  final bool tripOpenMaps;
  final int travelPingSeconds;
  final int onSitePingMinutes;

  factory LocationSettings.fromJson(Map<String, dynamic> json) {
    return LocationSettings(
      shopLatitude: (json['shopLatitude'] as num?)?.toDouble(),
      shopLongitude: (json['shopLongitude'] as num?)?.toDouble(),
      geofenceRadiusM: (json['geofenceRadiusM'] as num?)?.toInt() ?? 150,
      checkInEnabled: json['checkInEnabled'] as bool? ?? false,
      attendanceEnabled: json['attendanceEnabled'] as bool? ?? false,
      trackingEnabled: json['trackingEnabled'] as bool? ?? false,
      travelEnabled: json['travelEnabled'] as bool? ?? false,
      etaEnabled: json['etaEnabled'] as bool? ?? false,
      pingIntervalMinutes: (json['pingIntervalMinutes'] as num?)?.toInt() ?? 5,
      pingDistanceM: (json['pingDistanceM'] as num?)?.toInt() ?? 200,
      dayAutoEndTime: clockText(json['dayAutoEndTime']),
      allowStartOutsideGeofence: json['allowStartOutsideGeofence'] as bool? ?? false,
      farFromAddressM: (json['farFromAddressM'] as num?)?.toInt() ?? 300,
      rateTwoWheelerPaise: (json['rateTwoWheelerPaise'] as num?)?.toInt() ?? 500,
      rateFourWheelerPaise: (json['rateFourWheelerPaise'] as num?)?.toInt() ?? 1000,
      retentionDays: (json['retentionDays'] as num?)?.toInt() ?? 90,
      privacyNoticeVersion: json['privacyNoticeVersion'] as String? ?? '1',
      strictTripMode: json['strictTripMode'] as bool? ?? true,
      tripOpenMaps: json['tripOpenMaps'] as bool? ?? true,
      travelPingSeconds: (json['travelPingSeconds'] as num?)?.toInt() ?? 120,
      onSitePingMinutes: (json['onSitePingMinutes'] as num?)?.toInt() ?? 60,
    );
  }

  Map<String, dynamic> toJson() => {
        'shopLatitude': shopLatitude,
        'shopLongitude': shopLongitude,
        'geofenceRadiusM': geofenceRadiusM,
        'checkInEnabled': checkInEnabled,
        'attendanceEnabled': attendanceEnabled,
        'trackingEnabled': trackingEnabled,
        'travelEnabled': travelEnabled,
        'etaEnabled': etaEnabled,
        'pingIntervalMinutes': pingIntervalMinutes,
        'pingDistanceM': pingDistanceM,
        'dayAutoEndTime': dayAutoEndTime,
        'allowStartOutsideGeofence': allowStartOutsideGeofence,
        'farFromAddressM': farFromAddressM,
        'rateTwoWheelerPaise': rateTwoWheelerPaise,
        'rateFourWheelerPaise': rateFourWheelerPaise,
        'retentionDays': retentionDays,
        'privacyNoticeVersion': privacyNoticeVersion,
        'strictTripMode': strictTripMode,
        'tripOpenMaps': tripOpenMaps,
        'travelPingSeconds': travelPingSeconds,
        'onSitePingMinutes': onSitePingMinutes,
      };
}

/// `LocalTime` from the API arrives as "19:00:00" or `[19, 0]`. Returns "HH:mm".
String clockText(Object? value) {
  if (value is String && value.length >= 5) return value.substring(0, 5);
  if (value is List && value.length >= 2) {
    final hour = value[0].toString().padLeft(2, '0');
    final minute = value[1].toString().padLeft(2, '0');
    return '$hour:$minute';
  }
  return '19:00';
}

/// Feature switches from `GET /location/config` (any signed-in user). Missing
/// trip fields mean an older server, so they fall back to the agreed defaults.
class LocationConfig {
  const LocationConfig({
    this.trackingEnabled = false,
    this.travelEnabled = false,
    this.strictTripMode = true,
    this.tripOpenMaps = true,
    this.travelPingSeconds = 120,
    this.onSitePingMinutes = 60,
    this.dayAutoEndTime = '19:00',
    this.privacyNoticeVersion = '1',
  });

  final bool trackingEnabled;
  final bool travelEnabled;
  final bool strictTripMode;
  final bool tripOpenMaps;
  final int travelPingSeconds;
  final int onSitePingMinutes;
  final String dayAutoEndTime;
  final String privacyNoticeVersion;

  /// Strict mode only applies while trips are recorded at all.
  bool get tripRequiredToStart => travelEnabled && strictTripMode;

  factory LocationConfig.fromJson(Map<String, dynamic> json) {
    return LocationConfig(
      trackingEnabled: json['trackingEnabled'] == true,
      travelEnabled: json['travelEnabled'] == true,
      strictTripMode: json['strictTripMode'] as bool? ?? true,
      tripOpenMaps: json['tripOpenMaps'] as bool? ?? true,
      travelPingSeconds:
          ((json['travelPingSeconds'] as num?)?.toInt() ?? 120).clamp(30, 600),
      onSitePingMinutes:
          ((json['onSitePingMinutes'] as num?)?.toInt() ?? 60).clamp(15, 240),
      dayAutoEndTime: clockText(json['dayAutoEndTime']),
      privacyNoticeVersion: json['privacyNoticeVersion']?.toString() ?? '1',
    );
  }
}
