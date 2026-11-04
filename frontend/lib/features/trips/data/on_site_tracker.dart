import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:geolocator/geolocator.dart';

import '../../../core/auth/roles.dart';
import '../../../core/auth/session_controller.dart';
import '../../../core/location/location_models.dart' show LocationConfig;
import '../../../core/location/location_service.dart';
import '../../../core/location/maps_repository.dart';
import '../../jobs/data/jobs_repository.dart';
import 'trip_recorder.dart';

final onSiteTrackerProvider =
    NotifierProvider<OnSiteTracker, OnSiteState>(OnSiteTracker.new);

class OnSiteState {
  const OnSiteState({this.jobId, this.jobReference, this.intervalMinutes = 30});

  final String? jobId;
  final String? jobReference;
  final int intervalMinutes;

  bool get active => jobId != null;
}

/// Time left until [clock] ("HH:mm", India time) today, or null once it has passed.
Duration? timeUntilIndiaClock(String clock, DateTime now) {
  final parts = clock.split(':');
  final hour = int.tryParse(parts.first) ?? 20;
  final minute = parts.length > 1 ? int.tryParse(parts[1]) ?? 0 : 0;
  final india = now.toUtc().add(const Duration(hours: 5, minutes: 30));
  final target = DateTime.utc(india.year, india.month, india.day, hour, minute);
  final left = target.difference(india);
  return left > Duration.zero ? left : null;
}

/// While a job is In progress (and no trip is recording), sends
/// `POST /me/location` with state `ON_SITE` every `onSitePingMinutes`, until
/// the job moves on, sign-out, or the day's auto end time.
class OnSiteTracker extends Notifier<OnSiteState> {
  StreamSubscription<Position>? _sub;
  Timer? _autoEnd;
  DateTime? _lastSent;
  Future<void>? _running;
  bool _again = false;

  @override
  OnSiteState build() {
    ref.listen(sessionControllerProvider, (previous, next) {
      if (previous?.token != null && next.token == null) unawaited(stop());
    });
    ref.onDispose(() {
      _sub?.cancel();
      _autoEnd?.cancel();
    });
    return const OnSiteState();
  }

  bool get _appInForeground {
    final lifecycle = WidgetsBinding.instance.lifecycleState;
    return lifecycle == null || lifecycle == AppLifecycleState.resumed;
  }

  /// Re-checks the switch, the technician's In progress job and the clock,
  /// then starts or stops. Safe to call after every job action.
  Future<void> refresh() async {
    final running = _running;
    if (running != null) {
      _again = true;
      return running;
    }
    final future = () async {
      do {
        _again = false;
        await _refreshOnce();
      } while (_again);
    }();
    _running = future;
    try {
      await future;
    } finally {
      _running = null;
    }
  }

  Future<void> _refreshOnce() async {
    final session = ref.read(sessionControllerProvider);
    final userId = session.userId;
    if (!session.isAuthenticated ||
        userId == null ||
        !Roles.isTechnician(session.role) ||
        ref.read(tripRecorderProvider).active) {
      await stop();
      return;
    }
    final LocationConfig config;
    try {
      config = await ref.read(mapsRepositoryProvider).locationConfig();
    } catch (_) {
      return;
    }
    if (!config.trackingEnabled ||
        (config.autoEndDay &&
            timeUntilIndiaClock(config.dayAutoEndTime, DateTime.now()) == null)) {
      await stop();
      return;
    }
    final List<JobSummary> jobs;
    try {
      jobs = await ref
          .read(jobsRepositoryProvider)
          .list(status: 'IN_PROGRESS', technicianId: userId);
    } catch (_) {
      return;
    }
    final mine = jobs
        .where((job) =>
            job.status == 'IN_PROGRESS' && job.assignedTechnicianId == userId)
        .toList()
      ..sort((a, b) => (b.updatedAt ?? '').compareTo(a.updatedAt ?? ''));
    if (mine.isEmpty ||
        ref.read(tripRecorderProvider).active ||
        !await LocationService().hasAccess()) {
      await stop();
      return;
    }
    await _start(mine.first, config);
  }

  Future<void> _start(JobSummary job, LocationConfig config) async {
    final minutes = config.onSitePingMinutes;
    if (_sub != null && state.jobId == job.id && state.intervalMinutes == minutes) {
      _scheduleAutoEnd(config);
      return;
    }
    await _closeStream();
    if (!_appInForeground) return;
    await LocationService.oneOffFixesDone();
    if (_sub != null || ref.read(tripRecorderProvider).active) return;

    LocationService.recorderStarted();
    _lastSent = null;
    state = OnSiteState(
      jobId: job.id,
      jobReference: job.referenceNumber,
      intervalMinutes: minutes,
    );
    _sub = Geolocator.getPositionStream(
      locationSettings: _settings(job.referenceNumber, minutes),
    ).listen(
      _onPosition,
      onError: (Object _) => unawaited(stop()),
      onDone: () {
        _sub = null;
        LocationService.recorderStopped();
        state = const OnSiteState();
      },
    );
    _scheduleAutoEnd(config);
  }

  LocationSettings _settings(String jobReference, int minutes) {
    if (defaultTargetPlatform != TargetPlatform.android) {
      return const LocationSettings(accuracy: LocationAccuracy.medium);
    }
    return AndroidSettings(
      accuracy: LocationAccuracy.medium,
      distanceFilter: 0,
      intervalDuration: Duration(minutes: minutes),
      foregroundNotificationConfig: ForegroundNotificationConfig(
        notificationTitle: 'On job $jobReference',
        notificationText: 'Sharing your location every $minutes min',
        notificationChannelName: locationNotificationChannel,
        notificationIcon:
            const AndroidResource(name: 'ic_stat_on_site', defType: 'drawable'),
        setOngoing: true,
      ),
    );
  }

  void _onPosition(Position position) {
    final jobId = state.jobId;
    if (jobId == null) return;
    final fix = LocationFix.fromPosition(position);
    LocationService.recorderFix(fix);
    final now = DateTime.now();
    final previous = _lastSent;
    final spacing =
        Duration(minutes: state.intervalMinutes) - const Duration(minutes: 1);
    if (previous != null && now.difference(previous) < spacing) return;
    _lastSent = now;
    unawaited(_send(jobId, fix, previous));
  }

  Future<void> _send(String jobId, LocationFix fix, DateTime? previous) async {
    try {
      await ref.read(mapsRepositoryProvider).sharePresence(
            latitude: fix.latitude,
            longitude: fix.longitude,
            accuracyM: fix.accuracyM,
            jobId: jobId,
            state: 'ON_SITE',
          );
    } catch (_) {
      _lastSent = previous;
    }
  }

  void _scheduleAutoEnd(LocationConfig config) {
    _autoEnd?.cancel();
    if (!config.autoEndDay) return;
    final left = timeUntilIndiaClock(config.dayAutoEndTime, DateTime.now());
    if (left == null) {
      unawaited(stop());
      return;
    }
    _autoEnd = Timer(left, () => unawaited(stop()));
  }

  Future<void> stop() async {
    _autoEnd?.cancel();
    _autoEnd = null;
    await _closeStream();
    if (state.active) state = const OnSiteState();
  }

  Future<void> _closeStream() async {
    final sub = _sub;
    _sub = null;
    if (sub != null) {
      await sub.cancel();
      LocationService.recorderStopped();
    }
  }
}
