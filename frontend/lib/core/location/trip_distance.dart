import 'dart:math' as math;

const _earthRadiusM = 6371008.8;

/// Great-circle distance in metres.
double haversineMetres(double lat1, double lng1, double lat2, double lng2) {
  double rad(double degrees) => degrees * math.pi / 180;
  final dLat = rad(lat2 - lat1);
  final dLng = rad(lng2 - lng1);
  final a = math.pow(math.sin(dLat / 2), 2) +
      math.cos(rad(lat1)) * math.cos(rad(lat2)) * math.pow(math.sin(dLng / 2), 2);
  return 2 * _earthRadiusM * math.asin(math.min(1, math.sqrt(a)));
}

/// Live km for the trip banner. Uses the same point filters as the server
/// (accuracy, 25 m jitter, 120 km/h jumps); the server's figure is the one paid.
class TripDistanceEstimator {
  TripDistanceEstimator({
    this.distanceM = 0,
    this.anchorLatitude,
    this.anchorLongitude,
    this.anchorAt,
  });

  static const maxAccuracyM = 50.0;
  static const minStepM = 25.0;
  static const maxSpeedMps = 120 / 3.6;

  double distanceM;
  double? anchorLatitude;
  double? anchorLongitude;
  DateTime? anchorAt;

  /// Returns true when the point counted (it became the new anchor).
  bool add({
    required double latitude,
    required double longitude,
    required DateTime at,
    double? accuracyM,
  }) {
    if (accuracyM != null && accuracyM > maxAccuracyM) return false;
    final lat = anchorLatitude;
    final lng = anchorLongitude;
    final when = anchorAt;
    if (lat == null || lng == null || when == null) {
      _anchor(latitude, longitude, at);
      return true;
    }
    final step = haversineMetres(lat, lng, latitude, longitude);
    if (step < minStepM) return false;
    final seconds = at.difference(when).inMilliseconds / 1000;
    if (seconds <= 0 || step / seconds > maxSpeedMps) return false;
    distanceM += step;
    _anchor(latitude, longitude, at);
    return true;
  }

  void _anchor(double latitude, double longitude, DateTime at) {
    anchorLatitude = latitude;
    anchorLongitude = longitude;
    anchorAt = at;
  }

  Map<String, dynamic> toJson() => {
        'distanceM': distanceM,
        'anchorLatitude': anchorLatitude,
        'anchorLongitude': anchorLongitude,
        'anchorAt': anchorAt?.toUtc().toIso8601String(),
      };

  factory TripDistanceEstimator.fromJson(Map<String, dynamic> json) =>
      TripDistanceEstimator(
        distanceM: (json['distanceM'] as num?)?.toDouble() ?? 0,
        anchorLatitude: (json['anchorLatitude'] as num?)?.toDouble(),
        anchorLongitude: (json['anchorLongitude'] as num?)?.toDouble(),
        anchorAt: DateTime.tryParse(json['anchorAt'] as String? ?? ''),
      );
}

/// Decides when to ask "You're at the customer — end trip?": inside [radiusM]
/// of the job pin and no real movement for [dwell].
///
/// The trip stream has a 25 m distance filter, so a parked phone sends no
/// readings at all. Silence inside the radius therefore counts as standing
/// still; only a reading faster than [stillSpeedMps] restarts the clock.
class ArrivalDetector {
  ArrivalDetector({
    required this.latitude,
    required this.longitude,
    this.radiusM = 150,
    this.dwell = const Duration(minutes: 2),
    this.stillSpeedMps = 1.0,
  });

  final double latitude;
  final double longitude;
  final double radiusM;
  final Duration dwell;
  final double stillSpeedMps;

  DateTime? _stillSince;

  bool get inside => _stillSince != null;

  void add({
    required double latitude,
    required double longitude,
    required DateTime at,
    double? speedMps,
  }) {
    final distance =
        haversineMetres(this.latitude, this.longitude, latitude, longitude);
    if (distance > radiusM) {
      _stillSince = null;
      return;
    }
    final moving = speedMps != null && speedMps > stillSpeedMps;
    if (_stillSince == null || moving) _stillSince = at;
  }

  bool arrived(DateTime now) {
    final since = _stillSince;
    return since != null && now.difference(since) >= dwell;
  }
}
