import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';
import 'package:uuid/uuid.dart';

import 'trip_distance.dart';
import 'trip_models.dart';

/// Trip points waiting for upload, and the running trip, kept on the phone
/// per user. Survives app restarts and sign-out, so the same technician's
/// points are sent after the next sign-in.
class TripStore {
  TripStore(this._prefs, this.userId) : _points = _read(_prefs, userId);

  static const maxBatch = 500;
  static const _uuid = Uuid();

  final SharedPreferences _prefs;
  final String userId;
  final Map<String, List<TripPoint>> _points;

  static Future<TripStore> open(String userId) async =>
      TripStore(await SharedPreferences.getInstance(), userId);

  static String _pointsKey(String userId) => 'trip_points_v1:$userId';
  String get _activeKey => 'trip_active_v1:$userId';

  static Map<String, List<TripPoint>> _read(SharedPreferences prefs, String userId) {
    final raw = prefs.getString(_pointsKey(userId));
    if (raw == null || raw.isEmpty) return {};
    try {
      final decoded = jsonDecode(raw) as Map<String, dynamic>;
      return decoded.map((tripId, list) => MapEntry(
            tripId,
            (list as List<dynamic>)
                .whereType<Map<String, dynamic>>()
                .map(TripPoint.fromJson)
                .toList(),
          ));
    } catch (_) {
      return {};
    }
  }

  Future<void> _write() {
    _points.removeWhere((_, list) => list.isEmpty);
    if (_points.isEmpty) return _prefs.remove(_pointsKey(userId));
    return _prefs.setString(
      _pointsKey(userId),
      jsonEncode(_points.map(
          (tripId, list) => MapEntry(tripId, list.map((p) => p.toJson()).toList()))),
    );
  }

  /// Queues a reading with a fresh `clientPointId`, so a repeated upload is harmless.
  Future<TripPoint> addPoint(
    String tripId, {
    required double latitude,
    required double longitude,
    required DateTime recordedAt,
    double? accuracyM,
    double? speedMps,
    bool mocked = false,
  }) async {
    final point = TripPoint(
      clientPointId: _uuid.v4(),
      recordedAt: recordedAt,
      latitude: latitude,
      longitude: longitude,
      accuracyM: accuracyM,
      speedMps: speedMps,
      mocked: mocked,
    );
    _points.putIfAbsent(tripId, () => []).add(point);
    await _write();
    return point;
  }

  List<String> get tripIds =>
      _points.entries.where((e) => e.value.isNotEmpty).map((e) => e.key).toList();

  int pending([String? tripId]) => tripId == null
      ? _points.values.fold(0, (sum, list) => sum + list.length)
      : _points[tripId]?.length ?? 0;

  /// Oldest points first, at most [max] (the server takes 500 per request).
  List<TripPoint> nextBatch(String tripId, {int max = maxBatch}) {
    final list = [...?_points[tripId]]
      ..sort((a, b) => a.recordedAt.compareTo(b.recordedAt));
    return list.take(max).toList();
  }

  Future<void> removePoints(String tripId, Iterable<String> clientPointIds) async {
    final ids = clientPointIds.toSet();
    _points[tripId]?.removeWhere((point) => ids.contains(point.clientPointId));
    await _write();
  }

  Future<void> dropTrip(String tripId) async {
    _points.remove(tripId);
    await _write();
  }

  Future<void> saveActive(Trip trip, TripDistanceEstimator distance) =>
      _prefs.setString(
        _activeKey,
        jsonEncode({'trip': trip.toJson(), 'distance': distance.toJson()}),
      );

  ({Trip trip, TripDistanceEstimator distance})? loadActive() {
    final raw = _prefs.getString(_activeKey);
    if (raw == null || raw.isEmpty) return null;
    try {
      final decoded = jsonDecode(raw) as Map<String, dynamic>;
      return (
        trip: Trip.fromJson(decoded['trip'] as Map<String, dynamic>),
        distance: TripDistanceEstimator.fromJson(
            decoded['distance'] as Map<String, dynamic>? ?? const {}),
      );
    } catch (_) {
      return null;
    }
  }

  Future<void> clearActive() => _prefs.remove(_activeKey);
}
