import 'package:flutter/material.dart';
import 'package:google_maps_flutter/google_maps_flutter.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../shared/theme/app_theme.dart';
import 'service_location.dart';

/// Read-only address and pin, shown on request and job detail.
class ServiceLocationCard extends StatelessWidget {
  const ServiceLocationCard({super.key, required this.location});

  final ServiceLocation? location;

  @override
  Widget build(BuildContext context) {
    final location = this.location;
    if (location == null) return const SizedBox.shrink();
    final text = location.displayText;
    // Android platform-view maps draw above Flutter dialogs. Hide the map
    // while another route (confirm sheet / dialog) is on top.
    final mapOnTop = ModalRoute.of(context)?.isCurrent != true;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        if (location.hasPin) ...[
          ClipRRect(
            borderRadius: BorderRadius.circular(12),
            child: SizedBox(
              height: 180,
              child: mapOnTop
                  ? const ColoredBox(color: Color(0xFFE8EEF4))
                  : GoogleMap(
                      liteModeEnabled: true,
                      initialCameraPosition: CameraPosition(
                        target: LatLng(location.latitude!, location.longitude!),
                        zoom: 16,
                      ),
                      markers: {
                        Marker(
                          markerId: const MarkerId('pin'),
                          position:
                              LatLng(location.latitude!, location.longitude!),
                        ),
                      },
                      myLocationButtonEnabled: false,
                      zoomControlsEnabled: false,
                      scrollGesturesEnabled: false,
                      zoomGesturesEnabled: false,
                      rotateGesturesEnabled: false,
                      tiltGesturesEnabled: false,
                    ),
            ),
          ),
          const SizedBox(height: 8),
        ],
        if (text.isNotEmpty)
          Text(text, style: const TextStyle(color: AppTheme.textPrimary)),
        if (location.hasPin) ...[
          if (text.isNotEmpty) const SizedBox(height: 4),
          Text(
            location.pinSource == 'MANUAL_PIN'
                ? 'Pin placed on the map'
                : location.pinSource == 'CHECK_IN'
                    ? 'Pin saved from a technician check-in'
                    : location.pinSource == 'CUSTOMER_LINK'
                        ? 'Pin from the customer’s location link'
                        : 'Pin found from the address',
            style: const TextStyle(fontSize: 12, color: AppTheme.textSecondary),
          ),
        ],
        if (location.directionsUri != null) ...[
          const SizedBox(height: 12),
          Align(
            alignment: Alignment.centerLeft,
            child: FilledButton.icon(
              onPressed: () => _openDirections(location),
              icon: const Icon(Icons.navigation_outlined),
              label: const Text('Start Navigating'),
            ),
          ),
        ],
      ],
    );
  }

  Future<void> _openDirections(ServiceLocation location) async {
    final uri = location.directionsUri;
    if (uri == null) return;
    final opened = await launchUrl(uri, mode: LaunchMode.externalApplication);
    if (!opened) {
      await launchUrl(uri, mode: LaunchMode.platformDefault);
    }
  }
}
