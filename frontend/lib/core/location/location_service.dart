import 'dart:async';

import 'package:geolocator/geolocator.dart';

/// One GPS reading. [mocked] is true when Android reports a fake-location app.
class LocationFix {
  const LocationFix({
    required this.latitude,
    required this.longitude,
    required this.accuracyM,
    required this.capturedAt,
    required this.mocked,
    this.speedMps,
  });

  final double latitude;
  final double longitude;
  final double accuracyM;
  final DateTime capturedAt;
  final bool mocked;
  final double? speedMps;

  factory LocationFix.fromPosition(Position position) => LocationFix(
        latitude: position.latitude,
        longitude: position.longitude,
        accuracyM: position.accuracy,
        capturedAt: position.timestamp,
        mocked: position.isMocked,
        speedMps: position.speed >= 0 ? position.speed : null,
      );
}

/// Why a recorder could not get location access.
enum LocationAccess { granted, serviceOff, denied, deniedForever }

/// Picks the most accurate fix available within [timeout], and stops early
/// once accuracy is 20 metres or better. Returns null when location is off
/// or permission is denied. Never throws.
class LocationService {
  // geolocator keeps a single native position stream per app, and the first
  // subscriber's settings (including the foreground-service notification)
  // apply to everyone who subscribes after it. One-off fixes must therefore
  // not open the stream while a recorder needs it, and vice versa.
  static int _oneOffsInFlight = 0;
  static Completer<void>? _oneOffsDone;
  static bool _recorderRunning = false;
  static LocationFix? _recorderFix;

  /// Trip and on-site recorders call these around their own stream.
  static void recorderStarted() => _recorderRunning = true;
  static void recorderFix(LocationFix fix) => _recorderFix = fix;
  static void recorderStopped() {
    _recorderRunning = false;
    _recorderFix = null;
  }

  static LocationFix? get latestRecorderFix => _recorderFix;

  /// Lets a recorder wait for one-off fixes to finish before it subscribes.
  static Future<void> oneOffFixesDone({
    Duration limit = const Duration(seconds: 25),
  }) async {
    if (_oneOffsInFlight == 0) return;
    final done = _oneOffsDone ??= Completer<void>();
    await done.future.timeout(limit, onTimeout: () {});
  }

  Future<LocationFix?> bestFix({
    Duration timeout = const Duration(seconds: 20),
  }) async {
    if (_recorderRunning) return _fixBesideRecorder(timeout);
    _oneOffsInFlight++;
    try {
      return await _streamFix(timeout);
    } finally {
      _oneOffsInFlight--;
      if (_oneOffsInFlight == 0) {
        _oneOffsDone?.complete();
        _oneOffsDone = null;
      }
    }
  }

  Future<LocationFix?> _streamFix(Duration timeout) async {
    if (!await Geolocator.isLocationServiceEnabled()) return null;

    var permission = await Geolocator.checkPermission();
    if (permission == LocationPermission.denied) {
      permission = await Geolocator.requestPermission();
    }
    if (permission == LocationPermission.denied ||
        permission == LocationPermission.deniedForever) {
      return null;
    }

    final done = Completer<LocationFix?>();
    LocationFix? best;
    late final StreamSubscription<Position> sub;
    sub = Geolocator.getPositionStream(
      locationSettings: const LocationSettings(
        accuracy: LocationAccuracy.high,
        distanceFilter: 0,
      ),
    ).listen((position) {
      final fix = LocationFix.fromPosition(position);
      if (best == null || fix.accuracyM < best!.accuracyM) best = fix;
      if (best!.accuracyM <= 20 && !done.isCompleted) done.complete(best);
    }, onError: (_) {});

    final result = await done.future.timeout(timeout, onTimeout: () => best);
    await sub.cancel();
    return result;
  }

  /// While a recorder owns the stream: its own recent reading, or a single
  /// current-position request that does not touch the stream.
  Future<LocationFix?> _fixBesideRecorder(Duration timeout) async {
    final recent = _recorderFix;
    if (recent != null &&
        DateTime.now().difference(recent.capturedAt) < const Duration(minutes: 2)) {
      return recent;
    }
    try {
      final position = await Geolocator.getCurrentPosition(
        locationSettings: LocationSettings(
          accuracy: LocationAccuracy.high,
          timeLimit: timeout,
        ),
      );
      return LocationFix.fromPosition(position);
    } catch (_) {
      return recent;
    }
  }

  /// Asks for permission when it has not been decided yet.
  Future<LocationAccess> ensureAccess() async {
    try {
      if (!await Geolocator.isLocationServiceEnabled()) {
        return LocationAccess.serviceOff;
      }
      var permission = await Geolocator.checkPermission();
      if (permission == LocationPermission.denied) {
        permission = await Geolocator.requestPermission();
      }
      return switch (permission) {
        LocationPermission.denied => LocationAccess.denied,
        LocationPermission.deniedForever => LocationAccess.deniedForever,
        _ => LocationAccess.granted,
      };
    } catch (_) {
      return LocationAccess.denied;
    }
  }

  /// Without prompting: is location on and already allowed?
  Future<bool> hasAccess() async {
    try {
      if (!await Geolocator.isLocationServiceEnabled()) return false;
      final permission = await Geolocator.checkPermission();
      return permission == LocationPermission.whileInUse ||
          permission == LocationPermission.always;
    } catch (_) {
      return false;
    }
  }

  Future<bool> openSettings() => Geolocator.openAppSettings();

  Future<bool> openLocationSettings() => Geolocator.openLocationSettings();
}
