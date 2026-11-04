import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:geolocator/geolocator.dart';

import '../../../core/auth/roles.dart';
import '../../../core/auth/session_controller.dart';
import '../../../core/error/app_exception.dart';
import '../../../core/location/location_models.dart' show LocationConfig;
import '../../../core/location/location_service.dart';
import '../../../core/location/maps_repository.dart';
import '../../../core/location/notification_permission.dart';
import '../../../core/location/trip_distance.dart';
import '../../../core/location/trip_models.dart';
import '../../../core/location/trip_repository.dart';
import '../../../core/location/trip_store.dart';
import 'on_site_tracker.dart';

/// Shared by the location notifications of trips and on-site updates.
const locationNotificationChannel = 'Job trips and location';

final jobTripsProvider = FutureProvider.autoDispose.family<JobTrips, String>(
  (ref, jobId) => ref.watch(tripRepositoryProvider).forJob(jobId),
);

final tripRecorderProvider =
    NotifierProvider<TripRecorder, TripRecorderState>(TripRecorder.new);

/// Location is off or not allowed, so a trip cannot record.
class LocationAccessException implements Exception {
  const LocationAccessException(this.access);
  final LocationAccess access;
}

class TripRecorderState {
  const TripRecorderState({
    this.trip,
    this.distanceM = 0,
    this.pendingPoints = 0,
    this.recording = false,
    this.atCustomer = false,
    this.busy = false,
    this.problem,
  });

  /// The running trip, or null.
  final Trip? trip;

  /// Live estimate from the phone's own readings.
  final double distanceM;
  final int pendingPoints;

  /// The position stream (and its notification) is open.
  final bool recording;

  /// Within 150 m of the job pin and still for 2 minutes.
  final bool atCustomer;
  final bool busy;
  final String? problem;

  bool get active => trip != null;

  bool isFor(String jobId) => trip?.jobId == jobId;

  Duration elapsed(DateTime now) {
    final started = trip?.startedAt;
    if (started == null) return Duration.zero;
    final value = now.difference(started);
    return value.isNegative ? Duration.zero : value;
  }

  static const _keep = Object();

  TripRecorderState copyWith({
    Trip? trip,
    double? distanceM,
    int? pendingPoints,
    bool? recording,
    bool? atCustomer,
    bool? busy,
    Object? problem = _keep,
  }) {
    return TripRecorderState(
      trip: trip ?? this.trip,
      distanceM: distanceM ?? this.distanceM,
      pendingPoints: pendingPoints ?? this.pendingPoints,
      recording: recording ?? this.recording,
      atCustomer: atCustomer ?? this.atCustomer,
      busy: busy ?? this.busy,
      problem: identical(problem, _keep) ? this.problem : problem as String?,
    );
  }
}

/// Records a technician's drive to a job: a foreground location stream with a
/// notification, points queued on the phone, uploads every
/// `travelPingSeconds`, and the arrival prompt.
class TripRecorder extends Notifier<TripRecorderState> {
  TripStore? _store;
  LocationConfig _config = const LocationConfig();
  StreamSubscription<Position>? _sub;
  Timer? _uploadTimer;
  Timer? _arrivalTimer;
  TripDistanceEstimator _distance = TripDistanceEstimator();
  ArrivalDetector? _arrival;
  bool _arrivalDismissed = false;
  LocationFix? _lastFix;
  Future<void>? _flushing;
  Future<void>? _restoring;

  @override
  TripRecorderState build() {
    ref.listen(sessionControllerProvider, (previous, next) {
      if (previous?.token != null && next.token == null) {
        unawaited(_closeForSignOut());
      }
    });
    ref.onDispose(() {
      _sub?.cancel();
      _uploadTimer?.cancel();
      _arrivalTimer?.cancel();
    });
    return const TripRecorderState();
  }

  TripRepository get _trips => ref.read(tripRepositoryProvider);

  bool get _appInForeground {
    final lifecycle = WidgetsBinding.instance.lifecycleState;
    return lifecycle == null || lifecycle == AppLifecycleState.resumed;
  }

  Future<TripStore?> _storeForSession() async {
    final session = ref.read(sessionControllerProvider);
    final userId = session.userId;
    if (!session.isAuthenticated ||
        userId == null ||
        !Roles.isTechnician(session.role)) {
      return null;
    }
    if (_store?.userId != userId) _store = await TripStore.open(userId);
    return _store;
  }

  Future<void> _loadConfig() async {
    try {
      _config = await ref.read(mapsRepositoryProvider).locationConfig();
    } catch (_) {
      // Keep the last known intervals.
    }
  }

  /// App start, resume, and sign-in: picks up the running trip from the
  /// server (or the phone, when offline) and sends anything queued.
  Future<void> restore() {
    return _restoring ??= _restore().whenComplete(() => _restoring = null);
  }

  Future<void> _restore() async {
    final store = await _storeForSession();
    if (store == null) return;
    await _loadConfig();
    Trip? server;
    var reached = false;
    try {
      server = await _trips.active();
      reached = true;
    } catch (_) {
      // Offline: fall back to the trip saved on the phone.
    }
    if (reached) {
      if (server != null) {
        await _adopt(server);
      } else if (state.trip != null) {
        await _finishLocal();
      } else {
        await store.clearActive();
      }
    } else if (state.trip == null) {
      final cached = store.loadActive();
      if (cached != null) await _adopt(cached.trip);
    } else {
      await _openStream();
    }
    unawaited(flush());
  }

  Future<void> _adopt(Trip trip) async {
    final store = _store;
    if (store == null) return;
    if (state.trip?.id == trip.id) {
      state = state.copyWith(trip: trip);
      await _openStream();
      return;
    }
    if (state.trip != null) await _closeStream();
    final cached = store.loadActive();
    _distance = cached != null && cached.trip.id == trip.id
        ? cached.distance
        : TripDistanceEstimator(distanceM: trip.trackedDistanceM.toDouble());
    _begin(trip, trip.destination);
    await store.saveActive(trip, _distance);
    await ref.read(onSiteTrackerProvider.notifier).stop();
    await _openStream();
  }

  void _begin(Trip trip, LatLngPoint? pin) {
    _arrival = pin == null
        ? null
        : ArrivalDetector(latitude: pin.latitude, longitude: pin.longitude);
    _arrivalDismissed = false;
    _lastFix = null;
    state = TripRecorderState(
      trip: trip,
      distanceM: _distance.distanceM,
      pendingPoints: _store?.pending(trip.id) ?? 0,
    );
  }

  /// Start travel. Throws [LocationAccessException] when location is off or
  /// refused, and [AppException] when the server refuses the trip.
  Future<Trip> startTrip({
    required String jobId,
    LatLngPoint? destination,
  }) async {
    final store = await _storeForSession();
    if (store == null) {
      throw const AppException(
          errorCode: 'FORBIDDEN', message: 'Only technicians record trips.');
    }
    final access = await LocationService().ensureAccess();
    if (access != LocationAccess.granted) throw LocationAccessException(access);
    await NotificationPermission.request();

    final onSite = ref.read(onSiteTrackerProvider.notifier);
    state = state.copyWith(busy: true);
    try {
      await onSite.stop();
      await _loadConfig();
      final fix =
          await LocationService().bestFix(timeout: const Duration(seconds: 8));
      final previous = state.trip;
      final trip = await _trips.start(jobId, fix);
      if (previous?.id == trip.id) {
        state = state.copyWith(trip: trip, busy: false);
        await _openStream();
        return trip;
      }
      // The server ended the previous trip with REPLACED; its queued points still upload.
      if (previous != null) await _closeStream();
      _distance = TripDistanceEstimator();
      if (fix != null) {
        _distance.add(
          latitude: fix.latitude,
          longitude: fix.longitude,
          at: fix.capturedAt,
          accuracyM: fix.accuracyM,
        );
      }
      _begin(trip, trip.destination ?? destination);
      _lastFix = fix;
      await store.saveActive(trip, _distance);
      await _openStream();
      unawaited(flush());
      return trip;
    } catch (_) {
      state = state.copyWith(busy: false);
      if (state.trip == null) unawaited(onSite.refresh());
      rethrow;
    }
  }

  /// Banner button after location was turned off or the stream stopped.
  Future<void> resumeRecording() async {
    if (state.trip == null) return;
    final access = await LocationService().ensureAccess();
    if (access != LocationAccess.granted) throw LocationAccessException(access);
    await _openStream();
  }

  Future<void> _openStream() async {
    final trip = state.trip;
    if (trip == null || _sub != null) return;
    // Android only lets the app start a location foreground service while it
    // is on screen; resume calls restore() again.
    if (!_appInForeground) return;
    await LocationService.oneOffFixesDone();
    if (state.trip?.id != trip.id || _sub != null) return;

    LocationService.recorderStarted();
    final firstReading = Completer<void>();
    _sub = Geolocator.getPositionStream(
      locationSettings: _settings(trip.jobReference),
    ).listen(
      (position) {
        _onPosition(position);
        if (!firstReading.isCompleted) firstReading.complete();
      },
      onError: (Object error) {
        unawaited(_onStreamError(error));
        if (!firstReading.isCompleted) firstReading.complete();
      },
      onDone: () {
        _sub = null;
        LocationService.recorderStopped();
        if (state.trip != null) state = state.copyWith(recording: false);
      },
    );
    _restartUploadTimer();
    _arrivalTimer?.cancel();
    _arrivalTimer =
        Timer.periodic(const Duration(seconds: 15), (_) => _checkArrival());
    state = state.copyWith(recording: true, problem: null);
    // Opening Google Maps right away would background the app before the
    // foreground service is up, so wait briefly for the first reading.
    await firstReading.future
        .timeout(const Duration(seconds: 3), onTimeout: () {});
  }

  LocationSettings _settings(String jobReference) {
    if (defaultTargetPlatform != TargetPlatform.android) {
      return const LocationSettings(
          accuracy: LocationAccuracy.high, distanceFilter: 25);
    }
    return AndroidSettings(
      accuracy: LocationAccuracy.high,
      distanceFilter: 25,
      intervalDuration: const Duration(seconds: 10),
      foregroundNotificationConfig: ForegroundNotificationConfig(
        notificationTitle: 'Trip to $jobReference',
        notificationText: 'Recording your route',
        notificationChannelName: locationNotificationChannel,
        notificationIcon:
            const AndroidResource(name: 'ic_stat_trip', defType: 'drawable'),
        enableWakeLock: true,
        setOngoing: true,
      ),
    );
  }

  void _restartUploadTimer() {
    _uploadTimer?.cancel();
    _uploadTimer = Timer.periodic(
      Duration(seconds: _config.travelPingSeconds),
      (_) => unawaited(flush()),
    );
  }

  void _onPosition(Position position) {
    final trip = state.trip;
    if (trip == null || _store == null) return;
    final fix = LocationFix.fromPosition(position);
    _lastFix = fix;
    LocationService.recorderFix(fix);
    _distance.add(
      latitude: fix.latitude,
      longitude: fix.longitude,
      at: fix.capturedAt,
      accuracyM: fix.accuracyM,
    );
    final arrival = _arrival;
    if (arrival != null) {
      arrival.add(
        latitude: fix.latitude,
        longitude: fix.longitude,
        at: DateTime.now(),
        speedMps: fix.speedMps,
      );
      if (!arrival.inside) {
        _arrivalDismissed = false;
        if (state.atCustomer) state = state.copyWith(atCustomer: false);
      }
    }
    unawaited(_queue(trip, fix));
  }

  Future<void> _queue(Trip trip, LocationFix fix) async {
    final store = _store;
    if (store == null) return;
    await store.addPoint(
      trip.id,
      latitude: fix.latitude,
      longitude: fix.longitude,
      recordedAt: fix.capturedAt,
      accuracyM: fix.accuracyM,
      speedMps: fix.speedMps,
      mocked: fix.mocked,
    );
    if (state.trip?.id != trip.id) return;
    await store.saveActive(trip, _distance);
    state = state.copyWith(
      distanceM: _distance.distanceM,
      pendingPoints: store.pending(trip.id),
      recording: true,
      problem: null,
    );
  }

  Future<void> _onStreamError(Object error) async {
    await _closeStream();
    if (state.trip == null) return;
    final problem = switch (error) {
      LocationServiceDisabledException() =>
        'Location is off. Turn it on, then tap Resume recording.',
      PermissionDeniedException() =>
        'Location permission was removed. Allow it, then tap Resume recording.',
      _ => 'Recording stopped. Tap Resume recording.',
    };
    state = state.copyWith(recording: false, problem: problem);
  }

  void _checkArrival() {
    final arrival = _arrival;
    if (arrival == null ||
        state.trip == null ||
        state.atCustomer ||
        state.busy ||
        _arrivalDismissed) {
      return;
    }
    if (arrival.arrived(DateTime.now())) {
      state = state.copyWith(atCustomer: true);
    }
  }

  /// "Not yet" on the arrival prompt. Asks again only after leaving and re-entering.
  void dismissArrival() {
    _arrivalDismissed = true;
    if (state.atCustomer) state = state.copyWith(atCustomer: false);
  }

  /// Sends every queued point of this user, oldest first, 500 per request.
  /// A network failure stops the round; the next timer tick retries.
  Future<void> flush() async {
    final running = _flushing;
    if (running != null) return running;
    final future = _flushAll();
    _flushing = future;
    try {
      await future;
    } finally {
      _flushing = null;
    }
  }

  Future<void> _flushAll() async {
    final store = _store;
    if (store == null) return;
    for (final tripId in store.tripIds) {
      while (store.pending(tripId) > 0) {
        if (_store != store) return;
        final batch = store.nextBatch(tripId);
        try {
          await _trips.uploadPoints(tripId, batch);
          await store.removePoints(tripId, batch.map((p) => p.clientPointId));
        } on AppException catch (error) {
          if (!_rejected(error)) {
            _showPending();
            return;
          }
          // The trip no longer takes points (ended over 30 minutes ago, or gone).
          await store.dropTrip(tripId);
          if (state.trip?.id == tripId) unawaited(_recheckActive());
        } catch (_) {
          _showPending();
          return;
        }
      }
    }
    _showPending();
    if (state.trip == null && store.pending() == 0) {
      _uploadTimer?.cancel();
      _uploadTimer = null;
    }
  }

  bool _rejected(AppException error) {
    final status = error.statusCode;
    return status != null &&
        status >= 400 &&
        status < 500 &&
        status != 401 &&
        status != 408 &&
        status != 429;
  }

  void _showPending() {
    final trip = state.trip;
    if (trip == null) return;
    state = state.copyWith(pendingPoints: _store?.pending(trip.id) ?? 0);
  }

  Future<void> _recheckActive() async {
    try {
      final server = await _trips.active();
      if (server == null) {
        await _finishLocal();
      } else if (server.id != state.trip?.id) {
        await _adopt(server);
      }
    } catch (_) {
      // Try again on the next resume.
    }
  }

  /// Arrived, the arrival prompt, or Cancel trip.
  Future<Trip> endTrip(TripEndReason reason) async {
    final trip = state.trip;
    if (trip == null) {
      throw const AppException(
          errorCode: 'NOT_FOUND', message: 'No trip is recording.');
    }
    state = state.copyWith(busy: true, atCustomer: false);
    try {
      final cancelled = reason == TripEndReason.cancelled;
      if (!cancelled) {
        await flush().timeout(const Duration(seconds: 10), onTimeout: () {});
      }
      final ended = await _trips.end(trip.id, reason, cancelled ? null : _lastFix);
      await _finishLocal();
      if (cancelled) {
        await _store?.dropTrip(trip.id);
      } else {
        unawaited(flush());
      }
      return ended;
    } catch (_) {
      if (state.trip?.id == trip.id) state = state.copyWith(busy: false);
      rethrow;
    }
  }

  /// Before `POST /jobs/{id}/start`: send what was recorded for this job.
  Future<void> prepareJobStart(String jobId) async {
    if (!state.isFor(jobId)) return;
    await flush().timeout(const Duration(seconds: 8), onTimeout: () {});
  }

  /// After a successful Start job. The server already ended this job's trip.
  Future<void> jobStarted(String jobId) async {
    if (!state.isFor(jobId)) return;
    await _finishLocal();
    unawaited(flush());
  }

  /// Account → Sign out: one last upload while the token still works.
  Future<void> beforeSignOut() async {
    await flush().timeout(const Duration(seconds: 5), onTimeout: () {});
    await _closeForSignOut();
  }

  Future<void> _finishLocal() async {
    await _closeStream();
    _arrivalTimer?.cancel();
    _arrivalTimer = null;
    _arrival = null;
    _lastFix = null;
    await _store?.clearActive();
    state = const TripRecorderState();
    if ((_store?.pending() ?? 0) == 0) {
      _uploadTimer?.cancel();
      _uploadTimer = null;
    }
    unawaited(ref.read(onSiteTrackerProvider.notifier).refresh());
  }

  /// Stops recording but keeps the queue and the saved trip on the phone for
  /// this user's next sign-in.
  Future<void> _closeForSignOut() async {
    await _closeStream();
    _uploadTimer?.cancel();
    _uploadTimer = null;
    _arrivalTimer?.cancel();
    _arrivalTimer = null;
    _arrival = null;
    _store = null;
    state = const TripRecorderState();
  }

  Future<void> _closeStream() async {
    final sub = _sub;
    _sub = null;
    if (sub != null) {
      await sub.cancel();
      LocationService.recorderStopped();
    }
    if (state.recording) state = state.copyWith(recording: false);
  }
}
