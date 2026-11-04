import 'dart:async';

import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/auth/roles.dart';
import '../../../core/auth/session_controller.dart';
import '../../../core/location/location_models.dart' show LocationConfig;
import '../../../core/location/location_service.dart';
import '../../../core/location/maps_repository.dart';
import 'on_site_tracker.dart';
import 'trip_recorder.dart';

final presenceTrackerProvider =
    NotifierProvider<PresenceTracker, bool>(PresenceTracker.new);

/// When tracking is on and the technician is not travelling or on site,
/// share a position every `pingIntervalMinutes` (default 30).
class PresenceTracker extends Notifier<bool> {
  Timer? _timer;
  Future<void>? _running;
  bool _again = false;

  @override
  bool build() {
    ref.listen(sessionControllerProvider, (previous, next) {
      unawaited(refresh());
    });
    ref.listen(tripRecorderProvider, (previous, next) {
      unawaited(refresh());
    });
    ref.listen(onSiteTrackerProvider, (previous, next) {
      unawaited(refresh());
    });
    ref.onDispose(() => _timer?.cancel());
    return false;
  }

  bool get _appInForeground {
    final lifecycle = WidgetsBinding.instance.lifecycleState;
    return lifecycle == null || lifecycle == AppLifecycleState.resumed;
  }

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
    if (!session.isAuthenticated || !Roles.isTechnician(session.role)) {
      _stop();
      return;
    }
    if (ref.read(tripRecorderProvider).recording ||
        ref.read(onSiteTrackerProvider).active) {
      _stop();
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
      _stop();
      return;
    }
    if (!await LocationService().hasAccess()) {
      _stop();
      return;
    }
    final minutes = config.pingIntervalMinutes.clamp(1, 30);
    if (state && _timer != null) {
      unawaited(_ping());
      return;
    }
    _timer?.cancel();
    state = true;
    unawaited(_ping());
    _timer = Timer.periodic(Duration(minutes: minutes), (_) => unawaited(_ping()));
  }

  Future<void> _ping() async {
    if (!state || !_appInForeground) return;
    if (ref.read(tripRecorderProvider).recording ||
        ref.read(onSiteTrackerProvider).active) {
      _stop();
      return;
    }
    try {
      final fix = await LocationService().bestFix(timeout: const Duration(seconds: 8));
      if (fix == null || !state) return;
      await ref.read(mapsRepositoryProvider).sharePresence(
            latitude: fix.latitude,
            longitude: fix.longitude,
            accuracyM: fix.accuracyM,
          );
    } catch (_) {}
  }

  void _stop() {
    _timer?.cancel();
    _timer = null;
    if (state) state = false;
  }
}
