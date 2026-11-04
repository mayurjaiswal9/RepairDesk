import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:google_maps_flutter/google_maps_flutter.dart';
import 'package:intl/intl.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../../core/error/app_exception.dart';
import '../../../core/location/location_models.dart';
import '../../../core/location/maps_repository.dart';
import '../../../core/location/service_location.dart';
import '../../../shared/theme/app_theme.dart';

final locationBoardProvider = FutureProvider.autoDispose<LocationBoard>(
  (ref) => ref.watch(mapsRepositoryProvider).board(),
);

/// Staff and Admin view of the office, technician positions, and job travel times.
class LocationBoardScreen extends ConsumerStatefulWidget {
  const LocationBoardScreen({super.key});

  @override
  ConsumerState<LocationBoardScreen> createState() => _LocationBoardScreenState();
}

class _LocationBoardScreenState extends ConsumerState<LocationBoardScreen> {
  String? _technicianId;

  @override
  Widget build(BuildContext context) {
    final boardAsync = ref.watch(locationBoardProvider);
    return Scaffold(
      appBar: AppBar(
        title: const Text('Locations'),
        actions: [
          IconButton(
            tooltip: 'Refresh',
            onPressed: () => ref.invalidate(locationBoardProvider),
            icon: const Icon(Icons.refresh),
          ),
        ],
      ),
      body: boardAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => _Message(
          text: error is AppException ? error.userMessage : error.toString(),
          onRetry: () => ref.invalidate(locationBoardProvider),
        ),
        data: (board) {
          final technicians = _technicianId == null
              ? board.technicians
              : board.technicians.where((tech) => tech.id == _technicianId).toList();
          final jobs = _technicianId == null
              ? board.jobs
              : board.jobs.where((job) => job.technicianId == _technicianId).toList();
          final selectedId = (_technicianId != null &&
                  board.technicians.any((tech) => tech.id == _technicianId))
              ? _technicianId
              : '';
          return RefreshIndicator(
            onRefresh: () => ref.refresh(locationBoardProvider.future),
            child: ListView(
              padding: const EdgeInsets.all(AppTheme.spaceM),
              children: [
                const Text(
                  'Office, open jobs, and the latest position each technician has shared. '
                  'Travel time uses the technician when a position is available, otherwise the office.',
                  style: TextStyle(color: AppTheme.textSecondary),
                ),
                const SizedBox(height: AppTheme.spaceM),
                InputDecorator(
                  decoration: const InputDecoration(
                    labelText: 'Technician',
                    border: OutlineInputBorder(),
                    isDense: true,
                  ),
                  child: DropdownButtonHideUnderline(
                    child: DropdownButton<String>(
                      isExpanded: true,
                      value: selectedId,
                      items: [
                        const DropdownMenuItem(value: '', child: Text('All technicians')),
                        ...board.technicians.map(
                          (tech) => DropdownMenuItem(value: tech.id, child: Text(tech.name)),
                        ),
                      ],
                      onChanged: (value) => setState(
                        () => _technicianId = (value == null || value.isEmpty) ? null : value,
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: AppTheme.spaceM),
                _BoardMap(board: board, technicians: technicians, jobs: jobs),
                const SizedBox(height: AppTheme.spaceS),
                const Text(
                  'O office · T technician · red pins are jobs',
                  style: TextStyle(fontSize: 12, color: AppTheme.textSecondary),
                ),
                const SizedBox(height: AppTheme.spaceL),
                Text('Technicians', style: Theme.of(context).textTheme.titleMedium),
                const SizedBox(height: AppTheme.spaceS),
                if (technicians.isEmpty)
                  const Text('No technicians yet.',
                      style: TextStyle(color: AppTheme.textSecondary))
                else
                  ...technicians.map(_techTile),
                const SizedBox(height: AppTheme.spaceL),
                Text('Jobs and ETA', style: Theme.of(context).textTheme.titleMedium),
                const SizedBox(height: AppTheme.spaceS),
                if (jobs.isEmpty)
                  const Text('No open job has a service pin yet.',
                      style: TextStyle(color: AppTheme.textSecondary))
                else
                  ...jobs.map(_jobTile),
              ],
            ),
          );
        },
      ),
    );
  }

  Widget _techTile(TechnicianMarker tech) {
    final when = tech.recordedAt == null
        ? null
        : DateFormat('d MMM yyyy, h:mm a').format(tech.recordedAt!.toLocal());
    final state = switch (tech.state) {
      'TRAVELLING' => 'Travelling',
      'ON_SITE' => 'On site',
      _ => null,
    };
    final lines = [
      if (!tech.hasPosition) 'No position shared yet',
      if (tech.hasPosition && state != null) state,
      if (tech.hasPosition && when != null) when,
    ];
    return ListTile(
      contentPadding: EdgeInsets.zero,
      title: Text(tech.name),
      subtitle: Text(
        lines.isEmpty ? 'Position shared' : lines.join('\n'),
      ),
    );
  }

  Widget _jobTile(JobMarker job) {
    return Card(
      margin: const EdgeInsets.only(bottom: AppTheme.spaceS),
      child: Padding(
        padding: const EdgeInsets.all(AppTheme.spaceM),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(job.referenceNumber,
                style: const TextStyle(fontWeight: FontWeight.w600)),
            if ((job.address ?? '').isNotEmpty)
              Text(job.address!, style: const TextStyle(color: AppTheme.textSecondary)),
            Text(job.technicianName?.isNotEmpty == true
                ? job.technicianName!
                : 'Unassigned'),
            const SizedBox(height: AppTheme.spaceXS),
            Text(etaLabel(
              etaMinutes: job.etaMinutes,
              distanceMetres: job.distanceMetres,
              estimated: job.etaEstimated,
              etaFrom: job.etaFrom,
            )),
            if (job.hasPin) ...[
              const SizedBox(height: AppTheme.spaceS),
              Align(
                alignment: Alignment.centerLeft,
                child: FilledButton.icon(
                  onPressed: () => _open(job.latitude!, job.longitude!),
                  icon: const Icon(Icons.navigation_outlined),
                  label: const Text('Start Navigating'),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Future<void> _open(double latitude, double longitude) async {
    final uri = ServiceLocation.directionsTo(latitude, longitude);
    final opened = await launchUrl(uri, mode: LaunchMode.externalApplication);
    if (!opened) await launchUrl(uri, mode: LaunchMode.platformDefault);
  }
}

class _BoardMap extends StatelessWidget {
  const _BoardMap({
    required this.board,
    required this.technicians,
    required this.jobs,
  });
  final LocationBoard board;
  final List<TechnicianMarker> technicians;
  final List<JobMarker> jobs;

  @override
  Widget build(BuildContext context) {
    final markers = <Marker>{};
    if (board.hasOffice) {
      markers.add(Marker(
        markerId: const MarkerId('office'),
        position: LatLng(board.shopLatitude!, board.shopLongitude!),
        infoWindow: const InfoWindow(title: 'Office'),
        icon: BitmapDescriptor.defaultMarkerWithHue(BitmapDescriptor.hueAzure),
      ));
    }
    for (final tech in technicians) {
      if (!tech.hasPosition) continue;
      markers.add(Marker(
        markerId: MarkerId('t-${tech.id}'),
        position: LatLng(tech.latitude!, tech.longitude!),
        infoWindow: InfoWindow(title: tech.name),
        icon: BitmapDescriptor.defaultMarkerWithHue(BitmapDescriptor.hueGreen),
      ));
    }
    for (final job in jobs) {
      if (!job.hasPin) continue;
      markers.add(Marker(
        markerId: MarkerId('j-${job.id}'),
        position: LatLng(job.latitude!, job.longitude!),
        infoWindow: InfoWindow(title: job.referenceNumber),
        icon: BitmapDescriptor.defaultMarkerWithHue(BitmapDescriptor.hueRed),
      ));
    }
    if (markers.isEmpty) {
      return const Text('No pins to show yet.',
          style: TextStyle(color: AppTheme.textSecondary));
    }
    final first = markers.first.position;
    return ClipRRect(
      borderRadius: BorderRadius.circular(12),
      child: SizedBox(
        height: 280,
        child: GoogleMap(
          initialCameraPosition: CameraPosition(target: first, zoom: 11),
          markers: markers,
          myLocationButtonEnabled: false,
          zoomControlsEnabled: false,
          onMapCreated: (controller) {
            if (markers.length < 2) return;
            final bounds = markers.fold<LatLngBounds?>(null, (box, marker) {
              final point = marker.position;
              if (box == null) {
                return LatLngBounds(southwest: point, northeast: point);
              }
              return LatLngBounds(
                southwest: LatLng(
                  point.latitude < box.southwest.latitude
                      ? point.latitude
                      : box.southwest.latitude,
                  point.longitude < box.southwest.longitude
                      ? point.longitude
                      : box.southwest.longitude,
                ),
                northeast: LatLng(
                  point.latitude > box.northeast.latitude
                      ? point.latitude
                      : box.northeast.latitude,
                  point.longitude > box.northeast.longitude
                      ? point.longitude
                      : box.northeast.longitude,
                ),
              );
            });
            if (bounds != null) {
              controller.moveCamera(CameraUpdate.newLatLngBounds(bounds, 48));
            }
          },
        ),
      ),
    );
  }
}

class _Message extends StatelessWidget {
  const _Message({required this.text, required this.onRetry});
  final String text;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(AppTheme.spaceL),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(text, textAlign: TextAlign.center),
            const SizedBox(height: AppTheme.spaceM),
            FilledButton(onPressed: onRetry, child: const Text('Retry')),
          ],
        ),
      ),
    );
  }
}
