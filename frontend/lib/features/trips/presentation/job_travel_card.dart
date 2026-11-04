import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../../core/error/app_exception.dart';
import '../../../core/location/location_models.dart';
import '../../../core/location/maps_repository.dart';
import '../../../core/location/service_location.dart';
import '../../../core/location/trip_models.dart';
import '../../../shared/theme/app_theme.dart';
import '../../jobs/data/jobs_repository.dart';
import '../data/trip_recorder.dart';

const _travelStatuses = {
  'ASSIGNED',
  'SCHEDULED',
  'IN_PROGRESS',
  'AWAITING_PARTS',
};

/// Start travel, the live trip banner, Arrived, and the arrival prompt.
class JobTravelCard extends ConsumerStatefulWidget {
  const JobTravelCard({super.key, required this.job});
  final JobDetail job;

  @override
  ConsumerState<JobTravelCard> createState() => _JobTravelCardState();
}

class _JobTravelCardState extends ConsumerState<JobTravelCard> {
  bool _promptedArrival = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      ref.read(tripRecorderProvider.notifier).restore();
    });
  }

  @override
  Widget build(BuildContext context) {
    final config = ref.watch(locationConfigProvider).value ?? const LocationConfig();
    if (!config.travelEnabled) return const SizedBox.shrink();
    if (!_travelStatuses.contains(widget.job.status)) {
      return const SizedBox.shrink();
    }

    final recorder = ref.watch(tripRecorderProvider);
    ref.listen<TripRecorderState>(tripRecorderProvider, (previous, next) {
      if (!next.isFor(widget.job.id) || !next.atCustomer) {
        _promptedArrival = false;
        return;
      }
      if (previous?.atCustomer == true || _promptedArrival) return;
      _promptedArrival = true;
      _askArrival();
    });

    if (recorder.isFor(widget.job.id)) {
      return _TripBanner(
        job: widget.job,
        recorder: recorder,
        onArrived: () => _end(TripEndReason.arrived),
        onCancel: _cancel,
        onResume: _resume,
      );
    }

    return Card(
      margin: const EdgeInsets.only(bottom: AppTheme.spaceM),
      child: Padding(
        padding: const EdgeInsets.all(AppTheme.spaceM),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Travel', style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: AppTheme.spaceXS),
            const Text(
              'Start travel before you leave. The app records the route so km can be paid.',
              style: TextStyle(color: AppTheme.textSecondary, fontSize: 13),
            ),
            const SizedBox(height: AppTheme.spaceS),
            FilledButton.icon(
              onPressed: recorder.busy ? null : () => _start(config),
              icon: recorder.busy
                  ? const SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.directions_car_outlined),
              label: Text(recorder.busy ? 'Starting…' : 'Start travel'),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _start(LocationConfig config) async {
    final recorder = ref.read(tripRecorderProvider);
    if (recorder.active && !recorder.isFor(widget.job.id)) {
      final ok = await showDialog<bool>(
        context: context,
        builder: (dlg) => AlertDialog(
          title: const Text('End the other trip?'),
          content: Text(
            'End the trip to ${recorder.trip?.jobReference ?? 'the other job'} '
            'and start this one?',
          ),
          actions: [
            TextButton(
                onPressed: () => Navigator.pop(dlg, false),
                child: const Text('Not now')),
            FilledButton(
                onPressed: () => Navigator.pop(dlg, true),
                child: const Text('Start this trip')),
          ],
        ),
      );
      if (ok != true || !mounted) return;
    }

    final pin = widget.job.serviceLocation;
    try {
      await ref.read(tripRecorderProvider.notifier).startTrip(
            jobId: widget.job.id,
            destination: pin?.hasPin == true
                ? LatLngPoint(pin!.latitude!, pin.longitude!)
                : null,
          );
      if (!mounted) return;
      if (config.tripOpenMaps && pin?.hasPin == true) {
        await _openMaps(pin!.latitude!, pin.longitude!);
      }
    } on LocationAccessException {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
        content: Text(
          'Allow location to record the trip. The job still opens without it.',
        ),
      ));
    } on AppException catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(error.userMessage), backgroundColor: AppTheme.error),
      );
    }
  }

  Future<void> _openMaps(double latitude, double longitude) async {
    final uri = ServiceLocation.directionsTo(latitude, longitude);
    final opened = await launchUrl(uri, mode: LaunchMode.externalApplication);
    if (!opened) await launchUrl(uri, mode: LaunchMode.platformDefault);
  }

  Future<void> _askArrival() async {
    if (!mounted) return;
    final ok = await showDialog<bool>(
      context: context,
      builder: (dlg) => AlertDialog(
        title: const Text("You're at the customer — end trip?"),
        content: const Text(
          'The phone has been near the job pin and still for two minutes.',
        ),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(dlg, false),
              child: const Text('Not yet')),
          FilledButton(
              onPressed: () => Navigator.pop(dlg, true),
              child: const Text('End trip')),
        ],
      ),
    );
    if (!mounted) return;
    if (ok == true) {
      await _end(TripEndReason.geofence);
    } else {
      ref.read(tripRecorderProvider.notifier).dismissArrival();
    }
  }

  Future<void> _end(TripEndReason reason) async {
    try {
      await ref.read(tripRecorderProvider.notifier).endTrip(reason);
      if (!mounted) return;
      ref.invalidate(jobTripsProvider(widget.job.id));
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(reason == TripEndReason.cancelled
              ? 'Trip cancelled. No km will be paid.'
              : 'Trip ended.'),
        ),
      );
    } on AppException catch (error) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(error.userMessage), backgroundColor: AppTheme.error),
      );
    }
  }

  Future<void> _cancel() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (dlg) => AlertDialog(
        title: const Text('Cancel this trip?'),
        content: const Text('The route will not be paid.'),
        actions: [
          TextButton(
              onPressed: () => Navigator.pop(dlg, false),
              child: const Text('Keep recording')),
          FilledButton(
              onPressed: () => Navigator.pop(dlg, true),
              child: const Text('Cancel trip')),
        ],
      ),
    );
    if (ok == true) await _end(TripEndReason.cancelled);
  }

  Future<void> _resume() async {
    try {
      await ref.read(tripRecorderProvider.notifier).resumeRecording();
    } on LocationAccessException {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
        content: Text('Turn location on, then tap Resume recording.'),
      ));
    }
  }
}

class _TripBanner extends StatelessWidget {
  const _TripBanner({
    required this.job,
    required this.recorder,
    required this.onArrived,
    required this.onCancel,
    required this.onResume,
  });

  final JobDetail job;
  final TripRecorderState recorder;
  final VoidCallback onArrived;
  final VoidCallback onCancel;
  final VoidCallback onResume;

  @override
  Widget build(BuildContext context) {
    return Card(
      margin: const EdgeInsets.only(bottom: AppTheme.spaceM),
      color: AppTheme.primary.withValues(alpha: 0.06),
      child: Padding(
        padding: const EdgeInsets.all(AppTheme.spaceM),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Trip to ${job.referenceNumber}',
                style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: AppTheme.spaceXS),
            Text(
              '${kmText(recorder.distanceM.round())} · '
              '${tripDurationText(recorder.elapsed(DateTime.now()))}',
              style: const TextStyle(fontWeight: FontWeight.w600),
            ),
            if (recorder.problem != null) ...[
              const SizedBox(height: AppTheme.spaceXS),
              Text(recorder.problem!,
                  style: const TextStyle(color: AppTheme.warning, fontSize: 13)),
              TextButton(onPressed: onResume, child: const Text('Resume recording')),
            ],
            const SizedBox(height: AppTheme.spaceS),
            FilledButton.icon(
              onPressed: recorder.busy ? null : onArrived,
              icon: const Icon(Icons.flag_outlined),
              label: const Text('Arrived'),
            ),
            TextButton(
              onPressed: recorder.busy ? null : onCancel,
              child: const Text('Cancel trip'),
            ),
          ],
        ),
      ),
    );
  }
}
